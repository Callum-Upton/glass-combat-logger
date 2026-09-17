# Zenyte

A RuneLite plugin that records encounters as local JSON files for later replay and analysis. Companion website: [Zenyte](https://zenyte.gg).

The plugin does not upload recordings, contact the website, require an account, or automate gameplay. All users have the same recording features. The Plugin Hub currently lists the earlier release as Glass Combat Logger. This source prepares the next update under the Zenyte name.

## Recording a fight

1. Enable **Zenyte** in RuneLite.
2. For research testing, enable **Combined capture** before fighting. Leave standalone **Research mode** off.
3. Fight normally. Recording starts automatically from observed incoming or outgoing damage, including zero hits. Up to 15 preceding ticks of player state are retained.
4. Wait for **Log saved** and, with research enabled, **Research saved** messages. A recording-stopped message alone does not mean the file has finished saving.
5. Click the gold gem in RuneLite's sidebar, then **Copy folder path** and paste it into your file manager.

Regular logs save under `.runelite/encounter-ledger/` in your user home folder. Combined capture groups the encounter JSON, numbered research parts and arena snapshots under `research/<boss>_<date>_<time>_<UUID>/`. Folder times use your computer's local timezone. Existing settings and file paths are preserved from earlier Encounter Ledger / Zenyte builds.

Upload the regular encounter JSON to Zenyte for review. Research parts and arena snapshots are separate diagnostic formats and cannot be imported as fights. Accounts with researcher permission can upload the combined folder on My Logs. Uploading or sharing is a separate user action; read the website's data-use notice before uploading.

## Settings

| Setting | Default | Behaviour |
| --- | --- | --- |
| Combined capture | Off | Automatically starts research with an encounter, groups the files, and waits between encounters. Turning it off during a linked encounter allows that recording to finish. |
| Research mode | Off | Standalone diagnostic recording from the next logged-in tick until disabled. Use with Combined capture off when exploring an arena or researching a new boss. |
| Show recorded tick | On | Displays recording status and the current recorded tick for matching a video to a replay. |
| End after idle ticks | 15 | Ends generic encounters after this many ticks without damage or a live NPC interaction. |

## Coverage and limitations

Yama, Scurrius and post-quest Vorkath have explicit encounter boundaries. Yama and Vorkath wait briefly for an official kill-time message after boss death. Judge phases, stepping stones and Vorkath's acid phase remain inside the active encounter. Adds do not finish the main boss fight. A surviving Vorkath spawn fought after a saved kill can produce a separate generic recording. Generic recording is not restricted to a boss allowlist: close successive fights can share a file, and incoming environmental damage can start one. New bosses need validation before relying on exact kill boundaries or rankings.

Encounters otherwise end on player death, idle timeout, logout/hop/disconnect, plugin disable, or 2,000 recorded ticks (about 20 minutes). The last limit ends the regular log; it is not a continuous raid recording. Research splits into consecutive files at 30,000 events or 1,000 ticks, preserving its session timeline. There is no total disk quota; remove recordings you no longer need.

Weapon interpretation belongs to the website. The plugin retains each non-idle local animation start while targeting an NPC, with the weapon ID/name, selected style, target and client timing, including unknown weapons. Defensive and utility animations may also be present; an observation is not a confirmed attack.

Client observations do not reveal every server calculation. NPC health is a ratio, attack animations can be ambiguous, and a damage hitsplat does not directly identify the attacker. Missing evidence must not be treated as confirmed damage attribution.

## Data recorded

Regular logs include your character name, equipment and inventory, skills, active prayers and buffs, special energy, position, target, animations, observed hitsplats and encounter effects. Yama's participant evidence includes nearby character names. During recognized Yama, Scurrius and active Chambers encounters, regular snapshots also retain visible nearby players' character names, stable recording-local identities and tile positions (same world view, up to 48 tiles away). Missing samples do not establish that someone left the fight. These samples do not include another player's equipment, prayers or exact health. Research adds nearby NPC/object/projectile/graphic observations, anonymous other-player actor references, game/system messages, selected menu-action metadata and raw numeric game variables. It does not inspect another player's equipment. System messages can contain names.

Public, private, clan and friends chat message types, menu target strings, typed chat text and login credentials are not collected. Raw numeric variables can include settings unrelated to combat. See [REVIEW.md](REVIEW.md) for capture boundaries and implementation details.

Saves run on a bounded background queue, writing a temporary file before moving it into place. Storage failures are reported in chat and pause capture. Fix storage and restart the plugin after an error. Closing or crashing the client before saving completes can lose an active or queued recording.

## Build and run from source

Install JDK 17 or newer to run Gradle 8.10; the plugin targets Java 11. The first build needs internet access for Gradle and RuneLite dependencies.

Windows:

```powershell
.\gradlew.bat test build
.\gradlew.bat run
```

Linux/macOS:

```sh
sh gradlew test build
sh gradlew run
```

The `run` task launches RuneLite's development client with this plugin loaded. A JAR cannot simply be dropped into a normal RuneLite installation. For Jagex accounts, follow [RuneLite's development-login guide](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

The source package is standalone: it does not require a RuneLite fork, the website source, or any recorded logs. See [SUBMISSION.md](SUBMISSION.md) for the Plugin Hub submission steps.
