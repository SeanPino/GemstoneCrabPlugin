package com.gemstonecrab;

import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MessageNode;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Gemstone Crab",
	description = "Useful plugin for the Gemstone Crab. Counting your kill count, total mines, and more.",
	tags = {"gemstone", "crab", "afk", "kc", "combat", "mining"}
)
public class GemstoneCrabPlugin extends Plugin
{
	private static final String GEMSTONE_CRAB_DEATH_MESSAGE = "The gemstone crab burrows away, leaving a piece of its shell behind.";
	private static final String GEMSTONE_CRAB_MINE_SUCCESS_MESSAGE = "You swing your pick at the crab shell.";
	private static final String GEMSTONE_CRAB_MINE_FAIL_MESSAGE = "Your understanding of the gemstone crab is not great enough to mine its shell.";
	private static final Pattern GEMSTONE_CRAB_KILL_CHECK_PATTERN = Pattern.compile("!Kc [crab|gemstone|gemstone crab]");
	
	private static final String CONFIG_GROUP = "gemstonecrab";
    private static final String CONFIG_KEY_COUNT = "crabCount";
	private static final String CONFIG_KEY_MINING_ATTEMPTS = "miningAttemptsCount";
	private static final String CONFIG_KEY_MINED = "minedCount";
	private static final String CONFIG_KEY_FAILED = "failedMiningCount";

	private static final int KILL_THRESHOLD_MINUTES = 5;
	private static final int DISTANCE_THRESHOLD = 13; 

	private static final WorldPoint EAST_CRAB = new WorldPoint(1353, 3112, 0);
	private static final WorldPoint SOUTH_CRAB = new WorldPoint(1239,3043, 0);
	private static final WorldPoint NORTH_CRAB = new WorldPoint(1273,3173, 0);

	@Inject
	private Client client;

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
	private boolean isAttackingCrab = false;
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
    protected void startUp() throws Exception {
        log.info("Gemstone Crab Counter started!");

		// Load the saved counts from configuration
        crabCount = util.loadConfigValue(CONFIG_GROUP, CONFIG_KEY_COUNT);
		miningAttempts = util.loadConfigValue(CONFIG_GROUP, CONFIG_KEY_MINING_ATTEMPTS);
		minedCount = util.loadConfigValue(CONFIG_GROUP, CONFIG_KEY_MINED);
		miningFailedCount = util.loadConfigValue(CONFIG_GROUP, CONFIG_KEY_FAILED);

        overlayManager.add(overlay);
    }

    @Override
    protected void shutDown() throws Exception {
        log.info("Gemstone Crab Counter stopped!");
        overlayManager.remove(overlay);
    }

	/*
	 * Handles Gemstone Crab Events
	 * "Kill" - crab moves
	 * Mining Attempt, Success, and Failure
	 * Kill count checked
	 */
    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
		String message = chatMessage.getMessage();
        if (chatMessage.getType() == ChatMessageType.GAMEMESSAGE || 
			chatMessage.getType() == ChatMessageType.SPAM) {  

			switch (message) {
				case (GEMSTONE_CRAB_DEATH_MESSAGE):
					if (isKillable()) {
						crabCount++;
						log.info("Gemstone crab killed! KC: {}", crabCount);
						String msg = new ChatMessageBuilder().append(Color.RED, String.format("Gemstone Crab Killed! KC: %d", crabCount)).build();
						client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", msg, "");
					}
					else {
						log.info("Gemstone crab kill did not count! Less than 5mins at the boss or not attacking.");
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
					break;
				case (GEMSTONE_CRAB_MINE_FAIL_MESSAGE):
					if (!isMiningBeforeCooldown()) {
						log.info("Failed to mine Gemstone Crab!");
						miningAttempts++;
						miningFailedCount++;
						setLastMiningAttempt();
					}
					break;
				default:
					break;
			}
			saveCrabCounts();
        } else if (chatMessage != null
					&& chatMessage.getName() != null
					&& client.getLocalPlayer() != null
					&& client.getLocalPlayer().getName() != null
					&& chatMessage.getName().contains(client.getLocalPlayer().getName())) {
			Matcher matcher = GEMSTONE_CRAB_KILL_CHECK_PATTERN.matcher(message);
			if (matcher.find()) {
				String response = new ChatMessageBuilder()
					.append(ChatColorType.HIGHLIGHT)
					.append("Gemstone Crab")
					.append(ChatColorType.NORMAL)
					.append(" kill count: ")
					.append(ChatColorType.HIGHLIGHT)
					.append(String.format("%,d", crabCount))
					.build();

				log.debug("Setting response {}", response);
				final MessageNode messageNode = chatMessage.getMessageNode();
				messageNode.setRuneLiteFormatMessage(response);
				client.refreshChat();
			}
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
			if (!isAttackingCrab && checkCrabInteraction()) {
				log.info("is attacking crab");
				isAttackingCrab = true;
			}
        } else {
			overlayManager.remove(overlay);
        }
    }

	/*
	 * On Gemstone Crab spawn start spawnTimer
	 */
	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		final NPC npc = event.getNpc();
		if (npc.getId() ==  NpcID.GEMSTONE_CRAB) {
			spawnTime = LocalDateTime.now();
			isAttackingCrab = false;
			log.info("Starting timer for {} at {}", npc.getName(), spawnTime.toString());
		}
	}

    public int getCrabCount() {
        return crabCount;
    }

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

	/*
	 * Used to stop mining from counting multiple times
	 */
	private boolean isMiningBeforeCooldown() {
		if(isKillable() && (lastMiningAttempt == null || LocalDateTime.now().isAfter(lastMiningAttempt.plus(KILL_THRESHOLD_MINUTES, ChronoUnit.MINUTES)))) {
			return false;
		} 
		return true;
	}

	/*
	 * Crab was alive at least 5mins and player was attacking it at least once
	 */
	private boolean isKillable() {
		return LocalDateTime.now().minus(KILL_THRESHOLD_MINUTES, ChronoUnit.MINUTES).isAfter(spawnTime) && isAttackingCrab;
	}

	private boolean checkCrabInteraction() {
		Actor actor = client.getLocalPlayer().getInteracting();
		if (actor != null) {
			if (actor instanceof NPC) {
				if (((NPC)actor).getId() == NpcID.GEMSTONE_CRAB) {
					return true;
				}
			};
		}
		return false;
	}
}
