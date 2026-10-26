package com.jedk1.jedcore.ability.earthbending.metalcables;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.region.RegionProtection;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class MetalCablesGrapple extends MetalCablesSubAbility {

	@Attribute(Attribute.RANGE)
	private double range;
	@Attribute(Attribute.SPEED)
	private double speed;
	private double pullSpeed;
	private double maxSwingAngle;

	private boolean hit;

	public MetalCablesGrapple(Player player, MetalCables parent, int slot, boolean leftArm) {
		super(player, parent, slot, leftArm);
		setFields();
		start();
	}

	public void setFields() {
		ConfigurationSection config = getCablesConfig();

		range = config.getDouble("Abilities.Earth.MetalCables.Grapple.Range");
		speed = config.getDouble("Abilities.Earth.MetalCables.Grapple.Speed");
		pullSpeed = config.getDouble("Abilities.Earth.MetalCables.Grapple.PullSpeed");
		maxSwingAngle = config.getDouble("Abilities.Earth.MetalCables.Grapple.MaximumSwingAngle");
	}

	@Override
	public void progress() {
		if (!isStillValid()) return;

		if (!hit) {
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

			if (RegionProtection.isRegionProtected(this, hook)) {
				remove();
				return;
			}
			if (GeneralMethods.isSolid(hook.getBlock())) {
				hit = true;
				playMetalbendingSound(hook);
				playMetalbendingSound(player.getLocation());
				return;
			}
		}
	}

	private void handlePull() {
		// The block the cable was anchored to is gone.
		if (!GeneralMethods.isSolid(hook.getBlock())) {
			remove();
			return;
		}

		// The "both" slot is pulled by MetalCables itself.
		if (!isHoldingSlot()) {
			parent.setPulling(getPullKey(), false);
			return;
		}

		if (!player.isSneaking()) {
			parent.setPulling(getPullKey(), false);
			if (player.getLocation().distanceSquared(hook) <= 1) {
				remove();
			}
			return;
		}

		Vector pull = GeneralMethods.getDirection(player.getLocation(), hook);
		if (pull.length() > 3) {
			pull.multiply(pullSpeed / pull.length());
		} else if (pull.length() > 1) {
			pull.multiply(1 / pull.length());
		} else {
			pull.multiply(0);
		}

		// Let the player steer (swing) if they're looking close enough to the cable.
		Vector look = player.getLocation().getDirection().multiply(pull.length());
		double angle = pull.lengthSquared() == 0 ? 180 : Math.toDegrees(look.angle(pull));

		parent.setPulling(getPullKey(), true);
		GeneralMethods.setVelocity(this, player, angle < maxSwingAngle ? look : pull);
	}

	private String getPullKey() {
		return "MetalCables_Grapple" + (leftArm ? "Left" : "Right");
	}

	public boolean hasHit() {
		return hit;
	}

	/**
	 * The vector from the user's arm to the anchor point.
	 */
	public Vector getActualDirection() {
		return GeneralMethods.getDirection(getHand(), hook);
	}

	@Override
	public void remove() {
		if (parent != null) {
			parent.setPulling(getPullKey(), false);
		}
		super.remove();
	}

	@Override
	public long getCooldown() {
		return 0;
	}

	@Override
	public String getName() {
		return "MetalCablesGrapple";
	}
}
