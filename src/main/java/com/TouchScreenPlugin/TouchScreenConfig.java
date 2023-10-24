package com.TouchScreenPlugin;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Units;

@ConfigGroup("TouchScreen")
public interface TouchScreenConfig extends Config
{
	@ConfigItem(
		keyName = "allowMouse",
		name = "Enable For Mouse",
		description = "Enable the plugin's features for regular mouse inputs."
	)
	default boolean allowMouse() { return false; }

	@ConfigItem(
		keyName = "touchDragThreshold",
		name = "Drag Threshold (px)",
		description = "The distance in pixels that the mouse must move before a drag is registered."
	)
	default int touchDragThreshold() { return 10; }

	@ConfigItem(
			keyName = "deadTouchTime",
			name = "Touch Delay (ms)",
			description = "Delay before sending a tap as a click."
	)
	default int touchDelayMs() { return 30; }
}
