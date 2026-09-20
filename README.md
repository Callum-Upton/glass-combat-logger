# Zenyte

A RuneLite plugin that records local tick-by-tick encounters for replay and analysis at https://zenyte.gg. Local recording needs no account and does not automate gameplay.

## Recording

Enable Zenyte and fight normally. Regular recording starts from combat evidence and saves beneath `.runelite/encounter-ledger/`. Wait for **Log saved** before importing a file. The gold gem sidebar shows the folder path.

Automatic encounter profiles cover Yama, Scurrius, Vorkath, Royal Titans, Zulrah, Duke Sucellus, Phosani's Nightmare, Chambers of Xeric and Vardorvis. Capture support does not imply complete website damage attribution or ranking eligibility. Unrecognised creatures do not start regular recordings by default. Explicitly enabling research also allows generic combat recordings for mapping new encounters.

## Optional uploads and live logging

Visit https://zenyte.gg/connection while signed in, review the public-sharing and research-contribution consent, and generate a connection key. Paste it into the masked **Plugin connection key** setting. Enable **Allow Zenyte networking** and accept its privacy warning, then enable **Automatically upload logs**, **Public live logging**, or both. All three switches default OFF. Disabling **Allow Zenyte networking** blocks new requests, including queued uploads and stop notifications. Live sharing also requires website consent.

These options send regular recordings for recognised encounters to the fixed HTTPS zenyte.gg API. Completed uploads are public; live sharing broadcasts combat data including character names, gear and positions. Research files are never automatically transmitted. Local files remain available if the connection fails. Live sharing alone is not a saved website archive; enable automatic upload to save completed recordings there.

Keys expire after 90 days and can be revoked on the website. RuneLite stores the key in its configuration; the key is excluded from recordings. Requests use a bounded background queue, timeouts and no redirects. Failed uploads require manual upload through My Logs. The live service currently limits streams to 64 MiB each / 128 MiB combined and eight simultaneous streams, and shows offline after 20 seconds without updates.

## Research and bookmarks

Research starts OFF each plugin session, including after updating. Regular recording remains available without research.

- `::zenyte research on`: attach combined diagnostics to encounters.
- `::zenyte research off`: stop research for new encounters; attached research finishes with its fight.
- `::zenyte research status`: show the current setting.
- `::zenyte research continuous`: explicitly capture outside combat for arena mapping; combined fight capture stays enabled.

The optional **Research mode hotkey** toggles combined research with chat and overlay feedback. **Bookmark hotkey** marks the current recording tick. Both are unassigned by default and neither automates gameplay. **Show recorded tick** defaults ON; the generic idle timeout defaults to 15 ticks.

Combined research saves under `research/<boss>_<date>_<time>_<UUID>/`, alongside the regular replay. Research parts are diagnostics, not separate replay files. Researcher accounts can manually upload a combined folder through the website.

## Data and limits

Regular logs include your character name, equipment, inventory, stats, prayers, buffs, position, targets, animations, hitsplats and encounter effects. Scoped participant capture in Yama, Scurrius, Royal Titans and active CoX records visible nearby names and positions, not other players' gear or prayers. Research adds nearby actor/object/projectile/graphic observations, game/system messages, selected menu-action metadata and numeric game variables. Player chat channels, typed chat and login credentials are not collected. System messages can contain names.

Ordinary encounters allow 2,000 snapshots; CoX allows 12,000. Research rotates at 30,000 events or 1,000 ticks. There is no total local disk quota or crash recovery. Background save failures pause capture with a notice; fix storage and restart the plugin. A crash before saving finishes can lose queued data.

Weapon IDs and animation observations are interpreted on the website, without a client weapon allowlist. Client evidence cannot reveal every server calculation; unknown damage should remain unknown. Vardorvis has automated kill/exit/death boundary tests but still needs dedicated in-game validation of those outcomes. See REVIEW.md for detailed capture and privacy boundaries.

## Build

Use JDK 17 or newer for Gradle; the plugin targets Java 11. Run `gradlew.bat test build` on Windows or `sh gradlew test build` elsewhere. `gradlew run` launches a development client; a JAR cannot simply be dropped into a normal installation.

The plugin folder is a standalone source repository. Do not publish the surrounding website, deployments or player logs. See SUBMISSION.md for release steps.
