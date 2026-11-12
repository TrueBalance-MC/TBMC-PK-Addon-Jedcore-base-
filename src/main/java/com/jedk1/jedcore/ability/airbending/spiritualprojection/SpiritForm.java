package com.jedk1.jedcore.ability.airbending.spiritualprojection;

import com.projectkorra.projectkorra.ability.Ability;
import com.projectkorra.projectkorra.ability.PassiveAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.util.ChatUtil;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.PlayerInventory;

/**
 * Leave your body behind and roam as an untouchable spirit for a while. Left-click to return to your body.
 */
public class SpiritForm extends SpiritualProjectionSubAbility {

	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	@Attribute(Attribute.DURATION)
	private long duration;
	private int energyCost;

	private Location bodyLocation;
	private Mannequin body;

	// The player's state before leaving their body, restored when they return.
	private boolean wasInvulnerable;
	private boolean wasGlowing;
	private boolean couldPickupItems;

	public SpiritForm(Player player, SpiritualProjection parent) {
		super(player, parent);
		if (hasAbility(player, SpiritForm.class) || bPlayer.isOnCooldown(this)) {
			return;
		}

		setFields();
		if (!player.getLocation().subtract(0, 0.1, 0).getBlock().getType().isSolid()) {
			ChatUtil.sendActionBar(Component.text("You must be standing on solid ground to use this ability.", NamedTextColor.GRAY, TextDecoration.BOLD), player);
			return;
		}
		if (!parent.consumeEnergy(energyCost)) {
			return;
		}

		bodyLocation = player.getLocation();
		start();
		if (isRemoved()) {
			return;
		}

		spawnBody();
		wasInvulnerable = player.isInvulnerable();
		wasGlowing = player.isGlowing();
		couldPickupItems = player.getCanPickupItems();

		player.setInvulnerable(true);
		player.setGlowing(true);
		player.setCanPickupItems(false);
		flightHandler.createInstance(player, getName());
		player.setAllowFlight(true);
		player.setFlying(true);
	}

	public void setFields() {
		ConfigurationSection config = getProjectionConfig();

		cooldown = config.getLong("Abilities.Air.SpiritualProjection.Spirit.Cooldown");
		duration = config.getLong("Abilities.Air.SpiritualProjection.Spirit.Duration");
		energyCost = config.getInt("Abilities.Air.SpiritualProjection.Spirit.Energy");
	}

	private void spawnBody() {
		body = bodyLocation.getWorld().spawn(bodyLocation, Mannequin.class, mannequin -> {
			mannequin.setProfile(ResolvableProfile.resolvableProfile(player.getPlayerProfile()));
			mannequin.setDescription(null);
			mannequin.customName(player.name());
			mannequin.setCustomNameVisible(true);
			mannequin.setImmovable(true);
			mannequin.setInvulnerable(true);
			mannequin.setPersistent(false);
			mannequin.setRemoveWhenFarAway(false);

			PlayerInventory inventory = player.getInventory();
			EntityEquipment equipment = mannequin.getEquipment();
			for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.HAND, EquipmentSlot.OFF_HAND }) {
				equipment.setItem(slot, inventory.getItem(slot).clone());
				equipment.setDropChance(slot, 0);
			}
		});
	}

	@Override
	public void progress() {
		if (!isStillValid()) return;

		if (System.currentTimeMillis() > getStartTime() + duration || body == null || !body.isValid()) {
			remove();
			return;
		}

		// Keep the spirit floating.
		if (!player.isFlying()) {
			player.setAllowFlight(true);
			player.setFlying(true);
		}
	}

	/**
	 * Returns true if the entity is the body of a player currently in spirit form.
	 */
	public static boolean isBody(Entity entity) {
		for (SpiritForm spirit : getAbilities(SpiritForm.class)) {
			if (spirit.body != null && spirit.body.getEntityId() == entity.getEntityId()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Spirits can only use SpiritualProjection's own modes, not regular bending.
	 */
	public static boolean canUseWhileSpirit(Player player, Ability ability) {
		return !hasAbility(player, SpiritForm.class) || ability instanceof PassiveAbility
				|| ability instanceof SpiritualProjectionSubAbility || ability instanceof SpiritualProjection;
	}

	public static boolean isSpirit(Player player) {
		return hasAbility(player, SpiritForm.class);
	}

	@Override
	public void remove() {
		super.remove();
		bPlayer.addCooldown(this);

		// Nothing to restore if the spirit never left its body (e.g. the start was cancelled).
		if (body == null) {
			return;
		}

		body.remove();
		player.setInvulnerable(wasInvulnerable);
		player.setGlowing(wasGlowing);
		player.setCanPickupItems(couldPickupItems);
		player.setFallDistance(0);
		flightHandler.removeInstance(player, getName());
		player.teleport(bodyLocation);
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
		return "SpiritForm";
	}

	@Override
	public boolean isHarmlessAbility() {
		return true;
	}

	@Override
	public boolean isSneakAbility() {
		return true;
	}
}
