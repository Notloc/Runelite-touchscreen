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
		description = "The distance in pixels you must move your finger for a drag to occur."
	)
	default int touchDragThreshold() { return 35; }

	@ConfigItem(
			keyName = "touchScrollThreshold",
			name = "Scroll Threshold (px)",
			description = "The distance in pixels you must move your finger to scroll one tick."
	)
	default int touchScrollThreshold() { return 8; }

	@ConfigItem(
			keyName = "touchScrollInvert",
			name = "Invert Scrolling",
			description = "Inverts the direction of scrolling."
	)
	default boolean invertScrolling() { return false; }

	@ConfigItem(
			keyName = "touchMinimapScrollThreshold",
			name = "Minimap Zoom Threshold (px)",
			description = "The distance in pixels that the mouse must move to scroll one tick."
	)
	default int touchMinimapScrollThreshold() { return 5; }

	@ConfigItem(
			keyName = "touchZoomInvert",
			name = "Invert Zooming",
			description = "Inverts the direction of 3D zooming."
	)
	default boolean invertZoom() { return true; }

	@ConfigItem(
			keyName = "touchMinimapZoomInvert",
			name = "Invert Minimap Zooming",
			description = "Inverts the direction of minimap zooming."
	)
	default boolean invertMinimapZoom() { return false; }
}
