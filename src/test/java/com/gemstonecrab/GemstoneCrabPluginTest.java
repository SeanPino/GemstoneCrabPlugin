package com.gemstonecrab;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class GemstoneCrabPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(GemstoneCrabPlugin.class);
		RuneLite.main(args);
	}
}