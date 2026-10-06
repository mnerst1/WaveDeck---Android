# WaveDeck privacy

Effective date: 2026-10-06. WaveDeck has no account, server, telemetry, advertisements, analytics SDK or internet permission.

Audio metadata and user-created favorites, playlists, lyrics, history, queue and settings remain in the app's private database/preferences. MediaStore audio permission is requested to read the device library: READ_MEDIA_AUDIO on Android 13+, READ_EXTERNAL_STORAGE only through Android 12. No broad file-management permission is used. Lyrics/playlists use Android's document picker and one-time access to chosen documents.

The optional visualizer asks for RECORD_AUDIO because Android requires it to capture the playback waveform. It does not create microphone recordings, save captured bytes or send them anywhere. Denial or unsupported effects leaves playback available. Foreground media service and wake lock support background playback; system notification controls are provided by Media3.

Android application-data backup is disabled. Exported playlists are written only to the location you choose. Uninstall removes WaveDeck's private data and synthetic demo, while your media files remain on storage. Playlist deletion never deletes audio. Library indexing and duplicate detection never perform destructive filesystem operations.

Other apps granted media-controller access by Android can browse/control the session as allowed by Media3. The current browsing implementation lists songs. WaveDeck itself does not send data to an online service. Third-party document providers chosen by the user have their own policies.
