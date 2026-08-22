package io.github.ciaassured.yrush.config;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YRushConfigTest {
    @Test
    void readsEveryDefaultFromPackagedYaml() {
        YRushConfig config = YRushConfig.from(loadPackagedConfig());

        assertAll(
            () -> assertEquals(5, config.countdownSeconds()),
            () -> assertEquals(5, config.betweenRoundsSeconds()),
            () -> assertEquals(240, config.timeoutSeconds()),
            () -> assertEquals(10, config.targetMinimumDistance()),
            () -> assertEquals(50, config.targetMaximumDistance()),
            () -> assertEquals(3000, config.startRadius()),
            () -> assertFalse(config.botPacketsEnabled())
        );
    }

    @Test
    void readsConfiguredValues() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("round.countdown-seconds", 8);
        config.set("round.between-rounds-seconds", 9);
        config.set("round.timeout-seconds", 120);
        config.set("target-y.minimum-distance", 12);
        config.set("target-y.maximum-distance", 70);
        config.set("start-location.radius", 1500);
        config.set("bot-packets.enabled", true);

        YRushConfig parsed = YRushConfig.from(config);

        assertAll(
            () -> assertEquals(8, parsed.countdownSeconds()),
            () -> assertEquals(9, parsed.betweenRoundsSeconds()),
            () -> assertEquals(120, parsed.timeoutSeconds()),
            () -> assertEquals(12, parsed.targetMinimumDistance()),
            () -> assertEquals(70, parsed.targetMaximumDistance()),
            () -> assertEquals(1500, parsed.startRadius()),
            () -> assertTrue(parsed.botPacketsEnabled())
        );
    }

    @Test
    void ignoresRemovedPacketSetting() {
        YamlConfiguration config = new YamlConfiguration();
        String removedSection = "training" + "-packets";
        config.set(removedSection + ".enabled", true);

        assertFalse(YRushConfig.from(config).botPacketsEnabled());
    }

    private YamlConfiguration loadPackagedConfig() {
        InputStream input = YRushConfigTest.class.getResourceAsStream("/config.yml");
        assertNotNull(input, "Packaged config.yml was not found on the test classpath");
        try (input; InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (Exception exception) {
            throw new AssertionError("Could not load packaged config.yml", exception);
        }
    }
}
