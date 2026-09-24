# TrueTone

**Make a little melody. Pass it on. Make it your own.**

TrueTone is an offline Android melody studio. Version 2 replaces the original application entry point with a small, testable music core and a new three-part interface: **Library · Create · Import**.

## What works

- Draw notes on a piano roll, or record a keyboard performance including pitch, velocity and held duration.
- Start with a blank project or two musical templates. Use C-major assistance, a quarter-note metronome, tempo changes, note editing, quantization, undo/redo and overdubbing.
- Save drafts automatically and keep named library copies in app-private storage. Completed note edits survive closing or killing the process. Interrupted takes retain completed notes; a key that has not been released is not committed until the activity pauses or the key is released.
- Preview a project and export MIDI, an editable `.ttn` backup, or synthesized WAV audio. WAV and in-app playback use the same renderer.
- Share short melodies as versioned offline QR codes; use a file for larger projects. Scan inside the app, import a QR image, or paste QR text. Imports require preview/confirmation before entering the library.
- Enable optional tilt expression, calibrate the neutral angle and adjust sensitivity. Fixed velocity is the default.

There is no account, network permission, microphone recording, contacts access or telephone access. Camera permission is requested only when opening the scanner. File import/export uses the Android system document picker. This build contains no native audio libraries.

## Build and verify

Requirements: JDK 17+, Android SDK platform **36.1**, Build Tools **36.0.0**. Android **8.0 / API 26** or later is supported by the build configuration. Device tests in this change were run on an Android 15 / API 35 ARM64 emulator; minimum-version physical-device testing remains pending.

```sh
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
./scripts/check.sh
# With an emulator or test device connected:
./scripts/check.sh --device
```

Gradle **9.1.0** is pinned with its distribution SHA-256. Android Gradle Plugin is **9.0.0**. The `:app` Gradle project points to `studio/`; `core/` is an ordinary Java library. The APK is `studio/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions builds the APK and runs lint/unit tests, with a separate emulator-test job. Local results and remaining acceptance work are recorded in [verification](docs/VERIFICATION.md). The recorded verification covers local runs; consult the pull request checks for GitHub Actions results.

## Architecture and boundaries

| Location | Responsibility |
| --- | --- |
| `core/.../Project.java`, `NoteEvent.java` | Immutable musical state; 960 ticks per quarter-note beat |
| `core/.../Editor.java` | Undo/redo and quantization |
| `core/.../ProjectCodec.java`, `ProjectStore.java` | Versioned, bounded interchange; atomic local persistence |
| `core/.../MidiCodec.java` | SMF type 0/1 import and type 1 export |
| `core/.../Synth.java` | Deterministic frame scheduling and WAV synthesis |
| `studio/.../MainActivity.java` | Library, editor, performance and import/share flows |
| `studio/.../AudioEngine.java` | Streaming AudioTrack output, live keyboard and audio focus |
| `studio/.../ScanActivity.java`, `ShareProvider.java` | In-app camera decoding and temporary read-only file grants |

The music model supports **40–240 integer BPM**, **256 quarter-note beats**, **16 tracks**, **4,096 note events** and **32 simultaneous project notes**. QR payloads are limited to **1,200 characters**. Limits are validated before a project is accepted; events are never silently truncated.

MIDI import accepts constant-tempo, constant-meter, beat-based type 0/1 files. Variable/fractional tempo, SMPTE timing, sustain/pitch-bend expression and unfinished notes are rejected with an explanation. Instrument programs and other controller data are not part of the melody model; imported notes use TrueTone's instrument. MIDI export rejects overlapping notes with the same pitch and channel within a track because their note-off identity is ambiguous; editable `.ttn` sharing remains available. See [format specification](docs/FORMATS.md).

## Original application and migration

The original Android source, resources and third-party code remain in `app/` as historical compatibility material. They are **not compiled or merged into the new APK**. Their Gradle file and Manifest describe the old app, not the current configuration. The active build configuration is `studio/build.gradle` and `studio/src/main/AndroidManifest.xml`.

The application ID is retained, with version code 2. An installed historical build can only be upgraded using the same signing key; the debug APK is not a production release. Version 2 imports existing MIDI files through the document picker. It does not infer missing pitches from historical SoundPool IDs, restore unexported in-memory tracks, or silently scan legacy external-storage folders. Exported legacy MIDI may already contain timing/pitch errors that cannot be reconstructed reliably.

Two original MIDI samples are preserved under `core/src/test/resources/legacy/`. `TrueTone.mid` imports as 42 notes at 120 BPM and round-trips; the Minuet sample is explicitly rejected for unsupported expression events.

The project retains its [MIT license](LICENSE). Original bundled third-party headers remain intact; the active ZXing dependency is Apache-2.0. See [third-party notices](docs/THIRD_PARTY.md).

## Product scope

This is a working local-first melody MVP, not a cloud/social product. Web playback, cloud synchronization, public feeds, AI composition, microphone editing, ringtone/contact assignment and a production Oboe engine are deferred. Physical latency, acoustic quality, camera optics and interruption behavior require the device acceptance pass in [verification](docs/VERIFICATION.md).
