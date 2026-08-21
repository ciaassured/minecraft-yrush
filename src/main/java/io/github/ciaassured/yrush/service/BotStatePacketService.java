package io.github.ciaassured.yrush.service;

import com.google.gson.Gson;
import io.github.ciaassured.yrush.YRushPlugin;
import io.github.ciaassured.yrush.game.RoundContext;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;

public final class BotStatePacketService {
    public static final String CHANNEL = "yrush:bot_state";
    private static final int SCHEMA_VERSION = 1;
    private static final Gson GSON = new Gson();

    private BotStatePacketService() {}

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

        send(plugin, player, new BotStatePayload(
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

        send(plugin, player, new BotStatePayload(
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

    private static void send(YRushPlugin plugin, Player player, BotStatePayload payload, DebugService debug) {
        String json = GSON.toJson(payload);
        if (!player.getListeningPluginChannels().contains(CHANNEL)) {
            debug.log("Skipped bot packet. player=" + player.getName()
                + " channel=" + CHANNEL
                + " reason=client-not-listening"
                + " payload=" + json);
            return;
        }

        try {
            player.sendPluginMessage(plugin, CHANNEL, json.getBytes(StandardCharsets.UTF_8));
            debug.log("Sent bot packet. player=" + player.getName()
                + " channel=" + CHANNEL
                + " payload=" + json);
        } catch (RuntimeException ex) {
            debug.log("Could not send bot packet. player=" + player.getName()
                + " channel=" + CHANNEL
                + " error=" + ex.getMessage()
                + " payload=" + json);
        }
    }

    private record BotStatePayload(
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
