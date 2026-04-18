package io.github.ciaassured.yrush.game;

import io.github.ciaassured.yrush.YRushPlugin;
import io.github.ciaassured.yrush.config.YRushConfig;
import io.github.ciaassured.yrush.location.StartCategory;
import io.github.ciaassured.yrush.service.MessageService;
import io.github.ciaassured.yrush.service.PlayerStateService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the game at a high level: starts and stops rounds, handles auto mode scheduling,
 * and restores players who reconnect after their round ended while they were offline.
 */
public final class GameController implements Listener {
    private final YRushPlugin plugin;

    private Round currentRound;
    private boolean autoMode;
    private BukkitTask betweenRoundsTask;
    private StartCategory lastStartCategory;
    private RoundResult lastResult;

    // Original game modes for players who disconnected before cleanup could run.
    private final Map<UUID, GameMode> pendingRestores = new HashMap<>();

    public GameController(YRushPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Commands ─────────────────────────────────────────────────────────────

    public void start(CommandSender sender, boolean auto) {
        if (currentRound != null || betweenRoundsTask != null) {
            sender.sendMessage("YRush is already running.");
            return;
        }
        autoMode = auto;
        launchRound(sender);
    }

    public void stop(CommandSender sender) {
        if (currentRound == null && betweenRoundsTask == null) {
            sender.sendMessage("YRush is not currently running.");
            return;
        }
        autoMode = false;
        cancelBetweenRoundsTask();
        if (currentRound != null) {
            currentRound.close();
            pendingRestores.putAll(currentRound.drainOfflineRestores());
            currentRound = null;
            lastResult = RoundResult.stopped();
        }
        MessageService.broadcast("YRush stopped.");
    }

    public void shutdown() {
        autoMode = false;
        cancelBetweenRoundsTask();
        if (currentRound != null) {
            currentRound.close();
            currentRound = null;
        }
    }

    public void sendStatus(CommandSender sender) {
        if (currentRound != null) {
            currentRound.sendStatus(sender);
            sender.sendMessage("Auto mode: " + (autoMode ? "on" : "off"));
        } else if (betweenRoundsTask != null) {
            sender.sendMessage("YRush: between rounds. Auto mode: on");
        } else {
            sender.sendMessage("YRush: idle. Auto mode: " + (autoMode ? "on" : "off"));
            if (lastResult != null) sender.sendMessage("Last result: " + lastResult.type());
        }
    }

    public void setLobby(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can set the YRush lobby.");
            return;
        }
        Location loc = player.getLocation();
        FileConfiguration config = plugin.getConfig();
        config.set("lobby.world", loc.getWorld().getName());
        config.set("lobby.x",     loc.getX());
        config.set("lobby.y",     loc.getY());
        config.set("lobby.z",     loc.getZ());
        config.set("lobby.yaw",   loc.getYaw());
        config.set("lobby.pitch", loc.getPitch());
        plugin.saveConfig();
        sender.sendMessage("YRush lobby set.");
    }

    // ── Round lifecycle ───────────────────────────────────────────────────────

    private void launchRound(CommandSender initiator) {
        YRushConfig config = YRushConfig.from(plugin.getConfig());
        Location lobby = getLobbyLocation();
        List<Player> eligible = collectEligiblePlayers(lobby.getWorld());

        if (eligible.isEmpty()) {
            initiator.sendMessage("No eligible players found in the lobby world (must be in survival or adventure mode).");
            if (autoMode) scheduleNextRound();
            return;
        }

        currentRound = new Round(plugin, eligible, config, lobby, lastStartCategory, this::onRoundComplete);
    }

    private void onRoundComplete(RoundResult result, Map<UUID, GameMode> offlineRestores, StartCategory categoryUsed) {
        lastResult = result;
        lastStartCategory = categoryUsed;
        currentRound = null;
        pendingRestores.putAll(offlineRestores);

        if (autoMode) {
            scheduleNextRound();
        }
    }

    private void scheduleNextRound() {
        int delaySecs = YRushConfig.from(plugin.getConfig()).betweenRoundsSeconds();
        betweenRoundsTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            betweenRoundsTask = null;
            launchRound(Bukkit.getConsoleSender());
        }, delaySecs * 20L);
    }

    private void cancelBetweenRoundsTask() {
        if (betweenRoundsTask != null) {
            betweenRoundsTask.cancel();
            betweenRoundsTask = null;
        }
    }

    // ── Deferred player restore ───────────────────────────────────────────────

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        GameMode original = pendingRestores.remove(player.getUniqueId());
        if (original == null) return;
        // Defer one tick so join processing finishes before we teleport/restore.
        Bukkit.getScheduler().runTask(plugin,
            () -> PlayerStateService.restoreAfterRound(player, original, getLobbyLocation()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<Player> collectEligiblePlayers(World world) {
        return Bukkit.getOnlinePlayers().stream()
            .map(p -> (Player) p)
            .filter(p -> p.getWorld().equals(world))
            .filter(p -> p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)
            .toList();
    }

    public Location getLobbyLocation() {
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
        return Bukkit.getWorlds().getFirst().getSpawnLocation();
    }
}
