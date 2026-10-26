package com.jedk1.jedcore.util;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

/**
 * Persistent data container markers used to identify entities spawned by JedCore abilities.
 */
public final class EntityTags {

	public static final NamespacedKey DAGGER_THROW = new NamespacedKey("jedcore", "daggerthrow");
	public static final NamespacedKey METAL_HOOK = new NamespacedKey("jedcore", "metalhook");
	public static final NamespacedKey MAGMA_BLAST = new NamespacedKey("jedcore", "magmablast");

	private EntityTags() {
	}

	public static void tag(Entity entity, NamespacedKey key) {
		entity.getPersistentDataContainer().set(key, PersistentDataType.BOOLEAN, true);
	}

	public static boolean has(Entity entity, NamespacedKey key) {
		return entity.getPersistentDataContainer().has(key, PersistentDataType.BOOLEAN);
	}
}
