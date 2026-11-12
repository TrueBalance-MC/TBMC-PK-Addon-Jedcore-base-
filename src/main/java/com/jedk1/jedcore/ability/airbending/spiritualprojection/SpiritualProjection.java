package com.jedk1.jedcore.ability.airbending.spiritualprojection;

import com.jedk1.jedcore.JedCore;
import com.jedk1.jedcore.configuration.JedCoreConfig;
import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.MultiAbility;
import com.projectkorra.projectkorra.ability.SpiritualAbility;
import com.projectkorra.projectkorra.ability.util.MultiAbilityManager;
import com.projectkorra.projectkorra.ability.util.MultiAbilityManager.MultiAbilityInfoSub;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.util.ChatUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A multi-ability that lets a spiritual airbender focus their spiritual energy into attack, defense or mobility.
 * Based on the SpiritualProjection addon originally made by Loony.
 */
public class SpiritualProjection extends SpiritualAbility implements AddonAbility, MultiAbility {

	private static final int MEDITATE = 0;
	private static final int SPIRIT = 1;
	private static final int ASTRAL_ATTACK = 2;
	private static final int SPIRITUAL_DRAIN = 3;
	private static final int EXIT = 4;

	// Energy is kept between uses so leaving and re-entering the multi-ability doesn't refill it.
	private static final Map<UUID, Integer> ENERGY = new ConcurrentHashMap<>();

	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	private int maxEnergy;

	private BossBar energyBar;

	public SpiritualProjection(Player player) {
		super(player);
		if (hasAbility(player, SpiritualProjection.class) || !bPlayer.canBend(this)) {
			return;
		}

		setFields();
		ENERGY.putIfAbsent(player.getUniqueId(), maxEnergy);
		ENERGY.computeIfPresent(player.getUniqueId(), (uuid, energy) -> Math.min(energy, maxEnergy));
		start();

		if (!isRemoved()) {
			MultiAbilityManager.bindMultiAbility(player, getName());
			energyBar = BossBar.bossBar(Component.text()
					.append(Component.text("I ", NamedTextColor.YELLOW, TextDecoration.OBFUSCATED))
					.append(Component.text("Spiritual Energy", NamedTextColor.GRAY, TextDecoration.BOLD))
					.append(Component.text(" I", NamedTextColor.YELLOW, TextDecoration.OBFUSCATED))
					.build(), getEnergyProgress(), BossBar.Color.BLUE, BossBar.Overlay.NOTCHED_12);
			player.showBossBar(energyBar);
		}
	}

	public void setFields() {
		ConfigurationSection config = JedCoreConfig.getConfig(this.player);

		cooldown = config.getLong("Abilities.Air.SpiritualProjection.Cooldown");
		maxEnergy = Math.max(1, config.getInt("Abilities.Air.SpiritualProjection.MaxEnergy"));
	}

	@Override
	public void progress() {
		if (!player.isOnline() || player.isDead() || !bPlayer.canBendIgnoreBindsCooldowns(this)) {
			remove();
			return;
		}
		energyBar.progress(getEnergyProgress());
	}

	/**
	 * Called when the user starts sneaking while SpiritualProjection is their bound multi-ability.
	 */
	public static void activate(Player player) {
		SpiritualProjection projection = getAbility(player, SpiritualProjection.class);
		if (projection == null || !projection.bPlayer.canBendIgnoreBindsCooldowns(projection)) {
			return;
		}

		switch (player.getInventory().getHeldItemSlot()) {
			case MEDITATE -> new SpiritualMeditate(player, projection);
			case SPIRIT -> new SpiritForm(player, projection);
			case ASTRAL_ATTACK -> new AstralAttack(player, projection);
			case SPIRITUAL_DRAIN -> new SpiritualDrain(player, projection);
			case EXIT -> projection.remove();
			default -> {}
		}
	}

	/**
	 * Called when the user left-clicks while SpiritualProjection is their bound multi-ability. Returns to the body
	 * if the user is currently in spirit form.
	 */
	public static void click(Player player) {
		SpiritForm spirit = getAbility(player, SpiritForm.class);
		if (spirit != null) {
			spirit.remove();
		}
	}

	public int getEnergy() {
		return ENERGY.getOrDefault(player.getUniqueId(), 0);
	}

	public int getMaxEnergy() {
		return maxEnergy;
	}

	/**
	 * Takes energy from the user if they have enough, otherwise tells them they don't.
	 */
	public boolean consumeEnergy(int amount) {
		if (getEnergy() < amount) {
			ChatUtil.sendActionBar(Component.text("You do not possess enough spiritual connection to use this ability.", NamedTextColor.GRAY, TextDecoration.BOLD), player);
			return false;
		}
		ENERGY.put(player.getUniqueId(), getEnergy() - amount);
		return true;
	}

	public void addEnergy(int amount) {
		ENERGY.put(player.getUniqueId(), Math.min(maxEnergy, getEnergy() + amount));
	}

	public boolean isFullyCharged() {
		return getEnergy() >= maxEnergy;
	}

	private float getEnergyProgress() {
		return Math.max(0, Math.min(1, getEnergy() / (float) maxEnergy));
	}

	public static void clearEnergy(Player player) {
		ENERGY.remove(player.getUniqueId());
	}

	@Override
	public void remove() {
		super.remove();

		for (Class<? extends SpiritualProjectionSubAbility> type : java.util.List.of(SpiritualMeditate.class, SpiritForm.class, AstralAttack.class, SpiritualDrain.class)) {
			for (SpiritualProjectionSubAbility sub : getAbilities(player, type)) {
				sub.remove();
			}
		}

		if (energyBar != null) {
			player.hideBossBar(energyBar);
		}
		MultiAbilityManager.unbindMultiAbility(player);
		if (player.isOnline()) {
			bPlayer.addCooldown(this);
		}
	}

	@Override
	public ArrayList<MultiAbilityInfoSub> getMultiAbilities() {
		ArrayList<MultiAbilityInfoSub> subs = new ArrayList<>();
		subs.add(new MultiAbilityInfoSub("Meditate", Element.SPIRITUAL));
		subs.add(new MultiAbilityInfoSub("Spirit", Element.SPIRITUAL));
		subs.add(new MultiAbilityInfoSub("AstralAttack", Element.SPIRITUAL));
		subs.add(new MultiAbilityInfoSub("SpiritualDrain", Element.SPIRITUAL));
		subs.add(new MultiAbilityInfoSub("Exit", Element.SPIRITUAL));
		return subs;
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
		return "SpiritualProjection";
	}

	@Override
	public boolean isHarmlessAbility() {
		return true;
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
		return "* JedCore Addon *\n" + config.getString("Abilities.Air.SpiritualProjection.Description");
	}

	@Override
	public void load() {}

	@Override
	public void stop() {}

	@Override
	public boolean isEnabled() {
		ConfigurationSection config = JedCoreConfig.getConfig(this.player);
		return config.getBoolean("Abilities.Air.SpiritualProjection.Enabled");
	}
}
