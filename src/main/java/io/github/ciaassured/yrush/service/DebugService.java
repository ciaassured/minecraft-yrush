package io.github.ciaassured.yrush.service;

import io.github.ciaassured.yrush.YRushPlugin;

public final class DebugService {
    private final YRushPlugin plugin;

    public DebugService(YRushPlugin plugin) {
        this.plugin = plugin;
    }

    public void log(String message) {
        if (plugin.getConfig().getBoolean("debug.enabled", false)) {
            plugin.getLogger().info("[debug] " + message);
        }
    }
}
