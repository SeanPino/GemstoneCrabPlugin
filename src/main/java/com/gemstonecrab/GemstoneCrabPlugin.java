package com.gemstonecrab;

import java.awt.Color;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Gemstone Crab"
)
public class GemstoneCrabPlugin extends Plugin
{
	private static final String GEMSTONE_CRAB_DEATH_MESSAGE = "The gemstone crab burrows away, leaving a piece of its shell behind.";
	private static final String GEMSTONE_CRAB_MINE_SUCCESS_MESSAGE = "You swing your pick at the crab shell.";
	private static final String GEMSTONE_CRAB_MINE_FAIL_MESSAGE = "Your understanding of the gemstone crab is not great enough to mine its shell.";
	private static final String CONFIG_GROUP = "gemstonecrab";
    private static final String CONFIG_KEY_COUNT = "crabCount";
	private static final String CONFIG_KEY_MINING_ATTEMPTS = "miningAttemptsCount";
	private static final String CONFIG_KEY_MINED = "minedCount";
	private static final String CONFIG_KEY_FAILED = "failedMiningCount";
	// private static final Point2D EASTERN_CRAB_BOTTOM_LEFT_CORNER = new Point(1341,3097);
	// private static final Point2D EASTERN_CRAB_TOP_RIGHT_CORNER = new Point(1370,3133);
	// private static final Point2D NORTHERN_CRAB_BOTTOM_LEFT_CORNER = new Point(1267,3164);
	// private static final Point2D NORTHERN_CRAB_TOP_RIGHT_CORNER = new Point(1282,3182);
	// private static final Point2D SOUTHERN_CRAB_BOTTOM_LEFT_CORNER = new Point(1249,3033);
	// private static final Point2D SOUTHERN_CRAB_TOP_RIGHT_CORNER = new Point(1230,3051);
	private static final int MINING_COOLDOWN_MINUTES = 5;
	private static final int DISTANCE_THRESHOLD = 13; 
	private static final WorldPoint EAST_CRAB = new WorldPoint(1353, 3112, 0);
	private static final WorldPoint SOUTH_CRAB = new WorldPoint(1239,3043, 0);
	private static final WorldPoint NORTH_CRAB = new WorldPoint(1273,3173, 0);
	@Inject
	private Client client;

	@Inject
	private GemstoneCrabConfig config;

	@Inject
    private ConfigManager configManager;

    @Inject
    private OverlayManager overlayManager;

	@Inject
    private GemstoneCrabOverlay overlay;

    @Inject
    private GemstoneCrabUtil util;

    private int crabCount;
	private int minedCount;
	private int miningAttempts;
	private int miningFailedCount;
	private LocalDateTime spawnTime;
	private LocalDateTime lastMiningAttempt;

    /**
     * Specify our config
     * @param configManager The Runelite Config manager
     * @return The configuration for this plugin
     */
    @Provides
    GemstoneCrabConfig providesConfig(ConfigManager configManager) {
        return configManager.getConfig(GemstoneCrabConfig.class);
    }

    @Override
    protected void startUp() throws Exception
    {
        log.info("Gemstone Crab Counter started!");

		// Load the saved counts from configuration
        crabCount = util.loadConfigValue(CONFIG_GROUP, CONFIG_KEY_COUNT);
		miningAttempts = util.loadConfigValue(CONFIG_GROUP, CONFIG_KEY_MINING_ATTEMPTS);
		minedCount = util.loadConfigValue(CONFIG_GROUP, CONFIG_KEY_MINED);
		miningFailedCount = util.loadConfigValue(CONFIG_GROUP, CONFIG_KEY_FAILED);

        overlayManager.add(overlay);
    }

