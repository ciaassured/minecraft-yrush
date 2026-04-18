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
    public static RoundResult stopped() {
        return new RoundResult(RoundResultType.STOPPED, Optional.empty(), 0, Duration.ZERO, 0);
    }
}
