package com.jedk1.jedcore.ability.earthbending.metalcables;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.Ability;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.PassiveAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.command.Commands;
import com.projectkorra.projectkorra.region.RegionProtection;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MetalCablesGrab extends MetalCablesSubAbility {

	private static final Set<UUID> GRABBED = ConcurrentHashMap.newKeySet();
	private static final String FLIGHT_KEY = "MetalCablesGrab";

	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	@Attribute(Attribute.RANGE)
	private double range;
	@Attribute(Attribute.SPEED)
	private double speed;
	private double pullSpeed;
	private final Set<String> allowedAbilities = new HashSet<>();

	private LivingEntity target;
	private boolean targetFlightEnabled;

	public MetalCablesGrab(Player player, MetalCables parent, int slot, boolean leftArm) {
		super(player, parent, slot, leftArm);
		setFields();
		start();
	}

	public void setFields() {
		ConfigurationSection config = getCablesConfig();

		cooldown = config.getLong("Abilities.Earth.MetalCables.Grab.Cooldown");
		range = config.getDouble("Abilities.Earth.MetalCables.Grab.Range");
		speed = config.getDouble("Abilities.Earth.MetalCables.Grab.Speed");
		pullSpeed = config.getDouble("Abilities.Earth.MetalCables.Grab.PullSpeed");
		for (String ability : config.getStringList("Abilities.Earth.MetalCables.Grab.AllowedAbilities")) {
			allowedAbilities.add(ability.toLowerCase());
		}
	}

	@Override
	public void progress() {
		if (!isStillValid()) return;

		if (target == null) {
			moveCable();
		} else {
			handlePull();
		}
		if (isRemoved()) return;

		if (hook.distanceSquared(getHand()) > range * range || !drawCable()) {
			remove();
		}
	}

	private void moveCable() {
		for (double step = 0; step < speed; step += direction.length()) {
			hook.add(direction);

			if (GeneralMethods.isSolid(hook.getBlock()) || RegionProtection.isRegionProtected(this, hook)) {
				remove();
				return;
			}

			for (Entity entity : GeneralMethods.getEntitiesAroundPoint(hook, 2)) {
				if (entity instanceof LivingEntity living && canGrab(living)) {
					grab(living);
					return;
				}
			}
		}
	}

	private boolean canGrab(LivingEntity entity) {
		if (entity.getEntityId() == player.getEntityId() || entity instanceof ArmorStand) return false;
		if (GRABBED.contains(entity.getUniqueId())) return false;
		if (entity instanceof Player targetPlayer && (targetPlayer.getGameMode() == GameMode.SPECTATOR || Commands.invincible.contains(targetPlayer.getName()))) {
			return false;
		}
		return !RegionProtection.isRegionProtected(this, entity.getLocation());
	}

	private void grab(LivingEntity entity) {
		target = entity;
		GRABBED.add(entity.getUniqueId());

		if (entity instanceof Player targetPlayer) {
			targetPlayer.setFlying(false);
			// Cancel anything the target was already doing that isn't allowed while tangled.
			for (Object instance : CoreAbility.getAbilitiesByInstances()) {
				CoreAbility ability = (CoreAbility) instance;
				if (ability.getPlayer() != null && ability.getPlayer().getUniqueId().equals(targetPlayer.getUniqueId()) && !isAllowed(ability)) {
					ability.remove();
				}
			}
		}

		playMetalbendingSound(entity.getLocation());
		playMetalbendingSound(player.getLocation());
	}

	private void handlePull() {
		if (target.isDead() || !target.isValid() || target.getWorld() != player.getWorld()
				|| target.getLocation().distanceSquared(player.getLocation()) > range * range) {
			remove();
			return;
		}

		hook = target.getLocation().add(0, 1, 0);

		if (!isHoldingSlot()) {
			setTargetFlight(false);
			return;
		}

		Location destination = player.getEyeLocation().add(player.getLocation().getDirection().multiply(1.5));
		if (!player.isSneaking()) {
			setTargetFlight(false);
			if (destination.distanceSquared(hook) <= 1) {
				remove();
			}
			return;
		}

		Vector pull = GeneralMethods.getDirection(hook, destination);
		if (pull.length() > 5) {
			pull.multiply(pullSpeed / pull.length());
		} else if (pull.length() > 1) {
			pull.multiply(0.3 / pull.length());
		} else {
			pull.multiply(0);
		}

		target.setFallDistance(0);
		setTargetFlight(true);
		// Pull is added to the target's own velocity, so a target that's moving away (e.g. FireJet) can resist.
		GeneralMethods.setVelocity(this, target, target.getVelocity().add(pull));
	}

	/**
	 * Lets a grabbed player be lifted without being kicked for flying, then restores their original flight state.
	 */
	private void setTargetFlight(boolean enabled) {
		if (!(target instanceof Player targetPlayer)) return;

		if (enabled) {
			if (!targetFlightEnabled) {
				targetFlightEnabled = true;
				flightHandler.createInstance(targetPlayer, player, FLIGHT_KEY);
			}
			targetPlayer.setAllowFlight(true);
			targetPlayer.setFlying(false);
		} else if (targetFlightEnabled) {
			targetFlightEnabled = false;
			flightHandler.removeInstance(targetPlayer, FLIGHT_KEY);
		}
	}

	private boolean isAllowed(Ability ability) {
		return ability instanceof PassiveAbility || allowedAbilities.contains(ability.getName().toLowerCase());
	}

	/**
	 * Returns false if the player is tangled by a Grab cable and the ability isn't one of the allowed basic moves.
	 */
	public static boolean canUseWhileGrabbed(Player player, Ability ability) {
		if (!GRABBED.contains(player.getUniqueId())) return true;

		for (MetalCablesGrab grab : getAbilities(MetalCablesGrab.class)) {
			if (grab.target != null && grab.target.getUniqueId().equals(player.getUniqueId())) {
				return grab.isAllowed(ability);
			}
		}
		return true;
	}

	public static boolean isGrabbed(Entity entity) {
		return GRABBED.contains(entity.getUniqueId());
	}

	public LivingEntity getTarget() {
		return target;
	}

	@Override
	public void remove() {
		super.remove();
		bPlayer.addCooldown(this);
		if (target != null) {
			setTargetFlight(false);
			GRABBED.remove(target.getUniqueId());
		}
	}

	@Override
	public long getCooldown() {
		return cooldown;
	}

	@Override
	public String getName() {
		return "MetalCablesGrab";
	}
}
