package dev.latvian.mods.kubejs.item;

import dev.latvian.mods.kubejs.player.PlayerEventJS;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class ItemPickedUpEventJS extends PlayerEventJS {
	private final Player player;
	private final ItemEntity entity;
	private final ItemStack stack;

	public ItemPickedUpEventJS(Player player, ItemEntity entity, ItemStack stack) {
		this.player = player;
		this.entity = entity;
		this.stack = stack;
	}

	@Override
	public Player getEntity() {
		return player;
	}

	public ItemEntity getItemEntity() {
		return entity;
	}

	public ItemStack getItem() {
		return stack;
	}
}