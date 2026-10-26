package com.jedk1.jedcore.ability.earthbending.metalcables;

import com.jedk1.jedcore.JedCore;
import com.jedk1.jedcore.configuration.JedCoreConfig;
import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.MetalAbility;
import com.projectkorra.projectkorra.ability.MultiAbility;
import com.projectkorra.projectkorra.ability.util.MultiAbilityManager;
import com.projectkorra.projectkorra.ability.util.MultiAbilityManager.MultiAbilityInfoSub;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.util.ChatUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Metal cables extended from the user's arms, used to grapple around, pull entities in and slam them.
 * Based on the MetalCables addon originally made by Finn_Bueno_.
 */
public class MetalCables extends MetalAbility implements AddonAbility, MultiAbility {

	private static final int GRAPPLE_LEFT = 0;
	private static final int GRAPPLE_BOTH = 1;
	private static final int GRAPPLE_RIGHT = 2;
	private static final int GRAPPLE_CLEAR = 3;
	private static final int GRAB_LEFT = 4;
	private static final int GRAB_RIGHT = 5;
	private static final int SLAM_LEFT = 6;
	private static final int SLAM_RIGHT = 7;
	private static final int EXIT = 8;
	private static final String PULL_BOTH_KEY = "MetalCables_GrappleBoth";

	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	private int maxUses;
	private boolean requireItems;
	private final Set<Material> helmetTypes = EnumSet.noneOf(Material.class);
	private final Set<Material> chestplateTypes = EnumSet.noneOf(Material.class);
	private final Set<Material> leggingsTypes = EnumSet.noneOf(Material.class);
	private final Set<Material> bootsTypes = EnumSet.noneOf(Material.class);
	private final Set<Material> handTypes = EnumSet.noneOf(Material.class);
	private double grapplePullSpeed;
	private int grabUsePoints;
	private int slamUsePoints;

	private final MetalCablesSubAbility[] activeCables = new MetalCablesSubAbility[EXIT + 1];
	private final Set<String> pullingKeys = new HashSet<>();
	private int uses;
	private int ticks;

	public MetalCables(Player player) {
		super(player);
		if (hasAbility(player, MetalCables.class) || !bPlayer.canBend(this)) {
			return;
		}

		setFields();
		if (!hasAppropriateItems()) {
			return;
		}

		uses = maxUses;
		start();

		if (!isRemoved()) {
			MultiAbilityManager.bindMultiAbility(player, getName());
			playMetalbendingSound(player.getEyeLocation());
		}
	}

	public void setFields() {
		ConfigurationSection config = JedCoreConfig.getConfig(this.player);

		cooldown = config.getLong("Abilities.Earth.MetalCables.Cooldown");
		maxUses = config.getInt("Abilities.Earth.MetalCables.Uses");
		requireItems = config.getBoolean("Abilities.Earth.MetalCables.RequireItems");
		loadMaterials(config, "Abilities.Earth.MetalCables.ArmorRequirements.Helmet", helmetTypes);
		loadMaterials(config, "Abilities.Earth.MetalCables.ArmorRequirements.Chestplate", chestplateTypes);
		loadMaterials(config, "Abilities.Earth.MetalCables.ArmorRequirements.Leggings", leggingsTypes);
		loadMaterials(config, "Abilities.Earth.MetalCables.ArmorRequirements.Boots", bootsTypes);
		loadMaterials(config, "Abilities.Earth.MetalCables.ItemRequirements", handTypes);
		grapplePullSpeed = config.getDouble("Abilities.Earth.MetalCables.Grapple.PullSpeed");
		grabUsePoints = config.getInt("Abilities.Earth.MetalCables.Grab.UsePoints");
		slamUsePoints = config.getInt("Abilities.Earth.MetalCables.Slam.UsePoints");
	}

	private static void loadMaterials(ConfigurationSection config, String path, Set<Material> materials) {
		materials.clear();
		for (String name : config.getStringList(path)) {
			Material material = Material.matchMaterial(name);
			if (material != null) {
				materials.add(material);
			} else {
				JedCore.log.warning("Invalid material '" + name + "' in " + path);
			}
		}
	}

