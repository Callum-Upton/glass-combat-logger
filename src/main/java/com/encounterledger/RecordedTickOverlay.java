package com.encounterledger;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Locale;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

/** Displays the recorded snapshot index, never a separately running timer. */
final class RecordedTickOverlay extends OverlayPanel
{
    private final EncounterLedgerPlugin plugin;
    private final EncounterLedgerConfig config;

    RecordedTickOverlay(EncounterLedgerPlugin plugin, EncounterLedgerConfig config)
    {
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        panelComponent.setPreferredSize(new Dimension(250, 0));
    }

    @Override public Dimension render(Graphics2D graphics)
    {
        panelComponent.getChildren().clear();
        if (!config.showRecordedTick()) return null;
        int tick = plugin.displayedRecordedTick();
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Glass Combat Logger")
            .right(tick < 0 ? "Waiting" : plugin.tickRecording() ? "Recording" : "Stopped")
            .rightColor(plugin.tickRecording() ? Color.GREEN : Color.LIGHT_GRAY).build());
        if (tick >= 0)
        {
            panelComponent.getChildren().add(LineComponent.builder().left("Recorded tick")
                .right(Integer.toString(tick)).rightColor(Color.YELLOW).build());
            panelComponent.getChildren().add(LineComponent.builder().left("Replay time")
                .right(String.format(Locale.ROOT, "%.1fs", tick * 0.6)).build());
        }
        return super.render(graphics);
    }
}
