package io.github.ciaassured.yrush.service;

import com.google.gson.Gson;
import io.github.ciaassured.yrush.YRushPlugin;
import io.github.ciaassured.yrush.game.RoundContext;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TrainingStatePacketService {
    public static final String CHANNEL = "yrush:training_state";
    private static final int SCHEMA_VERSION = 1;
    private static final Gson GSON = new Gson();
    private static final Set<UUID> SUBSCRIBED_PLAYERS = ConcurrentHashMap.newKeySet();

    private TrainingStatePacketService() {}

    public static void sendRoundState(
        YRushPlugin plugin,
        boolean enabled,
        Player player,
        RoundContext context,
        String phase,
        boolean playerActive,
        int activePlayers,
        int totalPlayers,
        long secondsRemaining,
        DebugService debug
    ) {
        if (!enabled || !player.isOnline()) return;

        send(plugin, player, new TrainingStatePayload(
            SCHEMA_VERSION,
            true,
            playerActive,
            phase,
            context.direction().name(),
            context.targetY(),
            activePlayers,
            totalPlayers,
            Math.max(0L, secondsRemaining)
        ), debug);
    }

    public static void sendInactive(YRushPlugin plugin, boolean enabled, Player player, DebugService debug) {
        if (!enabled || !player.isOnline()) return;

        send(plugin, player, new TrainingStatePayload(
            SCHEMA_VERSION,
            false,
            false,
            "INACTIVE",
            null,
            null,
            null,
            null,
            null
        ), debug);
    }

    public static void receiveSubscriptionMessage(String channel, Player player, byte[] message, DebugService debug) {
        if (!CHANNEL.equals(channel)) return;

        String command = new String(message, StandardCharsets.UTF_8).trim();
        if (command.equalsIgnoreCase("unsubscribe")) {
            SUBSCRIBED_PLAYERS.remove(player.getUniqueId());
            debug.log("Training packet client unsubscribed. player=" + player.getName() + " channel=" + CHANNEL);
            return;
        }

        SUBSCRIBED_PLAYERS.add(player.getUniqueId());
        debug.log("Training packet client subscribed. player=" + player.getName()
            + " channel=" + CHANNEL
            + " message=" + command);
    }

    public static void forget(Player player) {
        SUBSCRIBED_PLAYERS.remove(player.getUniqueId());
    }

    private static void send(YRushPlugin plugin, Player player, TrainingStatePayload payload, DebugService debug) {
        String json = GSON.toJson(payload);
        if (!SUBSCRIBED_PLAYERS.contains(player.getUniqueId())) {
            debug.log("Skipped training packet. player=" + player.getName()
                + " channel=" + CHANNEL
                + " reason=client-not-subscribed"
                + " payload=" + json);
            return;
        }

        try {
            player.sendPluginMessage(plugin, CHANNEL, json.getBytes(StandardCharsets.UTF_8));
            debug.log("Sent training packet. player=" + player.getName()
                + " channel=" + CHANNEL
                + " payload=" + json);
        } catch (RuntimeException ex) {
            debug.log("Could not send training packet. player=" + player.getName()
                + " channel=" + CHANNEL
                + " error=" + ex.getMessage()
                + " payload=" + json);
        }
    }

    private record TrainingStatePayload(
        int schema_version,
        boolean round_active,
        boolean player_active,
        String phase,
        String direction,
        Integer target_y,
        Integer active_players,
        Integer total_players,
        Long seconds_remaining
    ) {}
}
