package com.jedk1.jedcore.util.versionadapter;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

public class PaperPotionEffectAdapter implements PotionEffectAdapter {

    @Override
    public PotionType getHarmingPotionType() {
        return PotionType.HARMING;
    }

    @Override
    public PotionEffect getSlownessEffect(int duration, int strength) {
        return new PotionEffect(PotionEffectType.SLOWNESS, duration / 50, strength - 1);
    }

    @Override
    public PotionEffect getResistanceEffect(int duration, int strength) {
        return new PotionEffect(PotionEffectType.RESISTANCE, duration / 50, strength - 1);
    }

    @Override
    public PotionEffect getNauseaEffect(int duration) {
        return new PotionEffect(PotionEffectType.NAUSEA, duration / 50, 1);
    }

    @Override
    public void applyJumpBoost(Player player, int duration, int strength) {
        if (player.hasPotionEffect(PotionEffectType.JUMP_BOOST)) {
            player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, duration / 50, strength - 1));
    }

    @Override
    public boolean hasWaterPotion(Inventory inventory) {
        if (inventory.contains(Material.POTION)) {
            ItemStack item = inventory.getItem(inventory.first(Material.POTION));
            if (item == null) return false;
            if (!(item.getItemMeta() instanceof PotionMeta meta)) return false;

            return meta.getBasePotionType() == PotionType.WATER;
        }
        return false;
    }
}
