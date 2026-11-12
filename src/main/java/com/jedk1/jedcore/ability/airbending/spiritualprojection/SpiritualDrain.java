package com.jedk1.jedcore.ability.airbending.spiritualprojection;

import com.jedk1.jedcore.JedCore;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.Ability;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.PassiveAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.command.Commands;
import com.projectkorra.projectkorra.region.RegionProtection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hold sneak to drain the spiritual energy of nearby players. Drained players can't bend for a while, and draining
 * someone heals you.
 */
public class SpiritualDrain extends SpiritualProjectionSubAbility {

	// Player UUID -> time their bending is restored.
	private static final Map<UUID, Long> DRAINED = new ConcurrentHashMap<>();
	private static final Color DRAIN_COLOR = Color.fromARGB(160, 200, 220, 255);

	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	@Attribute(Attribute.RANGE)
	private double range;
	@Attribute(Attribute.SPEED)
	private double drainSpeed;
	@Attribute(Attribute.DURATION)
	private long drainedDuration;
	private int energyCost;
	private double healAmount;

	// Target UUID -> where the energy being drained from them currently is.
	private final Map<UUID, Location> drainLocations = new HashMap<>();
	private boolean energyTaken;

	public SpiritualDrain(Player player, SpiritualProjection parent) {
		super(player, parent);
		if (hasAbility(player, SpiritualDrain.class) || bPlayer.isOnCooldown(this)) {
			return;
		}

		setFields();
		if (parent.getEnergy() < energyCost) {
			parent.consumeEnergy(energyCost); // Shows the "not enough energy" message.
			return;
		}
		start();
	}

	public void setFields() {
		ConfigurationSection config = getProjectionConfig();

		cooldown = config.getLong("Abilities.Air.SpiritualProjection.SpiritualDrain.Cooldown");
		range = config.getDouble("Abilities.Air.SpiritualProjection.SpiritualDrain.Range");
		drainSpeed = config.getDouble("Abilities.Air.SpiritualProjection.SpiritualDrain.DrainSpeed");
		drainedDuration = config.getLong("Abilities.Air.SpiritualProjection.SpiritualDrain.DrainedDuration");
		energyCost = config.getInt("Abilities.Air.SpiritualProjection.SpiritualDrain.Energy");
		healAmount = config.getDouble("Abilities.Air.SpiritualProjection.SpiritualDrain.HealAmount");
	}

	@Override
	public void progress() {
		if (!isStillValid()) return;

		if (!player.isSneaking()) {
			remove();
			return;
		}

		Location eye = player.getEyeLocation();
		for (Entity entity : player.getNearbyEntities(range, range, range)) {
			if (!(entity instanceof Player target) || !canDrain(target)) {
				continue;
			}

			Location drainLocation = drainLocations.get(target.getUniqueId());
			if (drainLocation == null) {
				// The energy cost is only paid once something is actually being drained.
				if (!energyTaken) {
					if (!parent.consumeEnergy(energyCost)) {
						remove();
						return;
					}
					energyTaken = true;
				}
				drainLocation = target.getEyeLocation();
				drainLocations.put(target.getUniqueId(), drainLocation);
			}

			if (drainLocation.distanceSquared(eye) < 1) {
				drain(target);
				continue;
			}

			Vector toPlayer = GeneralMethods.getDirection(drainLocation, eye).normalize().multiply(drainSpeed);
			drainLocation.add(toPlayer);
			drainLocation.getWorld().spawnParticle(Particle.ENTITY_EFFECT, drainLocation, 10, 0.1, 0.1, 0.1, 0, DRAIN_COLOR);
		}

		// Stop tracking targets that are no longer in range or valid.
		drainLocations.keySet().removeIf(uuid -> {
			Player target = Bukkit.getPlayer(uuid);
			return target == null || !canDrain(target) || target.getLocation().distanceSquared(player.getLocation()) > range * range * 3;
		});
	}

	private boolean canDrain(Player target) {
		if (target.getUniqueId().equals(player.getUniqueId()) || isDrained(target)) return false;
		if (target.getGameMode() == GameMode.SPECTATOR || Commands.invincible.contains(target.getName())) return false;
		if (target.getWorld() != player.getWorld() || !player.hasLineOfSight(target)) return false;
		return !RegionProtection.isRegionProtected(this, target.getLocation());
	}

	private void drain(Player target) {
		drainLocations.remove(target.getUniqueId());
		long restoreTime = System.currentTimeMillis() + drainedDuration;
		DRAINED.put(target.getUniqueId(), restoreTime);

		// Cancel anything the target was already doing.
		for (Object instance : CoreAbility.getAbilitiesByInstances()) {
			CoreAbility ability = (CoreAbility) instance;
			if (ability.getPlayer() != null && ability.getPlayer().getUniqueId().equals(target.getUniqueId()) && !(ability instanceof PassiveAbility)) {
				ability.remove();
			}
		}

		target.sendMessage(Component.text("Your spiritual energy has been drained and you are unable to bend.", NamedTextColor.GRAY, TextDecoration.BOLD));
		target.getWorld().playSound(target.getLocation(), Sound.ENTITY_GHAST_SCREAM, 1, 1);
		heal();

		Bukkit.getScheduler().runTaskLater(JedCore.plugin, () -> {
			Long restore = DRAINED.get(target.getUniqueId());
			// Only restore if they weren't drained again in the meantime.
			if (restore != null && restore <= System.currentTimeMillis()) {
				DRAINED.remove(target.getUniqueId());
				if (target.isOnline()) {
					target.sendMessage(Component.text("Your spiritual energy has been restored.", NamedTextColor.GRAY, TextDecoration.BOLD));
				}
			}
		}, Math.max(1, drainedDuration / 50));
	}

	private void heal() {
		if (healAmount <= 0) return;
		AttributeInstance maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
		double max = maxHealth == null ? 20 : maxHealth.getValue();
		player.setHealth(Math.min(max, player.getHealth() + healAmount));
	}

	public static boolean isDrained(Player player) {
		Long restore = DRAINED.get(player.getUniqueId());
		return restore != null && restore > System.currentTimeMillis();
	}

	/**
	 * Drained players can't start any bending apart from passives.
	 */
	public static boolean canUseWhileDrained(Player player, Ability ability) {
		return !isDrained(player) || ability instanceof PassiveAbility;
	}

	@Override
	public void remove() {
		super.remove();
		if (energyTaken) {
			bPlayer.addCooldown(this);
		}
	}

	@Override
	public long getCooldown() {
		return cooldown;
	}

	@Override
	public Location getLocation() {
		return player.getLocation();
	}

	@Override
	public String getName() {
		return "SpiritualDrain";
	}

	@Override
	public boolean isHarmlessAbility() {
		return false;
	}

	@Override
	public boolean isSneakAbility() {
		return true;
	}
}
