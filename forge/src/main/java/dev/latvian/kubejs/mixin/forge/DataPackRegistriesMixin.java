package dev.latvian.kubejs.mixin.forge;

import dev.latvian.kubejs.core.DataPackRegistriesHelper;
import dev.latvian.kubejs.core.DataPackRegistriesKJS;
import net.minecraft.command.Commands;
import net.minecraft.resources.DataPackRegistries;
import net.minecraft.resources.IResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * @author LatvianModder
 */
@Mixin(DataPackRegistries.class)
public abstract class DataPackRegistriesMixin implements DataPackRegistriesKJS
{
	@Inject(method = "<init>", at = @At("RETURN"))
	private void init(CallbackInfo ci)
	{
		initKJS();
	}

	@ModifyArg(method = "loadResources", at = @At(value = "INVOKE", ordinal = 0,
			target = "Lnet/minecraft/resources/IReloadableResourceManager;reload(Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;Ljava/util/List;Ljava/util/concurrent/CompletableFuture;)Ljava/util/concurrent/CompletableFuture;"),
			index = 2)
	private static List<IResourcePack> resourcePackList(List<IResourcePack> list)
	{
		return DataPackRegistriesHelper.getResourcePackListKJS(list);
	}

	@Inject(method = "loadResources", at = @At(value = "INVOKE", target = "Lnet/minecraft/resources/IReloadableResourceManager;reload(Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;Ljava/util/List;Ljava/util/concurrent/CompletableFuture;)Ljava/util/concurrent/CompletableFuture;", shift = At.Shift.BEFORE), cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD)
	private static void doThing(List<IResourcePack> list, Commands.EnvironmentType environmentType, int permissionLevel, Executor executor1, Executor executor2, CallbackInfoReturnable<CompletableFuture> cir, DataPackRegistries dataPackRegistries)
	{

	}
}
