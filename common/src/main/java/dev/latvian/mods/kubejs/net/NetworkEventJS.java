package dev.latvian.mods.kubejs.net;

import dev.latvian.mods.kubejs.player.PlayerEventJS;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public class NetworkEventJS extends PlayerEventJS {
	private final Player player;
	private final String channel;
	private final CompoundTag data;

	public NetworkEventJS(Player p, String c, @Nullable CompoundTag d) {
		player = p;
		channel = c;
		data = d;
	}

	@Override
	public Player getEntity() {
		return player;
	}

	public String getChannel() {
		return channel;
	}

	@Nullable
	public CompoundTag getData() {
		return data;
	}
}