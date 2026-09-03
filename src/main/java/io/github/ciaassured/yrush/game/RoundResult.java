package io.github.ciaassured.yrush.game;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public record RoundResult(
    RoundResultType type,
    Optional<UUID> winnerId,
    int targetY,
    Duration duration,
    int participantCount
) {
    public PlayerOutcome outcomeFor(UUID playerId, boolean eliminated) {
        return switch (type) {
            case STOPPED -> PlayerOutcome.STOPPED;
            case DRAW -> eliminated ? PlayerOutcome.ELIMINATED : PlayerOutcome.DRAW;
            case WIN -> {
                if (eliminated) yield PlayerOutcome.ELIMINATED;
                yield winnerId.filter(playerId::equals).isPresent() ? PlayerOutcome.WON : PlayerOutcome.LOST;
            }
        };
    }

    public static RoundResult stopped() {
        return new RoundResult(RoundResultType.STOPPED, Optional.empty(), 0, Duration.ZERO, 0);
    }

    public enum PlayerOutcome {
        WON,
        LOST,
        ELIMINATED,
        DRAW,
        STOPPED
    }
}
