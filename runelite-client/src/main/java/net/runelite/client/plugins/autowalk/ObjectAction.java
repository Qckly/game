package net.runelite.client.plugins.autowalk;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Only unambiguous, one-action object transitions can be executed automatically. */
public final class ObjectAction
{
	private static final Pattern FORMAT = Pattern.compile("^(Open|Climb-up|Climb-down) (.+) ([0-9]+)$");
	public final String option;
	public final String name;
	public final int objectId;

	private ObjectAction(String option, String name, int objectId)
	{
		this.option = option;
		this.name = name;
		this.objectId = objectId;
	}

	public static ObjectAction parse(String info)
	{
		if (info == null)
		{
			return null;
		}
		Matcher m = FORMAT.matcher(info);
		if (!m.matches())
		{
			return null;
		}
		try
		{
			return new ObjectAction(m.group(1), m.group(2), Integer.parseInt(m.group(3)));
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}
}
