package dev.latvian.kubejs.text;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;

/**
 * @author LatvianModder
 */
public class TextString extends Text
{
	private final String string;

	public TextString(@Nullable Object text)
	{
		string = String.valueOf(text);
	}

	public String getString()
	{
		return string;
	}

	@Override
	public ITextComponent rawComponent()
	{
		return new TextComponentString(string);
	}

	@Override
	public Text rawCopy()
	{
		return new TextString(string);
	}

	@Override
	public JsonElement getJson()
	{
		JsonObject o = getPropertiesAsJson();

		if (o.size() == 0)
		{
			return new JsonPrimitive(string);
		}

		o.addProperty("text", string);
		return o;
	}
}