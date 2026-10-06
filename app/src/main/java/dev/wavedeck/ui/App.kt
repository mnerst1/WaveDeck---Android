@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.wavedeck.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import dev.wavedeck.*
import dev.wavedeck.R
import dev.wavedeck.data.*
import dev.wavedeck.model.*
import kotlinx.coroutines.*

val permissionName
    get() =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
fun WaveDeck(vm: PlayerViewModel = viewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    WaveTheme(s) {
        val context = LocalContext.current
        var allowed by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(context, permissionName) ==
                    PackageManager.PERMISSION_GRANTED
            )
        }
        val grant =
            rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
                allowed = ok
                if (ok) vm.app.library.refresh()
            }
        LaunchedEffect(allowed) { if (allowed) vm.app.library.refresh() }
        val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
        DisposableEffect(lifecycle) {
            val observer =
                androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        val now =
                            ContextCompat.checkSelfPermission(context, permissionName) ==
                                PackageManager.PERMISSION_GRANTED
                        if (now) vm.app.library.refresh()
                        allowed = now
                    }
                }
            lifecycle.lifecycle.addObserver(observer)
            onDispose { lifecycle.lifecycle.removeObserver(observer) }
        }
        Surface(Modifier.fillMaxSize()) {
            if (!s.onboarded) {
                Column(
                    Modifier.safeDrawingPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    Text("≋  WaveDeck", fontWeight = FontWeight.Bold, fontSize = 26.sp)
                    Spacer(Modifier.height(36.dp))
                    Text(
                        stringResource(R.string.welcome),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.onboarding),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    ChoiceRow(R.string.language, s.language, listOf("system", "en", "ru", "kk")) {
                        lang ->
                        vm.setting("language", lang)
                        AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.forLanguageTags(if (lang == "system") "" else lang)
                        )
                    }
                    DesignPicker(s.mode) {
                        vm.setting("mode", it)
                    }
                    Button({ grant.launch(permissionName) }, Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.grant))
                    }
                    OutlinedButton(
                        {
                            vm.action {
                                Demo.create(context, vm.dao)
                                vm.app.settings.flag("onboarded", true)
                            }
                        },
                        Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.demo))
                    }
                    TextButton({ vm.setting("onboarded", true) }, Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.continue_action))
                    }
                }
            } else MainShell(vm, allowed, { grant.launch(permissionName) })
        }
    }
}

