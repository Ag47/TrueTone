# Third-party notices

- TrueTone's original repository license is MIT; see the root LICENSE. Existing attribution is preserved.
- The active scanner/QR encoder uses **ZXing core 3.5.3**, Apache License 2.0, copyright ZXing authors. Upstream: https://github.com/zxing/zxing ; license: https://github.com/zxing/zxing/blob/zxing-3.5.3/LICENSE
- The original `app/src/main/java/com/google/zxing`, `com/leff/midi`, `com/ringdroid`, `com/midisheetmusic` and other bundled sources retain their existing headers and notices. These sources are historical and excluded from the version 2 build. No claim is made that the root MIT license overrides their individual licenses.
- Test-only dependencies are JUnit 4.13.2 (EPL-1.0) and AndroidX Test runner 1.6.2 / ext-junit 1.2.1 (Apache-2.0). They are not production app features.

The new musical model, parser, renderer and storage implementation do not copy the legacy MIDI or Ringdroid implementation. The two legacy MIDI fixtures are retained from the original repository solely to document compatibility outcomes.
