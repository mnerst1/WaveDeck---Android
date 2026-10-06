@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.wavedeck

import android.app.Application
import android.content.ComponentName
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.media3.common.*
import androidx.media3.session.*
import dev.wavedeck.data.*
import dev.wavedeck.media.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class PlaybackState(
    val queue: List<String> = emptyList(),
    val index: Int = 0,
    val playing: Boolean = false,
    val position: Long = 0,
    val duration: Long = 0,
    val shuffle: Boolean = false,
    val repeat: Int = 0,
    val speed: Float = 1f,
    val error: Boolean = false,
    val connected: Boolean = false,
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as WaveDeckApp
    val dao = app.database.music()
    val tracks =
        dao.tracks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val playlists =
        dao.playlists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val entries =
        dao.entries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val history =
        dao.history().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings = app.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val state = MutableStateFlow(PlaybackState())
    val operationError = MutableStateFlow(false)
    val audioSession = MutableStateFlow(0)
    private var controller: MediaController? = null
    private var queueSnapshot: List<String> = emptyList()
    private var pendingPlay: Pair<List<Track>, Int>? = null
    private val future =
        MediaController.Builder(
                application,
                SessionToken(application, ComponentName(application, PlaybackService::class.java)),
            )
            .buildAsync()

    init {
        future.addListener(
            {
                try {
                    controller = future.get()
                    controller?.addListener(
                        object : Player.Listener {
                            override fun onEvents(player: Player, events: Player.Events) {
                                update(events.contains(Player.EVENT_TIMELINE_CHANGED))
                            }

                            override fun onPlayerError(error: PlaybackException) {
                                state.value = state.value.copy(error = true)
                            }
                        }
                    )
                    update(true)
                    pendingPlay?.let { (tracks, index) ->
                        pendingPlay = null
                        play(tracks, index)
                    }
                } catch (_: Exception) {
                    state.value = state.value.copy(error = true)
                }
            },
            ContextCompat.getMainExecutor(application),
        )
        viewModelScope.launch {
            while (isActive) {
                delay(400)
                update()
                if (state.value.playing && audioSession.value == 0) requestAudioSession()
            }
        }
    }

    private fun requestAudioSession() {
        controller?.let { c ->
            val result =
                c.sendCustomCommand(SessionCommand("audioSession", Bundle.EMPTY), Bundle.EMPTY)
            result.addListener(
                { runCatching { audioSession.value = result.get().extras.getInt("id") } },
                ContextCompat.getMainExecutor(app),
            )
        }
    }

    private fun update(refreshQueue: Boolean = false) {
        controller?.let { c ->
            if (refreshQueue || queueSnapshot.size != c.mediaItemCount) {
                queueSnapshot = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }
            }
            state.value =
                PlaybackState(
                    queueSnapshot,
                    c.currentMediaItemIndex.coerceAtLeast(0),
                    c.isPlaying,
                    c.currentPosition.coerceAtLeast(0),
                    c.duration.coerceAtLeast(0),
                    c.shuffleModeEnabled,
                    c.repeatMode,
                    c.playbackParameters.speed,
                    c.playerError != null,
                    true,
                )
        }
    }

    fun play(list: List<Track>, index: Int = 0) {
        if (list.isEmpty()) return
        if (controller == null) {
            pendingPlay = list to index
            return
        }
        controller?.run {
            setMediaItems(list.map { it.mediaItem() }, index.coerceIn(list.indices), 0)
            prepare()
            play()
        }
    }

    fun toggle() {
        controller?.run {
            if (isPlaying) pause()
            else {
                if (playerError != null) prepare()
                play()
            }
        }
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    fun seek(position: Long) {
        controller?.seekTo(position.coerceAtLeast(0))
    }

    fun shuffle() {
        controller?.run { shuffleModeEnabled = !shuffleModeEnabled }
    }

    fun repeat() {
        controller?.run {
            repeatMode =
                when (repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
        }
    }

    fun enqueue(list: List<Track>, next: Boolean = false) {
        controller?.run {
            addMediaItems(
                if (next) (currentMediaItemIndex + 1).coerceIn(0, mediaItemCount)
                else mediaItemCount,
                list.map { it.mediaItem() },
            )
            prepare()
        }
    }

    fun move(from: Int, to: Int) {
        controller?.run {
            if (from in 0 until mediaItemCount && to in 0 until mediaItemCount)
                moveMediaItem(from, to)
        }
    }

    fun remove(index: Int) {
        controller?.run { if (index in 0 until mediaItemCount) removeMediaItem(index) }
    }

    fun jump(index: Int) {
        controller?.run {
            seekToDefaultPosition(index)
            play()
        }
    }

    fun speed(value: Float) {
        controller?.setPlaybackSpeed(value)
    }

    fun sleep(minutes: Int) {
        controller?.sendCustomCommand(
            SessionCommand("sleep", Bundle.EMPTY),
            Bundle().apply { putInt("minutes", minutes) },
        )
    }

    fun action(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                operationError.value = true
            }
        }
    }

    fun favorite(id: Long) = action { dao.favorite(id) }

    fun setting(key: String, value: String) = action { app.settings.string(key, value) }

    fun setting(key: String, value: Float) = action { app.settings.number(key, value) }

    fun setting(key: String, value: Boolean) = action { app.settings.flag(key, value) }

    override fun onCleared() {
        MediaController.releaseFuture(future)
        super.onCleared()
    }
}
