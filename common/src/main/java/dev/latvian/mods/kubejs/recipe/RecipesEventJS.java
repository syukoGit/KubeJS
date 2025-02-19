package dev.latvian.mods.kubejs.recipe;

import com.google.common.base.Stopwatch;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.architectury.platform.Platform;
import dev.latvian.mods.kubejs.CommonProperties;
import dev.latvian.mods.kubejs.DevProperties;
import dev.latvian.mods.kubejs.bindings.event.ServerEvents;
import dev.latvian.mods.kubejs.event.EventJS;
import dev.latvian.mods.kubejs.item.ingredient.IngredientWithCustomPredicate;
import dev.latvian.mods.kubejs.item.ingredient.TagContext;
import dev.latvian.mods.kubejs.platform.RecipePlatformHelper;
import dev.latvian.mods.kubejs.recipe.filter.ConstantFilter;
import dev.latvian.mods.kubejs.recipe.filter.IDFilter;
import dev.latvian.mods.kubejs.recipe.filter.RecipeFilter;
import dev.latvian.mods.kubejs.recipe.schema.JsonRecipeSchema;
import dev.latvian.mods.kubejs.recipe.schema.RecipeNamespace;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchema;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaType;
import dev.latvian.mods.kubejs.recipe.special.SpecialRecipeSerializerManager;
import dev.latvian.mods.kubejs.registry.KubeJSRegistries;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.server.DataExport;
import dev.latvian.mods.kubejs.server.KubeJSReloadListener;
import dev.latvian.mods.kubejs.util.ConsoleJS;
import dev.latvian.mods.kubejs.util.JsonIO;
import dev.latvian.mods.kubejs.util.UtilsJS;
import dev.latvian.mods.rhino.mod.util.JsonUtils;
import dev.latvian.mods.rhino.util.HideFromJS;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RecipesEventJS extends EventJS {
	private static final Pattern SKIP_ERROR = Pattern.compile("at\\s+dev\\.latvian\\.mods\\.kubejs\\.recipe\\.RecipesEventJS\\.post");
	public static Map<UUID, IngredientWithCustomPredicate> customIngredientMap = null;
	public static Map<UUID, ModifyRecipeResultCallback> modifyResultCallbackMap = null;

	public static RecipesEventJS instance;

	public final Map<ResourceLocation, RecipeJS> originalRecipes;
	public final Collection<RecipeJS> addedRecipes;
	public final Collection<RecipeJS> removedRecipes;
	final Map<String, Object> recipeFunctions;
	public final AtomicInteger failedCount;

	public final RecipeTypeFunction shaped;
	public final RecipeTypeFunction shapeless;
	public final RecipeTypeFunction smelting;
	public final RecipeTypeFunction blasting;
	public final RecipeTypeFunction smoking;
	public final RecipeTypeFunction campfireCooking;
	public final RecipeTypeFunction stonecutting;
	public final RecipeTypeFunction smithing;

	RecipeSerializer<?> stageSerializer;

	public RecipesEventJS() {
		ConsoleJS.SERVER.info("Scanning recipes...");
		originalRecipes = new HashMap<>();
		addedRecipes = new ConcurrentLinkedQueue<>();
		removedRecipes = new ConcurrentLinkedQueue<>();
		recipeFunctions = new HashMap<>();

		failedCount = new AtomicInteger(0);

		var allNamespaces = RecipeNamespace.getAll();

		for (var namespace : allNamespaces.values()) {
			var nsMap = new HashMap<String, RecipeTypeFunction>();
			recipeFunctions.put(namespace.name, new NamespaceFunction(namespace, nsMap));

			for (var entry : namespace.entrySet()) {
				var func = new RecipeTypeFunction(this, entry.getValue());
				nsMap.put(entry.getValue().id.getPath(), func);
				recipeFunctions.put(entry.getValue().id.toString(), func);
			}
		}

		shaped = (RecipeTypeFunction) recipeFunctions.get(CommonProperties.get().serverOnly ? "minecraft:crafting_shaped" : "kubejs:shaped");
		shapeless = (RecipeTypeFunction) recipeFunctions.get(CommonProperties.get().serverOnly ? "minecraft:crafting_shapeless" : "kubejs:shapeless");
		smelting = (RecipeTypeFunction) recipeFunctions.get("minecraft:smelting");
		blasting = (RecipeTypeFunction) recipeFunctions.get("minecraft:blasting");
		smoking = (RecipeTypeFunction) recipeFunctions.get("minecraft:smoking");
		campfireCooking = (RecipeTypeFunction) recipeFunctions.get("minecraft:campfire_cooking");
		stonecutting = (RecipeTypeFunction) recipeFunctions.get("minecraft:stonecutting");
		smithing = (RecipeTypeFunction) recipeFunctions.get("minecraft:smithing");

		for (var entry : new ArrayList<>(recipeFunctions.entrySet())) {
			if (entry.getValue() instanceof RecipeTypeFunction && entry.getKey().indexOf(':') != -1) {
				var s = UtilsJS.snakeCaseToCamelCase(entry.getKey());

				if (!s.equals(entry.getKey())) {
					recipeFunctions.put(s, entry.getValue());
				}
			}
		}

		for (var entry : RecipeNamespace.getMappedRecipes().entrySet()) {
			var type = recipeFunctions.get(entry.getValue().toString());

			if (type instanceof RecipeTypeFunction) {
				recipeFunctions.put(entry.getKey(), type);
			}
		}

		recipeFunctions.put("shaped", shaped);
		recipeFunctions.put("shapeless", shapeless);
		recipeFunctions.put("smelting", smelting);
		recipeFunctions.put("blasting", blasting);
		recipeFunctions.put("smoking", smoking);
		recipeFunctions.put("campfireCooking", campfireCooking);
		recipeFunctions.put("stonecutting", stonecutting);
		recipeFunctions.put("smithing", smithing);

		stageSerializer = KubeJSRegistries.recipeSerializers().get(new ResourceLocation("recipestages:stage"));
	}

	@HideFromJS
	public void post(RecipeManager recipeManager, Map<ResourceLocation, JsonObject> datapackRecipeMap) {
		RecipeJS.itemErrors = false;

		TagContext.INSTANCE.setValue(KubeJSReloadListener.resources.tagManager.getResult()
				.stream()
				.filter(result -> result.key() == Registry.ITEM_REGISTRY)
				.findFirst()
				.map(result -> TagContext.usingResult(UtilsJS.cast(result)))
				.orElseGet(() -> {
					ConsoleJS.SERVER.warn("Failed to load item tags during recipe event! Using replaceInput etc. will not work!");
					return TagContext.EMPTY;
				}));

		var timer = Stopwatch.createStarted();

		var allRecipeMap = new JsonObject();

		for (var entry : datapackRecipeMap.entrySet()) {
			var recipeId = entry.getKey();

			if (recipeId == null || entry.getValue() == null || entry.getValue().size() == 0) {
				continue;
			}

			if (Platform.isForge() && recipeId.getPath().startsWith("_")) {
				continue; //Forge: filter anything beginning with "_" as it's used for metadata.
			}

			var recipeIdAndType = recipeId + "[unknown:type]";
			JsonObject json;

			try {
				json = RecipePlatformHelper.get().checkConditions(entry.getValue());
			} catch (Exception ex) {
				if (DevProperties.get().logSkippedRecipes) {
					ConsoleJS.SERVER.info("Skipping recipe " + recipeId + ", failed to check conditions: " + ex);
				}

				continue;
			}

			if (json == null) {
				if (DevProperties.get().logSkippedRecipes) {
					ConsoleJS.SERVER.info("Skipping recipe " + recipeId + ", conditions not met");
				}

				continue;
			} else if (!json.has("type")) {
				if (DevProperties.get().logSkippedRecipes) {
					ConsoleJS.SERVER.info("Skipping recipe " + recipeId + ", missing type");
				}

				continue;
			}

			if (DataExport.dataExport != null) {
				allRecipeMap.add(recipeId.toString(), JsonUtils.copy(json));
			}

			var typeStr = GsonHelper.getAsString(json, "type");
			recipeIdAndType = recipeId + "[" + typeStr + "]";
			var type = getRecipeFunction(typeStr);

			if (type == null) {
				if (DevProperties.get().logSkippedRecipes) {
					ConsoleJS.SERVER.info("Skipping recipe " + recipeId + ", unknown type: " + typeStr);
				}

				continue;
			}

			try {
				var recipe = type.schemaType.schema.deserialize(type, recipeId, json);
				recipe.afterLoaded();
				originalRecipes.put(recipeId, recipe);

				if (ConsoleJS.SERVER.shouldPrintDebug()) {
					if (SpecialRecipeSerializerManager.INSTANCE.isSpecial(recipe.getOriginalRecipe())) {
						ConsoleJS.SERVER.debug("Loaded recipe " + recipeIdAndType + ": <dynamic>");
					} else {
						ConsoleJS.SERVER.debug("Loaded recipe " + recipeIdAndType + ": " + recipe.getFromToString());
					}
				}
			} catch (Throwable ex) {
				if (DevProperties.get().logErroringRecipes) {
					ConsoleJS.SERVER.warn("Failed to parse recipe '" + recipeIdAndType + "'! Falling back to vanilla", ex, SKIP_ERROR);
				}

				try {
					originalRecipes.put(recipeId, JsonRecipeSchema.SCHEMA.deserialize(type, recipeId, json));
				} catch (NullPointerException | IllegalArgumentException | JsonParseException ex2) {
					if (DevProperties.get().logErroringRecipes) {
						ConsoleJS.SERVER.warn("Failed to parse recipe " + recipeIdAndType, ex2, SKIP_ERROR);
					}
				} catch (Exception ex3) {
					ConsoleJS.SERVER.warn("Failed to parse recipe " + recipeIdAndType + ":");
					ConsoleJS.SERVER.printStackTrace(false, ex3, SKIP_ERROR);
				}
			}
		}

		ConsoleJS.SERVER.info("Found " + originalRecipes.size() + " recipes in " + timer.stop());

		timer.reset().start();
		ServerEvents.RECIPES.post(ScriptType.SERVER, this);

		int modifiedCount = 0;

		for (var r : originalRecipes.values()) {
			if (r.changed) {
				modifiedCount++;
			}
		}

		ConsoleJS.SERVER.info("Posted recipe events in " + timer.stop());

		timer.reset().start();
		addedRecipes.removeIf(r -> !r.newRecipe);

		var recipesByName = new HashMap<ResourceLocation, Recipe<?>>(originalRecipes.size() + addedRecipes.size());

		try {
			recipesByName.putAll(originalRecipes.values().parallelStream()
					.map(recipe -> {
						try {
							return recipe.createRecipe();
						} catch (Throwable ex) {
							ConsoleJS.SERVER.warn("Error parsing recipe " + recipe + ": " + recipe.json, ex, SKIP_ERROR);
							failedCount.incrementAndGet();
							return null;
						}
					})
					.filter(Objects::nonNull)
					.collect(Collectors.toMap(Recipe::getId, Function.identity())));
		} catch (Throwable ex) {
			ConsoleJS.SERVER.error("Error creating datapack recipes", ex, SKIP_ERROR);
		}

		try {
			recipesByName.putAll(addedRecipes.parallelStream()
					.map(recipe -> {
						try {
							return recipe.createRecipe();
						} catch (Throwable ex) {
							ConsoleJS.SERVER.warn("Error parsing recipe " + recipe + ": " + recipe.json, ex, SKIP_ERROR);
							failedCount.incrementAndGet();
							return null;
						}
					})
					.filter(Objects::nonNull)
					.collect(Collectors.toMap(Recipe::getId, Function.identity())));
		} catch (Throwable ex) {
			ConsoleJS.SERVER.error("Error creating script recipes", ex, SKIP_ERROR);
		}

		if (DataExport.dataExport != null) {
			for (var r : removedRecipes) {
				if (allRecipeMap.get(r.getId()) instanceof JsonObject json) {
					json.addProperty("removed", true);
				}
			}

			DataExport.dataExport.add("recipes", allRecipeMap);
		}

		var newRecipeMap = new HashMap<RecipeType<?>, Map<ResourceLocation, Recipe<?>>>();

		for (var entry : recipesByName.entrySet()) {
			var type = entry.getValue().getType();
			var recipes = newRecipeMap.computeIfAbsent(type, k -> new HashMap<>());
			recipes.put(entry.getKey(), entry.getValue());
		}

		RecipePlatformHelper.get().pingNewRecipes(newRecipeMap);
		recipeManager.byName = recipesByName;
		recipeManager.recipes = newRecipeMap;
		ConsoleJS.SERVER.info("Added " + addedRecipes.size() + " recipes, removed " + removedRecipes.size() + " recipes, modified " + modifiedCount + " recipes, with " + failedCount.get() + " failed recipes in " + timer.stop());
		RecipeJS.itemErrors = false;

		if (DevProperties.get().debugInfo) {
			ConsoleJS.SERVER.info("======== Debug output of all added recipes ========");

			for (var r : addedRecipes) {
				ConsoleJS.SERVER.info(r.getOrCreateId() + ": " + r.json);
			}

			ConsoleJS.SERVER.info("======== Debug output of all modified recipes ========");

			for (var r : originalRecipes.values()) {
				if (r.changed) {
					ConsoleJS.SERVER.info(r.getOrCreateId() + ": " + r.json + " FROM " + r.originalJson);
				}
			}

			ConsoleJS.SERVER.info("======== Debug output of all removed recipes ========");

			for (var r : removedRecipes) {
				ConsoleJS.SERVER.info(r.getOrCreateId() + ": " + r.json);
			}
		}
	}

	public Map<String, Object> getRecipes() {
		return recipeFunctions;
	}

	public RecipeJS addRecipe(RecipeJS r, boolean json) {
		addedRecipes.add(r);

		if (DevProperties.get().logAddedRecipes) {
			ConsoleJS.SERVER.info("+ " + r.getType() + ": " + r.getFromToString() + (json ? " [json]" : ""));
		} else if (ConsoleJS.SERVER.shouldPrintDebug()) {
			ConsoleJS.SERVER.debug("+ " + r.getType() + ": " + r.getFromToString() + (json ? " [json]" : ""));
		}

		return r;
	}

	public RecipeFilter customFilter(RecipeFilter filter) {
		return filter;
	}

	public Stream<RecipeJS> recipeStream(RecipeFilter filter, boolean async) {
		if (filter == ConstantFilter.FALSE) {
			return Stream.empty();
		}

		Stream<RecipeJS> stream;

		if (async && CommonProperties.get().allowAsyncStreams) {
			stream = originalRecipes.values().parallelStream();
		} else {
			stream = originalRecipes.values().stream();
		}

		if (filter != ConstantFilter.TRUE) {
			stream = stream.filter(filter);
		}

		return stream;
	}

	public void forEachRecipe(RecipeFilter filter, Consumer<RecipeJS> consumer) {
		recipeStream(filter, false).forEach(consumer);
	}

	public int countRecipes(RecipeFilter filter) {
		return (int) recipeStream(filter, true).count();
	}

	public boolean containsRecipe(RecipeFilter filter) {
		return recipeStream(filter, true).anyMatch(filter);
	}

	public void remove(RecipeFilter filter) {
		if (filter instanceof IDFilter id) {
			var r = originalRecipes.remove(id.id);

			if (r != null && !r.removed) {
				r.removed = true;
				removedRecipes.add(r);

				if (DevProperties.get().logRemovedRecipes) {
					ConsoleJS.SERVER.info("- " + r + ": " + r.getFromToString());
				} else if (ConsoleJS.SERVER.shouldPrintDebug()) {
					ConsoleJS.SERVER.debug("- " + r + ": " + r.getFromToString());
				}
			}

			return;
		}

		for (var r : recipeStream(filter, true).toList()) {
			if (!r.removed) {
				r.removed = true;
				removedRecipes.add(r);
				originalRecipes.remove(r.id);

				if (DevProperties.get().logRemovedRecipes) {
					ConsoleJS.SERVER.info("- " + r + ": " + r.getFromToString());
				} else if (ConsoleJS.SERVER.shouldPrintDebug()) {
					ConsoleJS.SERVER.debug("- " + r + ": " + r.getFromToString());
				}
			}
		}
	}

	public void replaceInput(RecipeFilter filter, ReplacementMatch match, InputReplacement with) {
		var dstring = (DevProperties.get().logModifiedRecipes || ConsoleJS.SERVER.shouldPrintDebug()) ? (": IN " + match + " -> " + with) : "";

		recipeStream(filter, true).forEach(r -> {
			if (r.replaceInput(match, with)) {
				if (DevProperties.get().logModifiedRecipes) {
					ConsoleJS.SERVER.info("~ " + r + dstring);
				} else if (ConsoleJS.SERVER.shouldPrintDebug()) {
					ConsoleJS.SERVER.debug("~ " + r + dstring);
				}
			}
		});
	}

	public void replaceOutput(RecipeFilter filter, ReplacementMatch match, OutputReplacement with) {
		var dstring = (DevProperties.get().logModifiedRecipes || ConsoleJS.SERVER.shouldPrintDebug()) ? (": OUT " + match + " -> " + with) : "";

		recipeStream(filter, true).forEach(r -> {
			if (r.replaceOutput(match, with)) {
				if (DevProperties.get().logModifiedRecipes) {
					ConsoleJS.SERVER.info("~ " + r + dstring);
				} else if (ConsoleJS.SERVER.shouldPrintDebug()) {
					ConsoleJS.SERVER.debug("~ " + r + dstring);
				}
			}
		});
	}

	public RecipeTypeFunction getRecipeFunction(@Nullable String id) {
		if (id == null || id.isEmpty()) {
			return null;
		} else if (recipeFunctions.get(UtilsJS.getID(id)) instanceof RecipeTypeFunction fn) {
			return fn;
		} else {
			return null;
		}
	}

	public RecipeJS custom(JsonObject json) {
		if (json == null || !json.has("type")) {
			throw new RecipeExceptionJS("JSON must contain 'type'!");
		}

		var type = getRecipeFunction(json.get("type").getAsString());

		if (type == null) {
			throw new RecipeExceptionJS("Unknown recipe type: " + json.get("type").getAsString());
		}

		var recipe = type.schemaType.schema.deserialize(type, null, json);
		recipe.afterLoaded();
		return addRecipe(recipe, true);
	}

	private void printTypes(Predicate<RecipeSchemaType> predicate) {
		int t = 0;
		var map = new IdentityHashMap<RecipeSchema, Set<ResourceLocation>>();

		for (var ns : RecipeNamespace.getAll().values()) {
			for (var type : ns.values()) {
				if (predicate.test(type)) {
					t++;
					map.computeIfAbsent(type.schema, s -> new HashSet<>()).add(type.id);
				}
			}
		}

		for (var entry : map.entrySet()) {
			ConsoleJS.SERVER.info("- " + entry.getValue().stream().map(ResourceLocation::toString).collect(Collectors.joining(", ")));

			for (var c : entry.getKey().constructors().values()) {
				ConsoleJS.SERVER.info("  - " + c);
			}
		}

		ConsoleJS.SERVER.info(t + " types");
	}

	public void printTypes() {
		ConsoleJS.SERVER.info("== All recipe types [used] ==");
		var set = recipeStream(ConstantFilter.TRUE, true).map(r -> r.type.id).collect(Collectors.toSet());
		printTypes(t -> set.contains(t.id));
	}

	public void printAllTypes() {
		ConsoleJS.SERVER.info("== All recipe types [available] ==");
		printTypes(t -> KubeJSRegistries.recipeSerializers().get(t.id) != null);
	}

	public void printExamples(String type) {
		var list = originalRecipes.values().stream().filter(recipeJS -> recipeJS.type.toString().equals(type)).collect(Collectors.toList());
		Collections.shuffle(list);

		ConsoleJS.SERVER.info("== Random examples of '" + type + "' ==");

		for (var i = 0; i < Math.min(list.size(), 5); i++) {
			var r = list.get(i);
			ConsoleJS.SERVER.info("- " + r.getOrCreateId() + ":\n" + JsonIO.toPrettyString(r.json));
		}
	}

	public void setItemErrors(boolean b) {
		RecipeJS.itemErrors = b;
	}

	public void stage(RecipeFilter filter, String stage) {
		recipeStream(filter, true).forEach(r -> r.stage(stage));
	}
}