package io.github.ciaassured.yrush.service;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class PlayerStateService {
    private PlayerStateService() {}

    public static void resetForRound(Player player) {
        clearInventory(player);
        resetVitals(player);
    }

    public static void giveUndergroundStartItems(Player player) {
        player.getInventory().addItem(new ItemStack(Material.WOODEN_PICKAXE));
    }

    public static void giveNightVision(Player player, int durationSeconds) {
        int ticks = Math.max(20, durationSeconds * 20);
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, ticks, 0, false, false, true));
    }

    public static void applyLockedCountdownEffects(Player player, int countdownSeconds, boolean waterStart) {
        int ticks = Math.max(20, (countdownSeconds + 2) * 20);
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks, 0, false, false, false));
        if (waterStart) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, ticks, 0, false, false, false));
            resetAir(player);
        }
    }

    public static void clearLockedCountdownEffects(Player player) {
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.WATER_BREATHING);
        resetAir(player);
    }

    public static void restoreAfterRound(Player player, GameMode originalGameMode, Location lobby) {
        player.setGameMode(originalGameMode);
        clearInventory(player);
        resetVitals(player);
        player.teleport(lobby);
    }

    public static void eliminateToSpectator(Player player, Location spectatorAnchor) {
        resetVitals(player);
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(spectatorAnchor);
    }

    public static void resetAir(Player player) {
        player.setRemainingAir(player.getMaximumAir());
    }

    private static void clearInventory(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
    }

    private static void resetVitals(Player player) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.setFireTicks(0);
        player.setFallDistance(0);
        resetAir(player);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setExhaustion(0.0f);

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null && player.isValid()) {
            player.setHealth(maxHealth.getValue());
        }
    }
}
