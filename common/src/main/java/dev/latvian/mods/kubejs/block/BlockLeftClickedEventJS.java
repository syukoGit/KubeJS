package dev.latvian.mods.kubejs.block;

import dev.latvian.mods.kubejs.level.BlockContainerJS;
import dev.latvian.mods.kubejs.player.PlayerEventJS;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class BlockLeftClickedEventJS extends PlayerEventJS {
	private final Player player;
	private final InteractionHand hand;
	private final BlockPos pos;
	private final Direction direction;

	public BlockLeftClickedEventJS(Player player, InteractionHand hand, BlockPos pos, Direction direction) {
		this.player = player;
		this.hand = hand;
		this.pos = pos;
		this.direction = direction;
	}

	@Override
	public Player getEntity() {
		return player;
	}

	public BlockContainerJS getBlock() {
		return new BlockContainerJS(player.level, pos);
	}

	public ItemStack getItem() {
		return player.getItemInHand(hand);
	}

	@Nullable
	public Direction getFacing() {
		return direction;
	}
}