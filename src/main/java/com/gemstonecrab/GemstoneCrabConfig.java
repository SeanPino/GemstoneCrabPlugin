package com.gemstonecrab;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("gemstonecrabcounter")
public interface GemstoneCrabConfig extends Config
{
    @ConfigItem(
        keyName = "showOverlay",
        name = "Show overlay",
        description = "Show the gemstone crab count overlay"
    )
    default boolean showOverlay()
    {
        return true;
    }

    @ConfigItem(
        keyName = "displayKillCount",
        name = "Display kill count",
        description = "Display total Gemstone Crab kill count in overlay"
    )
    default boolean displayKillCount()
    {
        return true;
    }

	@ConfigItem(
        keyName = "displayMiningAttempts",
        name = "Display total mining attempts",
        description = "Display total Gemstone Crabs attempts at mining in overlay"
    )
    default boolean displayMiningAttempts()
    {
        return false;
    }

	@ConfigItem(
        keyName = "displayMinedCount",
        name = "Display total successful",
        description = "Display total successful mining attempts at Gemstone Crabs in overlay"
    )
    default boolean displayMinedCount()
    {
        return false;
    }

	@ConfigItem(
        keyName = "displayFailedMiningCount",
        name = "Display total failed",
        description = "Display total failed mining attempts at Gemstone Crabs in overlay"
    )
    default boolean displayFailedMiningCount()
    {
        return false;
    }
}
