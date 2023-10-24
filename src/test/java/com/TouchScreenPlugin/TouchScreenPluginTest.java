package com.TouchScreenPlugin;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class TouchScreenPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(TouchScreenPlugin.class);
		RuneLite.main(args);
	}
}