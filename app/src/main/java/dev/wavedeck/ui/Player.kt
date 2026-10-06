@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.wavedeck.ui

import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import dev.wavedeck.*
import dev.wavedeck.R
import dev.wavedeck.data.Track
import kotlin.math.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun NowPlaying(
    vm: PlayerViewModel,
    t: Track?,
    state: PlaybackState,
    back: () -> Unit,
    queue: () -> Unit,
    lyrics: () -> Unit,
    options: () -> Unit,
) {
    val s = LocalAppearance.current
    val context = LocalContext.current
    var extra by remember { mutableStateOf(false) }
    var eqMissing by remember { mutableStateOf(false) }
    val layout =
        if (s.layout == "classic" && s.mode in listOf("expressive", "glass", "minimal", "compact"))
            s.mode
        else s.layout
    val audioSession by vm.audioSession.collectAsStateWithLifecycle()
    var tint by remember(t?.id) { mutableStateOf<Color?>(null) }
    LaunchedEffect(t?.id, s.performance) {
        if (t != null && !s.performance)
            tint =
                withContext(Dispatchers.IO) {
                    runCatching {
                        val drawable =
                            context.imageLoader
                                .execute(
                                    ImageRequest.Builder(context)
                                        .data(t.artwork)
                                        .size(32)
                                        .allowHardware(false)
                                        .build()
                                )
                                .drawable ?: return@runCatching null
                        val bitmap = drawable.toBitmap(32, 32)
                        var r = 0L
                        var g = 0L
                        var b = 0L
                        for (x in 0 until bitmap.width) for (y in 0 until bitmap.height) {
                            val p = bitmap.getPixel(x, y)
                            r += android.graphics.Color.red(p)
                            g += android.graphics.Color.green(p)
                            b += android.graphics.Color.blue(p)
                        }
                        val n = bitmap.width * bitmap.height
                        Color(
                            android.graphics.Color.rgb(
                                (r / n).toInt(),
                                (g / n).toInt(),
                                (b / n).toInt(),
                            )
                        )
                    }
                        .getOrNull()
                }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        if (s.background == "gradient" || layout == "immersive" || s.mode == "glass")
            Box(
                Modifier.fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                (tint ?: MaterialTheme.colorScheme.primary).copy(alpha = .32f),
                                MaterialTheme.colorScheme.background,
                            )
                        )
                    )
            )
        if (
            s.background == "artwork" && !s.performance && Build.VERSION.SDK_INT >= 31 && t != null
        ) {
            AsyncImage(
                t.artwork,
                null,
                Modifier.fillMaxSize().blur(s.blur.dp).alpha(.3f),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            Box(
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = .35f))
            )
        }
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(back) {
                    Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.close))
                }
                Text(
                    stringResource(R.string.now_playing),
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                )
                IconButton(options, enabled = t != null) {
                    Icon(Icons.Rounded.MoreHoriz, stringResource(R.string.more))
                }
            }
            if (t == null) {
                EmptyState(R.string.no_track)
                return@Column
            }
            if (wide)
                Row(
                    Modifier.weight(1f).padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                ) {
                    PlayerArt(t, state.playing, layout, Modifier.weight(1f).fillMaxHeight())
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        TrackHeading(t, vm)
                        SeekControls(state, vm)
                        Transport(state, vm)
                        PlayerFooter(queue, lyrics) { extra = true }
                    }
                }
            else
                Column(
                    Modifier.weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = if (layout == "immersive") 12.dp else 28.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(if (layout == "compact") 10.dp else 22.dp),
                ) {
                    when (layout) {
                        "minimal" -> {
                            Spacer(Modifier.height(28.dp))
                            TrackHeading(t, vm)
                            Artwork(t, Modifier.size(108.dp))
                            Spacer(Modifier.height(16.dp))
                        }
                        "compact" ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Artwork(t, Modifier.size(92.dp))
                                Box(Modifier.weight(1f).padding(start = 16.dp)) {
                                    TrackHeading(t, vm)
                                }
                            }
                        "expressive" -> {
                            Text(
                                t.album,
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                            )
                            PlayerArt(
                                t,
                                state.playing,
                                layout,
                                Modifier.fillMaxWidth().aspectRatio(1.15f),
                            )
                            TrackHeading(t, vm)
                        }
                        "immersive" -> {
                            PlayerArt(
                                t,
                                state.playing,
                                layout,
                                Modifier.fillMaxWidth().aspectRatio(.88f),
                            )
                            TrackHeading(t, vm)
                        }
                        else -> {
                            Spacer(Modifier.height(8.dp))
                            PlayerArt(
                                t,
                                state.playing,
                                layout,
                                Modifier.fillMaxWidth().aspectRatio(1f),
                            )
                            TrackHeading(t, vm)
                        }
                    }
                    if (layout == "glass" || s.mode == "glass")
                        GlassPanel {
                            SeekControls(state, vm)
                            Spacer(Modifier.height(12.dp))
                            Transport(state, vm)
                        }
                    else {
                        SeekControls(state, vm)
                        Transport(state, vm)
                    }
                    PlayerFooter(queue, lyrics) { extra = true }
                    if (s.visualizer) AudioVisualizer(audioSession, state.playing)
                    Spacer(Modifier.height(20.dp))
                }
        }
    }
    if (extra)
        ModalBottomSheet(onDismissRequest = { extra = false }) {
            ChoiceRow(
                R.string.speed,
                state.speed.toString(),
                listOf("0.75", "1.0", "1.25", "1.5", "2.0"),
            ) {
                vm.speed(it.toFloat())
            }
            Text(
                stringResource(R.string.sleep),
                Modifier.padding(20.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                listOf(0, 15, 30, 45, 60, 90).forEach { m ->
                    TextButton({
                        vm.sleep(m)
                        extra = false
                    }) {
                        Text(
                            if (m == 0) stringResource(R.string.timer_off)
                            else stringResource(R.string.minutes, m)
                        )
                    }
                }
            }
            MenuAction(R.string.equalizer, Icons.Rounded.Equalizer) {
                val intent =
                    Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                        .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                        .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                        .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, vm.audioSession.value)
                runCatching { context.startActivity(intent) }.onFailure { eqMissing = true }
                extra = false
            }
            MenuAction(R.string.output, Icons.Rounded.Speaker) {
                runCatching {
                    context.startActivity(
                        Intent(
                            if (Build.VERSION.SDK_INT >= 29) AndroidSettings.Panel.ACTION_VOLUME
                            else AndroidSettings.ACTION_SOUND_SETTINGS
                        )
                    )
                }
                extra = false
            }
            Spacer(Modifier.height(24.dp))
        }
    if (eqMissing)
        AlertDialog(
            onDismissRequest = { eqMissing = false },
            text = { Text(stringResource(R.string.eq_unavailable)) },
            confirmButton = {
                TextButton({ eqMissing = false }) { Text(stringResource(R.string.close)) }
            },
        )
}

