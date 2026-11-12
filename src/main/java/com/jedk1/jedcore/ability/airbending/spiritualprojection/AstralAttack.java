package com.jedk1.jedcore.ability.airbending.spiritualprojection;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AirAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.command.Commands;
import com.projectkorra.projectkorra.region.RegionProtection;
import com.projectkorra.projectkorra.util.DamageHandler;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.util.Vector;

/**
 * Sneak to send out an astral projection that damages the first creature it touches. Keep sneaking to steer it with
 * your view; release sneak and it carries on in a straight line.
 */
public class AstralAttack extends SpiritualProjectionSubAbility {

	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	@Attribute(Attribute.DURATION)
	private long duration;
	@Attribute(Attribute.SPEED)
	private double speed;
	@Attribute(Attribute.DAMAGE)
	private double damage;
	@Attribute(Attribute.KNOCKBACK)
	private double knockback;
	@Attribute(Attribute.RADIUS)
	private double hitRadius;
	private int energyCost;

	private Location location;
	private Vector direction;
	private ArmorStand projection;

	public AstralAttack(Player player, SpiritualProjection parent) {
		super(player, parent);
		if (hasAbility(player, AstralAttack.class) || bPlayer.isOnCooldown(this)) {
			return;
		}

		setFields();
		if (!parent.consumeEnergy(energyCost)) {
			return;
		}

		location = player.getLocation();
		direction = player.getEyeLocation().getDirection();
		start();
		if (!isRemoved()) {
			spawnProjection();
		}
	}

	public void setFields() {
		ConfigurationSection config = getProjectionConfig();

		cooldown = config.getLong("Abilities.Air.SpiritualProjection.AstralAttack.Cooldown");
		duration = config.getLong("Abilities.Air.SpiritualProjection.AstralAttack.Duration");
		speed = config.getDouble("Abilities.Air.SpiritualProjection.AstralAttack.Speed");
		damage = config.getDouble("Abilities.Air.SpiritualProjection.AstralAttack.Damage");
		knockback = config.getDouble("Abilities.Air.SpiritualProjection.AstralAttack.Knockback");
		hitRadius = config.getDouble("Abilities.Air.SpiritualProjection.AstralAttack.HitRadius");
		energyCost = config.getInt("Abilities.Air.SpiritualProjection.AstralAttack.Energy");
	}

	private void spawnProjection() {
		projection = location.getWorld().spawn(location, ArmorStand.class, stand -> {
			stand.setInvisible(true);
			stand.setMarker(true);
			stand.setGravity(false);
			stand.setInvulnerable(true);
			stand.setPersistent(false);
			stand.setSilent(true);

			EntityEquipment equipment = stand.getEquipment();
			equipment.setHelmet(whiteArmor(Material.LEATHER_HELMET));
			equipment.setChestplate(whiteArmor(Material.LEATHER_CHESTPLATE));
			equipment.setLeggings(whiteArmor(Material.LEATHER_LEGGINGS));
			equipment.setBoots(whiteArmor(Material.LEATHER_BOOTS));
		});
	}

	private static ItemStack whiteArmor(Material type) {
		ItemStack item = new ItemStack(type);
		LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
		meta.setColor(Color.WHITE);
		item.setItemMeta(meta);
		return item;
	}

	@Override
	public void progress() {
		if (!isStillValid()) return;

		if (projection == null || !projection.isValid() || System.currentTimeMillis() > getStartTime() + duration) {
			remove();
			return;
		}

		// Steer with the user's view while they keep sneaking.
		if (player.isSneaking() && player.getWorld() == location.getWorld()) {
			direction = player.getEyeLocation().getDirection();
		}

		location.add(direction.clone().multiply(speed));
		location.setDirection(direction);
		Location center = location.clone().add(0, 1, 0);

		if (GeneralMethods.isSolid(center.getBlock()) || RegionProtection.isRegionProtected(this, center)) {
			remove();
			return;
		}

		projection.teleport(location);
		AirAbility.playAirbendingParticles(center, 10, 0.3f, 0.6f, 0.3f);

		for (Entity entity : GeneralMethods.getEntitiesAroundPoint(center, hitRadius)) {
			if (entity instanceof LivingEntity target && canHit(entity)) {
				hit(target, center);
				return;
			}
		}
	}

	private boolean canHit(Entity entity) {
		if (entity.getEntityId() == player.getEntityId() || entity instanceof ArmorStand || SpiritForm.isBody(entity)) return false;
		if (entity instanceof Player target && (target.getGameMode() == GameMode.SPECTATOR || Commands.invincible.contains(target.getName()))) {
			return false;
		}
		return !RegionProtection.isRegionProtected(this, entity.getLocation());
	}

	private void hit(LivingEntity target, Location center) {
		DamageHandler.damageEntity(target, damage, this);
		GeneralMethods.setVelocity(this, target, direction.clone().normalize().multiply(knockback));
		AirAbility.breakBreathbendingHold(target);

		center.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);
		center.getWorld().playSound(center, Sound.ENTITY_GHAST_SCREAM, 1, 1);
		remove();
	}

	@Override
	public void remove() {
		super.remove();
		bPlayer.addCooldown(this);
		if (projection != null) {
			projection.remove();
		}
	}

	@Override
	public long getCooldown() {
		return cooldown;
	}

	@Override
	public Location getLocation() {
		return location;
	}

	@Override
	public String getName() {
		return "AstralAttack";
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
