@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.wavedeck.media

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.*
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dev.wavedeck.*
import dev.wavedeck.R
import dev.wavedeck.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun Track.mediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(android.net.Uri.parse(artwork))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build()
        )
        .build()

class PlaybackService : MediaLibraryService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val saveLock = Mutex()
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaLibrarySession
    private val dao
        get() = (application as WaveDeckApp).database.music()

    private var restoring = true
    private var sleepJob: Job? = null
    private var pendingCount: String? = null
    private var listened = 0L

    override fun onCreate() {
        super.onCreate()
        player =
            ExoPlayer.Builder(this)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true,
                )
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build()
        val intent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val sessionPlayer =
            object : ForwardingPlayer(player) {
                override fun play() {
                    if (this@PlaybackService.player.playbackState == Player.STATE_ENDED) {
                        this@PlaybackService.player.seekToDefaultPosition()
                    }
                    this@PlaybackService.player.play()
                }
            }
        session =
            MediaLibrarySession.Builder(this, sessionPlayer, Callback())
                .setSessionActivity(intent)
                .build()
        player.addListener(
            object : Player.Listener {
                override fun onEvents(p: Player, events: Player.Events) {
                    if (
                        !restoring &&
                            (events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                                events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                                events.contains(Player.EVENT_IS_PLAYING_CHANGED) ||
                                events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                                events.contains(Player.EVENT_REPEAT_MODE_CHANGED) ||
                                events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED) ||
                                events.contains(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED))
                    )
                        persist()
                }

                override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                    pendingCount = item?.mediaId
                    listened = 0
                }
            }
        )
        scope.launch {
            val ids = dao.queue()
            val saved = dao.resume()
            val valid = ids.mapNotNull { dao.track(it.trackId) }
            if (valid.isNotEmpty()) {
                val selectedId = ids.getOrNull(saved?.index ?: 0)?.trackId
                val index = valid.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
                player.setMediaItems(valid.map { it.mediaItem() }, index, saved?.position ?: 0)
                player.repeatMode = saved?.repeat ?: Player.REPEAT_MODE_OFF
                player.shuffleModeEnabled = saved?.shuffle ?: false
                player.setPlaybackSpeed(saved?.speed ?: 1f)
                player.prepare()
            }
            restoring = false
            while (isActive) {
                delay(1000)
                if (player.isPlaying) {
                    listened += 1000
                    val threshold = minOf(30000L, (player.duration.takeIf { it > 0 } ?: 60000L) / 2)
                    if (listened >= threshold) {
                        pendingCount?.toLongOrNull()?.let { dao.recordPlay(it) }
                        pendingCount = null
                    }
                }
                if (player.mediaItemCount > 0 && player.isPlaying && listened % 5000L == 0L)
                    persist()
            }
        }
    }

    private fun persist() {
        if (restoring) return
        PlayerWidget.update(
            this,
            player.currentMediaItem?.mediaMetadata?.title?.toString()
                ?: getString(R.string.no_track),
            player.isPlaying,
        )
        val ids =
            (0 until player.mediaItemCount).mapNotNull {
                player.getMediaItemAt(it).mediaId.toLongOrNull()
            }
        val state =
            Resume(
                index = player.currentMediaItemIndex.coerceAtLeast(0),
                position = player.currentPosition.coerceAtLeast(0),
                repeat = player.repeatMode,
                shuffle = player.shuffleModeEnabled,
                speed = player.playbackParameters.speed,
            )
        scope.launch { saveLock.withLock { dao.saveQueue(ids, state) } }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onDestroy() {
        sleepJob?.cancel()
        session.release()
        player.release()
        scope.cancel()
        super.onDestroy()
    }

    private fun <T> future(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        scope.launch {
            try {
                future.set(block())
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }

    inner class Callback : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult =
            if (controller.packageName != packageName && !controller.isTrusted)
                MediaSession.ConnectionResult.reject()
            else
                MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(
                        MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                            .buildUpon()
                            .add(SessionCommand("sleep", Bundle.EMPTY))
                            .add(SessionCommand("audioSession", Bundle.EMPTY))
                            .build()
                    )
                    .build()

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            command: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (command.customAction == "audioSession")
                return com.google.common.util.concurrent.Futures.immediateFuture(
                    SessionResult(
                        SessionResult.RESULT_SUCCESS,
                        Bundle().apply { putInt("id", player.audioSessionId) },
                    )
                )
            if (command.customAction != "sleep")
                return com.google.common.util.concurrent.Futures.immediateFuture(
                    SessionResult(SessionError.ERROR_NOT_SUPPORTED)
                )
            sleepJob?.cancel()
            val minutes = args.getInt("minutes").coerceIn(0, 180)
            if (minutes > 0)
                sleepJob = scope.launch {
                    delay(minutes * 60000L)
                    player.pause()
                    persist()
                }
            return com.google.common.util.concurrent.Futures.immediateFuture(
                SessionResult(SessionResult.RESULT_SUCCESS)
            )
        }

        override fun onAddMediaItems(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> = future {
            mediaItems.mapNotNull {
                it.mediaId.toLongOrNull()?.let { id -> dao.track(id)?.mediaItem() }
            }
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> = future {
            LibraryResult.ofItem(
                MediaItem.Builder()
                    .setMediaId("root")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(getString(R.string.library))
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                            .build()
                    )
                    .build(),
                params,
            )
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
            if (parentId != "root" || page < 0 || pageSize <= 0)
                LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            else
                LibraryResult.ofItemList(
                    dao.all()
                        .drop(
                            (page.toLong() * pageSize).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        )
                        .take(pageSize.coerceAtMost(500))
                        .map { it.mediaItem() },
                    params,
                )
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = future {
            mediaId.toLongOrNull()?.let {
                dao.track(it)?.let { t -> LibraryResult.ofItem(t.mediaItem(), null) }
            } ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }

        override fun onPlaybackResumption(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = future {
            val q = dao.queue()
            val state = dao.resume()
            val tracks = q.mapNotNull { dao.track(it.trackId) }
            val selected = q.getOrNull(state?.index ?: 0)?.trackId
            MediaSession.MediaItemsWithStartPosition(
                tracks.map { it.mediaItem() },
                tracks.indexOfFirst { it.id == selected }.coerceAtLeast(0),
                state?.position ?: 0,
            )
        }
    }
}
