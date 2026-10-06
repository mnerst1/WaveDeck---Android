# Local release checklist

Automated execution results belong in `BUILD_REPORT.md`. Unchecked items below are manual release gates, not claims of completion.

- [ ] Clean install on API 26, 29, 32, 33 and 37; deny permission, allow it later and revoke it while running.
- [ ] English / Русский / Қазақша and System language; restart app; font scaling 1.3–2.0; inspect all dialogs for clipping.
- [ ] MediaStore scan with real MP3, AAC, FLAC, Opus and WAV files as supported on each device; corrupt/unsupported files show recovery.
- [ ] Thousands/tens of thousands of tracks: scan latency, scrolling, search, memory, cover cache and observer bursts.
- [ ] Removed/moved files, SD card disconnect/reconnect, process recreation and queue restoration.
- [ ] Real sound, seeking, next/previous, shuffle, repeat off/all/one, speed; pause without activity; resumed queue does not autoplay.
- [ ] Background/locked-screen playback, notification actions, headset buttons, unplug pause, Bluetooth reconnect, calls/audio-focus interruption.
- [ ] Queue duplicates, play-next, add, move, remove current/last track and empty queue.
- [ ] Sleep timer pauses while screen locked; cancel/re-arm; process death clears the service-owned timer.
- [ ] Favorites, counts after listening threshold, recent/most-played, history limit and non-destructive duplicate candidates.
- [ ] Playlist create/rename/delete, multi-select add/remove, SAF M3U8 round-trip, unmatched/ambiguous filenames.
- [ ] Plain/local LRC import, repeated timestamps, fractional times, negative/global/manual offsets, tap-to-seek and editing.
- [ ] Album art from MediaStore and embedded fallback; missing art, huge covers and demo cover.
- [ ] Both complete design systems and the six Material player layouts, portrait/landscape/tablet, light/dark/AMOLED and all settings after restart.
- [ ] Glass blur fallback on API <31, reduced effects on slow devices and reduced-motion behavior.
- [ ] Visualizer permission denial, unsupported audio session, real waveform changes and release of the effect; playback always independent.
- [ ] System equalizer present/absent, output settings, codec/bitrate/sample-rate availability.
- [ ] Resizable widget actions after process recreation, app shortcut, external browser root/pagination; test actual Android Auto host separately.
- [ ] TalkBack labels, checked states, 48dp controls, D-pad/keyboard, contrast and color-independent selection indicators.
- [ ] Room 1→2 migration retains annotations; no destructive fallback; signed release install/upgrade with your own key.
- [ ] Airplane-mode playback; APK permission audit; no analytics/network; clean ZIP without machine paths, caches or secrets.

## Two-design regression

- [ ] The design selector offers only Material Design and Liquid Glass.
- [ ] Glass replaces the home, library, navigation, mini-player and fullscreen player composition.
- [ ] Switching styles preserves the playing queue and favorite/playlist data.
- [ ] Glass volume slider adjusts STREAM_MUSIC and follows hardware volume changes.
- [ ] Glass queue, lyrics, speed, sleep, output and equalizer actions work.
- [ ] Settings persist after force-stop/relaunch; old styles migrate to Material.
- [ ] Light/dark/AMOLED, text scaling and reduced-effects options remain legible.
- [ ] Tablet sidebar and landscape fullscreen player remain usable.
