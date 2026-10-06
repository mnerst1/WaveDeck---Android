@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.wavedeck.ui

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import dev.wavedeck.*
import dev.wavedeck.R
import dev.wavedeck.data.*
import kotlinx.coroutines.delay

/** A separate presentation using the same library and service-backed playback state. */
@Composable
fun DesignPicker(mode: String, select: (String) -> Unit) {
    Column(
        Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.mode), style = MaterialTheme.typography.titleSmall)
        listOf("material", "glass").forEach { key ->
            val selected = mode == key
            val shape = RoundedCornerShape(if (key == "glass") 28.dp else 16.dp)
            Surface(
                Modifier.fillMaxWidth()
                    .testTag("design-$key")
                    .selectable(selected, role = Role.RadioButton, onClick = { select(key) })
                    .border(
                        if (selected) 2.dp else 1.dp,
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        shape,
                    ),
                shape = shape,
                color =
                    if (key == "glass") MaterialTheme.colorScheme.surface
                    else MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(54.dp)
                            .clip(if (key == "glass") CircleShape else RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        accent(LocalAppearance.current).copy(alpha = .5f),
                                        MaterialTheme.colorScheme.surface,
                                    )
                                )
                            )
                    ) {
                        Icon(
                            if (key == "glass") Icons.Rounded.BlurOn else Icons.Rounded.Dashboard,
                            null,
                            Modifier.align(Alignment.Center),
                        )
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(label(key), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            stringResource(
                                if (key == "glass") R.string.glass_description
                                else R.string.material_description
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        if (selected) Icons.Rounded.CheckCircle
                        else Icons.Rounded.RadioButtonUnchecked,
                        null,
                        tint =
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

/** Frosted local-artwork underlay. No expensive full-screen backdrop readbacks. */
@Composable
fun FrostedSurface(
    track: Track?,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(32.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val s = LocalAppearance.current
    val app = LocalContext.current.applicationContext as WaveDeckApp
    val model by
        produceState<Any?>(null, track?.id) { value = track?.let { app.artwork.model(it) } }
    val surface = MaterialTheme.colorScheme.surface
    Box(
        modifier.shadow(if (s.performance) 0.dp else 12.dp, shape).clip(shape).background(surface)
    ) {
        if (model != null && !s.performance)
            AsyncImage(
                model,
                null,
                Modifier.matchParentSize()
                    .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(s.blur.dp) else Modifier)
                    .alpha(.24f),
                contentScale = ContentScale.Crop,
            )
        Box(
            Modifier.matchParentSize()
                .background(
                    surface.copy(alpha = if (s.performance) 1f else s.opacity.coerceIn(.65f, 1f))
                )
        )
        Box(
            Modifier.matchParentSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = .24f),
                            Color.Transparent,
                            accent(s).copy(alpha = .08f),
                        )
                    )
                )
                .border(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = .65f),
                            Color.White.copy(alpha = .08f),
                            Color.White.copy(alpha = .24f),
                        )
                    ),
                    shape,
                )
        )
        content()
    }
}

