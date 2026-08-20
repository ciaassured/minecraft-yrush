package io.github.ciaassured.yrush.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ciaassured.yrush.YRushPlugin;
import io.github.ciaassured.yrush.game.RoundContext;
import io.github.ciaassured.yrush.game.RoundDirection;
import io.github.ciaassured.yrush.location.StartCategory;
import io.github.ciaassured.yrush.location.StartType;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrainingStatePacketServiceTest {
    private static final String ACTIVE_JSON = "{\"schema_version\":1,\"round_active\":true,"
        + "\"player_active\":true,\"phase\":\"ACTIVE\",\"direction\":\"DOWN\","
        + "\"target_y\":39,\"active_players\":3,\"total_players\":5,\"seconds_remaining\":482}";

    @Test
    void sendsRawUtf8JsonToListeningClient() {
        YRushPlugin plugin = mock(YRushPlugin.class);
        Player player = onlinePlayerListeningOn(TrainingStatePacketService.CHANNEL);
        DebugService debug = mock(DebugService.class);

        TrainingStatePacketService.sendRoundState(
            plugin, true, player, roundContext(), "ACTIVE", true, 3, 5, 482, debug
        );

        ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);
        verify(player).sendPluginMessage(eq(plugin), eq(TrainingStatePacketService.CHANNEL), payload.capture());
        assertArrayEquals(ACTIVE_JSON.getBytes(StandardCharsets.UTF_8), payload.getValue());

        JsonObject decoded = JsonParser.parseString(new String(payload.getValue(), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1, decoded.get("schema_version").getAsInt());
        assertEquals("ACTIVE", decoded.get("phase").getAsString());
        assertFalse(decoded.has("length"));
    }

    @Test
    void doesNotSendToClientThatIsNotListening() {
        YRushPlugin plugin = mock(YRushPlugin.class);
        Player player = onlinePlayerListeningOn("minecraft:brand");

        TrainingStatePacketService.sendRoundState(
            plugin, true, player, roundContext(), "ACTIVE", true, 3, 5, 482, mock(DebugService.class)
        );

        verify(player, never()).sendPluginMessage(any(), any(), any());
    }

    @Test
    void doesNotSendWhenPacketsAreDisabled() {
        YRushPlugin plugin = mock(YRushPlugin.class);
        Player player = onlinePlayerListeningOn(TrainingStatePacketService.CHANNEL);

        TrainingStatePacketService.sendRoundState(
            plugin, false, player, roundContext(), "ACTIVE", true, 3, 5, 482, mock(DebugService.class)
        );

        verify(player, never()).sendPluginMessage(any(), any(), any());
    }

    @Test
    void inactivePayloadIsRawUtf8Json() {
        YRushPlugin plugin = mock(YRushPlugin.class);
        Player player = onlinePlayerListeningOn(TrainingStatePacketService.CHANNEL);

        TrainingStatePacketService.sendInactive(plugin, true, player, mock(DebugService.class));

        ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);
        verify(player).sendPluginMessage(eq(plugin), eq(TrainingStatePacketService.CHANNEL), payload.capture());
        assertArrayEquals(
            "{\"schema_version\":1,\"round_active\":false,\"player_active\":false,\"phase\":\"INACTIVE\"}"
                .getBytes(StandardCharsets.UTF_8),
            payload.getValue()
        );
    }

    private Player onlinePlayerListeningOn(String channel) {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getListeningPluginChannels()).thenReturn(Set.of(channel));
        when(player.getName()).thenReturn("TrainingBot");
        return player;
    }

    private RoundContext roundContext() {
        return new RoundContext(
            null,
            List.of(),
            StartType.SURFACE,
            StartCategory.SURFACE_DRY,
            64,
            39,
            RoundDirection.DOWN,
            600,
            Instant.EPOCH
        );
    }
}
