package io.github.ciaassured.yrush.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YRushConfigTest {
    @Test
    void readsBotPacketsEnabled() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("bot-packets.enabled", true);

        assertTrue(YRushConfig.from(config).botPacketsEnabled());
    }

    @Test
    void ignoresRemovedPacketSetting() {
        YamlConfiguration config = new YamlConfiguration();
        String removedSection = "training" + "-packets";
        config.set(removedSection + ".enabled", true);

        assertFalse(YRushConfig.from(config).botPacketsEnabled());
    }
}
