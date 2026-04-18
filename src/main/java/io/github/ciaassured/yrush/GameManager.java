package io.github.ciaassured.yrush;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class GameManager implements Listener {
    private static final int MAX_ROUND_PREPARATION_ATTEMPTS = 20;

    private final YRushPlugin plugin;
    private final Random random = new Random();
    private final MessageService messages = new MessageService();
    private final PlayerStateService playerStateService = new PlayerStateService();
    private final SafeLocationValidator safeLocationValidator = new SafeLocationValidator();
    private final StartLocationService startLocationService = new StartLocationService(random, safeLocationValidator);
    private final TargetYSelector targetYSelector = new TargetYSelector(random);

    private GameState state = GameState.IDLE;
    private boolean autoMode;
    private RoundContext context;
    private RoundResult lastResult;
    private int totalParticipants;

    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> activePlayers = new HashSet<>();
    private final Set<UUID> eliminatedPlayers = new HashSet<>();
    private final Map<UUID, GameMode> originalGameModes = new HashMap<>();

    private BukkitTask countdownTask;
    private BukkitTask actionBarTask;
    private BukkitTask winCheckTask;
    private BukkitTask timeoutTask;
    private BukkitTask betweenRoundsTask;

    public GameManager(YRushPlugin plugin) {
        this.plugin = plugin;
    }

    public void start(CommandSender sender, boolean auto) {
        if (state != GameState.IDLE) {
            sender.sendMessage("YRush is already running.");
            return;
        }

        autoMode = auto;
        beginCountdown(sender);
    }

    public void stop(CommandSender sender) {
        if (state == GameState.IDLE) {
            sender.sendMessage("YRush is not currently running.");
            return;
        }

        state = GameState.STOPPING;
        autoMode = false;
        cancelTasks();
        cleanupPlayers(getLobbyLocation());
        clearRoundState();
        state = GameState.IDLE;
        messages.broadcast("YRush stopped. Returning players to lobby.");
    }

    public void shutdown() {
        autoMode = false;
        cancelTasks();
        cleanupPlayers(getLobbyLocation());
        clearRoundState();
        state = GameState.IDLE;
    }

    public void sendStatus(CommandSender sender) {
        sender.sendMessage("YRush state: " + state);
        sender.sendMessage("Auto mode: " + (autoMode ? "on" : "off"));
        if (context != null) {
            sender.sendMessage("Target: " + context.direction().label() + " to Y " + context.targetY());
            sender.sendMessage("Active players: " + activePlayers.size() + "/" + totalParticipants);
        } else if (lastResult != null) {
            sender.sendMessage("Last result: " + lastResult.type());
        }
    }

    public void setLobby(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can set the YRush lobby.");
            return;
        }

        Location location = player.getLocation();
        FileConfiguration config = plugin.getConfig();
        config.set("lobby.world", location.getWorld().getName());
        config.set("lobby.x", location.getX());
        config.set("lobby.y", location.getY());
        config.set("lobby.z", location.getZ());
        config.set("lobby.yaw", location.getYaw());
        config.set("lobby.pitch", location.getPitch());
        plugin.saveConfig();

        sender.sendMessage("YRush lobby set.");
    }

    private void beginCountdown(CommandSender sender) {
        YRushConfig config = YRushConfig.from(plugin.getConfig());
        Location lobby = getLobbyLocation();
        List<Player> eligiblePlayers = collectEligiblePlayers(lobby.getWorld());

        if (eligiblePlayers.isEmpty()) {
            sender.sendMessage("No eligible players found. Players must be in survival or adventure mode in the lobby world.");
            state = GameState.IDLE;
            autoMode = false;
            return;
        }

        state = GameState.COUNTDOWN;
        participants.clear();
        activePlayers.clear();
        eliminatedPlayers.clear();
        totalParticipants = eligiblePlayers.size();

        for (Player player : eligiblePlayers) {
            participants.add(player.getUniqueId());
            activePlayers.add(player.getUniqueId());
            originalGameModes.putIfAbsent(player.getUniqueId(), player.getGameMode());
            player.setGameMode(GameMode.SURVIVAL);
            playerStateService.resetForRound(player);
            player.teleport(lobby);
        }

        Optional<RoundContext> preparedRound = prepareRound(lobby, config, eligiblePlayers.size());
        if (preparedRound.isEmpty()) {
            cleanupPlayers(lobby);
            clearRoundState();
            state = GameState.IDLE;
            autoMode = false;
            sender.sendMessage("Could not find a safe YRush start location. Try again or increase the search radius.");
            return;
        }

        context = preparedRound.get();
        runCountdown(config.countdownSeconds());
    }

    private Optional<RoundContext> prepareRound(Location lobby, YRushConfig config, int playerCount) {
        World world = lobby.getWorld();
        for (int attempt = 0; attempt < MAX_ROUND_PREPARATION_ATTEMPTS; attempt++) {
            Optional<StartLocation> start = startLocationService.findStart(world, lobby, config.startRadius(), playerCount);
            if (start.isEmpty()) {
                return Optional.empty();
            }

            int startY = start.get().center().getBlockY();
            OptionalInt targetY = targetYSelector.select(
                world,
                startY,
                config.targetMinimumDistance(),
                config.targetMaximumDistance(),
                start.get().category().isWater() ? TargetDirectionPreference.DOWN_ONLY : TargetDirectionPreference.ANY
            );
            if (targetY.isEmpty()) {
                continue;
            }

            RoundDirection direction = targetY.getAsInt() > startY ? RoundDirection.UP : RoundDirection.DOWN;
            startLocationService.remember(start.get());
            return Optional.of(new RoundContext(
                start.get().center(),
                start.get().playerPositions(),
                start.get().type(),
                start.get().category(),
                startY,
                targetY.getAsInt(),
                direction,
                config.timeoutSeconds(),
                null
            ));
        }

        return Optional.empty();
    }

    private void runCountdown(int countdownSeconds) {
        final int[] secondsRemaining = {countdownSeconds};
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != GameState.COUNTDOWN || context == null) {
                return;
            }

            List<Player> players = onlineParticipants();
            if (players.isEmpty()) {
                finishRound(RoundResultType.DRAW, null);
                return;
            }

            if (secondsRemaining[0] <= 0) {
                launchRound();
                return;
            }

            messages.countdown(players, secondsRemaining[0]);
            secondsRemaining[0]--;
        }, 0L, 20L);
    }

    private void launchRound() {
        cancelTask(countdownTask);
        countdownTask = null;

        if (context == null) {
            finishRound(RoundResultType.DRAW, null);
            return;
        }

        List<Player> players = onlineParticipants();
        if (players.isEmpty()) {
            finishRound(RoundResultType.DRAW, null);
            return;
        }

        List<Location> starts = context.playerStarts();
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            player.teleport(starts.get(i % starts.size()));
            if (context.startType() == StartType.UNDERGROUND) {
                playerStateService.giveUndergroundStartItems(player);
            }
        }

        context = new RoundContext(
            context.startCenter(),
            context.playerStarts(),
            context.startType(),
            context.startCategory(),
            context.startY(),
            context.targetY(),
            context.direction(),
            context.timeoutSeconds(),
            Instant.now()
        );

        state = GameState.ACTIVE;
        messages.roundStart(players, context);
        startActiveTasks();
    }

    private void startActiveTasks() {
        actionBarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != GameState.ACTIVE || context == null) {
                return;
            }
            for (Player player : onlineParticipants()) {
                messages.actionBar(player, context, activePlayers.size(), totalParticipants);
            }
        }, 0L, 20L);

        winCheckTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != GameState.ACTIVE || context == null) {
                return;
            }
            checkForWinner();
        }, 0L, 5L);

        timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state == GameState.ACTIVE) {
                finishRound(RoundResultType.DRAW, null);
            }
        }, Math.max(1L, context.timeoutSeconds()) * 20L);
    }

    private void checkForWinner() {
        if (context == null) {
            return;
        }

        for (UUID playerId : new ArrayList<>(activePlayers)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                activePlayers.remove(playerId);
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

    private void finishRound(RoundResultType resultType, Player winner) {
        if (state == GameState.IDLE || state == GameState.STOPPING) {
            return;
        }

        RoundContext finishedContext = context;
        int participantsAtFinish = totalParticipants;
        state = GameState.BETWEEN_ROUNDS;
        cancelActiveRoundTasks();

        if (resultType == RoundResultType.WIN && winner != null && finishedContext != null) {
            messages.broadcastWinner(winner, finishedContext.targetY());
        } else {
            messages.broadcastDraw();
        }

        cleanupPlayers(getLobbyLocation());
        clearRoundState();

        if (autoMode) {
            scheduleNextRound();
        } else {
            state = GameState.IDLE;
        }

        lastResult = new RoundResult(
            resultType,
            Optional.ofNullable(winner).map(Entity::getUniqueId),
            finishedContext == null ? 0 : finishedContext.targetY(),
            finishedContext == null || finishedContext.startedAt() == null
                ? Duration.ZERO
                : Duration.between(finishedContext.startedAt(), Instant.now()),
            participantsAtFinish
        );
    }

    private void scheduleNextRound() {
        int delay = YRushConfig.from(plugin.getConfig()).betweenRoundsSeconds();
        betweenRoundsTask = Bukkit.getScheduler().runTaskLater(plugin, () -> beginCountdown(Bukkit.getConsoleSender()), delay * 20L);
    }

    private void cleanupPlayers(Location lobby) {
        for (UUID playerId : new HashSet<>(participants)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }

            GameMode originalMode = originalGameModes.getOrDefault(playerId, GameMode.SURVIVAL);
            playerStateService.restoreAfterRound(player, originalMode, lobby);
            originalGameModes.remove(playerId);
        }
    }

    private void clearRoundState() {
        participants.clear();
        activePlayers.clear();
        eliminatedPlayers.clear();
        context = null;
        totalParticipants = 0;
    }

    private List<Player> collectEligiblePlayers(World world) {
        List<Player> eligiblePlayers = new ArrayList<>();
        Bukkit.getOnlinePlayers().stream()
            .filter(player -> player.getWorld().equals(world))
            .filter(player -> player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
            .forEach(eligiblePlayers::add);
        return eligiblePlayers;
    }

    private List<Player> onlineParticipants() {
        List<Player> players = new ArrayList<>();
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                players.add(player);
            }
        }
        return players;
    }

    private Location getLobbyLocation() {
        FileConfiguration config = plugin.getConfig();
        String worldName = config.getString("lobby.world");
        if (worldName != null) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                return new Location(
                    world,
                    config.getDouble("lobby.x"),
                    config.getDouble("lobby.y"),
                    config.getDouble("lobby.z"),
                    (float) config.getDouble("lobby.yaw"),
                    (float) config.getDouble("lobby.pitch")
                );
            }
        }

        World defaultWorld = Bukkit.getWorlds().getFirst();
        return defaultWorld.getSpawnLocation();
    }

    private void cancelTasks() {
        cancelTask(countdownTask);
        cancelTask(actionBarTask);
        cancelTask(winCheckTask);
        cancelTask(timeoutTask);
        cancelTask(betweenRoundsTask);
        countdownTask = null;
        actionBarTask = null;
        winCheckTask = null;
        timeoutTask = null;
        betweenRoundsTask = null;
    }

    private void cancelActiveRoundTasks() {
        cancelTask(actionBarTask);
        cancelTask(winCheckTask);
        cancelTask(timeoutTask);
        actionBarTask = null;
        winCheckTask = null;
        timeoutTask = null;
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    @EventHandler
    public void onPlayerFatalDamage(EntityDamageEvent event) {
        if (state != GameState.ACTIVE || !(event.getEntity() instanceof Player player)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        if (!activePlayers.contains(playerId)) {
            return;
        }

        if (player.getHealth() - event.getFinalDamage() > 0.0) {
            return;
        }

        event.setCancelled(true);
        eliminate(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (state == GameState.ACTIVE && activePlayers.remove(playerId)) {
            eliminatedPlayers.add(playerId);
            if (activePlayers.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> finishRound(RoundResultType.DRAW, null));
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (state == GameState.ACTIVE && eliminatedPlayers.contains(playerId) && context != null) {
            Bukkit.getScheduler().runTask(plugin, () -> playerStateService.eliminateToSpectator(player, context.startCenter()));
            return;
        }

        if (state == GameState.IDLE && originalGameModes.containsKey(playerId)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                GameMode originalMode = originalGameModes.remove(playerId);
                playerStateService.restoreAfterRound(player, originalMode, getLobbyLocation());
            });
        }
    }

    private void eliminate(Player player) {
        UUID playerId = player.getUniqueId();
        activePlayers.remove(playerId);
        eliminatedPlayers.add(playerId);

        if (context != null) {
            playerStateService.eliminateToSpectator(player, context.startCenter());
            player.sendMessage(messages.component("You are out until the next round.", NamedTextColor.RED));
        }

        if (activePlayers.isEmpty()) {
            finishRound(RoundResultType.DRAW, null);
        }
    }
}
