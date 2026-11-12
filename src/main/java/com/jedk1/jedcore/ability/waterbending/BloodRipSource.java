package com.jedk1.jedcore.ability.waterbending;

import com.projectkorra.projectkorra.ability.ElementalAbility;
import com.projectkorra.projectkorra.util.TempBlock;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The blood left behind by a creature killed with {@link BloodRip}. It's a bendable water source that only lasts a
 * short time and is marked with blood particles.
 */
public class BloodRipSource {

	private static final List<BloodRipSource> INSTANCES = new CopyOnWriteArrayList<>();

	private final TempBlock tempBlock;
	private int ticks;

	private BloodRipSource(TempBlock tempBlock) {
		this.tempBlock = tempBlock;
	}

	/**
	 * Leaves a blood source where the creature died. Smaller mobs leave less blood, so it dries up sooner.
	 */
	public static void create(LivingEntity entity, long duration) {
		Block block = findBlock(entity.getLocation());
		if (block == null) {
			return;
		}

		if (!(entity instanceof Player)) {
			var maxHealthAttribute = entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
			double maxHealth = maxHealthAttribute == null ? 20 : maxHealthAttribute.getValue();
			duration -= 1000;
			if (maxHealth <= 10) duration /= 2;
			if (maxHealth <= 5) duration /= 2;
			duration = Math.max(1500, duration);
		}

		TempBlock tempBlock = new TempBlock(block, Material.WATER.createBlockData(data -> ((Levelled) data).setLevel(0)), duration);
		tempBlock.setBendableSource(true);
		INSTANCES.add(new BloodRipSource(tempBlock));
	}

	/**
	 * Finds the open block resting on the ground at or just below the creature.
	 */
	private static Block findBlock(Location location) {
		for (int i = 0; i < 3; i++) {
			Block block = location.clone().subtract(0, i, 0).getBlock();
			Block below = block.getRelative(0, -1, 0);
			if (!block.getType().isSolid() && below.getType().isSolid()) {
				boolean replaceable = ElementalAbility.isAir(block.getType()) || (block.isPassable() && !block.isLiquid());
				return replaceable && !TempBlock.isTempBlock(block) ? block : null;
			}
		}
		return null;
	}

	private boolean update() {
		Block block = tempBlock.getBlock();
		if (tempBlock.isReverted() || block.getType() != Material.WATER) {
			return false;
		}

		if (ticks++ % 4 == 0) {
			ThreadLocalRandom random = ThreadLocalRandom.current();
			Location location = block.getLocation();
			for (int i = 0; i < 16; i++) {
				block.getWorld().spawnParticle(Particle.DUST, location.clone().add(random.nextFloat(), random.nextFloat(), random.nextFloat()), 1, 0, 0, 0, new Particle.DustOptions(Color.RED, 1.5f));
			}
			block.getWorld().spawnParticle(Particle.BLOCK, location.clone().add(0.5, 0.5, 0.5), 40, 0.3, 0.3, 0.3, Material.REDSTONE_BLOCK.createBlockData());
		}
		return true;
	}

	public static void updateAll() {
		INSTANCES.removeIf(source -> !source.update());
	}

	public static void removeAll() {
		for (BloodRipSource source : INSTANCES) {
			source.tempBlock.revertBlock();
		}
		INSTANCES.clear();
	}
}