	@Override
	public void progress() {
		if (!player.isOnline() || player.isDead() || !bPlayer.canBendIgnoreBindsCooldowns(this)) {
			remove();
			return;
		}

		if (++ticks % 4 == 0 && !hasAppropriateItems()) {
			remove();
			return;
		}

		if (uses < 1) {
			remove();
			return;
		}

		// Coiled cables hanging from both arms.
		for (boolean leftArm : new boolean[] { true, false }) {
			Location hand = MetalCablesSubAbility.getHand(player, leftArm);
			for (double y = -0.6; y < 0; y += 0.2) {
				MetalCablesSubAbility.displayCableParticle(hand.clone().add(0, y, 0));
			}
		}

		for (int i = 0; i < activeCables.length; i++) {
			if (activeCables[i] != null && activeCables[i].isRemoved()) {
				activeCables[i] = null;
			}
		}

		pullBoth();
	}

	/**
	 * Pulls the user towards the middle of both grapple cables while sneaking on the "both" slot.
	 */
	private void pullBoth() {
		if (!player.isSneaking() || player.getInventory().getHeldItemSlot() != GRAPPLE_BOTH) {
			setPulling(PULL_BOTH_KEY, false);
			return;
		}

		Vector direction = null;
		for (int slot : new int[] { GRAPPLE_LEFT, GRAPPLE_RIGHT }) {
			if (activeCables[slot] instanceof MetalCablesGrapple grapple && grapple.hasHit()) {
				if (direction == null) {
					direction = new Vector();
				}
				direction.add(grapple.getActualDirection());
			}
		}

		if (direction == null) {
			setPulling(PULL_BOTH_KEY, false);
			return;
		}

		direction.multiply(0.5);
		if (direction.length() > 3) {
			direction.multiply(grapplePullSpeed / direction.length());
		} else if (direction.length() > 1) {
			direction.multiply(1 / direction.length());
		} else {
			direction.multiply(0);
		}

		setPulling(PULL_BOTH_KEY, true);
		GeneralMethods.setVelocity(this, player, direction);
	}

	/**
	 * Lets the user be pulled through the air without being kicked for flying. Each cable pulls under its own key so
	 * the user's original flight state is only restored once nothing is pulling them anymore.
	 */
	void setPulling(String key, boolean pulling) {
		if (pulling) {
			if (pullingKeys.add(key)) {
				flightHandler.createInstance(player, key);
			}
			player.setAllowFlight(true);
			player.setFlying(false);
			player.setFallDistance(0);
		} else if (pullingKeys.remove(key)) {
			flightHandler.removeInstance(player, key);
		}
	}

	public static void activate(Player player) {
		MetalCables cables = getAbility(player, MetalCables.class);
		if (cables != null) {
			cables.activateSubAbility();
		}
	}

	private void activateSubAbility() {
		if (!bPlayer.canBendIgnoreBindsCooldowns(this)) {
			return;
		}

		int slot = player.getInventory().getHeldItemSlot();
		switch (slot) {
			case GRAPPLE_LEFT, GRAPPLE_RIGHT -> launch(slot, new MetalCablesGrapple(player, this, slot, slot == GRAPPLE_LEFT), 0);
			case GRAPPLE_BOTH -> {
				launch(GRAPPLE_LEFT, new MetalCablesGrapple(player, this, GRAPPLE_LEFT, true), 0);
				launch(GRAPPLE_RIGHT, new MetalCablesGrapple(player, this, GRAPPLE_RIGHT, false), 0);
			}
			case GRAPPLE_CLEAR -> {
				playMetalbendingSound(player.getEyeLocation());
				removeCable(GRAPPLE_LEFT);
				removeCable(GRAPPLE_RIGHT);
			}
			case GRAB_LEFT, GRAB_RIGHT -> {
				if (!bPlayer.isOnCooldown("MetalCablesGrab")) {
					launch(slot, new MetalCablesGrab(player, this, slot, slot == GRAB_LEFT), grabUsePoints);
				}
			}
			case SLAM_LEFT, SLAM_RIGHT -> {
				if (!bPlayer.isOnCooldown("MetalCablesSlam")) {
					launch(slot, new MetalCablesSlam(player, this, slot, slot == SLAM_LEFT), slamUsePoints);
				}
			}
			case EXIT -> remove();
			default -> {}
		}
	}

