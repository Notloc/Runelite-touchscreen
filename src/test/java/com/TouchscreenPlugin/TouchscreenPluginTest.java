package com.TouchscreenPlugin;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class TouchscreenPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(TouchscreenPlugin.class);
		RuneLite.main(args);
	}
}