    @Override
    protected void shutDown() throws Exception
    {
        log.info("Gemstone Crab Counter stopped!");
        overlayManager.remove(overlay);
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage)
    {
        if (chatMessage.getType() == ChatMessageType.GAMEMESSAGE || 
            chatMessage.getType() == ChatMessageType.SPAM)
        {
            String message = chatMessage.getMessage();

			switch (message) {
				case (GEMSTONE_CRAB_DEATH_MESSAGE):
					if (LocalDateTime.now().plus(-5, ChronoUnit.MINUTES).isAfter(spawnTime))
					{
						crabCount++;
						log.info("Gemstone crab killed! KC: {}", crabCount);
						String msg = new ChatMessageBuilder().append(Color.RED, String.format("Gemstone Crab Killed! KC: %d", crabCount)).build();
						client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", msg, "");
					}
					else {
						log.info("Gemstone crab kill did not count! Less than 5mins at the boss. Kill Count: {}", crabCount);
						String msg = new ChatMessageBuilder().append(Color.MAGENTA, String.format("Gemstone Crab not fought long enough for kill count.")).build();
						client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", msg, "");
					}
					break;
				case (GEMSTONE_CRAB_MINE_SUCCESS_MESSAGE):
					if (!isMiningBeforeCooldown()) {
						log.info("Gemstone Crab successfully mined!");
						miningAttempts++;
						minedCount++;
						setLastMiningAttempt();
					}
				case (GEMSTONE_CRAB_MINE_FAIL_MESSAGE):
					if (!isMiningBeforeCooldown()) {
						log.info("Failed to mine Gemstone Crab!");
						miningAttempts++;
						miningFailedCount++;
						setLastMiningAttempt();
					}

				default:
					break;
			}
			saveCrabCounts();
        }
    }

	/**
     * On game tick, show/hide overlay
     * @param event The GameTick event
     */
    @Subscribe
    public void onGameTick(GameTick event) {
        if (client.getLocalPlayer() != null && isNearCrab(client.getLocalPlayer().getWorldLocation())) {
			overlayManager.add(overlay);
        } else {
			overlayManager.remove(overlay);
        }
    }

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		final NPC npc = event.getNpc();
		if (npc.getId() ==  NpcID.GEMSTONE_CRAB)
		{
			spawnTime = LocalDateTime.now();
			log.info("Starting timer for {} at {}", npc.getName(), spawnTime.toString());
		}
	}

    @Subscribe
    public void onConfigChanged(ConfigChanged c) {
        if (!c.getGroup().equalsIgnoreCase(CONFIG_GROUP)) {
            return;
        }
        log.info("key has been retrieved: {}", c.getKey());
    }



    public int getCrabCount() {
        return crabCount;
    }

    // public void resetCount()
    // {
    //     crabCount = 0;
    //     saveCrabCount();
    //     log.info("Gemstone crab count reset to 0");
    // }

	public int getMinedCount() {
        return minedCount;
    }

	public int getMiningAttemptsCount() {
        return miningAttempts;
    }

	public int getMiningFailedCount() {
        return miningFailedCount;
    }

	public boolean isNearCrab(WorldPoint point) {
		return point.distanceTo2D(EAST_CRAB) <= DISTANCE_THRESHOLD 
			|| point.distanceTo2D(SOUTH_CRAB) <= DISTANCE_THRESHOLD 
			|| point.distanceTo2D(NORTH_CRAB) <= DISTANCE_THRESHOLD;
		// if(point.getX() <= SOUTHERN_CRAB_BOTTOM_LEFT_CORNER.getX() && point.getX() >= SOUTHERN_CRAB_TOP_RIGHT_CORNER.getX())
		// {
		// 	return point.getY() <= SOUTHERN_CRAB_TOP_RIGHT_CORNER.getY() && point.getY() >= SOUTHERN_CRAB_BOTTOM_LEFT_CORNER.getY();
		// }
		//return false;
	}

	private void saveCrabCounts() {
        configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_COUNT, String.valueOf(crabCount));
		configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_MINING_ATTEMPTS, String.valueOf(miningAttempts));
		configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_MINED, String.valueOf(minedCount));
		configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_FAILED, String.valueOf(miningFailedCount));
    }

	private void setLastMiningAttempt() {
		lastMiningAttempt = LocalDateTime.now();
	}

	private boolean isMiningBeforeCooldown() {
		if(lastMiningAttempt == null || LocalDateTime.now().isAfter(lastMiningAttempt.plus(MINING_COOLDOWN_MINUTES, ChronoUnit.MINUTES))) {
			return false;
		} 
		return true;
	}

}
