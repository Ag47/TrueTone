# TrueTone music and sharing formats

## Canonical project

`Project` is an immutable snapshot. Its fields are version (codec v1), title, integer BPM, meter numerator/denominator, length in musical ticks, and 1–16 tracks. Each `NoteEvent` owns pitch (0–127), velocity (1–127), start tick, duration tick and channel (0–15). One quarter-note beat is exactly 960 ticks. A tick is never a millisecond or SoundPool ID.

Recording maps monotonic nanoseconds to ticks using the snapshot's BPM. Playback/export map ticks to sample frames using an absolute formula, with rounding only at the sample boundary. Repeating playback resets the renderer at the same integral loop length; it does not accumulate Timer delays. WAV is mono 48 kHz, signed 16-bit PCM. The fixed TrueTone synth is used for preview and WAV; MIDI players can use a different timbre.

Edits create new snapshots. A completed recording take becomes one undo step; every released recorded note is persisted while the take is in progress. Cancel restores and persists the pre-take snapshot. Notes are stably sorted by start time; parallel arrays and global velocity history are absent.

## `.ttn` version 1

Integers are big-endian, unsigned where indicated. Java modified UTF-8 is used for the title (`DataOutput.writeUTF` / `DataInput.readUTF`), including its 16-bit byte length. Consumers must implement this encoding, not assume ordinary UTF-8.

| Field | Width |
| --- | --- |
| Magic `TTNP` | 4 bytes |
| Version `1` | 1 byte |
| Title | Modified UTF-8 with 2-byte byte-length prefix |
| BPM | 2 bytes |
| Meter numerator, denominator | 1 byte each |
| Length in 960-PPQ ticks | 4 bytes |
| Track count | 1 byte |
| Per track: note count | 2 bytes |
| Per note: pitch, velocity, channel | 1 byte each |
| Per note: start tick, duration tick | 4 bytes each |
| CRC32 of all preceding bytes | 4 bytes |

The decoder enforces the exact version, total byte limit (128 KiB), title length (80 UTF-16 code units), field ranges, note count, 32-voice polyphony, track count, project duration and absence of trailing data. Control characters in titles are rejected. No compressed stream or object deserialization is accepted.

Local storage uses generated IDs, not imported titles. Writing is serialized: encode, write a same-directory temporary file, fsync, then atomic rename. The previous file remains intact if writing fails. Unreadable drafts are moved to a unique `.damaged` file before new work may replace the draft; if preserving the old file fails, draft writes are blocked and the user is told to export. This protects process-crash recovery; storage-device failure and uninstall are outside the guarantee. Users should export backups.

## QR

```
TRUETONE:1:<unpadded URL-safe base64 of the .ttn bytes>
```

The full payload is limited to 1,200 characters. Encoding uses QR error correction M with a four-module quiet zone. Capacity is checked before displaying the code; bigger projects get a `.ttn` file-sharing fallback. Actual scanning distance and display/print quality still matter.

There is no compression and therefore no decompression expansion. QR image imports read at most 8 MiB and downsample to at most 2,048 pixels per dimension before decoding. File imports read at most 1 MiB, then apply the stricter format-specific size limit. QR text is capped before base64 decoding. CRC32 detects accidental corruption, **not identity or authenticity**.

Decoding creates an in-memory preview. Cancel writes nothing. Keep & edit saves a uniquely named library copy and the editable draft. An existing draft is preserved as a separate library copy when switching to an imported melody.

## MIDI

Exports are SMF type 1, 960 PPQ, with a tempo/meter track and separate note tracks. Note-off sorts before note-on at the same tick, and all tracks include an end-of-track event at project length. Tempo is written as the closest integral microseconds per quarter-note value. Re-import recognizes that integer-BPM rounding within one microsecond.

Import supports SMF 0/1, running status and zero-velocity note-on as note-off. It bounds chunk/event reads, VLQ width, total bytes, duration, tracks and event count. Other PPQ resolutions are rounded to the 960-PPQ model; events shorter than one model tick cannot be represented and are rejected. Parsing pairs note-on/off FIFO within each track and channel/pitch. Unsupported expression/timing produces explicit errors. General MIDI program selection is not retained.

Same-pitch/channel overlaps within one track are rejected on MIDI export. This avoids claiming reliable independent note duration in a format with no note identity. `.ttn` is the lossless editable interchange for all supported project fields.

## Native/platform boundaries

App-private files are used for the library and cache. Android's system picker owns external import/export locations. The sharing provider exposes only UUID-named files in its own cache directory, read-only, using temporary URI grants. It never exposes the project directory. Stale share files are removed on later share operations after 24 hours. No Internet permission is declared.
