package io.github.ciaassured.yrush.game;

import io.github.ciaassured.yrush.YRushPlugin;
import io.github.ciaassured.yrush.config.YRushConfig;
import io.github.ciaassured.yrush.location.StartCategory;
import io.github.ciaassured.yrush.service.DebugService;
import io.github.ciaassured.yrush.service.MessageService;
import io.github.ciaassured.yrush.service.PlayerStateService;
import io.github.ciaassured.yrush.service.TrainingStatePacketService;
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
    private final DebugService debug;

    private Round currentRound;
    private boolean autoMode;
    private BukkitTask betweenRoundsTask;
    private StartCategory lastStartCategory;
    private RoundResult lastResult;
    private int nextRoundId = 1;

    // Original game modes for players who disconnected before cleanup could run.
    private final Map<UUID, GameMode> pendingRestores = new HashMap<>();

    public GameController(YRushPlugin plugin) {
        this.plugin = plugin;
        this.debug = new DebugService(plugin);
    }

    // ── Commands ─────────────────────────────────────────────────────────────

    public void start(CommandSender sender, boolean auto) {
        if (currentRound != null || betweenRoundsTask != null) {
            sender.sendMessage("YRush is already running.");
            return;
        }
        autoMode = auto;
        debug.log("Starting YRush. autoMode=" + auto);
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
            debug.log("Stopping active round.");
            currentRound.close();
            Map<UUID, GameMode> offlineRestores = currentRound.drainOfflineRestores();
            debug.log("Stop queued offline restores=" + offlineRestores.size());
            pendingRestores.putAll(offlineRestores);
            currentRound = null;
            lastResult = RoundResult.stopped();
        }
        MessageService.broadcast("YRush stopped.");
    }

    public void shutdown() {
        autoMode = false;
        cancelBetweenRoundsTask();
        if (currentRound != null) {
            debug.log("Plugin shutdown closing active round.");
            currentRound.close();
            currentRound = null;
        }
    }

    public void sendStatus(CommandSender sender) {
        sender.sendMessage("YRush version: " + plugin.getPluginMeta().getVersion());
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
            debug.log("Round launch skipped: no eligible players. autoMode=" + autoMode);
            if (autoMode) scheduleNextRound();
            return;
        }

        int roundId = nextRoundId++;
        debug.log("Launching round=" + roundId
            + " participants=" + eligible.size()
            + " autoMode=" + autoMode
            + " lastStartCategory=" + lastStartCategory);
        currentRound = new Round(plugin, roundId, eligible, config, lobby, lastStartCategory, debug, this::onRoundComplete);
    }

    private void onRoundComplete(RoundResult result, Map<UUID, GameMode> offlineRestores, StartCategory categoryUsed) {
        lastResult = result;
        lastStartCategory = categoryUsed;
        currentRound = null;
        pendingRestores.putAll(offlineRestores);
        debug.log("Round complete. result=" + result.type()
            + " winner=" + result.winnerId().map(UUID::toString).orElse("none")
            + " duration=" + result.duration()
            + " participants=" + result.participantCount()
            + " category=" + categoryUsed
            + " offlineRestores=" + offlineRestores.size());

        if (autoMode) {
            scheduleNextRound();
        }
    }

    private void scheduleNextRound() {
        int delaySecs = YRushConfig.from(plugin.getConfig()).betweenRoundsSeconds();
        debug.log("Scheduling next auto round in " + delaySecs + "s.");
        betweenRoundsTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            betweenRoundsTask = null;
            launchRound(Bukkit.getConsoleSender());
        }, delaySecs * 20L);
    }

    private void cancelBetweenRoundsTask() {
        if (betweenRoundsTask != null) {
            betweenRoundsTask.cancel();
            betweenRoundsTask = null;
            debug.log("Cancelled between-rounds task.");
        }
    }

    // ── Deferred player restore ───────────────────────────────────────────────

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        GameMode original = pendingRestores.remove(player.getUniqueId());
        if (original == null) return;
        debug.log("Restoring offline participant on join. player=" + player.getName());
        // Defer one tick so join processing finishes before we teleport/restore.
        Bukkit.getScheduler().runTask(plugin, () -> {
            PlayerStateService.restoreAfterRound(player, original, getLobbyLocation());
            TrainingStatePacketService.sendInactive(
                plugin,
                YRushConfig.from(plugin.getConfig()).trainingPacketsEnabled(),
                player,
                debug
            );
        });
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
