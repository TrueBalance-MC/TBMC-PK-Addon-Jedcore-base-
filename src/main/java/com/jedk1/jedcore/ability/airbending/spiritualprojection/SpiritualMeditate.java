package com.jedk1.jedcore.ability.airbending.spiritualprojection;

import com.projectkorra.projectkorra.ability.AirAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.util.ChatUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Hold sneak to recharge spiritual energy.
 */
public class SpiritualMeditate extends SpiritualProjectionSubAbility {

	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	@Attribute(Attribute.DURATION)
	private long duration;
	private int energyPerTick;

	private int ticks;

	public SpiritualMeditate(Player player, SpiritualProjection parent) {
		super(player, parent);
		if (hasAbility(player, SpiritualMeditate.class) || bPlayer.isOnCooldown(this)) {
			return;
		}

		setFields();
		if (parent.isFullyCharged()) {
			sendFullyCharged();
			return;
		}
		start();
	}

	public void setFields() {
		ConfigurationSection config = getProjectionConfig();

		cooldown = config.getLong("Abilities.Air.SpiritualProjection.Meditate.Cooldown");
		duration = config.getLong("Abilities.Air.SpiritualProjection.Meditate.Duration");
		energyPerTick = config.getInt("Abilities.Air.SpiritualProjection.Meditate.EnergyPerTick");
	}

	@Override
	public void progress() {
		if (!isStillValid()) return;

		if (!player.isSneaking() || System.currentTimeMillis() > getStartTime() + duration) {
			remove();
			return;
		}

		parent.addEnergy(energyPerTick);
		playParticles();

		if (parent.isFullyCharged()) {
			sendFullyCharged();
			remove();
		}
	}

	private void playParticles() {
		ticks++;
		double height = (ticks * 0.1) % 2.5;
		for (int offset : new int[] { 0, 180 }) {
			double angle = Math.toRadians(10 * ticks + offset);
			Location display = player.getLocation().add(Math.cos(angle) * 1.5, height, Math.sin(angle) * 1.5);
			AirAbility.playAirbendingParticles(display, 3, 0, 0, 0);
		}
		if (ticks % 4 == 0) {
			AirAbility.playAirbendingSound(player.getLocation());
		}
	}

	private void sendFullyCharged() {
		ChatUtil.sendActionBar(Component.text("Your spiritual connection has fully recharged.", NamedTextColor.GRAY, TextDecoration.BOLD), player);
	}

	@Override
	public void remove() {
		super.remove();
		bPlayer.addCooldown(this);
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
		return "SpiritualMeditate";
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
