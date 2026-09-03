package io.github.ciaassured.yrush.game;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RoundResultTest {
    private static final UUID WINNER_ID = UUID.fromString("12345678-1234-5678-9abc-123456789abc");
    private static final UUID OTHER_PLAYER_ID = UUID.fromString("87654321-4321-8765-cba9-987654321abc");

    @Test
    void mapsGlobalResultToRecipientOutcome() {
        RoundResult win = result(RoundResultType.WIN, Optional.of(WINNER_ID));
        RoundResult draw = result(RoundResultType.DRAW, Optional.empty());
        RoundResult stopped = RoundResult.stopped();

        assertAll(
            () -> assertEquals(RoundResult.PlayerOutcome.WON, win.outcomeFor(WINNER_ID, false)),
            () -> assertEquals(RoundResult.PlayerOutcome.LOST, win.outcomeFor(OTHER_PLAYER_ID, false)),
            () -> assertEquals(RoundResult.PlayerOutcome.ELIMINATED, win.outcomeFor(OTHER_PLAYER_ID, true)),
            () -> assertEquals(RoundResult.PlayerOutcome.DRAW, draw.outcomeFor(OTHER_PLAYER_ID, false)),
            () -> assertEquals(RoundResult.PlayerOutcome.ELIMINATED, draw.outcomeFor(OTHER_PLAYER_ID, true)),
            () -> assertEquals(RoundResult.PlayerOutcome.STOPPED, stopped.outcomeFor(OTHER_PLAYER_ID, false)),
            () -> assertEquals(RoundResult.PlayerOutcome.STOPPED, stopped.outcomeFor(OTHER_PLAYER_ID, true))
        );
    }

    private RoundResult result(RoundResultType type, Optional<UUID> winnerId) {
        return new RoundResult(type, winnerId, 39, Duration.ZERO, 5);
    }
}
