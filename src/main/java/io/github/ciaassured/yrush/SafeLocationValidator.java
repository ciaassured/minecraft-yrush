package io.github.ciaassured.yrush;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.EnumSet;
import java.util.Set;

public final class SafeLocationValidator {
    private static final Set<Material> DANGEROUS = EnumSet.of(
        Material.LAVA,
        Material.FIRE,
        Material.SOUL_FIRE,
        Material.CACTUS,
        Material.MAGMA_BLOCK,
        Material.CAMPFIRE,
        Material.SOUL_CAMPFIRE,
        Material.SWEET_BERRY_BUSH,
        Material.POWDER_SNOW,
        Material.POINTED_DRIPSTONE
    );

    private static final Set<Material> INVALID = EnumSet.of(
        Material.BARRIER,
        Material.BEDROCK,
        Material.COMMAND_BLOCK,
        Material.CHAIN_COMMAND_BLOCK,
        Material.REPEATING_COMMAND_BLOCK,
        Material.END_PORTAL,
        Material.END_PORTAL_FRAME,
        Material.NETHER_PORTAL
    );

    public boolean isSafe(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }

        int y = location.getBlockY();
        if (y <= world.getMinHeight() || y + 1 >= world.getMaxHeight()) {
            return false;
        }

        Block feet = world.getBlockAt(location.getBlockX(), y, location.getBlockZ());
        Block head = world.getBlockAt(location.getBlockX(), y + 1, location.getBlockZ());
        Block below = world.getBlockAt(location.getBlockX(), y - 1, location.getBlockZ());

        if (!isPlayerSpace(feet.getType()) || !isPlayerSpace(head.getType())) {
            return false;
        }

        if (!isSafeSupport(below.getType())) {
            return false;
        }

        return hasSafeImmediateArea(world, location.getBlockX(), y, location.getBlockZ());
    }

    private boolean isPlayerSpace(Material material) {
        if (material == Material.WATER) {
            return true;
        }
        if (DANGEROUS.contains(material) || INVALID.contains(material)) {
            return false;
        }
        return material.isAir() || !material.isSolid();
    }

    private boolean isSafeSupport(Material material) {
        if (material == Material.WATER) {
            return true;
        }
        if (DANGEROUS.contains(material) || INVALID.contains(material)) {
            return false;
        }
        return material.isSolid();
    }

    private boolean hasSafeImmediateArea(World world, int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Material material = world.getBlockAt(x + dx, y + dy, z + dz).getType();
                    if (DANGEROUS.contains(material)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}

