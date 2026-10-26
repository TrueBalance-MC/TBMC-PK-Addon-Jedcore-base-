package com.jedk1.jedcore;

import java.io.IOException;
import java.util.logging.*;

import com.google.common.reflect.ClassPath;
import com.jedk1.jedcore.util.*;
import com.jedk1.jedcore.util.versionadapter.PaperParticleAdapter;
import com.jedk1.jedcore.util.versionadapter.PaperPotionEffectAdapter;
import com.jedk1.jedcore.util.versionadapter.ParticleAdapter;
import com.jedk1.jedcore.util.versionadapter.PotionEffectAdapter;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import com.jedk1.jedcore.command.Commands;
import com.jedk1.jedcore.configuration.JedCoreConfig;
import com.jedk1.jedcore.listener.AbilityListener;
import com.jedk1.jedcore.listener.CommandListener;
import com.jedk1.jedcore.listener.JCListener;
import com.projectkorra.projectkorra.ability.CoreAbility;
import org.bukkit.scheduler.BukkitRunnable;

public class JedCore extends JavaPlugin {

	public static JedCore plugin;
	public static Logger log;
	public static String dev;
	public static String version;
	public static boolean logDebug;

    private ParticleAdapter particleAdapter;
	private PotionEffectAdapter potionEffectAdapter;

	@Override
	public void onEnable() {
		plugin = this;
		JedCore.log = this.getLogger();
		new JedCoreConfig(this);

		logDebug = JedCoreConfig.getConfig((World)null).getBoolean("Properties.LogDebug");
		
		dev = String.join(", ", this.getPluginMeta().getAuthors());
		version = this.getPluginMeta().getVersion();

		JCMethods.registerDisabledWorlds();
		CoreAbility.registerPluginAbilities(plugin, "com.jedk1.jedcore.ability");
		getServer().getPluginManager().registerEvents(new AbilityListener(this), this);
		getServer().getPluginManager().registerEvents(new CommandListener(this), this);
		getServer().getPluginManager().registerEvents(new JCListener(this), this);
		getServer().getPluginManager().registerEvents(new ChiRestrictor(), this);
		getServer().getScheduler().scheduleSyncRepeatingTask(this, new JCManager(this), 0, 1);

		new Commands();

		FireTick.loadMethod();

		particleAdapter = new PaperParticleAdapter();
		potionEffectAdapter = new PaperPotionEffectAdapter();

		checkMaintainer();

		new BukkitRunnable() {
			@Override
			public void run() {
				JCMethods.registerCombos();
				initializeCollisions();
			}
		}.runTaskLater(this, 1);
	}

	public static void checkMaintainer() {
		if (!dev.contains("Cozmyc (Maintainer)")) {
			dev = dev  + ", Cozmyc (Maintainer)";
		}
	}

	public void initializeCollisions() {
		boolean enabled = this.getConfig().getBoolean("Properties.AbilityCollisions.Enabled");

		if (!enabled) {
			getLogger().info("Collisions disabled.");
			return;
		}

		try {
			ClassPath cp = ClassPath.from(this.getClassLoader());

			for (ClassPath.ClassInfo info : cp.getTopLevelClassesRecursive("com.jedk1.jedcore.ability")) {
				try {
					@SuppressWarnings("unchecked")
					Class<? extends CoreAbility> abilityClass = (Class<? extends CoreAbility>)Class.forName(info.getName());

					if (abilityClass == null) continue;

					CollisionInitializer initializer = new CollisionInitializer<>(abilityClass);
					initializer.initialize();
				} catch (Exception e) {

				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void onDisable() {
		RegenTempBlock.revertAll();
	}

	public static void logDebug(String message) {
		if (logDebug) {
			plugin.getLogger().info(message);
		}
	}

	public ParticleAdapter getParticleAdapter() {
		return this.particleAdapter;
	}

	public PotionEffectAdapter getPotionEffectAdapter() {
		return this.potionEffectAdapter;
	}
}
