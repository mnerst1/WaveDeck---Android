# Architecture

## Boundaries

`WaveDeckApp` owns the Room database, DataStore settings, MediaStore scanner and artwork cache. It is a small composition root; objects are shared explicitly rather than through a DI container.

`data` contains entities, transactional DAO operations, settings serialization, media discovery, artwork resolution and synthetic demo generation. `model` contains deterministic LRC, queue-boundary, sorting/search, duplicate and M3U rules. `media` owns the actual ExoPlayer, MediaLibrarySession, media browsing and widget controls. `ui` renders immutable track/settings/playback snapshots and delegates actions to `PlayerViewModel` or document contracts.

The service owns playback and the sleep timer. Closing an activity releases its controller, not the service player. Media3 handles system notifications, audio focus and becoming noisy; the manifest declares a media-playback foreground service. Controller-added items are resolved against the local database so external clients cannot introduce arbitrary remote media URLs.

## State and persistence

Room stores tracks and annotations, playlists/ordered membership, bounded history, queue order and resumption state. Queue/state replacement is transactional. Snapshot writes are serialized with a mutex. Position checkpoints occur approximately every five seconds of playback and at meaningful transitions. Force-kill can lose the most recent checkpoint; restoration never auto-starts audio.

A play is counted after 30 seconds or half the track, whichever is shorter, rather than merely after tapping it. Recent/most-played collections derive from recorded plays. Duplicate candidates are metadata/size matches, not cryptographic equivalence, and are never automatically deleted.

The MediaStore scan runs off the main thread and observes changes. A complete rescan reconciles IDs, retains favorites/counts/imported lyrics for existing IDs and removes disappeared indexed tracks. The optional synthetic sample uses negative IDs and remains available without audio permission. Playlist memberships/history can reference disappeared tracks; UI resolves only available tracks and preserves these references if the same ID returns.

DataStore persists appearance choices and the language preference. AppCompat supplies application locales on old Android versions and integrates with platform app languages on recent versions. Language choice is stored by both DataStore and AppCompat; system-level locale changes may leave the displayed preference chip until the user chooses a language again.

## Database evolution

Exported Room schema JSON is committed. Schema 1 is the initial table set. Explicit `MIGRATION_1_2` adds the non-null, empty-default filename column without destroying playlists or annotations. Schema 2 is current. A migration test opens the actual schema-1 structure, inserts a track and verifies retention after Room migrates it. Future upgrades must add migrations and corresponding tests. Destructive fallback is not enabled.

## Performance and rendering

The library remains an in-memory list backed by Room Flow; Compose uses lazy rows/grids and stable track IDs. Search/sort runs on a background dispatcher and search is debounced. This is not a Paging implementation. Large-library benchmarking is an explicit release gate.

Coil downsamples and caches local covers. MediaStore album art is preferred; embedded pictures are retrieved off the main thread if missing. The bounded embedded-art cache is 8 MiB and accepts covers up to 4 MiB. The player derives a low-resolution artwork tint. Glass uses layered surfaces and an optional artwork blur on API 31+, with solid/tinted fallbacks. Reduced effects disables expensive artwork effects and waveform capture. Reduced motion stops vinyl rotation and uses immediate lyric scrolling.

The waveform visualizer attaches to the service audio-session ID only after explicit permission. Platform errors release the effect and show a localized fallback. There is no FFT/DSP manipulation. The seek bar's static wave is decorative.

## Scope decisions

One Gradle module avoids a fragile module/dependency graph for this delivery. No backend, network permission, WorkManager polling or analytics is needed. MediaBrowser clients get a root, paginated songs and item lookup; Android Auto certification/search hierarchy remains future work. The original expressive presentation uses stable Material 3 building blocks rather than experimental APIs.

## Two complete presentations

`Settings.mode` normalizes stored preferences to `material` or `glass`. Old non-glass style names become Material, while a former glass layout becomes Classic in Material. `MainShell` keeps its NavController and playback ViewModel when changing the design; it selects specialized Glass home, library, navigation and fullscreen player compositions. Shared queue, lyrics, playlist and search functions use the same Room/MediaController state. Glass library categories drill into the existing sortable/multi-select library; folders and smart collections remain accessible there.

`FrostedSurface` uses a downsampled local-artwork underlay with Android 12+ blur and layered translucent/specular surfaces. This is controlled artwork blur, not an arbitrary backdrop capture or an optical refraction shader. Reduced effects disable blur and shadows. The fullscreen player uses the actual cover and gradient scrim, flat white transport, a thin accessible seek control and the Android STREAM_MUSIC volume. Its volume observer runs only while that composition exists. Glass uses a sidebar at tablet width.
