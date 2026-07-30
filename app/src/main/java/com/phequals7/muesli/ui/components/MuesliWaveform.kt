package com.phequals7.muesli.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.phequals7.muesli.theme.MuesliSpacing
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Waveform visualizations ported from muesli-ios Shared/MuesliWaveformView.swift.
 * The bar math (patterns, phases, gating) is a 1:1 port — keep in sync.
 */

enum class MuesliWaveformMode {
    /** Live input level driven (recording). */
    Level,

    /** Idle shimmer while waiting (transcribing / connecting). */
    Waiting,
}

private val BASE_PATTERN = floatArrayOf(
    0.18f, 0.26f, 0.42f, 0.58f, 0.76f, 0.92f,
    0.72f, 0.46f, 0.28f, 0.36f, 0.62f, 0.86f,
    0.94f, 0.68f, 0.44f, 0.30f, 0.52f, 0.80f,
    0.66f, 0.48f, 0.34f, 0.24f, 0.18f, 0.14f
)

private const val NOISE_FLOOR = 0.26f

/**
 * The 24-bar live waveform used across the iOS app (voice notes, meetings,
 * keyboard strip). Renders on a Canvas and animates at ~30 fps while active.
 */
@Composable
fun MuesliInlineWaveform(
    mode: MuesliWaveformMode,
    color: Color,
    level: Float?,
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    barCount: Int = 24,
    spacing: Dp = 3.dp,
) {
    // Monotonic clock driving bar motion, matching iOS TimelineView(.animation).
    var elapsed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isActive) {
        if (!isActive) return@LaunchedEffect
        val start = System.nanoTime()
        while (true) {
            withFrameNanos { now ->
                elapsed = (now - start) / 1_000_000_000f
            }
        }
    }

    val count = max(12, barCount)

    Canvas(modifier = modifier) {
        val spacingPx = spacing.toPx()
        val totalSpacing = spacingPx * (count - 1)
        val barWidth = max(2f, min(5.dp.toPx(), (size.width - totalSpacing) / count))
        val xOffset = max((size.width - (barWidth * count + totalSpacing)) / 2f, 0f)
        val centerY = size.height / 2f

        for (index in 0 until count) {
            val sample = when (mode) {
                MuesliWaveformMode.Level -> liveLevelSample(index, level, elapsed)
                MuesliWaveformMode.Waiting -> waitingSample(index, elapsed)
            }
            val minH = 1.5f
            val height = min(size.height, max(minH, minH + (size.height - minH) * sample))
            val alpha = when (mode) {
                MuesliWaveformMode.Level -> 0.94f
                MuesliWaveformMode.Waiting -> waitingOpacity(index, elapsed)
            }
            drawRoundRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(xOffset + index * (barWidth + spacingPx), centerY - height / 2f),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}

private fun liveLevelSample(index: Int, level: Float?, elapsed: Float): Float {
    if (level == null) return 0f
    val normalized = min(max(level, 0f), 1f)
    val gated = max(0f, (normalized - NOISE_FLOOR) / (1f - NOISE_FLOOR))
    if (gated <= 0.02f) return 0f

    val shaped = gated.pow(0.72f)
    val base = BASE_PATTERN[index % BASE_PATTERN.size]
    val localPhase = elapsed * 8.6f + index * 0.58f
    val broadPhase = elapsed * 2.1f + index * 0.13f
    val motion = 0.48f + 0.30f * sin(localPhase) + 0.16f * sin(broadPhase) + base * 0.35f
    val texture = 0.90f + 0.12f * sin(localPhase * 1.47f)
    return min(0.98f, max(0.01f, shaped * motion * texture))
}

private fun waitingSample(index: Int, elapsed: Float): Float {
    val base = BASE_PATTERN[index % BASE_PATTERN.size]
    val phase = elapsed * 5.8f + index * 0.72f
    return min(0.86f, 0.12f + (sin(phase) + 1f) * 0.13f + base * 0.34f)
}

private fun waitingOpacity(index: Int, elapsed: Float): Float {
    val phase = elapsed * 5.8f + index * 0.72f
    return 0.48f + (sin(phase) + 1f) * 0.16f
}

/**
 * Monospaced elapsed-time pill shown while recording
 * (port of iOS VoiceNoteElapsedBadge).
 */
@Composable
fun MuesliElapsedBadge(
    elapsedSeconds: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val text = "%d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.13f), CircleShape)
            .padding(horizontal = MuesliSpacing.s8, vertical = MuesliSpacing.s4)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}
