package io.github.ciaassured.yrush;

import org.bukkit.Chunk;
import org.bukkit.Location;
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

    private final Random random;
    private final SafeLocationValidator validator;

    public StartLocationService(Random random, SafeLocationValidator validator) {
        this.random = random;
        this.validator = validator;
    }

    public Optional<StartLocation> findStart(World world, Location center, int radius, int playerCount) {
        for (int attempt = 0; attempt < MAX_LOCATION_ATTEMPTS; attempt++) {
            Location column = randomColumn(world, center, radius);
            preloadChunks(world, column.getBlockX(), column.getBlockZ());

            Optional<Location> safeCenter = findSafeY(world, column.getBlockX(), column.getBlockZ());
            if (safeCenter.isEmpty()) {
                continue;
            }

            List<Location> playerPositions = findPlayerPositions(safeCenter.get(), playerCount);
            if (playerPositions.size() >= playerCount) {
                return Optional.of(new StartLocation(safeCenter.get(), playerPositions));
            }
        }

        return Optional.empty();
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

    private Optional<Location> findSafeY(World world, int x, int z) {
        int minY = world.getMinHeight() + 1;
        int maxY = world.getMaxHeight() - 2;
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
}
