package com.jedk1.jedcore.ability.firebending;

import com.jedk1.jedcore.JCMethods;
import com.jedk1.jedcore.JedCore;
import com.jedk1.jedcore.configuration.JedCoreConfig;
import com.jedk1.jedcore.util.FireTick;
import com.projectkorra.projectkorra.Element.SubElement;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.BlueFireAbility;
import com.projectkorra.projectkorra.ability.FireAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.firebending.BlazeArc;
import com.projectkorra.projectkorra.firebending.HeatControl;
import com.projectkorra.projectkorra.region.RegionProtection;
import com.projectkorra.projectkorra.util.DamageHandler;

import com.projectkorra.projectkorra.util.TempBlock;
import com.projectkorra.projectkorra.waterbending.ice.PhaseChange;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FireBreath extends FireAbility implements AddonAbility {

	Random rand = new Random();
	private final List<Location> locations = new ArrayList<>();

	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	@Attribute(Attribute.DURATION)
	private long duration;
	private int particles;
	@Attribute("Player" + Attribute.DAMAGE)
	private double playerDamage;
	@Attribute("Mob" + Attribute.DAMAGE)
	private double mobDamage;
	@Attribute(Attribute.DURATION)
	private int fireDuration;
	@Attribute(Attribute.RANGE)
	private int range;
	private boolean spawnFire;
	private boolean meltEnabled;
	private int meltChance;
	private boolean meltOnlyIce;


	public FireBreath(Player player) {
		super(player);
		if (!bPlayer.canBend(this) || hasAbility(player, FireBreath.class)) {
			return;
		}

		setFields();
		
		if (bPlayer.isAvatarState()) {
			range = range * 2;
			playerDamage = playerDamage * 1.5;
			mobDamage = mobDamage * 2;
			duration = duration * 3;
		} else if (JCMethods.isSozinsComet(player.getWorld())) {
			range = range * 2;
			playerDamage = playerDamage * 1.5;
			mobDamage = mobDamage * 2;
		}
		start();
	}

	public void setFields() {
		ConfigurationSection config = JedCoreConfig.getConfig(this.player);

		cooldown = config.getLong("Abilities.Fire.FireBreath.Cooldown");
		duration = config.getLong("Abilities.Fire.FireBreath.Duration");
		particles = config.getInt("Abilities.Fire.FireBreath.Particles");
		playerDamage = config.getDouble("Abilities.Fire.FireBreath.Damage.Player");
		mobDamage = config.getDouble("Abilities.Fire.FireBreath.Damage.Mob");
		fireDuration = config.getInt("Abilities.Fire.FireBreath.FireDuration");
		range = config.getInt("Abilities.Fire.FireBreath.Range");
		spawnFire = config.getBoolean("Abilities.Fire.FireBreath.Avatar.FireEnabled");
		meltEnabled = config.getBoolean("Abilities.Fire.FireBreath.Melt.Enabled");
		meltOnlyIce = config.getBoolean("Abilities.Fire.FireBreath.Melt.OnlyIce");
		meltChance = config.getInt("Abilities.Fire.FireBreath.Melt.Chance");
		
		applyModifiers();
	}
	
	private void applyModifiers() {
		if (bPlayer.canUseSubElement(SubElement.BLUE_FIRE)) {
			cooldown *= BlueFireAbility.getCooldownFactor();
			range *= BlueFireAbility.getRangeFactor();
			playerDamage *= BlueFireAbility.getDamageFactor();
			mobDamage *= BlueFireAbility.getDamageFactor();
		}
		
		if (isDay(player.getWorld())) {
			cooldown -= ((long) getDayFactor(cooldown) - cooldown);
			range = (int) getDayFactor(range);
			playerDamage = getDayFactor(playerDamage);
			mobDamage = getDayFactor(mobDamage);
		}
	}

	@Override
	public void progress() {
		if (player.isDead() || !player.isOnline()) {
			remove();
			return;
		}
		if (!bPlayer.canBendIgnoreCooldowns(this)) {
			bPlayer.addCooldown(this);
			remove();
			return;
		}
		if (!player.isSneaking()) {
			bPlayer.addCooldown(this);
			remove();
			return;
		}
		if (System.currentTimeMillis() < getStartTime() + duration) {
			createBeam();
		} else {
			bPlayer.addCooldown(this);
			remove();
		}
	}

	private boolean isLocationSafe(Location loc) {
		Block block = loc.getBlock();
		if (RegionProtection.isRegionProtected(player, loc, this)) {
			return false;
		}
		if (!isTransparent(block)) {
			return false;
		}
		return !isWater(block);
	}

	private void createBeam() {
		Location loc = player.getEyeLocation();
		Vector dir = player.getLocation().getDirection();
		double step = 1;
		double size = 0;
		double damageRegion = 1.5;

		locations.clear();

		for (double k = 0; k < range; k += step) {
			loc = loc.add(dir.clone().multiply(step));
			size += 0.005;
			damageRegion += 0.01;
			if (meltEnabled) {
				for (Block b : GeneralMethods.getBlocksAroundPoint(loc, damageRegion)) {
					if (rand.nextInt(meltChance) == 0) {
						if (!meltOnlyIce) {
							HeatControl.melt(player, b);
						} else if (isIce(b) && TempBlock.isTempBlock(b)) {
							TempBlock temp = TempBlock.get(b);
							if (PhaseChange.getFrozenBlocksMap().remove(temp) != null) {
								temp.revertBlock();
							}
						}
					}
				}
			}
			if (!isLocationSafe(loc))
				return;

			locations.add(loc);

			for (Entity entity : GeneralMethods.getEntitiesAroundPoint(loc, damageRegion)) {
				if (entity instanceof LivingEntity && entity.getEntityId() != player.getEntityId()) {
					if (entity instanceof Player) {
						FireTick.set(entity, fireDuration / 50);
						DamageHandler.damageEntity(entity, playerDamage, this);
					} else {
						FireTick.set(entity, fireDuration / 50);
						DamageHandler.damageEntity(entity, mobDamage, this);
					}
				}
			}

			playFirebendingSound(loc);
			if (bPlayer.isAvatarState() && spawnFire) {
				new BlazeArc(player, loc, dir, 2);
			}

			playFirebendingParticles(loc, particles, Math.random(), Math.random(), Math.random());
			loc.getWorld().spawnParticle(Particle.SMOKE, loc, particles, Math.random(), Math.random(), Math.random(), size);
			JCMethods.emitLight(loc);
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
	public List<Location> getLocations() {
		return locations;
	}

	@Override
	public String getName() {
		return "FireBreath";
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
		return "* JedCore Addon *\n" + config.getString("Abilities.Fire.FireBreath.Description");
	}

	public void setCooldown(long cooldown) {
		this.cooldown = cooldown;
	}

	public long getDuration() {
		return duration;
	}

	public void setDuration(long duration) {
		this.duration = duration;
	}

	public int getParticles() {
		return particles;
	}

	public void setParticles(int particles) {
		this.particles = particles;
	}

	public double getPlayerDamage() {
		return playerDamage;
	}

	public void setPlayerDamage(double playerDamage) {
		this.playerDamage = playerDamage;
	}

	public double getMobDamage() {
		return mobDamage;
	}

	public void setMobDamage(double mobDamage) {
		this.mobDamage = mobDamage;
	}

	public int getFireDuration() {
		return fireDuration;
	}

	public void setFireDuration(int fireDuration) {
		this.fireDuration = fireDuration;
	}

	public int getRange() {
		return range;
	}

	public void setRange(int range) {
		this.range = range;
	}

	public boolean isSpawnFire() {
		return spawnFire;
	}

	public void setSpawnFire(boolean spawnFire) {
		this.spawnFire = spawnFire;
	}

	public boolean isMeltEnabled() {
		return meltEnabled;
	}

	public void setMeltEnabled(boolean meltEnabled) {
		this.meltEnabled = meltEnabled;
	}

	public int getMeltChance() {
		return meltChance;
	}

	public void setMeltChance(int meltChance) {
		this.meltChance = meltChance;
	}

	@Override
	public void load() {}

	@Override
	public void stop() {}

	@Override
	public boolean isEnabled() {
		ConfigurationSection config = JedCoreConfig.getConfig(this.player);
		return config.getBoolean("Abilities.Fire.FireBreath.Enabled");
	}
}
