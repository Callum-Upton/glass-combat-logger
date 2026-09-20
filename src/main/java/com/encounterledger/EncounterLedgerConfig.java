package com.encounterledger;

import net.runelite.client.config.*;

@ConfigGroup("encounterledger")
public interface EncounterLedgerConfig extends Config
{
    @ConfigItem(keyName="allowNetworking", name="Allow Zenyte networking", description="Allow optional live logging and uploads to Zenyte. Disable to block all plugin network requests; local recording continues.", warning="This feature submits your IP address and various account data to a 3rd-party server not controlled or verified by Runelite developers.")
    default boolean allowNetworking(){return false;}
    @ConfigItem(keyName="connectionKey",name="Plugin connection key",description="Generate in Zenyte account settings. Grants upload access only. Stored locally in RuneLite configuration; never included in recordings.",secret=true)
    default String connectionKey(){return "";}
    @ConfigItem(keyName="autoUpload",name="Automatically upload logs",description="Opt in: sends completed regular recordings to your Zenyte account as PUBLIC logs, including character names, gear and combat data. Local copies remain. Research files are not uploaded.")
    default boolean autoUpload(){return false;}
    @ConfigItem(keyName="liveLogging",name="Public live logging",description="Opt in: broadcasts regular combat data while fighting. Requires live-sharing consent on the website. Research is not needed. Local saves continue if the connection fails.")
    default boolean liveLogging(){return false;}
    @ConfigItem(keyName="bookmarkHotkey", name="Bookmark hotkey", description="Mark the current tick during a recording. Choose an unused key combination; bookmarks do not start recordings.")
    default Keybind bookmarkHotkey() { return Keybind.NOT_SET; }
    @ConfigItem(keyName="researchHotkey", name="Research mode hotkey", description="Toggle opt-in research with combined encounter capture. Research starts OFF each plugin session. Choose an unused key combination.")
    default Keybind researchHotkey() { return Keybind.NOT_SET; }
    @ConfigItem(hidden=true, keyName="combinedCapture", name="Combined capture", description="Automatically group each encounter log with research parts and arena snapshots. Research starts with combat, uses a boss-and-time folder, and waits between encounters. Includes your name, nearby combat data and game messages.")
    default boolean combinedCapture() { return false; }
    @ConfigItem(keyName="showRecordedTick", name="Show recorded tick", description="Show the current recorded tick index on screen to match video with the website replay. Starts at tick 0.")
    default boolean showRecordedTick() { return true; }
    @ConfigItem(hidden=true, keyName="researchMode",name="Research mode",description="Opt-in diagnostic recording. Toggle off to save. Automatically saves numbered parts every 30,000 events or 1,000 ticks and continues. Includes your name, nearby combat data and game messages.")
    default boolean researchMode() { return false; }
    @ConfigItem(keyName = "idleTicks", name = "End after idle ticks", description = "Close an encounter after this many ticks without combat activity")
    @Range(min = 5, max = 100)
    default int idleTicks() { return 15; }
}

