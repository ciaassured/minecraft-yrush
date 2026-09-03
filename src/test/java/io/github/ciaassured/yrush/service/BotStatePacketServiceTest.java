package io.github.ciaassured.yrush.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ciaassured.yrush.YRushPlugin;
import io.github.ciaassured.yrush.game.RoundContext;
import io.github.ciaassured.yrush.game.RoundDirection;
import io.github.ciaassured.yrush.game.RoundResult;
import io.github.ciaassured.yrush.game.RoundResultType;
import io.github.ciaassured.yrush.location.StartCategory;
import io.github.ciaassured.yrush.location.StartType;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BotStatePacketServiceTest {
    private static final UUID WINNER_ID = UUID.fromString("12345678-1234-5678-9abc-123456789abc");
    private static final String ACTIVE_JSON = "{\"schema_version\":1,\"round_active\":true,"
        + "\"player_active\":true,\"phase\":\"ACTIVE\",\"direction\":\"DOWN\","
        + "\"target_y\":39,\"active_players\":3,\"total_players\":5,\"seconds_remaining\":482}";
    private static final String INACTIVE_JSON =
        "{\"schema_version\":1,\"round_active\":false,\"player_active\":false,\"phase\":\"INACTIVE\"}";

    @Test
    void sendsRawUtf8JsonToListeningBotClient() {
        YRushPlugin plugin = mock(YRushPlugin.class);
        Player player = onlineBotClientListeningOn(BotStatePacketService.CHANNEL);
        DebugService debug = mock(DebugService.class);

        BotStatePacketService.sendRoundState(
            plugin, true, player, roundContext(), "ACTIVE", true, 3, 5, 482, debug
        );

        ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);
        verify(player).sendPluginMessage(eq(plugin), eq(BotStatePacketService.CHANNEL), payload.capture());
        assertArrayEquals(ACTIVE_JSON.getBytes(StandardCharsets.UTF_8), payload.getValue());

        JsonObject decoded = JsonParser.parseString(new String(payload.getValue(), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1, decoded.get("schema_version").getAsInt());
        assertEquals("ACTIVE", decoded.get("phase").getAsString());
        assertFalse(decoded.has("length"));
    }

    @Test
    void doesNotSendToBotClientThatIsNotListening() {
        YRushPlugin plugin = mock(YRushPlugin.class);
        Player player = onlineBotClientListeningOn("minecraft:brand");

        BotStatePacketService.sendRoundState(
            plugin, true, player, roundContext(), "ACTIVE", true, 3, 5, 482, mock(DebugService.class)
        );
        BotStatePacketService.sendRoundCompleteAndInactive(
            plugin, true, player, winResult(), RoundResult.PlayerOutcome.LOST, mock(DebugService.class)
        );

        verify(player, never()).sendPluginMessage(any(), any(), any());
    }

    @Test
    void doesNotSendWhenBotPacketsAreDisabled() {
        YRushPlugin plugin = mock(YRushPlugin.class);
        Player player = onlineBotClientListeningOn(BotStatePacketService.CHANNEL);

        BotStatePacketService.sendRoundState(
            plugin, false, player, roundContext(), "ACTIVE", true, 3, 5, 482, mock(DebugService.class)
        );
        BotStatePacketService.sendRoundCompleteAndInactive(
            plugin, false, player, winResult(), RoundResult.PlayerOutcome.LOST, mock(DebugService.class)
        );

        verify(player, never()).sendPluginMessage(any(), any(), any());
    }

    @Test
    void doesNotSendCompletionToOfflineBotClient() {
        YRushPlugin plugin = mock(YRushPlugin.class);
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(false);

        BotStatePacketService.sendRoundCompleteAndInactive(
            plugin, true, player, winResult(), RoundResult.PlayerOutcome.WON, mock(DebugService.class)
        );

        verify(player, never()).sendPluginMessage(any(), any(), any());
    }

    @Test
    void sendsWinCompletionImmediatelyBeforeInactive() {
        assertCompletionSequence(
            winResult(),
            RoundResult.PlayerOutcome.WON,
            "{\"schema_version\":1,\"round_active\":false,\"player_active\":false,"
                + "\"phase\":\"ROUND_COMPLETE\",\"result\":\"WIN\",\"player_outcome\":\"WON\","
                + "\"winner_uuid\":\"12345678-1234-5678-9abc-123456789abc\"}"
        );
    }

    @Test
    void completionWithoutWinnerOmitsWinnerUuid() {
        assertCompletionSequence(
            roundResult(RoundResultType.DRAW, Optional.empty()),
            RoundResult.PlayerOutcome.DRAW,
            "{\"schema_version\":1,\"round_active\":false,\"player_active\":false,"
                + "\"phase\":\"ROUND_COMPLETE\",\"result\":\"DRAW\",\"player_outcome\":\"DRAW\"}"
        );
        assertCompletionSequence(
            RoundResult.stopped(),
            RoundResult.PlayerOutcome.STOPPED,
            "{\"schema_version\":1,\"round_active\":false,\"player_active\":false,"
                + "\"phase\":\"ROUND_COMPLETE\",\"result\":\"STOPPED\","
                + "\"player_outcome\":\"STOPPED\"}"
        );
    }

    @Test
    void inactiveStillFollowsWhenCompletionSendFails() {
        YRushPlugin plugin = mock(YRushPlugin.class);
        Player player = onlineBotClientListeningOn(BotStatePacketService.CHANNEL);
        doThrow(new IllegalStateException("send failed"))
            .doNothing()
            .when(player).sendPluginMessage(eq(plugin), eq(BotStatePacketService.CHANNEL), any(byte[].class));

        BotStatePacketService.sendRoundCompleteAndInactive(
            plugin, true, player, winResult(), RoundResult.PlayerOutcome.WON, mock(DebugService.class)
        );

        ArgumentCaptor<byte[]> payloads = ArgumentCaptor.forClass(byte[].class);
        verify(player, times(2)).sendPluginMessage(
            eq(plugin), eq(BotStatePacketService.CHANNEL), payloads.capture()
        );
        assertEquals(INACTIVE_JSON, new String(payloads.getAllValues().get(1), StandardCharsets.UTF_8));
    }

    @Test
    void inactivePayloadIsRawUtf8Json() {
        YRushPlugin plugin = mock(YRushPlugin.class);
        Player player = onlineBotClientListeningOn(BotStatePacketService.CHANNEL);

        BotStatePacketService.sendInactive(plugin, true, player, mock(DebugService.class));

        ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);
        verify(player).sendPluginMessage(eq(plugin), eq(BotStatePacketService.CHANNEL), payload.capture());
        assertArrayEquals(INACTIVE_JSON.getBytes(StandardCharsets.UTF_8), payload.getValue());
    }

    private void assertCompletionSequence(
        RoundResult result,
        RoundResult.PlayerOutcome playerOutcome,
        String expectedCompletion
    ) {
        YRushPlugin plugin = mock(YRushPlugin.class);
        Player player = onlineBotClientListeningOn(BotStatePacketService.CHANNEL);

        BotStatePacketService.sendRoundCompleteAndInactive(
            plugin, true, player, result, playerOutcome, mock(DebugService.class)
        );

        ArgumentCaptor<byte[]> payloads = ArgumentCaptor.forClass(byte[].class);
        verify(player, times(2)).sendPluginMessage(
            eq(plugin), eq(BotStatePacketService.CHANNEL), payloads.capture()
        );
        assertEquals(
            List.of(expectedCompletion, INACTIVE_JSON),
            payloads.getAllValues().stream()
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                .toList()
        );
    }

    private RoundResult winResult() {
        return roundResult(RoundResultType.WIN, Optional.of(WINNER_ID));
    }

    private RoundResult roundResult(RoundResultType type, Optional<UUID> winnerId) {
        return new RoundResult(type, winnerId, 39, Duration.ZERO, 5);
    }

    private Player onlineBotClientListeningOn(String channel) {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getListeningPluginChannels()).thenReturn(Set.of(channel));
        when(player.getName()).thenReturn("BotClient");
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
