package com.jedk1.jedcore.ability.waterbending;

import com.jedk1.jedcore.JedCore;
import com.jedk1.jedcore.configuration.JedCoreConfig;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.BloodAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.command.Commands;
import com.projectkorra.projectkorra.region.RegionProtection;
import com.projectkorra.projectkorra.util.ChatUtil;
import com.projectkorra.projectkorra.util.DamageHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Hold sneak while looking at a creature to charge, then release sneak (or left-click) to rip the blood out of it.
 * Creatures killed this way can leave their blood behind as a waterbending source.
 * Based on the BloodRip addon originally made by StrangeOne101.
 */
public class BloodRip extends BloodAbility implements AddonAbility {

	// How far (in blocks) from the crosshair a creature can be and still be targeted.
	private static final double AIM_RADIUS = 0.75;
	private static final long MIN_CHARGE_TIME = 1000;

	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	@Attribute(Attribute.RANGE)
	private double range;
	@Attribute(Attribute.CHARGE_DURATION)
	private long chargeTime;
	@Attribute(Attribute.DAMAGE)
	private double maxDamage;
	private boolean canKill;
	private boolean nightOnly;
	private boolean fullMoonOnly;
	private boolean undeadMobs;
	private boolean bloodSourceEnabled;
	private long bloodSourceDuration;

	private LivingEntity target;
	private long chargeStart;
	private long lastChargeDisplay;

	public BloodRip(Player player) {
		super(player);
		if (hasAbility(player, BloodRip.class) || !bPlayer.canBend(this) || !bPlayer.canBloodbend()) {
			return;
		}

		setFields();
		if (nightOnly && !isNight(player.getWorld()) && !bPlayer.canBloodbendAtAnytime()) {
			return;
		}
		if (fullMoonOnly && !isFullMoon(player.getWorld()) && !bPlayer.canBloodbendAtAnytime()) {
			return;
		}
		start();
	}

	public void setFields() {
		ConfigurationSection config = JedCoreConfig.getConfig(this.player);

		cooldown = config.getLong("Abilities.Water.BloodRip.Cooldown");
		range = config.getDouble("Abilities.Water.BloodRip.Range");
		chargeTime = config.getLong("Abilities.Water.BloodRip.ChargeTime");
		maxDamage = config.getDouble("Abilities.Water.BloodRip.MaxDamage");
		canKill = config.getBoolean("Abilities.Water.BloodRip.CanKill");
		nightOnly = config.getBoolean("Abilities.Water.BloodRip.NightOnly");
		fullMoonOnly = config.getBoolean("Abilities.Water.BloodRip.FullMoonOnly");
		undeadMobs = config.getBoolean("Abilities.Water.BloodRip.UndeadMobs");
		bloodSourceEnabled = config.getBoolean("Abilities.Water.BloodRip.BloodSource.Enabled");
		bloodSourceDuration = config.getLong("Abilities.Water.BloodRip.BloodSource.Duration");
	}

	@Override
	public void progress() {
		if (!player.isOnline() || player.isDead() || !bPlayer.canBendIgnoreCooldowns(this)) {
			remove();
			return;
		}

		// Keep looking for a target while sneaking, instead of only checking the moment sneak was pressed.
		if (target == null) {
			if (!player.isSneaking()) {
				remove();
				return;
			}
			target = findTarget();
			if (target != null) {
				chargeStart = System.currentTimeMillis();
				lastChargeDisplay = 0;
			}
			return;
		}

		if (target.isDead() || !target.isValid() || target.getWorld() != player.getWorld()
				|| target.getLocation().distanceSquared(player.getLocation()) > range * range) {
			remove();
			return;
		}

		long needed = getNeededChargeTime();
		long charged = getChargedTime();
		if (lastChargeDisplay + 1000 < System.currentTimeMillis() || charged >= needed) {
			displayCharge(charged / (float) needed, charged >= needed);
			lastChargeDisplay = System.currentTimeMillis();
		}

		if (!player.isSneaking()) {
			rip();
		}
	}

	private LivingEntity findTarget() {
		Location eye = player.getEyeLocation();
		RayTraceResult result = player.getWorld().rayTrace(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true, AIM_RADIUS, this::isValidTarget);
		if (result != null && result.getHitEntity() instanceof LivingEntity living) {
			return living;
		}
		return null;
	}

	private boolean isValidTarget(Entity entity) {
		if (!(entity instanceof LivingEntity) || entity.getEntityId() == player.getEntityId() || entity instanceof ArmorStand) return false;
		if (!undeadMobs && GeneralMethods.isUndead(entity)) return false;
		if (entity instanceof Player targetPlayer && (targetPlayer.getGameMode() == GameMode.SPECTATOR || Commands.invincible.contains(targetPlayer.getName()))) {
			return false;
		}
		return !RegionProtection.isRegionProtected(this, entity.getLocation());
	}