@Composable
fun GlassChrome(
    t: Track?,
    state: PlaybackState,
    route: String,
    open: () -> Unit,
    toggle: () -> Unit,
    next: () -> Unit,
    navigate: (String) -> Unit,
    showTabs: Boolean,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (t != null)
            FrostedSurface(
                t,
                Modifier.fillMaxWidth().testTag("glass-mini").clickable(onClick = open),
                RoundedCornerShape(26.dp),
            ) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CompositionLocalProvider(
                        LocalAppearance provides LocalAppearance.current.copy(radius = 12f)
                    ) {
                        Artwork(t, Modifier.size(46.dp))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(
                            t.title,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 14.sp,
                        )
                        Text(
                            t.artist,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                        )
                    }
                    IconButton(toggle) {
                        Icon(
                            if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            stringResource(if (state.playing) R.string.pause else R.string.play),
                        )
                    }
                    IconButton(next) { Icon(Icons.Rounded.SkipNext, stringResource(R.string.next)) }
                }
            }
        if (showTabs)
            FrostedSurface(t, Modifier.fillMaxWidth().testTag("glass-navigation"), CircleShape) {
                Row(
                    Modifier.fillMaxWidth().padding(5.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    listOf(
                            Triple("home", R.string.home, Icons.Rounded.Home),
                            Triple("library", R.string.library, Icons.Rounded.LibraryMusic),
                            Triple("search", R.string.search, Icons.Rounded.Search),
                            Triple("settings", R.string.settings, Icons.Rounded.Tune),
                        )
                        .forEach { (key, title, icon) ->
                            val active = route == key || (route == "playlists" && key == "library")
                            Column(
                                Modifier.weight(1f)
                                    .clip(CircleShape)
                                    .background(
                                        if (active)
                                            MaterialTheme.colorScheme.primary.copy(alpha = .12f)
                                        else Color.Transparent
                                    )
                                    .selectable(
                                        active,
                                        role = Role.Tab,
                                        onClick = { navigate(key) },
                                    )
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    icon,
                                    null,
                                    tint =
                                        if (active) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp),
                                )
                                Text(
                                    stringResource(title),
                                    fontSize = 10.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                )
                            }
                        }
                }
            }
    }
}

@Composable
private fun GlassHeading(title: String, action: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            Modifier.weight(1f),
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp,
        )
        FrostedSurface(null, Modifier.size(48.dp), CircleShape) {
            IconButton(action) { Icon(Icons.Rounded.Refresh, stringResource(R.string.refresh)) }
        }
    }
}

@Composable
fun GlassHome(
    vm: PlayerViewModel,
    tracks: List<Track>,
    allowed: Boolean,
    grant: () -> Unit,
    library: () -> Unit,
    player: () -> Unit,
    options: (Long) -> Unit,
) {
    val latest = remember(tracks) { tracks.sortedByDescending { it.modified }.take(12) }
    val albums = remember(latest) { latest.distinctBy { it.albumId }.take(8) }
    LazyColumn(
        Modifier.testTag("glass-home"),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { GlassHeading(stringResource(R.string.home)) { vm.app.library.refresh() } }
        item {
            Text(
                stringResource(R.string.offline),
                Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (!allowed)
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        stringResource(R.string.permission),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(grant) { Text(stringResource(R.string.grant)) }
                }
            }
        if (tracks.isEmpty())
            item {
                EmptyState(R.string.empty)
                TextButton({ vm.action { Demo.create(vm.app, vm.dao) } }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.demo))
                }
            }
        if (albums.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.your_collection),
                    Modifier.padding(horizontal = 20.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 21.sp,
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(albums, key = { it.id }) { t ->
                        Box(
                            Modifier.width(286.dp)
                                .height(312.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable {
                                    vm.play(tracks.filter { it.albumId == t.albumId })
                                    player()
                                }
                        ) {
                            CompositionLocalProvider(
                                LocalAppearance provides LocalAppearance.current.copy(radius = 18f)
                            ) {
                                Artwork(t, Modifier.fillMaxSize())
                            }
                            Box(
                                Modifier.fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = .85f),
                                            ),
                                            startY = 220f,
                                        )
                                    )
                            )
                            Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                                Text(
                                    t.album,
                                    color = Color.White,
                                    fontSize = 27.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    t.artist,
                                    color = Color.White.copy(alpha = .8f),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                )
                            }
                            Icon(
                                Icons.Rounded.PlayCircle,
                                stringResource(R.string.play),
                                Modifier.align(Alignment.TopEnd).padding(14.dp).size(32.dp),
                                tint = Color.White,
                            )
                        }
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.new_in_library),
                        Modifier.weight(1f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 21.sp,
                    )
                    IconButton(library) {
                        Icon(Icons.Rounded.ChevronRight, stringResource(R.string.library))
                    }
                }
            }
            items(latest.take(6), key = { it.id }) { t ->
                GlassTrackRow(
                    t,
                    {
                        vm.play(latest, latest.indexOf(t))
                        player()
                    },
                    { options(t.id) },
                )
            }
        }
    }
}

