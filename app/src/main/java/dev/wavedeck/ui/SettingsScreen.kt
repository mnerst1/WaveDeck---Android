package dev.wavedeck.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.wavedeck.*
import dev.wavedeck.R

private val labels =
    mapOf(
        "system" to R.string.system,
        "light" to R.string.light,
        "dark" to R.string.dark,
        "amoled" to R.string.amoled,
        "material" to R.string.material_design,
        "original" to R.string.material_design,
        "expressive" to R.string.expressive,
        "glass" to R.string.glass,
        "minimal" to R.string.minimal,
        "compact" to R.string.compact,
        "soft" to R.string.soft,
        "classic" to R.string.classic,
        "immersive" to R.string.immersive,
        "vinyl" to R.string.vinyl,
        "lime" to R.string.lime,
        "ocean" to R.string.ocean,
        "rose" to R.string.rose,
        "amber" to R.string.amber,
        "violet" to R.string.violet,
        "custom" to R.string.custom,
        "solid" to R.string.solid,
        "gradient" to R.string.gradient,
        "artwork" to R.string.artwork,
        "card" to R.string.card,
        "bar" to R.string.bar,
        "floating" to R.string.floating,
        "line" to R.string.line,
        "wave" to R.string.wave,
        "bottom" to R.string.bottom,
        "rail" to R.string.rail,
        "title" to R.string.title,
        "artist" to R.string.artist,
        "duration" to R.string.duration,
        "recent" to R.string.recent,
        "songs" to R.string.songs,
        "albums" to R.string.albums,
        "artists" to R.string.artists,
        "genres" to R.string.genres,
        "folders" to R.string.folders,
        "favorites" to R.string.favorites,
        "recently" to R.string.recently,
        "most" to R.string.most,
        "history" to R.string.history,
        "duplicates" to R.string.duplicates,
    )

@Composable
fun label(key: String): String =
    when (key) {
        "en" -> "English"
        "ru" -> "Русский"
        "kk" -> "Қазақша"
        else ->
            if (key == "bars") stringResource(R.string.bars)
            else labels[key]?.let { stringResource(it) } ?: key
    }

@Composable
fun ChoiceRow(title: Int, value: String, options: List<String>, set: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            stringResource(title),
            Modifier.padding(horizontal = 20.dp),
            style = MaterialTheme.typography.titleSmall,
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { key ->
                if (LocalAppearance.current.mode == "glass")
                    Surface(
                        shape = CircleShape,
                        color =
                            if (value == key) MaterialTheme.colorScheme.primary.copy(alpha = .12f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
                    ) {
                        Text(
                            label(key),
                            Modifier.selectable(
                                    value == key,
                                    role = Role.RadioButton,
                                    onClick = { set(key) },
                                )
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            color =
                                if (value == key) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (value == key) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                else FilterChip(value == key, { set(key) }, label = { Text(label(key)) })
            }
        }
    }
}

@Composable
fun SettingSlider(
    title: Int,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    set: (Float) -> Unit,
) {
    var current by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row {
            Text(stringResource(title), Modifier.weight(1f))
            Text("%.2f".format(current), style = MaterialTheme.typography.labelMedium)
        }
        val desc = stringResource(title)
        Slider(
            current,
            { current = it },
            valueRange = range,
            onValueChangeFinished = { set(current) },
            modifier = Modifier.semantics { contentDescription = desc },
        )
    }
}

@Composable
fun SettingFlag(title: Int, value: Boolean, set: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(title), Modifier.weight(1f))
        Switch(value, set)
    }
}

