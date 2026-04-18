package io.github.ciaassured.yrush;

import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public final class StartLocationService {
    private static final int MAX_LOCATION_ATTEMPTS = 150;
    private static final int PLAYER_SPREAD_RADIUS = 4;
    private static final int PRELOAD_CHUNK_RADIUS = 1;
    private static final int SURFACE_START_PERCENT = 70;

    private final Random random;
    private final SafeLocationValidator validator;
    private StartCategory lastStartCategory;

    public StartLocationService(Random random, SafeLocationValidator validator) {
        this.random = random;
        this.validator = validator;
    }

    public Optional<StartLocation> findStart(World world, Location center, int radius, int playerCount) {
        StartType preferredType = random.nextInt(100) < SURFACE_START_PERCENT ? StartType.SURFACE : StartType.UNDERGROUND;
        Optional<StartLocation> preferredStart = findStart(world, center, radius, playerCount, preferredType);
        if (preferredStart.isPresent()) {
            return preferredStart;
        }

        StartType fallbackType = preferredType == StartType.SURFACE ? StartType.UNDERGROUND : StartType.SURFACE;
        return findStart(world, center, radius, playerCount, fallbackType);
    }

    public void remember(StartLocation startLocation) {
        lastStartCategory = startLocation.category();
    }

    private Optional<StartLocation> findStart(World world, Location center, int radius, int playerCount, StartType type) {
        Optional<StartLocation> dryStart = findStart(world, center, radius, playerCount, type, true);
        if (dryStart.isPresent()) {
            return dryStart;
        }

        return findStart(world, center, radius, playerCount, type, false);
    }

    private Optional<StartLocation> findStart(
        World world,
        Location center,
        int radius,
        int playerCount,
        StartType type,
        boolean avoidWaterIfRecent
    ) {
        for (int attempt = 0; attempt < MAX_LOCATION_ATTEMPTS; attempt++) {
            Location column = randomColumn(world, center, radius);
            preloadChunks(world, column.getBlockX(), column.getBlockZ());

            Optional<Location> safeCenter = switch (type) {
                case SURFACE -> findSurfaceStart(world, column.getBlockX(), column.getBlockZ());
                case UNDERGROUND -> findUndergroundStart(world, column.getBlockX(), column.getBlockZ());
            };
            if (safeCenter.isEmpty()) {
                continue;
            }

            StartCategory category = StartCategory.from(type, isWaterStart(safeCenter.get()));
            if (avoidWaterIfRecent && shouldAvoid(category)) {
                continue;
            }

            List<Location> playerPositions = findPlayerPositions(safeCenter.get(), playerCount);
            if (playerPositions.size() >= playerCount) {
                return Optional.of(new StartLocation(safeCenter.get(), playerPositions, type, category));
            }
        }

        return Optional.empty();
    }

    private boolean shouldAvoid(StartCategory category) {
        if (lastStartCategory == null) {
            return false;
        }
        return category == lastStartCategory || category.isWater() && lastStartCategory.isWater();
    }

    private Location randomColumn(World world, Location center, int radius) {
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = Math.sqrt(random.nextDouble()) * radius;
        int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
        int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
        return new Location(world, x, 0, z);
    }

    private void preloadChunks(World world, int x, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;

        for (int dx = -PRELOAD_CHUNK_RADIUS; dx <= PRELOAD_CHUNK_RADIUS; dx++) {
            for (int dz = -PRELOAD_CHUNK_RADIUS; dz <= PRELOAD_CHUNK_RADIUS; dz++) {
                Chunk chunk = world.getChunkAt(chunkX + dx, chunkZ + dz);
                chunk.load(true);
            }
        }
    }

    private Optional<Location> findSurfaceStart(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (y <= world.getMinHeight() || y + 1 >= world.getMaxHeight()) {
            return Optional.empty();
        }

        Location location = centered(world, x, y, z);
        if (validator.isSafe(location)) {
            return Optional.of(location);
        }

        return Optional.empty();
    }

    private Optional<Location> findUndergroundStart(World world, int x, int z) {
        int minY = world.getMinHeight() + 1;
        int maxY = Math.min(world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) - 6, world.getMaxHeight() - 2);
        if (minY > maxY) {
            return Optional.empty();
        }

        int height = maxY - minY + 1;
        int startOffset = random.nextInt(height);
        for (int offset = 0; offset < height; offset++) {
            int y = minY + ((startOffset + offset) % height);
            Location location = centered(world, x, y, z);
            if (validator.isSafe(location)) {
                return Optional.of(location);
            }
        }

        return Optional.empty();
    }

    private List<Location> findPlayerPositions(Location center, int playerCount) {
        List<Location> candidates = new ArrayList<>();
        World world = center.getWorld();
        if (world == null) {
            return candidates;
        }

        for (int dx = -PLAYER_SPREAD_RADIUS; dx <= PLAYER_SPREAD_RADIUS; dx++) {
            for (int dz = -PLAYER_SPREAD_RADIUS; dz <= PLAYER_SPREAD_RADIUS; dz++) {
                Location candidate = centered(world, center.getBlockX() + dx, center.getBlockY(), center.getBlockZ() + dz);
                if (validator.isSafe(candidate)) {
                    candidates.add(candidate);
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(center::distanceSquared));
        if (candidates.size() <= playerCount) {
            return candidates;
        }
        return new ArrayList<>(candidates.subList(0, playerCount));
    }

    private Location centered(World world, int x, int y, int z) {
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    private boolean isWaterStart(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }

        Material feet = world.getBlockAt(location.getBlockX(), location.getBlockY(), location.getBlockZ()).getType();
        Material below = world.getBlockAt(location.getBlockX(), location.getBlockY() - 1, location.getBlockZ()).getType();
        return feet == Material.WATER || below == Material.WATER;
    }
}
