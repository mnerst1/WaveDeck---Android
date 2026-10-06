package dev.wavedeck.ui

import android.os.Build
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import dev.wavedeck.R
import dev.wavedeck.data.*

val LocalAppearance = staticCompositionLocalOf { Settings() }

fun accent(s: Settings): Color =
    when (s.accent) {
        "ocean" -> Color(0xFF73CBEE)
        "rose" -> Color(0xFFF6A4BA)
        "amber" -> Color(0xFFF4C56B)
        "violet" -> Color(0xFFBEB0FF)
        "custom" ->
            runCatching { Color(android.graphics.Color.parseColor("#${s.customColor}")) }
                .getOrDefault(Color(0xFFB9F56B))
        else -> Color(0xFFB9F56B)
    }

@Composable
fun WaveTheme(s: Settings, content: @Composable () -> Unit) {
    val dark =
        when (s.theme) {
            "light" -> false
            "dark",
            "amoled" -> true
            else -> isSystemInDarkTheme()
        }
    val view = LocalView.current
    SideEffect {
        (view.context as? android.app.Activity)?.let { activity ->
            val bars = androidx.core.view.WindowCompat.getInsetsController(activity.window, view)
            bars.isAppearanceLightStatusBars = !dark
            bars.isAppearanceLightNavigationBars = !dark
        }
    }
    val a = accent(s)
    val colors =
        if (s.dynamic && Build.VERSION.SDK_INT >= 31) {
            if (dark) dynamicDarkColorScheme(LocalContext.current)
            else dynamicLightColorScheme(LocalContext.current)
        } else if (dark)
            darkColorScheme(
                primary = a,
                onPrimary = Color(0xFF152415),
                background = if (s.theme == "amoled") Color.Black else Color(0xFF10191C),
                surface = Color(0xFF192428),
                surfaceVariant = Color(0xFF29363B),
                onBackground = Color(0xFFF1F1E8),
                onSurface = Color(0xFFF1F1E8),
            )
        else
            lightColorScheme(
                primary =
                    Color(
                        android.graphics.Color.HSVToColor(
                            FloatArray(3).also { hsv ->
                                android.graphics.Color.colorToHSV(a.toArgb(), hsv)
                                hsv[2] = .43f
                                hsv[1] = hsv[1].coerceAtLeast(.45f)
                            }
                        )
                    ),
                onPrimary = Color.White,
                background = if (s.mode == "glass") Color(0xFFF9FAFC) else Color(0xFFF6F7EF),
                surface = Color.White,
                surfaceVariant = if (s.mode == "glass") Color(0xFFEBEEF2) else Color(0xFFE1E8D9),
            )
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalAppearance provides s,
        LocalDensity provides Density(density.density, density.fontScale * s.textScale),
    ) {
        val styled =
            if (s.mode == "soft")
                colors.copy(
                    surface = colors.surfaceVariant.copy(alpha = .96f),
                    surfaceVariant = a.copy(alpha = .12f),
                )
            else if (s.mode == "minimal")
                colors.copy(surface = colors.background, surfaceVariant = colors.background)
            else colors
        MaterialTheme(
            colorScheme = styled,
            typography = Typography(),
            shapes =
                Shapes(
                    medium =
                        RoundedCornerShape(
                            if (s.mode == "expressive") 32.dp
                            else if (s.mode == "minimal") 4.dp else 20.dp
                        ),
                    large = RoundedCornerShape(32.dp),
                ),
            content = content,
        )
    }
}

@Composable
fun Artwork(t: Track?, modifier: Modifier = Modifier, round: Boolean = false) {
    val s = LocalAppearance.current
    val app = LocalContext.current.applicationContext as dev.wavedeck.WaveDeckApp
    val model by
        produceState<Any?>(initialValue = null, t?.id) { value = t?.let { app.artwork.model(it) } }
    val shape = if (round) CircleShape else RoundedCornerShape(s.radius.dp)
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = .5f),
                        Color(0xFF2C4C5B),
                        Color(0xFF102C33),
                    )
                )
            )
    ) {
        Text(
            "≋",
            Modifier.align(Alignment.Center),
            color = Color.White.copy(alpha = .6f),
            fontSize = 50.sp,
            fontWeight = FontWeight.Light,
        )
        if (t != null)
            AsyncImage(
                model,
                stringResource(R.string.cover),
                Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
    }
}

@Composable
fun GlassPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val s = LocalAppearance.current
    Surface(
        modifier.border(
            1.dp,
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = .4f),
                    Color.White.copy(alpha = .04f),
                    Color.White.copy(alpha = .2f),
                )
            ),
            RoundedCornerShape(32.dp),
        ),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = s.opacity),
        tonalElevation = if (s.performance) 0.dp else 6.dp,
    ) {
        Column(
            Modifier.background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = .08f), Color.Transparent)
                    )
                )
                .padding(20.dp),
            content = content,
        )
    }
}

fun time(ms: Long): String =
    "%d:%02d".format(ms.coerceAtLeast(0) / 60000, (ms.coerceAtLeast(0) / 1000) % 60)

@Composable
fun trackCount(count: Int): String =
    androidx.compose.ui.res.pluralStringResource(R.plurals.track_count, count, count)