@Composable
fun SettingsScreen(vm: PlayerViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    var privacy by remember { mutableStateOf(false) }
    var custom by remember(s.customColor) { mutableStateOf(s.customColor) }
    val audioPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            vm.setting("visualizer", it)
        }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageTitle(stringResource(R.string.appearance))
        DesignPicker(s.mode) { vm.setting("mode", it) }
        ChoiceRow(R.string.theme, s.theme, listOf("system", "light", "dark", "amoled")) {
            vm.setting("theme", it)
        }
        if (s.mode == "material")
            ChoiceRow(
                R.string.layout,
                s.layout,
                listOf("classic", "immersive", "compact", "vinyl", "minimal", "expressive"),
            ) {
                vm.setting("layout", it)
            }
        ChoiceRow(
            R.string.accent,
            s.accent,
            listOf("lime", "ocean", "rose", "amber", "violet", "custom"),
        ) {
            vm.setting("accent", it)
        }
        if (s.accent == "custom")
            OutlinedTextField(
                custom,
                { v ->
                    custom = v.filter { it in "0123456789abcdefABCDEF" }.take(6)
                    if (custom.length == 6) vm.setting("customColor", custom)
                },
                Modifier.padding(20.dp),
                label = { Text(stringResource(R.string.custom_color)) },
                singleLine = true,
            )
        SettingFlag(R.string.dynamic, s.dynamic) { vm.setting("dynamic", it) }
        SettingSlider(R.string.radius, s.radius, 0f..64f) { vm.setting("radius", it) }
        ChoiceRow(R.string.background, s.background, listOf("solid", "gradient", "artwork")) {
            vm.setting("background", it)
        }
        SettingSlider(R.string.blur, s.blur, 0f..64f) { vm.setting("blur", it) }
        SettingSlider(R.string.opacity, s.opacity, .35f..1f) { vm.setting("opacity", it) }
        if (s.mode == "material")
            ChoiceRow(R.string.mini, s.mini, listOf("card", "bar", "floating")) {
                vm.setting("mini", it)
            }
        if (s.mode == "material")
            ChoiceRow(R.string.seek_style, s.seekStyle, listOf("line", "wave")) {
                vm.setting("seekStyle", it)
            }
        if (s.mode == "material")
            ChoiceRow(R.string.navigation, s.navigation, listOf("bottom", "rail")) {
                vm.setting("navigation", it)
            }
        SettingFlag(R.string.grid, s.grid) { vm.setting("grid", it) }
        ChoiceRow(R.string.columns, s.columns.toString(), listOf("2", "3", "4", "5")) {
            vm.action { vm.app.settings.columns(it.toInt()) }
        }
        SettingSlider(R.string.text_scale, s.textScale, .85f..1.35f) { vm.setting("textScale", it) }
        SettingSlider(R.string.density, s.density, .65f..1.8f) { vm.setting("density", it) }
        SettingFlag(R.string.reduced_motion, s.reducedMotion) { vm.setting("reducedMotion", it) }
        SettingFlag(R.string.performance, s.performance) { vm.setting("performance", it) }
        SettingFlag(R.string.visualizer, s.visualizer) {
            if (it) audioPermission.launch(Manifest.permission.RECORD_AUDIO)
            else vm.setting("visualizer", false)
        }
        Text(
            stringResource(R.string.visualizer_permission),
            Modifier.padding(horizontal = 20.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        ChoiceRow(R.string.visualizer_style, s.visualizerStyle, listOf("bars", "wave")) {
            vm.setting("visualizerStyle", it)
        }
        ChoiceRow(R.string.language, s.language, listOf("system", "en", "ru", "kk")) { lang ->
            vm.setting("language", lang)
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(if (lang == "system") "" else lang)
            )
        }
        TextButton({ privacy = true }, Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.privacy))
        }
        Text(
            stringResource(R.string.about),
            Modifier.padding(20.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        Text("WaveDeck ${BuildConfig.VERSION_NAME}", Modifier.padding(horizontal = 20.dp))
        Text(
            stringResource(R.string.limitations),
            Modifier.padding(20.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))
    }
    if (privacy)
        AlertDialog(
            onDismissRequest = { privacy = false },
            title = { Text(stringResource(R.string.privacy)) },
            text = {
                Text(
                    stringResource(R.string.privacy_body),
                    Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton({ privacy = false }) { Text(stringResource(R.string.close)) }
            },
        )
}
