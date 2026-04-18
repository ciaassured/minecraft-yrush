package io.github.ciaassured.yrush;

import org.bukkit.configuration.file.FileConfiguration;

public record YRushConfig(
    int countdownSeconds,
    int betweenRoundsSeconds,
    int timeoutSeconds,
    int targetMinimumDistance,
    int targetMaximumDistance,
    int startRadius
) {
    public static YRushConfig from(FileConfiguration config) {
        int minDistance = Math.max(1, config.getInt("target-y.minimum-distance", 10));
        int maxDistance = Math.max(minDistance, config.getInt("target-y.maximum-distance", 96));

        return new YRushConfig(
            Math.max(1, config.getInt("round.countdown-seconds", 5)),
            Math.max(0, config.getInt("round.between-rounds-seconds", 5)),
            Math.max(10, config.getInt("round.timeout-seconds", 600)),
            minDistance,
            maxDistance,
            Math.max(100, config.getInt("start-location.radius", 3000))
        );
    }
}

