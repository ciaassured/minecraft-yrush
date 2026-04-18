package io.github.ciaassured.yrush.game;

import io.github.ciaassured.yrush.location.StartCategory;
import org.bukkit.GameMode;

import java.util.Map;
import java.util.UUID;

@FunctionalInterface
public interface RoundCompletionHandler {
    void onComplete(RoundResult result, Map<UUID, GameMode> offlineRestores, StartCategory categoryUsed);
}