	private void launch(int slot, MetalCablesSubAbility cable, int usePoints) {
		if (cable.isRemoved()) {
			return;
		}
		removeCable(slot);
		activeCables[slot] = cable;

		if (usePoints > 0) {
			uses = Math.max(0, uses - usePoints);
			ChatUtil.sendActionBar(Component.text("Uses left: " + uses, Element.METAL.getTextColor()), player);
		}
	}

	private void removeCable(int slot) {
		MetalCablesSubAbility cable = activeCables[slot];
		if (cable != null) {
			cable.remove();
			activeCables[slot] = null;
		}
	}

	/**
	 * Shows the name of the selected cable function and the remaining uses.
	 */
	public void showSlot(int slot) {
		List<MultiAbilityInfoSub> subs = getMultiAbilities();
		if (slot >= 0 && slot < subs.size()) {
			MultiAbilityInfoSub sub = subs.get(slot);
			ChatUtil.sendActionBar(Component.text(sub.getName().replace("_", " ") + " - Uses left: " + uses, Element.METAL.getTextColor()), player);
		}
	}

	private boolean hasAppropriateItems() {
		if (!requireItems) {
			return true;
		}
		PlayerInventory inventory = player.getInventory();
		return isType(inventory.getHelmet(), helmetTypes)
				|| isType(inventory.getChestplate(), chestplateTypes)
				|| isType(inventory.getLeggings(), leggingsTypes)
				|| isType(inventory.getBoots(), bootsTypes)
				|| isType(inventory.getItemInMainHand(), handTypes)
				|| isType(inventory.getItemInOffHand(), handTypes);
	}

	private static boolean isType(ItemStack item, Set<Material> types) {
		return item != null && types.contains(item.getType());
	}

	@Override
	public void remove() {
		super.remove();

		for (int i = 0; i < activeCables.length; i++) {
			removeCable(i);
		}
		for (String key : new ArrayList<>(pullingKeys)) {
			setPulling(key, false);
		}

		MultiAbilityManager.unbindMultiAbility(player);
		if (player.isOnline()) {
			bPlayer.addCooldown(this);
			playMetalbendingSound(player.getEyeLocation());
		}
	}

	@Override
	public ArrayList<MultiAbilityInfoSub> getMultiAbilities() {
		ArrayList<MultiAbilityInfoSub> subs = new ArrayList<>();
		subs.add(new MultiAbilityInfoSub("Grapple_Left", Element.METAL));
		subs.add(new MultiAbilityInfoSub("Grapple_Both", Element.METAL));
		subs.add(new MultiAbilityInfoSub("Grapple_Right", Element.METAL));
		subs.add(new MultiAbilityInfoSub("Grapple_Clear", Element.METAL));
		subs.add(new MultiAbilityInfoSub("Grab_Left", Element.METAL));
		subs.add(new MultiAbilityInfoSub("Grab_Right", Element.METAL));
		subs.add(new MultiAbilityInfoSub("Slam_Left", Element.METAL));
		subs.add(new MultiAbilityInfoSub("Slam_Right", Element.METAL));
		subs.add(new MultiAbilityInfoSub("Exit", Element.METAL));
		return subs;
	}

	public int getUses() {
		return uses;
	}

	public void setUses(int uses) {
		this.uses = uses;
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
		return "MetalCables";
	}

	@Override
	public boolean isHarmlessAbility() {
		return false;
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
		return "* JedCore Addon *\n" + config.getString("Abilities.Earth.MetalCables.Description");
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
