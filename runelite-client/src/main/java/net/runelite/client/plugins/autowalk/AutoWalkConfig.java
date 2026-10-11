package net.runelite.client.plugins.autowalk;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("autowalk")
public interface AutoWalkConfig extends Config
{
	@ConfigItem(keyName = "dryRun", name = "Dry run", description = "Show planned actions without moving the account", position = 0)
	default boolean dryRun()
	{
		return false;
	}

	@Range(min = 4, max = 60)
	@ConfigItem(keyName = "stuckTicks", name = "Stuck timeout", description = "Ticks without position progress before replanning", position = 1)
	default int stuckTicks()
	{
		return 12;
	}

	@Range(min = 0, max = 5)
	@ConfigItem(keyName = "maxReplans", name = "Recovery attempts", description = "Maximum automatic recovery searches per journey", position = 2)
	default int maxReplans()
	{
		return 3;
	}

	@ConfigItem(keyName = "diagnostics", name = "Diagnostic logging", description = "Log journey, tick, position and action for RSPS testing", position = 3)
	default boolean diagnostics()
	{
		return false;
	}
}
