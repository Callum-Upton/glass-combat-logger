package com.encounterledger;

import net.runelite.client.config.*;

@ConfigGroup("encounterledger")
public interface EncounterLedgerConfig extends Config
{
    @ConfigSection(name="Account & uploads",description="Connect Zenyte and choose what to share",position=0)
    String accountSection="account";
    @ConfigSection(name="Recording & replay",description="Recording display and bookmarks",position=1)
    String recordingSection="recording";
    @ConfigSection(name="Research & testing",description="Optional diagnostic capture for testers",position=2,closedByDefault=true)
    String researchSection="research";
    @ConfigSection(name="Advanced capture",description="Capture rule updates and fallback timeout",position=3,closedByDefault=true)
    String advancedSection="advanced";
    @ConfigSection(name="The Fractured Archive (provisional)",description="Launch-day fallback: only enable after confirming the actual game messages",position=4,closedByDefault=true)
    String archiveSection="archive";
    @ConfigItem(keyName="archiveLocalTriggers",name="Use provisional triggers",description="Uses your message fields instead of downloaded Archive rules. Matches game-system messages anywhere; incorrect wording may start or stop recordings incorrectly. Does not enable networking or research.",section=archiveSection,position=0)
    default boolean archiveLocalTriggers(){return false;}
    @ConfigItem(keyName="archiveStartMessage",name="Start trigger (exact text)",description="Paste the full game-system message shown when the raid starts. Requires at least 8 characters. Does not match player chat.",section=archiveSection,position=1)
    default String archiveStartMessage(){return "";}
    @ConfigItem(keyName="archiveEndMessage",name="End trigger (message prefix)",description="Paste the beginning of the whole-raid completion message, before the changing time. Must include Fractured Archive. Only use confirmed completion wording, never a room-clear message.",section=archiveSection,position=2)
    default String archiveEndMessage(){return "";}
    @ConfigItem(keyName="captureTriggerTest",name="Allow downloaded trigger tests",description="Explicit diagnostic opt-in. Test profiles only save local recordings, never live stream or auto-upload them. Turn off after testing.",section=advancedSection,position=1)
    default boolean captureTriggerTest(){return false;}
    @ConfigItem(keyName="downloadCaptureRules",name="Download capture rules",description="With networking enabled and a connection key, checks Zenyte every minute for message/region rules. Sends no key or recording data. Updates apply to new raids; cached rules expire after at most seven days.",section=advancedSection,position=0)
    default boolean downloadCaptureRules(){return true;}
    @ConfigItem(keyName="allowNetworking",section=accountSection, name="Allow Zenyte networking", description="Allow optional live logging, uploads and capture-rule downloads from Zenyte. Disable to block all plugin network requests; local recording continues.", warning="This feature submits your IP address and various account data to a 3rd-party server not controlled or verified by Runelite developers.")
    default boolean allowNetworking(){return false;}
    @ConfigItem(keyName="connectionKey",section=accountSection,name="Plugin connection key",description="Generate in Zenyte account settings. Grants upload access only. Stored locally in RuneLite configuration; never included in recordings.",secret=true)
    default String connectionKey(){return "";}
    @ConfigItem(keyName="autoUpload",section=accountSection,name="Automatically upload logs",description="Opt in: sends completed regular recordings to your Zenyte account as PUBLIC logs, including character names, gear and combat data. Local copies remain. Research files are not uploaded.")
    default boolean autoUpload(){return false;}
    @ConfigItem(keyName="liveLogging",section=accountSection,name="Public live logging",description="Opt in: broadcasts regular combat data while fighting. Requires live-sharing consent on the website. Research is not needed. Local saves continue if the connection fails.")
    default boolean liveLogging(){return false;}
    @ConfigItem(keyName="bookmarkHotkey",section=recordingSection, name="Bookmark hotkey", description="Mark the current tick during a recording. Choose an unused key combination; bookmarks do not start recordings.")
    default Keybind bookmarkHotkey() { return Keybind.NOT_SET; }
    @ConfigItem(keyName="researchHotkey",section=researchSection, name="Research mode hotkey", description="Toggle opt-in research with combined encounter capture. Research starts OFF each plugin session. Choose an unused key combination.")
    default Keybind researchHotkey() { return Keybind.NOT_SET; }
    @ConfigItem(hidden=true, keyName="combinedCapture", name="Combined capture", description="Automatically group each encounter log with research parts and arena snapshots. Research starts with combat, uses a boss-and-time folder, and waits between encounters. Includes your name, nearby combat data and game messages.")
    default boolean combinedCapture() { return false; }
    @ConfigItem(keyName="showRecordedTick",section=recordingSection, name="Show recorded tick", description="Show the current recorded tick index on screen to match video with the website replay. Starts at tick 0.")
    default boolean showRecordedTick() { return true; }
    @ConfigItem(hidden=true, keyName="researchMode",name="Research mode",description="Opt-in diagnostic recording. Toggle off to save. Automatically saves numbered parts every 30,000 events or 1,000 ticks and continues. Includes your name, nearby combat data and game messages.")
    default boolean researchMode() { return false; }
    @ConfigItem(keyName = "idleTicks",section=advancedSection, name = "End after idle ticks", description = "Close an encounter after this many ticks without combat activity")
    @Range(min = 5, max = 100)
    default int idleTicks() { return 15; }
}
