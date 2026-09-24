# Delivery and rollback

The active implementation is the `studio/` app plus the `core/` Java library. The original `app/` source remains in place as historical reference and is excluded by the root Gradle settings. The implementation does not require a production service or account.

Install the debug APK on a test device:

```sh
adb install -r studio/build/outputs/apk/debug/app-debug.apk
```

An older build signed by a different key cannot be replaced with this debug key. Export needed legacy MIDI through the original app before any manual uninstall. This work does not uninstall an existing physical-device app.

Rollback source changes with version control after preserving any subsequent local work; the baseline for this task was `bfb20737`. To inspect the baseline without touching this checkout, create a separate worktree at that commit. The obsolete baseline build still requires its historical toolchain; returning to it does not make it compatible with the modern SDK.

For data rollback, import previously exported `.ttn` backups. Library saves and import confirmations use fresh IDs, so saving a copy does not overwrite another library item. An in-progress canceled take restores the prior draft. A process kill preserves completed committed notes; unfinished held notes are not guaranteed unless the pause callback runs. No automatic migration tries to reconstruct missing original pitches/durations from SoundPool IDs.

The CI workflow and tests accompany the source change. Release signing and publication remain separate from this debug delivery.
