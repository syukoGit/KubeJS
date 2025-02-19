package dev.latvian.mods.kubejs.forge;

import dev.latvian.mods.kubejs.BuiltinKubeJSPlugin;
import dev.latvian.mods.kubejs.fluid.FluidStackJS;
import dev.latvian.mods.kubejs.integration.forge.jei.JEIEvents;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.util.ClassFilter;
import dev.latvian.mods.kubejs.util.LegacyCodeHandler;
import dev.latvian.mods.rhino.util.wrap.TypeWrappers;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ModList;

public class BuiltinKubeJSForgePlugin extends BuiltinKubeJSPlugin {
	@Override
	public void registerEvents() {
		super.registerEvents();
		ForgeKubeJSEvents.register();

		if (ModList.get().isLoaded("jei")) {
			JEIEvents.register();
		}
	}

	@Override
	public void registerClasses(ScriptType type, ClassFilter filter) {
		super.registerClasses(type, filter);

		filter.allow("net.minecraftforge"); // Forge
		filter.deny("net.minecraftforge.fml");
		filter.deny("net.minecraftforge.accesstransformer");
		filter.deny("net.minecraftforge.coremod");

		filter.deny("cpw.mods.modlauncher"); // FML
		filter.deny("cpw.mods.gross");
	}

	@Override
	public void registerBindings(BindingsEvent event) {
		super.registerBindings(event);

		if (event.getType().isStartup()) {
			event.add("ForgeEvents", ForgeEventWrapper.class);
			event.add("onForgeEvent", new LegacyCodeHandler("onForgeEvent()"));
		}
	}

	@Override
	public void registerTypeWrappers(ScriptType type, TypeWrappers typeWrappers) {
		super.registerTypeWrappers(type, typeWrappers);

		typeWrappers.registerSimple(FluidStack.class, o -> {
			var fs = FluidStackJS.of(o);
			return fs.kjs$isEmpty() ? FluidStack.EMPTY : new FluidStack(fs.getFluid(), (int) fs.kjs$getAmount(), fs.getNbt());
		});
	}
}
