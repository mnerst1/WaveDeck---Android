# Contributing

Open an issue with device model/API, app version, reproduction steps and a sanitized error description. Never attach private music, lyrics, database contents or signing secrets.

Keep playback in the service and disk work off the UI thread. Add meaningful regression tests for behavior changes. Preserve en/ru/kk keys and formatting placeholders. Add explicit Room migrations and retain exported schemas. Run unit tests, lint, assembleDebug and connected tests on a disposable emulator before submitting playback changes.

Use original licensed artwork and assets. Do not scrape lyric services, add telemetry or request broad storage permissions. Dependency upgrades must retain real playback tests. Manual device checks in TEST_CHECKLIST remain necessary before release.
