package com.jedk1.jedcore.ability.earthbending.metalcables;

import com.jedk1.jedcore.JedCore;
import com.jedk1.jedcore.configuration.JedCoreConfig;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.MetalAbility;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * A single cable fired by {@link MetalCables}. Each cable belongs to one hotbar slot and comes out of one arm.
 */
public abstract class MetalCablesSubAbility extends MetalAbility implements AddonAbility {

	private static final String CABLE_COLOR = "#777777";
	private static final double STEP = 0.5;

	protected MetalCables parent;
	protected int slot;
	protected boolean leftArm;
	protected Location hook;
	protected Vector direction;

	public MetalCablesSubAbility(Player player, MetalCables parent, int slot, boolean leftArm) {
		super(player);
		this.parent = parent;
		this.slot = slot;
		this.leftArm = leftArm;
		this.hook = getHand(player, leftArm);
		this.direction = player.getEyeLocation().getDirection().multiply(STEP);
	}

	public static Location getHand(Player player, boolean leftArm) {
		Location eye = player.getEyeLocation();
		Location side = leftArm ? GeneralMethods.getLeftSide(eye, 0.4) : GeneralMethods.getRightSide(eye, 0.4);
		return side.subtract(0, 0.6, 0);
	}

	public static void displayCableParticle(Location location) {
		GeneralMethods.displayColoredParticle(CABLE_COLOR, location);
	}

	protected Location getHand() {
		return getHand(player, leftArm);
	}

	protected boolean isHoldingSlot() {
		return player.getInventory().getHeldItemSlot() == slot;
	}

	/**
	 * Common checks shared by every cable. Returns false if the cable was removed.
	 */
	protected boolean isStillValid() {
		if (!player.isOnline() || player.isDead() || parent == null || parent.isRemoved()) {
			remove();
			return false;
		}
		return true;
	}

	/**
	 * Draws the cable from the arm to the hook. Returns false if a solid block now cuts the cable.
	 */
	protected boolean drawCable() {
		Location origin = getHand();
		if (origin.getWorld() != hook.getWorld()) {
			return false;
		}
		double distance = origin.distance(hook);
		Vector dir = GeneralMethods.getDirection(origin, hook).normalize();

		for (double step = STEP; step < distance; step += STEP) {
			Location point = origin.clone().add(dir.clone().multiply(step));
			if (GeneralMethods.isSolid(point.getBlock()) && step < distance - 1.5) {
				return false;
			}
			displayCableParticle(point);
		}
		return true;
	}

	protected ConfigurationSection getCablesConfig() {
		return JedCoreConfig.getConfig(this.player);
	}

	@Override
	public Location getLocation() {
		return hook;
	}

	@Override
	public boolean isHiddenAbility() {
		return true;
	}

	@Override
	public boolean isHarmlessAbility() {
		return false;
	}

	@Override
	public boolean isSneakAbility() {
		return false;
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
		return null;
	}

	@Override
	public void load() {}

	@Override
	public void stop() {}

	@Override
	public boolean isEnabled() {
		ConfigurationSection config = JedCoreConfig.getConfig(this.player);
		return config.getBoolean("Abilities.Earth.MetalCables.Enabled");
	}
}
