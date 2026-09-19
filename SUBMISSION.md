# Plugin Hub update

Publish this plugin folder only to Callum-Upton/glass-combat-logger. Update the existing `plugins/glass-combat-logger` entry in runelite/plugin-hub to the exact source commit; do not create a second plugin entry. Preserve the package, config group and recording paths.

Before publishing, run Gradle test/build, review the diff against the latest approved source and verify capture-capabilities.json matches registered profiles. The capability resource lets the website report support from the approved commit.

This update adds opt-in public live logging and automatic regular uploads using a scoped website connection key. Both default off. Research files are local unless uploaded manually. It also adds explicit research commands, a research hotkey, Vardorvis capture and release capability metadata. Describe the networking and consent model in the PR; see REVIEW.md.

The user has tested live logging and automatic uploads successfully. Automated boundary tests cover Vardorvis; dedicated live kill/death/exit validation remains outstanding. Plugin Hub approval is separate from these tests.

Official instructions: https://github.com/runelite/plugin-hub#submitting-a-plugin