@Composable
fun MainShell(vm: PlayerViewModel, allowed: Boolean, grant: () -> Unit) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val scanning by vm.app.library.scanning.collectAsStateWithLifecycle()
    val scanError by vm.app.library.error.collectAsStateWithLifecycle()
    val opError by vm.operationError.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val tabs =
        listOf(
            Triple("home", R.string.home, Icons.Rounded.Home),
            Triple("library", R.string.library, Icons.Rounded.LibraryMusic),
            Triple("search", R.string.search, Icons.Rounded.Search),
            Triple("playlists", R.string.playlists, Icons.Rounded.QueueMusic),
            Triple("settings", R.string.settings, Icons.Rounded.Tune),
        )
    val current = tracks.firstOrNull { it.id.toString() == state.queue.getOrNull(state.index) }
    var options by rememberSaveable { mutableStateOf<Long?>(null) }
    val context = LocalContext.current
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .then(
                if (s.mode == "glass" && route == "player") Modifier
                else Modifier.safeDrawingPadding()
            )
    ) {
        val rail = maxWidth >= 840.dp || (s.navigation == "rail" && s.mode != "glass")
        Row {
            if (rail && route in tabs.map { it.first })
                if (s.mode == "glass")
                    GlassRail(route, current) { r ->
                        nav.navigate(r) {
                            launchSingleTop = true
                            popUpTo("home") { saveState = true }
                            restoreState = true
                        }
                    }
                else
                    NavigationRail {
                        tabs.forEach { (r, label, icon) ->
                            NavigationRailItem(
                                route == r,
                                {
                                    nav.navigate(r) {
                                        launchSingleTop = true
                                        popUpTo("home") { saveState = true }
                                        restoreState = true
                                    }
                                },
                                { Icon(icon, null) },
                                label = { Text(stringResource(label)) },
                            )
                        }
                    }
            Scaffold(
                Modifier.weight(1f),
                contentWindowInsets =
                    if (s.mode == "glass" && route == "player") WindowInsets(0, 0, 0, 0)
                    else ScaffoldDefaults.contentWindowInsets,
                bottomBar = {
                    if (route in tabs.map { it.first })
                        Column {
                            if (s.mode == "glass") {
                                GlassChrome(
                                    current,
                                    state,
                                    route,
                                    { nav.navigate("player") },
                                    vm::toggle,
                                    vm::next,
                                    { r ->
                                        nav.navigate(r) {
                                            launchSingleTop = true
                                            popUpTo("home") { saveState = true }
                                            restoreState = true
                                        }
                                    },
                                    !rail,
                                )
                            } else if (current != null)
                                MiniPlayer(
                                    current,
                                    state,
                                    s,
                                    { nav.navigate("player") },
                                    vm::toggle,
                                    vm::next,
                                )
                            if (!rail && s.mode != "glass")
                                NavigationBar(Modifier.testTag("material-navigation")) {
                                    tabs.forEach { (r, label, icon) ->
                                        NavigationBarItem(
                                            route == r,
                                            {
                                                nav.navigate(r) {
                                                    launchSingleTop = true
                                                    popUpTo("home") { saveState = true }
                                                    restoreState = true
                                                }
                                            },
                                            { Icon(icon, null) },
                                            label = { Text(stringResource(label), maxLines = 1) },
                                        )
                                    }
                                }
                        }
                },
            ) { padding ->
                Column(Modifier.padding(padding).fillMaxSize()) {
                    if (state.error) ErrorBanner(R.string.play_error, vm::toggle)
                    if (opError)
                        ErrorBanner(R.string.operation_error) { vm.operationError.value = false }
                    if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (scanError)
                        ErrorBanner(R.string.scan_error) {
                            if (allowed) vm.app.library.refresh() else grant()
                        }
                    NavHost(nav, "home", Modifier.weight(1f)) {
                        composable("home") {
                            if (s.mode == "glass")
                                GlassHome(
                                    vm,
                                    tracks,
                                    allowed,
                                    grant,
                                    { nav.navigate("library") },
                                    { nav.navigate("player") },
                                    { options = it },
                                )
                            else
                                HomeScreen(
                                    vm,
                                    tracks,
                                    allowed,
                                    grant,
                                    { nav.navigate("library") },
                                    { nav.navigate("player") },
                                    { options = it },
                                )
                        }
                        composable("library") {
                            if (s.mode == "glass")
                                GlassLibrary(
                                    vm,
                                    tracks,
                                    { options = it },
                                    { nav.navigate("player") },
                                    { nav.navigate("playlists") },
                                )
                            else
                                LibraryScreen(
                                    vm,
                                    tracks,
                                    { options = it },
                                    { nav.navigate("player") },
                                )
                        }
                        composable("search") {
                            SearchScreen(vm, tracks, { options = it }, { nav.navigate("player") })
                        }
                        composable("playlists") {
                            PlaylistScreen(vm, tracks, { options = it }, { nav.navigate("player") })
                        }
                        composable("settings") { SettingsScreen(vm) }
                        composable("player") {
                            if (s.mode == "glass")
                                GlassNowPlaying(
                                    vm,
                                    current,
                                    state,
                                    { nav.popBackStack() },
                                    { nav.navigate("queue") },
                                    { nav.navigate("lyrics") },
                                    { options = current?.id },
                                )
                            else
                                NowPlaying(
                                    vm,
                                    current,
                                    state,
                                    { nav.popBackStack() },
                                    { nav.navigate("queue") },
                                    { nav.navigate("lyrics") },
                                    { options = current?.id },
                                )
                        }
                        composable("queue") {
                            QueueScreen(vm, tracks, state) { nav.popBackStack() }
                        }
                        composable("lyrics") {
                            LyricsScreen(vm, current, state) { nav.popBackStack() }
                        }
                    }
                }
            }
        }
    }
    options?.let { id ->
        tracks.firstOrNull { it.id == id }?.let { t -> TrackMenu(vm, t) { options = null } }
    }
}

@Composable
fun ErrorBanner(text: Int, action: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(text), Modifier.weight(1f))
            TextButton(action) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable
fun PageTitle(title: String, back: (() -> Unit)? = null, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (back != null)
            IconButton(back) { Icon(Icons.Rounded.ArrowBack, stringResource(R.string.back)) }
        Text(
            title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        action?.invoke()
    }
}

