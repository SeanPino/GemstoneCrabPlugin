package com.gemstonecrab;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;

import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;

public class GemstoneCrabOverlay extends Overlay
{
    private final GemstoneCrabPlugin plugin;
    private final GemstoneCrabConfig config;
    private final PanelComponent panelComponent = new PanelComponent();

    @Inject
    private GemstoneCrabOverlay(GemstoneCrabPlugin plugin, GemstoneCrabConfig config)
    {
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Inject
	private Client client;

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.showOverlay())
        {
            return null;
        }

        var localPlayer = client.getLocalPlayer();
        if (localPlayer == null) {
            return null;
        }

        if (!plugin.isNearCrab(localPlayer.getWorldLocation())) {
            return null;
        }

        panelComponent.getChildren().clear();
        
        if (config.displayKillCount()) {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Gemstone Crabs:")
                .right(String.valueOf(plugin.getCrabCount()))
                .build());
        }

        if (config.displayMiningAttempts()) {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Mining Attempts:")
                .right(String.valueOf(plugin.getMiningAttemptsCount()))
                .build());
        }


        if (config.displayMinedCount()) {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Successful:")
                .right(String.valueOf(plugin.getMinedCount()))
                .build());
        }

        if (config.displayFailedMiningCount()) {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Failed:")
                .right(String.valueOf(plugin.getMiningFailedCount()))
                .build());
        }

        panelComponent.setPreferredSize(new Dimension(150, 0));
        panelComponent.setBackgroundColor(new Color(0, 0, 0, 100));

        return panelComponent.render(graphics);
    }
}
