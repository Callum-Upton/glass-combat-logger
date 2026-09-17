# Plugin Hub submission

## Next update: Zenyte branding

The initial Glass Combat Logger submission (#16276) is merged. Prepare the next release in the existing Callum-Upton/glass-combat-logger repository and update the existing plugins/glass-combat-logger manifest on a new update PR; do not create a second plugin entry. The display name is Zenyte, the author is Callum Upton, and icon.png is the 48px Hub logo. The sidebar uses the bundled 24px homepage gem. Keep the Java class, configuration group, and recording paths unchanged so existing settings and logs carry over. This update includes the accumulated recorder improvements and new capture-only profiles; see REVIEW.md for validation limits.

The initial-submission template below is retained for reference.


The source in this folder is the repository root for **Zenyte**. Do not publish the whole development workspace: the website, server, deployments and user recordings are separate.

1. Create or select a public GitHub repository, suggested name `glass-combat-logger`.
2. Put this folder's source, Gradle wrapper, metadata, README, review notes and license at its root. `.gitignore` excludes build/cache files. On Linux/macOS, `sh gradlew test build` works even if extracting a ZIP did not preserve executable permissions.
3. Run `gradlew.bat test build` (Windows) or `sh gradlew test build`. Launch `run` for the smoke test below.
4. Commit and push the source. Record the exact full 40-character commit hash.
5. Fork `runelite/plugin-hub`, create a branch, and add `plugins/glass-combat-logger` containing:

```properties
repository=https://github.com/YOUR-ACCOUNT/glass-combat-logger.git
commit=YOUR-FULL-COMMIT-HASH
```

6. Open the pull request using the description below. Watch its build checks and address any reviewer feedback in that same PR. If source changes, update the marker to the new commit.

An existing submission is tracked at https://github.com/runelite/plugin-hub/pull/16276 for https://github.com/Callum-Upton/glass-combat-logger. Preparing this package does not push source or update that PR. Use the existing submission when updating; do not create a duplicate. The example marker above must be replaced with the actual new source commit after review.

## Short smoke test

- Enable Zenyte; confirm its gold sidebar gem and recorded-tick overlay appear.
- Open the sidebar and click **Copy folder path**, then paste the result into your file manager. Before the first save the path is available, but the directory may not exist yet.
- Enable Combined capture with Research mode off. Confirm no research folder appears while waiting out of combat.
- Record a Yama fight, wait for saved messages, and check the regular log and research files share one folder.
- Wait between fights: confirm Combined capture does not start a spare recording. Try a second fight and check it gets a separate folder.
- Import the regular JSON to Zenyte; confirm the replay opens and any observed official time is present.
- Disable the plugin and confirm its sidebar icon and overlay disappear. Re-enable and verify normal operation.

## Suggested PR description

Zenyte records local tick-by-tick encounter JSON for post-fight analysis. It includes an optional research recorder for discovering boss mechanics and a sidebar button to copy the local logs folder path. Research and regular logs can be grouped automatically per encounter.

The plugin performs no automatic uploads or gameplay actions. Research is disabled by default. Capture scope, nearby actor observations, numeric game-variable capture, file limits and known encounter-boundary limitations are documented in REVIEW.md. The companion Zenyte website accepts separate user-initiated uploads.

Validation: Gradle test/build; report the completed in-game smoke-test results before submitting.

Official instructions: https://github.com/runelite/plugin-hub#submitting-a-plugin