@Composable
fun MiniPlayer(
    t: Track,
    state: PlaybackState,
    s: Settings,
    open: () -> Unit,
    toggle: () -> Unit,
    next: () -> Unit,
) {
    val modifier =
        Modifier.fillMaxWidth()
            .padding(horizontal = if (s.mini == "bar") 0.dp else 12.dp, vertical = 6.dp)
    Surface(
        modifier.clickable(onClick = open),
        shape =
            RoundedCornerShape(
                if (s.mini == "floating") 36.dp else if (s.mini == "bar") 0.dp else 20.dp
            ),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = if (s.mini == "floating" && !s.performance) 8.dp else 0.dp,
    ) {
        Column {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(t, Modifier.size(48.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        t.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(t.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(toggle) {
                    Icon(
                        if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        stringResource(if (state.playing) R.string.pause else R.string.play),
                    )
                }
                IconButton(next) { Icon(Icons.Rounded.SkipNext, stringResource(R.string.next)) }
            }
            LinearProgressIndicator(
                progress = {
                    if (state.duration > 0) state.position.toFloat() / state.duration else 0f
                },
                Modifier.fillMaxWidth().height(2.dp),
            )
        }
    }
}

@Composable
fun HomeScreen(
    vm: PlayerViewModel,
    tracks: List<Track>,
    allowed: Boolean,
    grant: () -> Unit,
    library: () -> Unit,
    player: () -> Unit,
    options: (Long) -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "≋  WaveDeck",
                    Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                )
                IconButton({ vm.app.library.refresh() }) {
                    Icon(Icons.Rounded.Refresh, stringResource(R.string.refresh))
                }
            }
        }
        item {
            Text(
                stringResource(R.string.offline),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.listen),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
        }
        if (!allowed)
            item {
                GlassPanel {
                    Text(stringResource(R.string.permission))
                    Row {
                        TextButton(grant) { Text(stringResource(R.string.grant)) }
                        IconButton({
                            context.startActivity(
                                Intent(
                                    AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}"),
                                )
                            )
                        }) {
                            Icon(Icons.Rounded.Settings, stringResource(R.string.open_settings))
                        }
                    }
                }
            }
        if (tracks.isEmpty())
            item {
                EmptyState(R.string.empty, R.string.empty_body)
                Button(library) { Text(stringResource(R.string.library)) }
                OutlinedButton({ vm.action { Demo.create(context, vm.dao) } }) {
                    Text(stringResource(R.string.demo))
                }
            }
        else {
            item {
                val featured = tracks.maxByOrNull { it.lastPlayed }!!
                GlassPanel(
                    Modifier.clickable {
                        vm.play(tracks, tracks.indexOf(featured))
                        player()
                    }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Artwork(featured, Modifier.size(112.dp))
                        Column(Modifier.padding(start = 20.dp).weight(1f)) {
                            Text(
                                stringResource(R.string.play_all),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                featured.title,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 2,
                            )
                            Text(trackCount(tracks.size))
                            Icon(Icons.Rounded.ArrowForward, null)
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button({
                        vm.play(tracks)
                        player()
                    }) {
                        Text(stringResource(R.string.play_all))
                    }
                    OutlinedButton({
                        vm.play(tracks)
                        if (!vm.state.value.shuffle) vm.shuffle()
                        player()
                    }) {
                        Text(stringResource(R.string.shuffle_all))
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.recently),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(
                tracks.filter { it.lastPlayed > 0 }.sortedByDescending { it.lastPlayed }.take(8),
                key = { it.id },
            ) { t ->
                TrackRow(
                    t,
                    {
                        vm.play(listOf(t))
                        player()
                    },
                    { options(t.id) },
                )
            }
            item {
                Text(
                    stringResource(R.string.albums),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(tracks.distinctBy { it.albumId }.take(20)) { t ->
                        Column(
                            Modifier.width(148.dp).clickable {
                                vm.play(tracks.filter { it.albumId == t.albumId })
                                player()
                            }
                        ) {
                            Artwork(t, Modifier.size(148.dp))
                            Text(
                                t.album,
                                Modifier.padding(top = 8.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(t.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyState(title: Int = R.string.no_results, body: Int? = null) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.LibraryMusic,
            null,
            Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
        if (body != null) Text(stringResource(body), Modifier.padding(top = 12.dp))
    }
}

@Composable
fun TrackRow(
    t: Track,
    play: () -> Unit,
    options: () -> Unit,
    selected: Boolean = false,
    select: (() -> Unit)? = null,
) {
    val s = LocalAppearance.current
    if (s.mode == "glass" && select == null) {
        GlassTrackRow(t, play, options)
        return
    }
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = play, onLongClick = select ?: options)
            .padding(vertical = (6 * s.density).dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (select != null) Checkbox(selected, { select() })
        Artwork(t, Modifier.size(if (s.mode == "compact") 42.dp else 56.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                t.title,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                t.artist.ifBlank { stringResource(R.string.unknown) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (t.favorite)
            Icon(
                Icons.Rounded.Favorite,
                null,
                Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        IconButton(options) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more)) }
    }
}

@Composable
fun LibraryScreen(
    vm: PlayerViewModel,
    tracks: List<Track>,
    options: (Long) -> Unit,
    player: () -> Unit,
    initialSection: String = "songs",
    overview: (() -> Unit)? = null,
) {
    var section by rememberSaveable { mutableStateOf(initialSection) }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf("title") }
    var multi by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(listOf<Long>()) }
    var add by remember { mutableStateOf(false) }
    val s = LocalAppearance.current
    val history by vm.history.collectAsStateWithLifecycle()
    val sections =
        listOf(
            "songs",
            "albums",
            "artists",
            "genres",
            "folders",
            "favorites",
            "recently",
            "most",
            "history",
            "duplicates",
        )
    val base =
        remember(tracks, section, group, history) {
            when (section) {
                "favorites" -> tracks.filter { it.favorite }
                "recently",
                "most" -> tracks.filter { it.plays > 0 }
                "history" ->
                    history
                        .mapNotNull { h -> tracks.firstOrNull { it.id == h.trackId } }
                        .distinctBy { it.id }
                "duplicates" -> LibraryRules.duplicates(tracks)
                else ->
                    if (group == null) tracks
                    else
                        tracks.filter {
                            when (section) {
                                "albums" -> it.albumId.toString() == group
                                "artists" -> it.artist == group
                                "genres" -> it.genre == group
                                "folders" -> it.folder == group
                                else -> true
                            }
                        }
            }
        }
    var sorted by remember { mutableStateOf<List<Track>>(emptyList()) }
    LaunchedEffect(base, sort, section) {
        sorted =
            withContext(Dispatchers.Default) {
                when (section) {
                    "recently" -> base.sortedByDescending { it.lastPlayed }
                    "most" -> base.sortedByDescending { it.plays }
                    "history" -> base
                    else -> LibraryRules.filter(base, "", sort)
                }
            }
    }
    Column {
        val groupTitle = if (section == "albums") base.firstOrNull()?.album else group
        PageTitle(
            groupTitle?.ifBlank { stringResource(R.string.unknown) }
                ?: stringResource(R.string.library),
            if (group != null) {
                { group = null }
            } else overview,
        ) {
            IconButton({
                multi = !multi
                selected = emptyList()
            }) {
                Icon(Icons.Rounded.Checklist, stringResource(R.string.select))
            }
            IconButton({ vm.app.library.refresh() }) {
                Icon(Icons.Rounded.Refresh, stringResource(R.string.refresh))
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(sections) { k ->
                FilterChip(
                    section == k,
                    {
                        section = k
                        group = null
                        selected = emptyList()
                    },
                    label = { Text(label(k)) },
                )
            }
        }
        ChoiceRow(R.string.sort, sort, listOf("title", "artist", "duration", "recent")) {
            sort = it
        }
        if (multi)
            Row(
                Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.selected, selected.size), Modifier.weight(1f))
                IconButton({ vm.enqueue(tracks.filter { it.id in selected }) }) {
                    Icon(Icons.Rounded.AddToQueue, stringResource(R.string.add_queue))
                }
                IconButton({ add = true }) {
                    Icon(Icons.Rounded.PlaylistAdd, stringResource(R.string.add_playlist))
                }
            }
        val grouped = group == null && section in listOf("albums", "artists", "genres", "folders")
        if (grouped) {
            val groups =
                remember(tracks, section) {
                    tracks
                        .groupBy {
                            when (section) {
                                "albums" -> it.albumId.toString()
                                "artists" -> it.artist
                                "genres" -> it.genre
                                else -> it.folder
                            }
                        }
                        .toList()
                        .sortedBy {
                            if (section == "albums") it.second.first().album.lowercase()
                            else it.first.lowercase()
                        }
                }
            if (section == "albums" && s.grid)
                LazyVerticalGrid(
                    GridCells.Fixed(s.columns),
                    contentPadding = PaddingValues(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(groups) { (name, list) ->
                        Column(Modifier.clickable { group = name }) {
                            Artwork(list.first(), Modifier.fillMaxWidth().aspectRatio(1f))
                            Text(
                                list.first().album.ifBlank { stringResource(R.string.unknown) },
                                maxLines = 2,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(trackCount(list.size), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            else
                LazyColumn(contentPadding = PaddingValues(20.dp)) {
                    items(groups) { (name, list) ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    (if (section == "albums") list.first().album else name)
                                        .ifBlank { stringResource(R.string.unknown) }
                                )
                            },
                            supportingContent = { Text(trackCount(list.size)) },
                            leadingContent = {
                                Artwork(list.first(), Modifier.size(60.dp), section == "artists")
                            },
                            trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
                            modifier = Modifier.clickable { group = name },
                        )
                    }
                }
        } else {
            if (group != null)
                Row(Modifier.padding(horizontal = 20.dp)) {
                    Button({
                        vm.play(sorted)
                        player()
                    }) {
                        Text(stringResource(R.string.play_all))
                    }
                }
            if (section == "history")
                LazyColumn(contentPadding = PaddingValues(20.dp)) {
                    items(history, key = { it.id }) { h ->
                        tracks
                            .firstOrNull { it.id == h.trackId }
                            ?.let { t ->
                                Column {
                                    Text(
                                        java.text.DateFormat.getDateTimeInstance(
                                                java.text.DateFormat.SHORT,
                                                java.text.DateFormat.SHORT,
                                            )
                                            .format(java.util.Date(h.at)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    TrackRow(
                                        t,
                                        {
                                            vm.play(listOf(t))
                                            player()
                                        },
                                        { options(t.id) },
                                    )
                                }
                            }
                    }
                    if (history.isEmpty()) item { EmptyState() }
                }
            else if (sorted.isEmpty()) EmptyState()
            else
                LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp)) {
                    items(sorted, key = { it.id }) { t ->
                        TrackRow(
                            t,
                            {
                                if (multi)
                                    selected =
                                        if (t.id in selected) selected - t.id else selected + t.id
                                else {
                                    vm.play(sorted, sorted.indexOf(t))
                                    player()
                                }
                            },
                            { options(t.id) },
                            t.id in selected,
                            if (multi) {
                                {
                                    selected =
                                        if (t.id in selected) selected - t.id else selected + t.id
                                }
                            } else null,
                        )
                    }
                }
        }
    }
    if (add)
        AddPlaylistDialog(vm, selected) {
            add = false
            selected = emptyList()
        }
}

@Composable
fun SearchScreen(
    vm: PlayerViewModel,
    tracks: List<Track>,
    options: (Long) -> Unit,
    player: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<List<Track>>(emptyList()) }
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    LaunchedEffect(query, tracks) {
        delay(150)
        result = withContext(Dispatchers.Default) { LibraryRules.filter(tracks, query, "title") }
    }
    Column {
        PageTitle(stringResource(R.string.search))
        if (LocalAppearance.current.mode == "glass")
            TextField(
                query,
                { query = it },
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor =
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f),
                        unfocusedContainerColor =
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f),
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
            )
        else
            OutlinedTextField(
                query,
                { query = it },
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
            )
        LazyColumn(contentPadding = PaddingValues(20.dp)) {
            items(playlists.filter { query.isNotBlank() && it.name.contains(query, true) }) { p ->
                ListItem(
                    headlineContent = { Text(p.name) },
                    leadingContent = { Icon(Icons.Rounded.QueueMusic, null) },
                    modifier =
                        Modifier.clickable {
                            val ids = entries.filter { it.playlistId == p.id }.map { it.trackId }
                            vm.play(ids.mapNotNull { id -> tracks.firstOrNull { it.id == id } })
                            player()
                        },
                )
            }
            items(result, key = { it.id }) { t ->
                TrackRow(
                    t,
                    {
                        vm.play(result, result.indexOf(t))
                        player()
                    },
                    { options(t.id) },
                )
            }
            if (result.isEmpty()) item { EmptyState() }
        }
    }
}
