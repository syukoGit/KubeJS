package dev.latvian.mods.kubejs.recipe;

import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.recipe.schema.RecipeOptional;
import dev.latvian.mods.kubejs.util.UtilsJS;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class RecipeKey<T> {
	public final RecipeComponent<T> component;
	public final String name;
	public final Set<String> names;
	public String preferred;
	public RecipeOptional<T> optional;
	public boolean excluded;
	public boolean allowEmpty;
	public boolean alwaysWrite;

	public RecipeKey(RecipeComponent<T> component, String name) {
		this.component = component;
		this.name = name;
		this.names = new LinkedHashSet<>(1);
		this.names.add(name);
		this.preferred = name;
		this.optional = null;
		this.excluded = false;
		this.allowEmpty = false;
		this.alwaysWrite = false;
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	@Override
	public String toString() {
		var sb = new StringBuilder(name);

		if (optional != null) {
			sb.append('?');
		}

		sb.append(':');
		sb.append(component);
		return sb.toString();
	}

	/**
	 * Sets this key to not be required in json and constructor
	 */
	public RecipeKey<T> optional(T value) {
		return optional(new RecipeOptional.Constant<>(value));
	}

	/**
	 * Sets this key to not be required in json and constructor
	 */
	public RecipeKey<T> optional(RecipeOptional<T> value) {
		optional = value;
		return this;
	}

	/**
	 * Use this in place of regular optional(x) only if the value is dynamic/too complicated to compute from type
	 */
	public RecipeKey<T> defaultOptional() {
		optional = UtilsJS.cast(RecipeOptional.DEFAULT);
		return this;
	}

	public boolean optional() {
		return optional != null;
	}

	public RecipeKey<T> alt(String name) {
		names.add(name);
		return this;
	}

	public RecipeKey<T> alt(String... names) {
		this.names.addAll(List.of(names));
		return this;
	}

	/**
	 * Used by doc and typing generators to select best builder method name
	 */
	public RecipeKey<T> preferred(String name) {
		names.add(name);
		preferred = name;
		return this;
	}

	/**
	 * Excludes this key from auto-generated constructors. Requires optional() value to also be set.
	 */
	public RecipeKey<T> exclude() {
		excluded = true;
		return this;
	}

	public boolean includeInAutoConstructors() {
		return optional == null || !excluded;
	}

	/**
	 * Allow empty values (such as minecraft:air) in results/ingredients
	 */
	public RecipeKey<T> allowEmpty() {
		allowEmpty = true;
		return this;
	}

	/**
	 * Always write optional key, even if it hasn't changed
	 */
	public RecipeKey<T> alwaysWrite() {
		alwaysWrite = true;
		return this;
	}
}
