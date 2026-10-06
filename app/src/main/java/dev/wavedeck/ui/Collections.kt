@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.wavedeck.ui

import android.media.MediaMetadataRetriever
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.wavedeck.*
import dev.wavedeck.R
import dev.wavedeck.data.*
import dev.wavedeck.model.*
import kotlinx.coroutines.*

@Composable
fun PlaylistScreen(
    vm: PlayerViewModel,
    tracks: List<Track>,
    options: (Long) -> Unit,
    player: () -> Unit,
) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    var edit by rememberSaveable { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var importReport by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val p = playlists.firstOrNull { it.id == selected }
    val list =
        entries
            .filter { it.playlistId == p?.id }
            .mapNotNull { e -> tracks.firstOrNull { it.id == e.trackId } }
    val defaultName = stringResource(R.string.import_name)
    val importer =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                vm.action {
                    val text =
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                                readLimited(it, 2_000_000)
                            } ?: error("No input")
                        }
                    val ids = M3u.resolve(text, tracks)
                    val id = vm.dao.create(Playlist(name = defaultName))
                    vm.dao.addTracks(id, ids)
                    importReport = ids.size
                    selected = id
                }
        }
    val exporter =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("audio/x-mpegurl")
        ) { uri ->
            if (uri != null)
                vm.action {
                    withContext(Dispatchers.IO) {
                        context.contentResolver
                            .openOutputStream(uri)
                            ?.bufferedWriter(Charsets.UTF_8)
                            ?.use { it.write(M3u.export(list)) } ?: error("No output")
                    }
                }
        }
    Column {
        PageTitle(
            p?.name ?: stringResource(R.string.playlists),
            if (p != null) {
                { selected = null }
            } else null,
        ) {
            if (p == null) {
                IconButton({
                    name = ""
                    edit = true
                }) {
                    Icon(Icons.Rounded.Add, stringResource(R.string.new_playlist))
                }
                IconButton({ importer.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Rounded.FileOpen, stringResource(R.string.import_action))
                }
            } else {
                IconButton({
                    name = p.name
                    edit = true
                }) {
                    Icon(Icons.Rounded.Edit, stringResource(R.string.rename))
                }
                IconButton({
                    exporter.launch("${p.name.replace(Regex("[^\\p{L}\\p{N} _-]"),"_")}.m3u8")
                }) {
                    Icon(Icons.Rounded.SaveAlt, stringResource(R.string.export_action))
                }
                IconButton({ delete = true }) {
                    Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.delete))
                }
            }
        }
        if (p == null)
            LazyColumn(contentPadding = PaddingValues(20.dp)) {
                items(playlists, key = { it.id }) { pl ->
                    ListItem(
                        headlineContent = { Text(pl.name) },
                        supportingContent = {
                            Text(trackCount(entries.count { it.playlistId == pl.id }))
                        },
                        leadingContent = { Icon(Icons.Rounded.QueueMusic, null) },
                        modifier = Modifier.clickable { selected = pl.id },
                    )
                }
                if (playlists.isEmpty())
                    item { EmptyState(R.string.new_playlist, R.string.empty_playlist) }
            }
        else {
            Row(Modifier.padding(horizontal = 20.dp)) {
                Button(
                    {
                        vm.play(list)
                        player()
                    },
                    enabled = list.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.play_all))
                }
                TextButton({ vm.enqueue(list) }, enabled = list.isNotEmpty()) {
                    Text(stringResource(R.string.add_queue))
                }
            }
            LazyColumn(contentPadding = PaddingValues(20.dp)) {
                items(list, key = { it.id }) { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            TrackRow(
                                t,
                                {
                                    vm.play(list, list.indexOf(t))
                                    player()
                                },
                                { options(t.id) },
                            )
                        }
                        IconButton({ vm.action { vm.dao.remove(p.id, t.id) } }) {
                            Icon(Icons.Rounded.RemoveCircleOutline, stringResource(R.string.remove))
                        }
                    }
                }
                if (list.isEmpty()) item { EmptyState(R.string.playlists, R.string.empty_playlist) }
            }
        }
    }
    if (edit)
        AlertDialog(
            onDismissRequest = { edit = false },
            title = {
                Text(stringResource(if (p == null) R.string.new_playlist else R.string.rename))
            },
            text = {
                OutlinedTextField(
                    name,
                    { name = it.take(100) },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    {
                        vm.action {
                            if (p == null) vm.dao.create(Playlist(name = name.trim()))
                            else vm.dao.rename(p.id, name.trim())
                        }
                        edit = false
                    },
                    enabled = name.isNotBlank(),
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton({ edit = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    if (delete && p != null)
        AlertDialog(
            onDismissRequest = { delete = false },
            text = { Text(stringResource(R.string.delete_confirm)) },
            confirmButton = {
                TextButton({
                    vm.action { vm.dao.deletePlaylist(p.id) }
                    selected = null
                    delete = false
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton({ delete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    importReport?.let { count ->
        AlertDialog(
            onDismissRequest = { importReport = null },
            text = { Text(stringResource(R.string.import_report, count)) },
            confirmButton = {
                TextButton({ importReport = null }) { Text(stringResource(R.string.close)) }
            },
        )
    }
}

@Composable
fun AddPlaylistDialog(vm: PlayerViewModel, ids: List<Long>, close: () -> Unit) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(stringResource(R.string.add_playlist)) },
        text = {
            Column {
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(playlists) { p ->
                        TextButton(
                            {
                                vm.action { vm.dao.addTracks(p.id, ids) }
                                close()
                            },
                            Modifier.fillMaxWidth(),
                        ) {
                            Text(p.name)
                        }
                    }
                }
                OutlinedTextField(
                    name,
                    { name = it.take(100) },
                    label = { Text(stringResource(R.string.new_playlist)) },
                )
            }
        },
        confirmButton = {
            TextButton(
                {
                    vm.action {
                        val id = vm.dao.create(Playlist(name = name.trim()))
                        vm.dao.addTracks(id, ids)
                    }
                    close()
                },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(close) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun TrackMenu(vm: PlayerViewModel, t: Track, close: () -> Unit) {
    var add by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = close) {
        Text(
            t.title,
            Modifier.padding(20.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        MenuAction(R.string.play, Icons.Rounded.PlayArrow) {
            vm.play(listOf(t))
            close()
        }
        MenuAction(R.string.play_next, Icons.Rounded.PlaylistPlay) {
            vm.enqueue(listOf(t), true)
            close()
        }
        MenuAction(R.string.add_queue, Icons.Rounded.AddToQueue) {
            vm.enqueue(listOf(t))
            close()
        }
        MenuAction(
            if (t.favorite) R.string.remove_favorite else R.string.add_favorite,
            if (t.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
        ) {
            vm.favorite(t.id)
            close()
        }
        MenuAction(R.string.add_playlist, Icons.Rounded.PlaylistAdd) { add = true }
        MenuAction(R.string.details, Icons.Rounded.Info) { detail = true }
        Spacer(Modifier.height(24.dp))
    }
    if (add)
        AddPlaylistDialog(vm, listOf(t.id)) {
            add = false
            close()
        }
    if (detail) DetailsDialog(t) { detail = false }
}

@Composable
fun MenuAction(
    label: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    action: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(label)) },
        leadingContent = { Icon(icon, null) },
        modifier = Modifier.clickable(onClick = action),
    )
}

@Composable
fun DetailsDialog(t: Track, close: () -> Unit) {
    val context = LocalContext.current
    var technical by remember { mutableStateOf("") }
    LaunchedEffect(t.uri) {
        technical =
            withContext(Dispatchers.IO) {
                runCatching {
                        val m = MediaMetadataRetriever()
                        try {
                            m.setDataSource(context, android.net.Uri.parse(t.uri))
                            listOfNotNull(
                                    m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                                        ?.toLongOrNull()
                                        ?.let { "${it/1000} kb/s" },
                                    if (android.os.Build.VERSION.SDK_INT >= 31)
                                        m.extractMetadata(
                                                MediaMetadataRetriever.METADATA_KEY_SAMPLERATE
                                            )
                                            ?.let { "$it Hz" }
                                    else null,
                                )
                                .joinToString(" · ")
                        } finally {
                            m.release()
                        }
                    }
                    .getOrDefault("")
            }
    }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(stringResource(R.string.details)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                listOf(
                        R.string.title to t.title,
                        R.string.artist to t.artist,
                        R.string.album to t.album,
                        R.string.folders to t.folder,
                        R.string.genres to t.genre,
                        R.string.duration to time(t.duration),
                        R.string.format to "${t.mime}\n$technical",
                        R.string.size to
                            android.text.format.Formatter.formatFileSize(context, t.size),
                        R.string.plays to t.plays.toString(),
                    )
                    .forEach { (key, value) ->
                        Text(
                            stringResource(key),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(value.ifBlank { stringResource(R.string.unknown) })
                    }
            }
        },
        confirmButton = { TextButton(close) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
fun QueueScreen(vm: PlayerViewModel, tracks: List<Track>, state: PlaybackState, back: () -> Unit) {
    Column {
        PageTitle(stringResource(R.string.queue), back)
        LazyColumn(contentPadding = PaddingValues(20.dp)) {
            itemsIndexed(state.queue, key = { i, _ -> i }) { i, id ->
                val t = tracks.firstOrNull { it.id.toString() == id }
                if (t != null)
                    Column {
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.jump(i) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Artwork(t, Modifier.size(48.dp))
                            Column(Modifier.weight(1f).padding(12.dp)) {
                                Text(
                                    t.title,
                                    fontWeight =
                                        if (i == state.index) FontWeight.Bold
                                        else FontWeight.Normal,
                                )
                                Text(t.artist, style = MaterialTheme.typography.bodySmall)
                            }
                            if (i == state.index)
                                Icon(
                                    Icons.Rounded.GraphicEq,
                                    stringResource(R.string.now_playing),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            IconButton({ vm.remove(i) }) {
                                Icon(Icons.Rounded.Close, stringResource(R.string.remove))
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconButton({ vm.move(i, i - 1) }, enabled = i > 0) {
                                Icon(
                                    Icons.Rounded.KeyboardArrowUp,
                                    stringResource(R.string.move_up),
                                )
                            }
                            IconButton({ vm.move(i, i + 1) }, enabled = i < state.queue.lastIndex) {
                                Icon(
                                    Icons.Rounded.KeyboardArrowDown,
                                    stringResource(R.string.move_down),
                                )
                            }
                        }
                    }
            }
            if (state.queue.isEmpty()) item { EmptyState(R.string.queue) }
        }
    }
}

@Composable
fun LyricsScreen(vm: PlayerViewModel, t: Track?, state: PlaybackState, back: () -> Unit) {
    val context = LocalContext.current
    var edit by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    var offset by rememberSaveable { mutableStateOf("0") }
    val importer =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null && t != null)
                vm.action {
                    val data =
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                                readLimited(it, 500000)
                            } ?: error("No file")
                        }
                    vm.dao.lyrics(t.id, data, t.lyricsOffset)
                }
        }
    val lines = remember(t?.lyrics) { Lrc.parse(t?.lyrics ?: "") }
    val active = Lrc.active(lines, state.position, t?.lyricsOffset ?: 0)
    val listState = rememberLazyListState()
    LaunchedEffect(active) {
        if (active >= 0) {
            if (vm.settings.value.reducedMotion) listState.scrollToItem(active)
            else listState.animateScrollToItem(active)
        }
    }
    Column {
        PageTitle(stringResource(R.string.lyrics), back) {
            IconButton({ importer.launch(arrayOf("*/*")) }, enabled = t != null) {
                Icon(Icons.Rounded.FileOpen, stringResource(R.string.import_action))
            }
            IconButton(
                {
                    text = t?.lyrics ?: ""
                    offset = t?.lyricsOffset?.toString() ?: "0"
                    edit = true
                },
                enabled = t != null,
            ) {
                Icon(Icons.Rounded.Edit, stringResource(R.string.edit))
            }
        }
        if (t == null) EmptyState(R.string.no_track)
        else if (lines.isEmpty())
            Text(
                t.lyrics.ifBlank { stringResource(R.string.no_lyrics) },
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(28.dp),
                style = MaterialTheme.typography.headlineSmall,
            )
        else
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(28.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                itemsIndexed(lines) { i, line ->
                    Text(
                        line.text,
                        Modifier.fillMaxWidth().clickable {
                            vm.seek((line.at + t.lyricsOffset).coerceAtLeast(0))
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color =
                            if (i == active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                    )
                }
            }
    }
    if (edit && t != null)
        AlertDialog(
            onDismissRequest = { edit = false },
            title = { Text(stringResource(R.string.lyrics)) },
            text = {
                Column {
                    OutlinedTextField(
                        text,
                        { text = it },
                        Modifier.heightIn(max = 300.dp),
                        label = { Text(stringResource(R.string.lyrics)) },
                    )
                    OutlinedTextField(
                        offset,
                        { offset = it },
                        label = { Text(stringResource(R.string.offset)) },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton({
                    vm.action {
                        vm.dao.lyrics(
                            t.id,
                            text,
                            offset.toLongOrNull()?.coerceIn(-60000, 60000) ?: 0,
                        )
                    }
                    edit = false
                }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton({ edit = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
}
