package com.jedk1.jedcore.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * Called when WakeFishing catches a fish, before it's dropped for the player. The fish can be changed (e.g. its amount)
 * or the catch cancelled.
 */
public class WakeFishingCatchEvent extends Event implements Cancellable {

	private static final HandlerList handlers = new HandlerList();
	private final Player player;
	private ItemStack fish;
	private boolean cancelled;

	public WakeFishingCatchEvent(final Player player, final ItemStack fish) {
		this.player = player;
		this.fish = fish;
	}

	/**
	 * @return The player using WakeFishing
	 */
	public Player getPlayer() {
		return player;
	}

	/**
	 * @return The fish that will be dropped
	 */
	public ItemStack getFish() {
		return fish;
	}

	/**
	 * @param fish The item to drop instead
	 */
	public void setFish(final ItemStack fish) {
		this.fish = fish;
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public void setCancelled(final boolean cancelled) {
		this.cancelled = cancelled;
	}

	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}
