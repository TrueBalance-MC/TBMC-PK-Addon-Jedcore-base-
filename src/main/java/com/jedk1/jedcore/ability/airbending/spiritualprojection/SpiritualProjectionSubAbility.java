package com.jedk1.jedcore.ability.airbending.spiritualprojection;

import com.jedk1.jedcore.JedCore;
import com.jedk1.jedcore.configuration.JedCoreConfig;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.SpiritualAbility;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * A mode of {@link SpiritualProjection}, activated by sneaking on its hotbar slot.
 */
public abstract class SpiritualProjectionSubAbility extends SpiritualAbility implements AddonAbility {

	protected SpiritualProjection parent;

	public SpiritualProjectionSubAbility(Player player, SpiritualProjection parent) {
		super(player);
		this.parent = parent;
	}

	/**
	 * Common checks shared by every mode. Returns false if the ability was removed.
	 */
	protected boolean isStillValid() {
		if (!player.isOnline() || player.isDead() || parent == null || parent.isRemoved()) {
			remove();
			return false;
		}
		return true;
	}

	protected ConfigurationSection getProjectionConfig() {
		return JedCoreConfig.getConfig(this.player);
	}

	@Override
	public boolean isHiddenAbility() {
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
		return null;
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
