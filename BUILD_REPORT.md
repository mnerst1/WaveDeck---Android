# Build and verification report

Delivery date: **2026-10-06**. Status: **local-test candidate**. No GitHub publication, production signing, physical-device certification or blanket production-readiness claim.

## Executed successfully

The final source was built with the installed Android Studio JDK 25, Gradle 9.6.0, AGP 9.4.1, Kotlin Compose compiler 2.2.20, Android SDK 37 and Build Tools 36.0.0. JVM source/bytecode target is 17; minimum API is 26 and target API is 37.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:assembleDebugAndroidTest --max-workers=2 --console plain
.\gradlew.bat :app:connectedDebugAndroidTest --max-workers=2 --console plain
```

The final build and isolated device-test runs returned **BUILD SUCCESSFUL**. An earlier concurrent run was interrupted after the emulator System UI stalled; its results are not used as successful verification. Earlier device-test synchronization failures were corrected with an explicit duration wait, a transition-aware hierarchy wait and lazy-list scrolling. The final rerun passes all three tests.

| Check | Result |
| --- | --- |
| Kotlin/Java compilation and assembleDebug | Passed; installable debug APK produced |
| Unit tests | 18 passed, zero failures/errors |
| Locale completeness/placeholder tests | English, Russian and Kazakh keys/format placeholders match |
| Room data and migration tests | Playlist CRUD, scan reconciliation, queue state, DataStore persistence and preserving schema 1→2 migration passed |
| Android lintDebug | Passed with zero errors and 45 warnings; no blanket warning suppression/baseline |
| Emulator tests | 3 passed on Pixel 10a AVD, Android 17 / API 37, x86_64 |
| Replay regression | Playback after end-of-queue seeks back to the start and plays; covered by device test |
| Background player test | Real PCM file playback, elapsed progress, seek, shuffle, repeat, speed, queue reorder/removal and continued playback with activity stopped |
| Compose UI tests | Onboarding → settings; Material → Glass changes chrome/library/fullscreen player, then returns to Material |
| assembleRelease / R8 | Passed; optimized **unsigned** release APK produced |
| Debug signature | Verified with Android apksigner, Android Debug certificate |
| APK permissions | Inspected with aapt2; no INTERNET, ACCESS_NETWORK_STATE or MANAGE_EXTERNAL_STORAGE permission |
| Actual-app visuals | Captured Material and the revised glass home, library, player and appearance selector; original synthetic demo cover |

Domain tests cover LRC multi-timestamps/fractions/offsets/plain text, queue bounds/reorder/repeat, search/sort, duplicate candidates and M3U matching/ambiguity. Machine paths and device-host identifiers are omitted from the sanitized `docs/verification.json` summary.

## Manual emulator checks

During the previous playback verification, two original 20-second PCM WAV fixtures were copied to the emulator's shared Music directory and indexed by Android. WaveDeck discovered their MediaStore entries alongside the device library. A fixture was selected through search; the system media session showed active playback with advancing position and 20-second duration through its MediaStore content URI.

The locally generated 16-second WaveDeck demo was selected for screenshots. The revised design switch was tested on-device, including library chrome and fullscreen player. Actual screenshots exposed scaffold padding around the player; the final build opts out of those insets so artwork extends behind system bars while controls retain safe-area padding. The corrected screen was captured and inspected. Existing non-glass design preferences migrate to Material; legacy glass player-layout preferences become Classic in Material. The emulator ran without host audio output: decoder/session progress was verified, but audible output on physical hardware was not assessed.

Warnings include pinned-but-older dependencies, supported-version attributes, resource/deprecation suggestions and build-tool/JDK diagnostics. They are not a claim that the app has no defects. The full local reports remain under the generated build directory and are excluded from the clean source ZIP.

## Artifacts

- `Day-015-WaveDeck-Android.zip`: full source, Gradle wrapper, Room schemas, tests, documentation and original assets/screenshots; caches, build directories, IDE state, local.properties and signing secrets excluded.
- `WaveDeck-1.0.0-test.2-debug.apk`: debug-signed, suitable for personal local installation.
- `WaveDeck-1.0.0-test.2-release-unsigned.apk`: actual R8 release output; requires your signing key and was not installed.
- `SHA256SUMS.txt`: hashes of the delivered ZIP and APKs. ZIP CRC/integrity is checked during packaging.

## Not verified / not implemented

Physical speaker/headset/Bluetooth controls, actual lock-screen interactions, Android Auto host certification, widget launcher variations, OEM power policies, old-SDK devices, large-library benchmarks and native-speaker proofreading remain manual gates in TEST_CHECKLIST.md.

No custom DSP, loudness normalization, mono/balance, ReplayGain, crossfade, FFT spectrum, embedded lyric extraction, custom rule editor for smart playlists or shared-element artwork morphing is claimed. The visualizer uses real waveform capture where the device allows it, with an optional permission and fallback; it has not been certified across audio-effect implementations. Glass is an original layered Android treatment with artwork blur/fallback, not optical refraction. See README for the complete feature/limitation list.
