package dev.wavedeck.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*

val Context.preferenceStore by preferencesDataStore("appearance")

data class Settings(
    val theme: String = "system",
    val mode: String = "material",
    val layout: String = "classic",
    val accent: String = "lime",
    val customColor: String = "B9F56B",
    val dynamic: Boolean = false,
    val radius: Float = 24f,
    val blur: Float = 32f,
    val opacity: Float = .78f,
    val textScale: Float = 1f,
    val density: Float = 1f,
    val reducedMotion: Boolean = false,
    val performance: Boolean = false,
    val grid: Boolean = false,
    val columns: Int = 2,
    val background: String = "gradient",
    val mini: String = "card",
    val seekStyle: String = "line",
    val navigation: String = "bottom",
    val language: String = "system",
    val onboarded: Boolean = false,
    val visualizer: Boolean = false,
    val visualizerStyle: String = "bars",
)

class SettingsStore(private val context: Context) {
    val flow =
        context.preferenceStore.data
            .catch { emit(emptyPreferences()) }
            .map { p ->
                fun s(k: String, d: String) = p[stringPreferencesKey(k)] ?: d
                fun f(k: String, d: Float) = p[floatPreferencesKey(k)] ?: d
                fun b(k: String) = p[booleanPreferencesKey(k)] ?: false
                Settings(
                    s("theme", "system"),
                    if (s("mode", "material") == "glass") "glass" else "material",
                    s("layout", "classic").let { if (it == "glass") "classic" else it },
                    s("accent", "lime"),
                    s("customColor", "B9F56B"),
                    b("dynamic"),
                    f("radius", 24f),
                    f("blur", 32f),
                    f("opacity", .78f),
                    f("textScale", 1f),
                    f("density", 1f),
                    b("reducedMotion"),
                    b("performance"),
                    b("grid"),
                    (p[intPreferencesKey("columns")] ?: 2).coerceIn(2, 5),
                    s("background", "gradient"),
                    s("mini", "card"),
                    s("seekStyle", "line"),
                    s("navigation", "bottom"),
                    s("language", "system"),
                    b("onboarded"),
                    b("visualizer"),
                    s("visualizerStyle", "bars"),
                )
            }

    suspend fun string(key: String, value: String) {
        context.preferenceStore.edit { it[stringPreferencesKey(key)] = value }
    }

    suspend fun number(key: String, value: Float) {
        context.preferenceStore.edit { it[floatPreferencesKey(key)] = value }
    }

    suspend fun flag(key: String, value: Boolean) {
        context.preferenceStore.edit { it[booleanPreferencesKey(key)] = value }
    }

    suspend fun columns(value: Int) {
        context.preferenceStore.edit { it[intPreferencesKey("columns")] = value.coerceIn(2, 5) }
    }
}
