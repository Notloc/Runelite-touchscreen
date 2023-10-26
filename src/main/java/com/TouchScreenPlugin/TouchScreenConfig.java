package com.TouchScreenPlugin;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Units;

@ConfigGroup("TouchScreen")
public interface TouchScreenConfig extends Config
{
	@ConfigItem(
		keyName = "touchDragThreshold",
		name = "Drag Threshold (px)",
		description = "The distance in pixels that the mouse must move before a drag is registered."
	)
	default int touchDragThreshold() { return 10; }
}
