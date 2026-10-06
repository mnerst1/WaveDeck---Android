package dev.wavedeck.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.wavedeck.R
import kotlin.math.abs

@Composable
fun AudioVisualizer(sessionId: Int, playing: Boolean) {
    val context = LocalContext.current
    val s = LocalAppearance.current
    var waveform by remember { mutableStateOf(ByteArray(0)) }
    var failed by remember { mutableStateOf(false) }
    val permitted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    DisposableEffect(sessionId, playing, permitted, s.performance) {
        var effect: Visualizer? = null
        failed = false
        if (sessionId > 0 && playing && permitted && !s.performance) {
            try {
                effect =
                    Visualizer(sessionId).apply {
                        captureSize = Visualizer.getCaptureSizeRange()[0]
                        setDataCaptureListener(
                            object : Visualizer.OnDataCaptureListener {
                                override fun onWaveFormDataCapture(
                                    v: Visualizer?,
                                    data: ByteArray?,
                                    rate: Int,
                                ) {
                                    waveform = data?.copyOf() ?: ByteArray(0)
                                }

                                override fun onFftDataCapture(
                                    v: Visualizer?,
                                    data: ByteArray?,
                                    rate: Int,
                                ) {}
                            },
                            minOf(Visualizer.getMaxCaptureRate(), 10000),
                            true,
                            false,
                        )
                        enabled = true
                    }
            } catch (_: Exception) {
                failed = true
                runCatching { effect?.release() }
                effect = null
            }
        }
        onDispose {
            runCatching { effect?.release() }
            waveform = ByteArray(0)
        }
    }
    if (!permitted || failed)
        Text(
            stringResource(R.string.visualizer_unavailable),
            style = MaterialTheme.typography.bodySmall,
        )
    else if (!s.performance) {
        val color = MaterialTheme.colorScheme.primary
        Canvas(Modifier.fillMaxWidth().height(48.dp)) {
            val data = waveform
            if (data.isNotEmpty()) {
                if (s.visualizerStyle == "wave") {
                    val path = Path()
                    data.forEachIndexed { i, b ->
                        val x = i * size.width / data.size
                        val y = size.height / 2 + ((b.toInt() and 255) - 128) * size.height / 256
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path,
                        color,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()),
                    )
                } else
                    for (i in 0 until 32) {
                        val sample =
                            data[(i * data.size / 32).coerceAtMost(data.lastIndex)].toInt() and 255
                        val h = abs(sample - 128) * size.height / 128
                        drawLine(
                            color,
                            Offset(size.width * i / 32, size.height / 2 - h / 2),
                            Offset(size.width * i / 32, size.height / 2 + h / 2),
                            strokeWidth = 4.dp.toPx(),
                        )
                    }
            }
        }
    }
}
