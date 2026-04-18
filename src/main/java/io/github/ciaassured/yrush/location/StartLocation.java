package io.github.ciaassured.yrush.location;

import org.bukkit.Location;

import java.util.List;

public record StartLocation(
    Location center,
    List<Location> playerPositions,
    StartType type,
    StartCategory category
) {}
