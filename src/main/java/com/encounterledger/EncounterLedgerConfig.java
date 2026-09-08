package com.encounterledger;

import net.runelite.client.config.*;

@ConfigGroup("encounterledger")
public interface EncounterLedgerConfig extends Config
{
    @ConfigItem(keyName="combinedCapture", name="Combined capture", description="Automatically group each encounter log with research parts and arena snapshots. Research starts with combat, uses a boss-and-time folder, and waits between encounters. Includes your name, nearby combat data and game messages.")
    default boolean combinedCapture() { return false; }
    @ConfigItem(keyName="showRecordedTick", name="Show recorded tick", description="Show the current recorded tick index on screen to match video with the website replay. Starts at tick 0.")
    default boolean showRecordedTick() { return true; }
    @ConfigItem(keyName="researchMode",name="Research mode",description="Opt-in diagnostic recording. Toggle off to save. Automatically saves numbered parts every 30,000 events or 1,000 ticks and continues. Includes your name, nearby combat data and game messages.")
    default boolean researchMode() { return false; }
    @ConfigItem(keyName = "idleTicks", name = "End after idle ticks", description = "Close an encounter after this many ticks without combat activity")
    @Range(min = 5, max = 100)
    default int idleTicks() { return 15; }
}