	/**
	 * Healthier creatures take longer to charge, scaled against the larger of their max health and the max damage.
	 */
	private long getNeededChargeTime() {
		double maxHealth = getMaxHealth(target);
		return Math.max(1, (long) (chargeTime / Math.max(maxHealth, maxDamage) * target.getHealth()));
	}

	private long getChargedTime() {
		return Math.min(System.currentTimeMillis() - chargeStart, getNeededChargeTime());
	}

	private static double getMaxHealth(LivingEntity entity) {
		var maxHealth = entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
		return maxHealth == null ? entity.getHealth() : maxHealth.getValue();
	}

	private void displayCharge(float charge, boolean fullyCharged) {
		charge = Math.min(1, charge);
		Location hand = GeneralMethods.getRightSide(player.getLocation(), 0.55).add(0, 1.3, 0)
				.add(player.getEyeLocation().getDirection().multiply(0.75));
		player.getWorld().spawnParticle(Particle.DUST, hand, 1, 0, 0, 0, new Particle.DustOptions(Color.fromRGB((int) (charge * 255), 0, 0), 1));

		ThreadLocalRandom random = ThreadLocalRandom.current();
		for (int i = 0; i < (fullyCharged ? 2 : 8); i++) {
			Location location = target.getLocation().add(0.5 - random.nextFloat(), 0.7 + random.nextFloat() / 2, 0.5 - random.nextFloat());
			target.getWorld().spawnParticle(Particle.SMOKE, location, 1, 0, 0.1, 0, 0.02);
		}
	}

	/**
	 * Releases the charge. Releasing too early (under a second, unless already fully charged) fizzles without a
	 * cooldown.
	 */
	private void rip() {
		long needed = getNeededChargeTime();
		long charged = getChargedTime();
		if (charged < MIN_CHARGE_TIME && charged < needed) {
			remove();
			return;
		}

		double damage = maxDamage * charged / needed;
		if (!canKill && damage >= target.getHealth()) {
			damage = Math.max(0, target.getHealth() - 0.5);
		}

		Location location = target.getLocation();
		DamageHandler.damageEntity(target, damage, this);
		location.getWorld().spawnParticle(Particle.DUST, location, 16, 0.5, 0.5, 0.5, new Particle.DustOptions(Color.RED, 2));
		location.getWorld().spawnParticle(Particle.BLOCK, location, 48, 0.4, 0, 0.5, Material.REDSTONE_BLOCK.createBlockData());

		if (target.isDead() && bloodSourceEnabled) {
			BloodRipSource.create(target, bloodSourceDuration);
		}

		bPlayer.addCooldown(this);
		remove();
	}

	/**
	 * Left-clicking while charging rips immediately.
	 */
	public static void rip(Player player) {
		BloodRip bloodRip = getAbility(player, BloodRip.class);
		if (bloodRip != null && bloodRip.target != null) {
			bloodRip.rip();
		}
	}

	/**
	 * Taking damage while charging breaks the bloodbender's focus.
	 */
	public static void interrupt(Player player) {
		BloodRip bloodRip = getAbility(player, BloodRip.class);
		if (bloodRip != null && bloodRip.target != null) {
			bloodRip.remove();
			ChatUtil.sendActionBar(Component.text("* You lose focus *", NamedTextColor.RED), player);
			player.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, player.getLocation().add(0, 1, 0), 4, 0.5, 0.5, 0.5);
		}
	}

	public LivingEntity getTarget() {
		return target;
	}

	@Override
	public long getCooldown() {
		return cooldown;
	}

	@Override
	public Location getLocation() {
		return target != null ? target.getLocation() : player.getLocation();
	}

	@Override
	public String getName() {
		return "BloodRip";
	}

	@Override
	public boolean isHarmlessAbility() {
		return false;
	}

	@Override
	public boolean isSneakAbility() {
		return true;
	}

	@Override
	public String getAuthor() {
		return JedCore.dev;
	}

	@Override
	public String getVersion() {
		return JedCore.version;
	}

	@Override
	public String getDescription() {
		ConfigurationSection config = JedCoreConfig.getConfig(this.player);
		return "* JedCore Addon *\n" + config.getString("Abilities.Water.BloodRip.Description");
	}

	@Override
	public void load() {}

	@Override
	public void stop() {}

	@Override
	public boolean isEnabled() {
		ConfigurationSection config = JedCoreConfig.getConfig(this.player);
		return config.getBoolean("Abilities.Water.BloodRip.Enabled");
	}
}
