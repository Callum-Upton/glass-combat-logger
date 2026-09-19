# Reviewer notes

## Purpose

Zenyte exports local observations for post-fight analysis. By default recording is local. Optional explicit settings use a connection key to send regular replay batches and/or completed logs to the fixed https://zenyte.gg/api/plugin/ endpoints. It does not listen on a port, use reflection in production, or automate input. The sidebar only displays the local logs path and copies it to the clipboard on request; it does not open directories or launch external applications. Test code uses Java proxies/reflection to provide API fakes; the test launcher is not included in the production JAR.

There are no account-specific feature switches, subscriptions or tester gates in the plugin. The companion website accepts manual and opted-in automatic regular uploads for replay, sharing and improving encounter analysis. Research recording is local and optional, not an automatic submission to that service.

## Entry points

- `EncounterLedgerPlugin`: ordinary encounter lifecycle, player state, observed combat events and background writer. Generic incoming/outgoing combat trigger, recognised boss area continuity and CoX raid-message boundaries. Generic encounters are not limited to an arena allowlist.
- `ResearchRecorder`: optional independent diagnostics, or diagnostics attached to ordinary encounter boundaries through Combined capture.
- `ArenaSnapshot`: loaded scene terrain, collision flags, object IDs and instance template coordinates. Snapshots are written locally, not displayed as a live safe-tile overlay.
- `RecordedTickOverlay`: recording status, tick index and elapsed replay time only.
- `LogsPanel`: selectable local folder path and clipboard-copy button. Does not start or upload a recording.

## Capture and lifecycle

The ordinary recorder maintains up to 15 ticks of player-state pre-roll while logged in. Only a triggered encounter causes those ticks to be saved. It retains its own equipment/inventory, stats, prayers, buffs, positions, target and combat observations. Scoped named participant positions are recorded in recognised Yama, Scurrius, Royal Titans and CoX encounters for replay and future POV alignment; missing or truncated observations are explicit. Other-player gear is not queried.

Research resets off at startup. The ::zenyte research commands and optional hotkey enable combined capture; continuous diagnostics require an explicit command. Combined capture waits for combat and keeps research alive through the attached encounter, including the final timer-message wait. Standalone research has no arena gate and should be switched off after use. Research observes actors within 48 tiles in the local player's world view, including anonymous other-player animation/hitsplat/graphic references. NPC references contain names and IDs. System/game messages may contain player names. Selected menu actions omit the target text. Raw numeric varps (up to 10,000 initially) and varp/varbit changes are retained for unknown-mechanic discovery and can include unrelated game settings.

Chat capture is limited to game/system message types, excluding player chat channels. An optional user-configured bookmark hotkey adds a marker to an active recording; it does not start recording or automate gameplay. A user-provided, upload-scoped website connection key is stored as a secret RuneLite setting; it is never recorded as telemetry. Terrain snapshots include the loaded scene across planes, rather than only the 48-tile actor radius.

All output is beneath RuneLite's user directory, in `encounter-ledger/`. File names use UUIDs, numbered research parts, and sanitised encounter labels. The existing package/config/schema identifiers are retained for compatibility; the public plugin name is Zenyte.

## Bounds

- Ordinary encounter: at most 2,000 snapshots, including up to 15 pre-roll snapshots. Recognised CoX raids allow at most 12,000 snapshots to span the whole raid.
- Research parts: 30,000 events or 1,000 ticks, then rotate with continuous sequence numbers.
- Per research scan: at most 128 nearby NPCs and 2,048 unique objects; truncation is explicit. Dense scenes can repeatedly reach the object cap. This is a known coverage limitation, not a claim that every object is captured.
- Arena snapshot: at most 65,536 tiles and 65,536 deduplicated objects.
- Up to 256 active scene graphics monitored between ticks.
- Eight queued background writes, with failure notices and capture pause on queue/disk failure.
- No total disk quota or crash recovery. Users should wait for saved messages before closing RuneLite.

## Validation scope

Automated tests cover recording boundaries, pre-roll, timer messages, combat observations, research splitting/file output, combined-capture waiting, and storage rejection. Real Yama solo/duo and Scurrius logs have been used during development. New UI and a fresh installation still require a short in-game smoke test; automated tests cannot establish Plugin Hub approval or every mechanic's attribution accuracy.

## Encounter boundaries

Recognised Yama encounters remain active throughout template region 6045 (instance plane 0), including both Judge islands and stepping stones. Scurrius uses the recorded public/private template bounds x3276–3309, y9857–9878, plane 0. These rules keep an already-started encounter alive; they do not start recording on arena entry. Exiting a recognised area requires three observed outside ticks before saving, allowing delayed local-player death evidence to take precedence. Boss death still ends a kill while inside, so subsequent Scurrius spawns get separate logs. Unknown/unavailable area data retains the configurable idle fallback. Logout and the existing length/storage limits still apply.


## Vorkath and generic weapon evidence (prepared update)

The development Vorkath profile covers post-quest NPC 8061, including acid-phase continuity, final timer wait, spawn observations (8063), and acid object snapshots (32000). Instance-template viewport x2255–2289, y4048–4082, plane 0 is conservatively mapped; quest Vorkath is not claimed supported. The shared three-tick exit grace and player-death precedence apply.

Weapon-specific recognition and cooldown code are absent from production. Every non-idle local animation start with an NPC interaction preserves weapon ID/name, attack style, target, tick and client cycle as `attack_observation`. Duplicate callbacks at the same animation/cycle are suppressed. No weapon allowlist controls capture. Website interpretation may identify attacks later, including those from new weapons.

A confirmed standalone divine-drink hit can be excluded from starting a regular fight when corroborated use, a single 10 hit and graphic 560 are present. The event is retained in pre-roll; combat hits or multiple hits still trigger recording. This does not universally identify all self-damage sources.


## Next candidate encounter support

Vorkath, Scurrius, Yama and Royal Titans have recognised encounter profiles. CoX uses authoritative raid start/completion messages, raid-area continuity and a longer cap; it preserves room/NPC/effect observations for website interpretation. Royal Titans does not finish when only one Titan becomes inactive. Known instance bounds use template coordinates. Death/exit confirmation and incomplete recordings remain separate outcomes.

Other-player names/positions are observable encounter evidence, not verified account ownership. No other-player equipment is queried. Research actor references remain anonymous except names that can occur in system messages. Capturing an actor or visual does not establish complete mechanic attribution.

## New capture-only profiles

Zulrah (template regions 9007/9008), Duke Sucellus (12132) and Vardorvis (4405) use instanced plane-zero arenas. Phosani's Nightmare uses region 15515 on plane three. These profiles retain combat-triggered recordings inside their respective arenas. Profiles match observed NPC forms only. Phase deaths/depletion do not end these encounters; boss-specific kill-count messages do. Player death, three-tick arena exit, logout and length/storage limits still terminate recording. Projectile paths and bounded nearby NPC observations are retained; new damage interpretation remains on the website. These new profiles have automated boundary tests, but still require live-client validation of full kills, exits and deaths. Duke preparation before the first combat trigger is not guaranteed to be recorded.

LiveUploader uses a bounded background queue, HTTP timeouts and no redirects. Research files are never transmitted. Live sharing requires both website consent and the plugin toggle. Revocation removes server access immediately. The user has confirmed successful live logging and automatic uploads. Vardorvis boundary tests pass, but dedicated live kill/death/exit validation remains outstanding.
