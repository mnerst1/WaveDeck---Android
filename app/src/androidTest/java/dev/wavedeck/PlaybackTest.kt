package dev.wavedeck

import android.content.ComponentName
import androidx.media3.common.Player
import androidx.media3.session.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.wavedeck.data.Demo
import dev.wavedeck.media.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PlaybackTest {
    @Test
    fun realAudioQueueControlsAndBackgroundPlayback() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as WaveDeckApp
        val tracks = runBlocking {
            Demo.create(app, app.database.music())
            val t = app.database.music().track(-1)!!
            app.database.music().upsert(listOf(t.copy(id = -2, title = "Second synthesis")))
            listOf(t, t.copy(id = -2, title = "Second synthesis"))
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val future =
            MediaController.Builder(
                    app,
                    SessionToken(app, ComponentName(app, PlaybackService::class.java)),
                )
                .buildAsync()
        val c = future.get(20, TimeUnit.SECONDS)
        fun await(predicate: () -> Boolean) {
            var ok = false
            for (i in 0 until 60) {
                instrumentation.runOnMainSync { ok = predicate() }
                if (ok) return
                Thread.sleep(200)
            }
            fail("Player condition timed out")
        }
        try {
            instrumentation.runOnMainSync {
                c.setMediaItems(tracks.map { it.mediaItem() })
                c.prepare()
                c.play()
            }
            await { c.isPlaying && c.duration >= 15000 && c.currentPosition > 800 }
            instrumentation.runOnMainSync {
                c.repeatMode = Player.REPEAT_MODE_OFF
                c.seekToDefaultPosition(1)
            }
            await { c.currentMediaItemIndex == 1 && c.duration >= 15000 }
            instrumentation.runOnMainSync { c.seekTo(c.duration) }
            await { c.playbackState == Player.STATE_ENDED }
            instrumentation.runOnMainSync { c.play() }
            await { c.isPlaying && c.currentPosition < 5000 }
            instrumentation.runOnMainSync { c.seekToDefaultPosition(0) }
            instrumentation.runOnMainSync {
                c.shuffleModeEnabled = true
                c.repeatMode = Player.REPEAT_MODE_ALL
                c.setPlaybackSpeed(1.25f)
                c.seekTo(5000)
            }
            await {
                c.currentPosition >= 5000 &&
                    c.shuffleModeEnabled &&
                    c.repeatMode == Player.REPEAT_MODE_ALL &&
                    c.playbackParameters.speed == 1.25f
            }
            instrumentation.runOnMainSync {
                c.shuffleModeEnabled = false
                c.moveMediaItem(0, 1)
            }
            await { c.getMediaItemAt(0).mediaId == "-2" }
            instrumentation.runOnMainSync { c.seekToDefaultPosition(0) }
            await { c.currentMediaItem?.mediaId == "-2" && c.isPlaying }
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            await { c.isPlaying && c.currentPosition > 600 }
            instrumentation.runOnMainSync {
                c.pause()
                c.removeMediaItem(1)
                c.repeatMode = Player.REPEAT_MODE_ONE
            }
            await {
                !c.isPlaying && c.mediaItemCount == 1 && c.repeatMode == Player.REPEAT_MODE_ONE
            }
            Thread.sleep(1000)
            val saved = runBlocking { app.database.music().queue() }
            assertEquals(listOf(-2L), saved.map { it.trackId })
        } finally {
            instrumentation.runOnMainSync {
                c.pause()
                MediaController.releaseFuture(future)
            }
            scenario.close()
        }
    }
}
