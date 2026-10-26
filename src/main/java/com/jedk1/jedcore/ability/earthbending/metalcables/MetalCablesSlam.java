package com.jedk1.jedcore.ability.earthbending.metalcables;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.command.Commands;
import com.projectkorra.projectkorra.region.RegionProtection;
import com.projectkorra.projectkorra.util.DamageHandler;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class MetalCablesSlam extends MetalCablesSubAbility {

	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	@Attribute(Attribute.RANGE)
	private double range;
	@Attribute(Attribute.SPEED)
	private double speed;
	@Attribute(Attribute.DAMAGE)
	private double damage;

	public MetalCablesSlam(Player player, MetalCables parent, int slot, boolean leftArm) {
		super(player, parent, slot, leftArm);
		setFields();
		start();
		if (!isRemoved()) {
			bPlayer.addCooldown(this);
		}
	}

	public void setFields() {
		ConfigurationSection config = getCablesConfig();

		cooldown = config.getLong("Abilities.Earth.MetalCables.Slam.Cooldown");
		range = config.getDouble("Abilities.Earth.MetalCables.Slam.Range");
		speed = config.getDouble("Abilities.Earth.MetalCables.Slam.Speed");
		damage = config.getDouble("Abilities.Earth.MetalCables.Slam.Damage");
	}

	@Override
	public void progress() {
		if (!isStillValid()) return;

		moveCable();
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
				if (!(entity instanceof LivingEntity target) || !canHit(entity)) continue;

				// Damage is attributed to MetalCables so the death message uses its name.
				DamageHandler.damageEntity(target, damage, parent);
				playMetalbendingSound(target.getLocation());
				playMetalbendingSound(player.getLocation());
				remove();
				return;
			}
		}
	}

	private boolean canHit(Entity entity) {
		if (entity.getEntityId() == player.getEntityId() || entity instanceof ArmorStand) return false;
		if (entity instanceof Player target && (target.getGameMode() == GameMode.SPECTATOR || Commands.invincible.contains(target.getName()))) {
			return false;
		}
		return !RegionProtection.isRegionProtected(this, entity.getLocation());
	}

	@Override
	public long getCooldown() {
		return cooldown;
	}

	@Override
	public String getName() {
		return "MetalCablesSlam";
	}
}