@Composable
fun GlassTrackRow(t: Track, play: () -> Unit, options: () -> Unit) {
    Column(Modifier.padding(start = 20.dp, end = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = play).padding(vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(
                LocalAppearance provides LocalAppearance.current.copy(radius = 7f)
            ) {
                Artwork(t, Modifier.size(46.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    t.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    t.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(options) { Icon(Icons.Rounded.MoreHoriz, stringResource(R.string.more)) }
        }
        HorizontalDivider(
            Modifier.padding(start = 58.dp),
            thickness = .5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f),
        )
    }
}

@Composable
fun GlassLibrary(
    vm: PlayerViewModel,
    tracks: List<Track>,
    options: (Long) -> Unit,
    player: () -> Unit,
    playlists: () -> Unit,
) {
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(section != null) { section = null }
    if (section != null) {
        key(section) { LibraryScreen(vm, tracks, options, player, section!!, { section = null }) }
        return
    }
    val artists = remember(tracks) { tracks.distinctBy { it.artist }.take(12) }
    val albums =
        remember(tracks) {
            tracks.sortedByDescending { it.modified }.distinctBy { it.albumId }.take(12)
        }
    LazyColumn(Modifier.testTag("glass-library"), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { GlassHeading(stringResource(R.string.library)) { vm.app.library.refresh() } }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(artists, key = { it.id }) { t ->
                    Column(
                        Modifier.width(94.dp).clickable { section = "artists" },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Artwork(t, Modifier.size(90.dp), true)
                        Text(
                            t.artist,
                            Modifier.padding(top = 7.dp),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
        listOf(
                "playlists" to Icons.Rounded.QueueMusic,
                "artists" to Icons.Rounded.Mic,
                "albums" to Icons.Rounded.Album,
                "songs" to Icons.Rounded.MusicNote,
                "genres" to Icons.Rounded.Category,
            )
            .forEach { (name, icon) ->
                item {
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { if (name == "playlists") playlists() else section = name }
                            .padding(horizontal = 20.dp)
                            .heightIn(min = 50.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            icon,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(23.dp),
                        )
                        Text(
                            if (name == "playlists") stringResource(R.string.playlists)
                            else label(name),
                            Modifier.weight(1f).padding(start = 15.dp),
                            fontSize = 17.sp,
                        )
                        Icon(
                            Icons.Rounded.ChevronRight,
                            null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    HorizontalDivider(
                        Modifier.padding(start = 58.dp, end = 20.dp),
                        thickness = .5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f),
                    )
                }
            }
        item {
            Text(
                stringResource(R.string.recently_added),
                Modifier.padding(20.dp),
                fontWeight = FontWeight.Bold,
                fontSize = 23.sp,
            )
        }
        if (albums.isEmpty()) item { EmptyState(R.string.empty) }
        items(albums.chunked(2)) { row ->
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                row.forEach { t ->
                    Column(
                        Modifier.weight(1f).clickable {
                            vm.play(tracks.filter { it.albumId == t.albumId })
                            player()
                        }
                    ) {
                        CompositionLocalProvider(
                            LocalAppearance provides LocalAppearance.current.copy(radius = 12f)
                        ) {
                            Artwork(t, Modifier.fillMaxWidth().aspectRatio(1f))
                        }
                        Text(
                            t.album,
                            Modifier.padding(top = 6.dp),
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            t.artist,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GlassSlider(
    value: Float,
    change: (Float) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    commit: () -> Unit = {},
) {
    Slider(
        value,
        change,
        modifier.semantics { contentDescription = description },
        onValueChangeFinished = commit,
        colors =
            SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = .3f),
            ),
        thumb = { Box(Modifier.size(10.dp).clip(CircleShape).background(Color.White)) },
        track = { slider ->
            Box(
                Modifier.fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = .28f))
            ) {
                Box(
                    Modifier.fillMaxWidth(slider.value.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(Color.White)
                )
            }
        },
    )
}

@Composable
fun GlassNowPlaying(
    vm: PlayerViewModel,
    t: Track?,
    state: PlaybackState,
    back: () -> Unit,
    queue: () -> Unit,
    lyrics: () -> Unit,
    options: () -> Unit,
) {
    if (t == null) {
        Column(Modifier.safeDrawingPadding()) {
            PageTitle(stringResource(R.string.now_playing), back)
            EmptyState()
        }
        return
    }
    val context = LocalContext.current
    val app = context.applicationContext as WaveDeckApp
    val model by produceState<Any?>(null, t.id) { value = app.artwork.model(t) }
    val view = LocalView.current
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    DisposableEffect(view, dark) {
        val activity = view.context as? android.app.Activity
        val bars = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        bars?.isAppearanceLightStatusBars = false
        bars?.isAppearanceLightNavigationBars = false
        onDispose {
            bars?.isAppearanceLightStatusBars = !dark
            bars?.isAppearanceLightNavigationBars = !dark
        }
    }
    val manager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember {
        manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }
    var volume by remember {
        mutableFloatStateOf(
            manager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
        )
    }
    LaunchedEffect(manager) {
        while (true) {
            volume = manager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
            delay(700)
        }
    }
    var extra by remember { mutableStateOf(false) }
    var eqMissing by remember { mutableStateOf(false) }
    val session by vm.audioSession.collectAsStateWithLifecycle()
    BoxWithConstraints(
        Modifier.fillMaxSize().testTag("glass-player").background(Color(0xFF0B1D24))
    ) {
        val wide = maxWidth > maxHeight
        val artworkSpace = (maxHeight * .39f).coerceAtLeast(120.dp)
        AsyncImage(
            model,
            stringResource(R.string.cover),
            Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier.fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = .18f),
                            Color.Transparent,
                            Color(0xFF07181E).copy(alpha = .78f),
                            Color(0xFF040C10).copy(alpha = .98f),
                        )
                    )
                )
        )
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(
                Modifier.fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = if (wide) 40.dp else 28.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().height(54.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(back) {
                        Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.close))
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "WaveDeck",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = .7f),
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(options) {
                        Icon(Icons.Rounded.MoreHoriz, stringResource(R.string.more))
                    }
                }
                Spacer(Modifier.height(if (wide) 0.dp else artworkSpace))
                Column(Modifier.widthIn(max = 620.dp).align(Alignment.CenterHorizontally)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                t.title,
                                fontSize = 25.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                t.artist,
                                fontSize = 16.sp,
                                color = Color.White.copy(alpha = .72f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton({ vm.favorite(t.id) }) {
                            Icon(
                                if (t.favorite) Icons.Rounded.Favorite
                                else Icons.Rounded.FavoriteBorder,
                                stringResource(R.string.favorites),
                                tint =
                                    if (t.favorite) accent(LocalAppearance.current)
                                    else Color.White,
                            )
                        }
                    }
                    var pendingSeek by remember(t.id) { mutableStateOf<Float?>(null) }
                    GlassSlider(
                        pendingSeek
                            ?: (state.position.toFloat() / state.duration.coerceAtLeast(1))
                                .coerceIn(0f, 1f),
                        { pendingSeek = it },
                        stringResource(R.string.seek),
                        Modifier.fillMaxWidth(),
                        {
                            pendingSeek?.let { vm.seek((it * state.duration).toLong()) }
                            pendingSeek = null
                        },
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            time(state.position),
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = .65f),
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "−" + time(state.duration - state.position),
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = .65f),
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(vm::previous, Modifier.size(56.dp)) {
                            Icon(
                                Icons.Rounded.SkipPrevious,
                                stringResource(R.string.previous),
                                Modifier.size(38.dp),
                            )
                        }
                        IconButton(vm::toggle, Modifier.size(72.dp)) {
                            Icon(
                                if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                stringResource(
                                    if (state.playing) R.string.pause else R.string.play
                                ),
                                Modifier.size(52.dp),
                            )
                        }
                        IconButton(vm::next, Modifier.size(56.dp)) {
                            Icon(
                                Icons.Rounded.SkipNext,
                                stringResource(R.string.next),
                                Modifier.size(38.dp),
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.VolumeDown,
                            null,
                            Modifier.size(18.dp),
                            tint = Color.White.copy(alpha = .6f),
                        )
                        GlassSlider(
                            volume,
                            {
                                volume = it
                                manager.setStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    (it * maxVolume).toInt(),
                                    0,
                                )
                            },
                            stringResource(R.string.volume),
                            Modifier.weight(1f).padding(horizontal = 12.dp),
                        )
                        Icon(
                            Icons.Rounded.VolumeUp,
                            null,
                            Modifier.size(18.dp),
                            tint = Color.White.copy(alpha = .6f),
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        IconButton(lyrics) {
                            Icon(Icons.Rounded.Subtitles, stringResource(R.string.lyrics))
                        }
                        IconToggleButton(state.shuffle, { vm.shuffle() }) {
                            Icon(
                                Icons.Rounded.Shuffle,
                                stringResource(R.string.shuffle),
                                tint =
                                    if (state.shuffle) accent(LocalAppearance.current)
                                    else Color.White.copy(alpha = .65f),
                            )
                        }
                        IconButton(
                            vm::repeat,
                            Modifier.semantics { stateDescription = state.repeat.toString() },
                        ) {
                            Icon(
                                if (state.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne
                                else Icons.Rounded.Repeat,
                                stringResource(
                                    when (state.repeat) {
                                        Player.REPEAT_MODE_ONE -> R.string.repeat_one
                                        Player.REPEAT_MODE_ALL -> R.string.repeat_all
                                        else -> R.string.repeat_off
                                    }
                                ),
                                tint =
                                    if (state.repeat != Player.REPEAT_MODE_OFF)
                                        accent(LocalAppearance.current)
                                    else Color.White.copy(alpha = .65f),
                            )
                        }
                        IconButton({ extra = true }) {
                            Icon(Icons.Rounded.Speaker, stringResource(R.string.output))
                        }
                        IconButton(queue) {
                            Icon(Icons.Rounded.QueueMusic, stringResource(R.string.queue))
                        }
                    }
                    if (LocalAppearance.current.visualizer) AudioVisualizer(session, state.playing)
                    Spacer(Modifier.height(16.dp))
                }
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
                fontWeight = FontWeight.Bold,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
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
            MenuAction(R.string.equalizer, Icons.Rounded.Equalizer) {
                runCatching {
                    context.startActivity(
                        Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                            .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                            .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, session)
                            .putExtra(
                                AudioEffect.EXTRA_CONTENT_TYPE,
                                AudioEffect.CONTENT_TYPE_MUSIC,
                            )
                    )
                }
                    .onFailure { eqMissing = true }
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
fun GlassRail(route: String, t: Track?, navigate: (String) -> Unit) {
    FrostedSurface(t, Modifier.padding(12.dp).width(90.dp).fillMaxHeight()) {
        Column(
            Modifier.fillMaxSize().padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("≋", fontSize = 32.sp, color = MaterialTheme.colorScheme.primary)
            listOf(
                    Triple("home", R.string.home, Icons.Rounded.Home),
                    Triple("library", R.string.library, Icons.Rounded.LibraryMusic),
                    Triple("search", R.string.search, Icons.Rounded.Search),
                    Triple("settings", R.string.settings, Icons.Rounded.Tune),
                )
                .forEach { (key, title, icon) ->
                    val active = route == key || (route == "playlists" && key == "library")
                    Column(
                        Modifier.fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                if (active) MaterialTheme.colorScheme.primary.copy(alpha = .12f)
                                else Color.Transparent
                            )
                            .selectable(active, role = Role.Tab, onClick = { navigate(key) })
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            icon,
                            null,
                            tint =
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(stringResource(title), fontSize = 10.sp, maxLines = 1)
                    }
                }
        }
    }
}