@Composable
fun PlayerArt(t: Track, playing: Boolean, layout: String, modifier: Modifier) {
    val s = LocalAppearance.current
    if (layout == "vinyl") {
        val angle =
            if (playing && !s.reducedMotion && !s.performance) {
                val infinite = rememberInfiniteTransition(label = "record")
                val rotation by
                    infinite.animateFloat(
                        0f,
                        360f,
                        infiniteRepeatable(tween(18000, easing = LinearEasing)),
                        label = "rotation",
                    )
                rotation
            } else 0f
        Box(modifier.padding(12.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.fillMaxSize()
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(Color(0xFF161A1B))
                    .border(2.dp, Color(0xFF465057), CircleShape)
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    for (i in 3..15) drawCircle(
                        Color.White.copy(alpha = .06f),
                        radius = size.minDimension * i / 32,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                    )
                }
                Artwork(
                    t,
                    Modifier.fillMaxSize(.46f)
                        .align(Alignment.Center)
                        .rotate(if (playing && !s.reducedMotion && !s.performance) angle else 0f),
                    true,
                )
                Box(
                    Modifier.size(12.dp)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(Color(0xFF102B30))
                )
            }
        }
    } else if (layout == "expressive")
        Box(modifier, contentAlignment = Alignment.Center) {
            Artwork(
                t,
                Modifier.fillMaxSize()
                    .padding(10.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 64.dp,
                            topEnd = 24.dp,
                            bottomStart = 24.dp,
                            bottomEnd = 64.dp,
                        )
                    ),
            )
            Surface(
                Modifier.align(Alignment.BottomEnd),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    Icons.Rounded.MusicNote,
                    null,
                    Modifier.padding(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    else Artwork(t, modifier)
}

@Composable
fun TrackHeading(t: Track, vm: PlayerViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                t.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                t.artist.ifBlank { stringResource(R.string.unknown) },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton({ vm.favorite(t.id) }) {
            Icon(
                if (t.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                stringResource(if (t.favorite) R.string.remove_favorite else R.string.add_favorite),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun SeekControls(state: PlaybackState, vm: PlayerViewModel) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val desc = stringResource(R.string.seek)
    val s = LocalAppearance.current
    val value = drag ?: state.position.toFloat().coerceAtMost(state.duration.toFloat())
    Column {
        if (s.seekStyle == "wave")
            Canvas(Modifier.fillMaxWidth().height(16.dp)) {
                val n = 50
                for (i in 0 until n) {
                    val h = (sin(i * .7) * .5 + .5).toFloat() * size.height
                    drawLine(
                        if (i.toFloat() / n <= value / state.duration.coerceAtLeast(1))
                            MaterialColor
                        else Color.Gray,
                        start =
                            androidx.compose.ui.geometry.Offset(
                                size.width * i / n,
                                size.height / 2 - h / 2,
                            ),
                        end =
                            androidx.compose.ui.geometry.Offset(
                                size.width * i / n,
                                size.height / 2 + h / 2,
                            ),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
        Slider(
            value,
            { drag = it },
            valueRange = 0f..state.duration.coerceAtLeast(1).toFloat(),
            onValueChangeFinished = {
                drag?.let { vm.seek(it.toLong()) }
                drag = null
            },
            enabled = state.duration > 0,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = desc },
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                time(value.toLong()),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                "−${time(state.duration-value.toLong())}",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

private val MaterialColor = Color(0xFF8CBB6B)

@Composable
fun Transport(state: PlaybackState, vm: PlayerViewModel) {
    val expressive =
        LocalAppearance.current.let { it.mode == "expressive" || it.layout == "expressive" }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        IconToggleButton(state.shuffle, { vm.shuffle() }) {
            Icon(Icons.Rounded.Shuffle, stringResource(R.string.shuffle))
        }
        IconButton(vm::previous, Modifier.size(56.dp)) {
            Icon(
                Icons.Rounded.SkipPrevious,
                stringResource(R.string.previous),
                Modifier.size(36.dp),
            )
        }
        FilledIconButton(
            vm::toggle,
            Modifier.size(if (expressive) 88.dp else 76.dp),
            shape = if (expressive) RoundedCornerShape(28.dp) else CircleShape,
        ) {
            Icon(
                if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                stringResource(if (state.playing) R.string.pause else R.string.play),
                Modifier.size(40.dp),
            )
        }
        IconButton(vm::next, Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipNext, stringResource(R.string.next), Modifier.size(36.dp))
        }
        val repeatLabel =
            when (state.repeat) {
                Player.REPEAT_MODE_ONE -> R.string.repeat_one
                Player.REPEAT_MODE_ALL -> R.string.repeat_all
                else -> R.string.repeat_off
            }
        IconToggleButton(state.repeat != Player.REPEAT_MODE_OFF, { vm.repeat() }) {
            Icon(
                if (state.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne
                else Icons.Rounded.Repeat,
                stringResource(repeatLabel),
            )
        }
    }
}

@Composable
fun PlayerFooter(queue: () -> Unit, lyrics: () -> Unit, more: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        IconButton(lyrics) { Icon(Icons.Rounded.Lyrics, stringResource(R.string.lyrics)) }
        IconButton(more) { Icon(Icons.Rounded.Tune, stringResource(R.string.more)) }
        IconButton(queue) { Icon(Icons.Rounded.QueueMusic, stringResource(R.string.queue)) }
    }
}
