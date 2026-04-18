package io.github.ciaassured.yrush.game;

import io.github.ciaassured.yrush.YRushPlugin;
import io.github.ciaassured.yrush.config.YRushConfig;
import io.github.ciaassured.yrush.location.SafeLocationValidator;
import io.github.ciaassured.yrush.location.StartCategory;
import io.github.ciaassured.yrush.location.StartLocationService;
import io.github.ciaassured.yrush.location.TargetDirectionPreference;
import io.github.ciaassured.yrush.location.TargetYSelector;
import io.github.ciaassured.yrush.service.MessageService;
import io.github.ciaassured.yrush.service.PlayerStateService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Owns the entire lifecycle of a single round: preparation, countdown, active play, and cleanup.
 * Registers itself as a Bukkit listener on construction and unregisters on close.
 * All round state lives here; nothing leaks to GameController.
 *
 * <p>Implements {@link AutoCloseable}: calling {@link #close()} is always safe, idempotent,
 * and performs full cleanup. After closing, call {@link #drainOfflineRestores()} to retrieve
 * the original game modes for any players who were offline at cleanup time.
 */
public final class Round implements Listener, AutoCloseable {
    private static final int MAX_PREPARATION_ATTEMPTS = 20;
    private static final int PREPARATION_TIMEOUT_SECONDS = 60;

    private final YRushPlugin plugin;
    private final YRushConfig config;
    private final Location lobby;
    private final RoundCompletionHandler onComplete;
    private final StartLocationService startLocationService;
    private final TargetYSelector targetYSelector;

    // Participant state — all keys are the full participant set, never modified after construction.
    private final Set<UUID> participants;
    private final Set<UUID> activePlayers;
    private final Set<UUID> eliminatedPlayers = new HashSet<>();
    private final Map<UUID, GameMode> originalGameModes;
    private final int totalParticipants;

    // Set once preparation succeeds; immutable after that until launchRound stamps startedAt.
    private RoundContext context;

    // Task handles — owned entirely by this Round.
    private BukkitTask countdownTask;
    private BukkitTask actionBarTask;
    private BukkitTask winCheckTask;
    private BukkitTask timeoutTask;
    private BukkitTask preparationTimeoutTask;
    private CompletableFuture<?> preparationFuture;

    // Guards against double-close and stale async callbacks.
    private boolean disposed = false;

    // Populated by close(); drained by drainOfflineRestores().
    private Map<UUID, GameMode> offlineRestores;

    Round(
        YRushPlugin plugin,
        List<Player> players,
        YRushConfig config,
        Location lobby,
        StartCategory lastStartCategory,
        RoundCompletionHandler onComplete
    ) {
        this.plugin = plugin;
        this.config = config;
        this.lobby = lobby;
        this.onComplete = onComplete;

        Random random = new Random();
        this.startLocationService = new StartLocationService(random, new SafeLocationValidator(), lastStartCategory);
        this.targetYSelector = new TargetYSelector(random);

        Map<UUID, GameMode> modes = new HashMap<>();
        Set<UUID> ids = new LinkedHashSet<>();
        for (Player player : players) {
            UUID id = player.getUniqueId();
            ids.add(id);
            modes.put(id, player.getGameMode());
            player.setGameMode(GameMode.SURVIVAL);
        }
        this.participants       = Collections.unmodifiableSet(ids);
        this.activePlayers      = new HashSet<>(ids);
        this.originalGameModes  = modes;
        this.totalParticipants  = players.size();

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        MessageService.broadcast("YRush is starting! Searching for a start location...");
        startPreparation();
    }

    // ── External API ────────────────────────────────────────────────────────

    /**
     * Cancels this round and releases all resources: unregisters events, cancels tasks,
     * and restores all participants to their pre-game state. Idempotent — safe to call
     * multiple times or from any code path (stop command, plugin disable, or round end).
     *
     * <p>After calling close(), call {@link #drainOfflineRestores()} to retrieve original
     * game modes for players who were offline and could not be restored immediately.
     */
    @Override
    public void close() {
        if (disposed) return;
        disposed = true;

        HandlerList.unregisterAll(this);
        cancelAllTasks();
        offlineRestores = restorePlayers();
    }

    /**
     * Returns the offline-player restore map populated by the most recent {@link #close()} call,
     * then clears it. Returns an empty map if close() has not been called or was already drained.
     */
    Map<UUID, GameMode> drainOfflineRestores() {
        Map<UUID, GameMode> result = offlineRestores != null ? offlineRestores : Map.of();
        offlineRestores = null;
        return result;
    }

    void sendStatus(CommandSender sender) {
        if (context == null) {
            sender.sendMessage("YRush: preparing round (" + activePlayers.size() + " players)");
        } else if (!context.hasStarted()) {
            sender.sendMessage("YRush: countdown in progress");
        } else {
            sender.sendMessage("YRush: active — " + context.direction().label() + " to Y " + context.targetY());
            sender.sendMessage("Active players: " + activePlayers.size() + "/" + totalParticipants);
        }
    }

    // ── Preparation ─────────────────────────────────────────────────────────

    private void startPreparation() {
        CompletableFuture<Optional<RoundContext>> future = new CompletableFuture<>();
        preparationFuture = future;

        preparationTimeoutTask = Bukkit.getScheduler().runTaskLater(plugin,
            () -> future.complete(Optional.empty()),
            PREPARATION_TIMEOUT_SECONDS * 20L
        );

        future.whenComplete((result, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (disposed) return;
            cancelTask(preparationTimeoutTask);
            preparationTimeoutTask = null;
            preparationFuture = null;
            onPreparationComplete(result, ex);
        }));

        attemptPreparation(0, future);
    }

    private void attemptPreparation(int attempt, CompletableFuture<Optional<RoundContext>> result) {
        if (disposed || result.isDone()) return;

        if (attempt >= MAX_PREPARATION_ATTEMPTS) {
            result.complete(Optional.empty());
            return;
        }

        World world = lobby.getWorld();
        startLocationService.findStartAsync(world, lobby, config.startRadius(), totalParticipants)
            .whenComplete((start, ex) -> {
                if (disposed || result.isDone()) return;
                if (ex != null) { result.completeExceptionally(ex); return; }
                if (start.isEmpty()) { result.complete(Optional.empty()); return; }

                int startY = start.get().center().getBlockY();
                OptionalInt targetY = targetYSelector.select(
                    world, startY,
                    config.targetMinimumDistance(), config.targetMaximumDistance(),
                    start.get().category().isWater() ? TargetDirectionPreference.DOWN_ONLY : TargetDirectionPreference.ANY
                );

                if (targetY.isEmpty()) {
                    // Location found but world bounds can't accommodate the required Y distance — retry.
                    Bukkit.getScheduler().runTask(plugin, () -> attemptPreparation(attempt + 1, result));
                    return;
                }

                RoundDirection direction = targetY.getAsInt() > startY ? RoundDirection.UP : RoundDirection.DOWN;
                result.complete(Optional.of(new RoundContext(
                    start.get().center(),
                    start.get().playerPositions(),
                    start.get().type(),
                    start.get().category(),
                    startY,
                    targetY.getAsInt(),
                    direction,
                    config.timeoutSeconds(),
                    null
                )));
            });
    }

    private void onPreparationComplete(Optional<RoundContext> prepared, Throwable ex) {
        if (ex != null) {
            plugin.getLogger().warning("YRush round preparation failed: " + ex.getMessage());
        }

        if (prepared == null || prepared.isEmpty()) {
            MessageService.broadcast("YRush could not find a safe start location. Cancelling round.");
            finishRound(RoundResultType.STOPPED, null);
            return;
        }

        context = prepared.get();
        startCountdown();
    }

    // ── Countdown ───────────────────────────────────────────────────────────

    private void startCountdown() {
        final int[] secondsRemaining = {config.countdownSeconds()};
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (disposed) return;

            List<Player> players = onlineParticipants();
            if (players.isEmpty()) {
                finishRound(RoundResultType.DRAW, null);
                return;
            }

            if (secondsRemaining[0] <= 0) {
                launchRound(players);
                return;
            }

            MessageService.countdown(players, secondsRemaining[0]);
            secondsRemaining[0]--;
        }, 0L, 20L);
    }

    // ── Active round ─────────────────────────────────────────────────────────

    private void launchRound(List<Player> players) {
        cancelTask(countdownTask);
        countdownTask = null;

        List<Location> starts = context.playerStarts();
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            player.teleport(starts.get(i % starts.size()));
            PlayerStateService.resetForRound(player);
            if (shouldGiveNightVision()) {
                PlayerStateService.giveNightVision(player, config.timeoutSeconds());
            }
            if (context.startType() == io.github.ciaassured.yrush.location.StartType.UNDERGROUND) {
                PlayerStateService.giveUndergroundStartItems(player);
            }
        }

        context = context.withStartedAt(Instant.now());
        MessageService.roundStart(players, context);
        startActiveTasks();
    }

    private boolean shouldGiveNightVision() {
        World world = context.startCenter().getWorld();
        long time = world == null ? 0L : world.getTime();
        boolean isNight = time >= 12542L && time <= 23459L;
        return context.direction() == RoundDirection.DOWN
            || context.startType() == io.github.ciaassured.yrush.location.StartType.UNDERGROUND
            || isNight;
    }

    private void startActiveTasks() {
        actionBarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (disposed) return;
            for (Player player : onlineParticipants()) {
                MessageService.actionBar(player, context, activePlayers.size(), totalParticipants);
            }
        }, 0L, 20L);

        winCheckTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (disposed) return;
            checkForWinner();
        }, 0L, 5L);

        timeoutTask = Bukkit.getScheduler().runTaskLater(plugin,
            () -> { if (!disposed) finishRound(RoundResultType.DRAW, null); },
            Math.max(1L, context.timeoutSeconds()) * 20L
        );
    }

    private void checkForWinner() {
        for (UUID id : new ArrayList<>(activePlayers)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                // onPlayerQuit should have caught this; remove defensively without elimination tracking.
                activePlayers.remove(id);
                continue;
            }

            double y = player.getLocation().getY();
            boolean reached = context.direction() == RoundDirection.UP
                ? y >= context.targetY()
                : y <= context.targetY();

            if (reached) {
                finishRound(RoundResultType.WIN, player);
                return;
            }
        }

        if (activePlayers.isEmpty()) {
            finishRound(RoundResultType.DRAW, null);
        }
    }

    // ── Round completion ─────────────────────────────────────────────────────

    private void finishRound(RoundResultType resultType, Player winner) {
        if (disposed) return;

        StartCategory categoryUsed = context != null ? context.startCategory() : null;
        Duration duration = context == null || context.startedAt() == null
            ? Duration.ZERO
            : Duration.between(context.startedAt(), Instant.now());

        RoundResult result = new RoundResult(
            resultType,
            Optional.ofNullable(winner).map(Player::getUniqueId),
            context != null ? context.targetY() : 0,
            duration,
            totalParticipants
        );

        if (resultType == RoundResultType.WIN && winner != null && context != null) {
            MessageService.broadcastWinner(winner, context.targetY());
        } else if (resultType == RoundResultType.DRAW) {
            MessageService.broadcastDraw();
        }

        close();
        onComplete.onComplete(result, drainOfflineRestores(), categoryUsed);
    }

    private void eliminate(Player player) {
        UUID id = player.getUniqueId();
        activePlayers.remove(id);
        eliminatedPlayers.add(id);

        if (context != null) {
            PlayerStateService.eliminateToSpectator(player, context.startCenter());
        }
        MessageService.sendEliminated(player);

        if (activePlayers.isEmpty()) {
            finishRound(RoundResultType.DRAW, null);
        }
    }

    // ── Events ───────────────────────────────────────────────────────────────

    @EventHandler
    public void onPlayerFatalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!activePlayers.contains(player.getUniqueId())) return;
        if (player.getHealth() - event.getFinalDamage() > 0.0) return;

        event.setCancelled(true);
        eliminate(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!activePlayers.remove(id)) return;

        eliminatedPlayers.add(id);

        if (activePlayers.isEmpty()) {
            // Defer one tick so the quitting player is fully gone before dispose() checks isOnline().
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!disposed) finishRound(RoundResultType.DRAW, null);
            });
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        if (eliminatedPlayers.contains(id) && context != null) {
            // Reconnecting eliminated player — put them back in spectator.
            Bukkit.getScheduler().runTask(plugin,
                () -> PlayerStateService.eliminateToSpectator(player, context.startCenter()));
        }
    }

    // ── Cleanup helpers ──────────────────────────────────────────────────────

    /**
     * Restores all participants. Returns a map of original modes for those currently offline,
     * so the caller can restore them when they reconnect.
     */
    private Map<UUID, GameMode> restorePlayers() {
        Map<UUID, GameMode> offline = new HashMap<>();
        for (UUID id : participants) {
            GameMode original = originalGameModes.getOrDefault(id, GameMode.SURVIVAL);
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                PlayerStateService.restoreAfterRound(player, original, lobby);
            } else {
                offline.put(id, original);
            }
        }
        return offline;
    }

    private void cancelAllTasks() {
        cancelTask(countdownTask);
        cancelTask(actionBarTask);
        cancelTask(winCheckTask);
        cancelTask(timeoutTask);
        cancelTask(preparationTimeoutTask);
        if (preparationFuture != null) {
            preparationFuture.cancel(false);
        }
        countdownTask = actionBarTask = winCheckTask = timeoutTask = preparationTimeoutTask = null;
        preparationFuture = null;
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) task.cancel();
    }

    private List<Player> onlineParticipants() {
        List<Player> players = new ArrayList<>();
        for (UUID id : participants) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) players.add(player);
        }
        return players;
    }
}
