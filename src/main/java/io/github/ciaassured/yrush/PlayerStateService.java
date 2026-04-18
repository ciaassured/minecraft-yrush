package io.github.ciaassured.yrush;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

public final class PlayerStateService {
    public void resetForRound(Player player) {
        clearInventory(player);
        resetVitals(player);
    }

    public void giveUndergroundStartItems(Player player) {
        player.getInventory().addItem(new ItemStack(Material.STONE_PICKAXE));
    }

    public void restoreAfterRound(Player player, GameMode originalGameMode, Location lobby) {
        player.setGameMode(originalGameMode);
        clearInventory(player);
        resetVitals(player);
        player.teleport(lobby);
    }

    public void eliminateToSpectator(Player player, Location spectatorLocation) {
        resetVitals(player);
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(spectatorLocation);
    }

    private void clearInventory(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
    }

    private void resetVitals(Player player) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        player.setFireTicks(0);
        player.setFallDistance(0);
        player.setRemainingAir(player.getMaximumAir());
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setExhaustion(0.0f);

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null && player.isValid()) {
            player.setHealth(maxHealth.getValue());
        }
    }
}
