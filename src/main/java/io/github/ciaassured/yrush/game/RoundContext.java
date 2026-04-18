package io.github.ciaassured.yrush.game;

import io.github.ciaassured.yrush.location.StartCategory;
import io.github.ciaassured.yrush.location.StartType;
import org.bukkit.Location;

import java.time.Instant;
import java.util.List;

public record RoundContext(
    Location startCenter,
    List<Location> playerStarts,
    StartType startType,
    StartCategory startCategory,
    int startY,
    int targetY,
    RoundDirection direction,
    int timeoutSeconds,
    Instant startedAt
) {
    public boolean hasStarted() {
        return startedAt != null;
    }

    public long remainingSeconds(Instant now) {
        if (startedAt == null) {
            return timeoutSeconds;
        }
        long elapsed = Math.max(0, now.getEpochSecond() - startedAt.getEpochSecond());
        return Math.max(0, timeoutSeconds - elapsed);
    }

    RoundContext withStartedAt(Instant started) {
        return new RoundContext(startCenter, playerStarts, startType, startCategory,
            startY, targetY, direction, timeoutSeconds, started);
    }
}
