# Reviewer notes

## Purpose

Glass Combat Logger exports local observations for post-fight analysis. The plugin does not make HTTP requests, upload files, listen on a port, use reflection in production, or automate input. Its only external UI action is a user-clicked folder opener using RuneLite's `LinkBrowser.open`. Test code uses Java proxies/reflection to provide API fakes; the test launcher is not included in the production JAR.

There are no account-specific feature switches, subscriptions or tester gates in the plugin. The companion website is a separate service accepting user-initiated uploads for replay, sharing and improving encounter analysis. Research recording is local and optional, not an automatic submission to that service.

## Entry points

- `EncounterLedgerPlugin`: ordinary encounter lifecycle, player state, observed combat events and background writer. Automatic incoming/outgoing hitsplat trigger; no arena allowlist in this release.
- `ResearchRecorder`: optional independent diagnostics, or diagnostics attached to ordinary encounter boundaries through Combined capture.
- `ArenaSnapshot`: loaded scene terrain, collision flags, object IDs and instance template coordinates. Snapshots are written locally, not displayed as a live safe-tile overlay.
- `RecordedTickOverlay`: recording status, tick index and elapsed replay time only.
- `LogsPanel`: local folder-opening button. Does not start or upload a recording.

## Capture and lifecycle

The ordinary recorder maintains up to 15 ticks of player-state pre-roll while logged in. Only a triggered encounter causes those ticks to be saved. It retains its own equipment/inventory, stats, prayers, buffs, positions, target and combat observations. Yama participant-name observations are included for team classification. Other-player gear is not queried.

Research mode is off by default and manually startable. Combined capture is also off by default; when enabled it waits for combat and keeps research alive through the attached encounter, including the final timer-message wait. Standalone research has no arena gate and should be switched off after use. Research observes actors within 48 tiles in the local player's world view, including anonymous other-player animation/hitsplat/graphic references. NPC references contain names and IDs. System/game messages may contain player names. Selected menu actions omit the target text. Raw numeric varps (up to 10,000 initially) and varp/varbit changes are retained for unknown-mechanic discovery and can include unrelated game settings.

Chat capture is limited to game/system message types, excluding player chat channels. No input hooks or credential collection are used. Terrain snapshots include the loaded scene across planes, rather than only the 48-tile actor radius.

All output is beneath RuneLite's user directory, in `encounter-ledger/`. File names use UUIDs, numbered research parts, and sanitised encounter labels. The existing package/config/schema identifiers are retained for compatibility; the public plugin name is Glass Combat Logger.

## Bounds

- Ordinary encounter: at most 2,000 snapshots, plus up to 15 pre-roll snapshots within that count.
- Research parts: 30,000 events or 1,000 ticks, then rotate with continuous sequence numbers.
- Per research scan: at most 128 nearby NPCs and 2,048 unique objects; truncation is explicit. Dense scenes can repeatedly reach the object cap. This is a known coverage limitation, not a claim that every object is captured.
- Arena snapshot: at most 65,536 tiles and 65,536 deduplicated objects.
- Up to 256 active scene graphics monitored between ticks.
- Eight queued background writes, with failure notices and capture pause on queue/disk failure.
- No total disk quota or crash recovery. Users should wait for saved messages before closing RuneLite.

## Validation scope

Automated tests cover recording boundaries, pre-roll, timer messages, combat observations, research splitting/file output, combined-capture waiting, and storage rejection. Real Yama solo/duo and Scurrius logs have been used during development. New UI and a fresh installation still require a short in-game smoke test; automated tests cannot establish Plugin Hub approval or every mechanic's attribution accuracy.
