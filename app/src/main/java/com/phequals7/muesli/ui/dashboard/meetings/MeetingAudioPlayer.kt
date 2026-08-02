package com.phequals7.muesli.ui.dashboard.meetings

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phequals7.muesli.theme.MuesliSpacing
import com.phequals7.muesli.theme.MuesliTheme
import com.phequals7.muesli.ui.dashboard.voicenotes.MuesliSurfaceCard
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

/**
 * Inline playback card for a meeting's retained WAV file —
 * the Android counterpart of the iOS retainedAudioSection.
 * Play/pause + seek slider + elapsed/total time only; no share/speed/waveform.
 */
@Composable
fun MeetingAudioPlayer(audioFile: File, modifier: Modifier = Modifier) {
    val colors = MuesliTheme.colors

    if (!audioFile.exists()) {
        AudioUnavailableRow(modifier)
        return
    }

    // prepare() is synchronous but fine for a local WAV; failure → graceful fallback.
    val player = remember(audioFile) {
        try {
            MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                prepare()
            }
        } catch (_: Exception) {
            null
        }
    }
    if (player == null) {
        AudioUnavailableRow(modifier)
        return
    }

    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableIntStateOf(0) }
    var dragPositionMs by remember { mutableStateOf<Float?>(null) }
    val durationMs = remember(player) { player.duration.coerceAtLeast(0) }

    DisposableEffect(player) {
        player.setOnCompletionListener {
            isPlaying = false
            positionMs = 0
            dragPositionMs = null
            try {
                player.pause()
                player.seekTo(0)
            } catch (_: Exception) {
            }
        }
        onDispose {
            try {
                if (player.isPlaying) player.pause()
            } catch (_: Exception) {
            }
            try {
                player.release()
            } catch (_: Exception) {
            }
        }
    }

    // Ticker: poll position while playing so the slider advances smoothly.
    LaunchedEffect(player, isPlaying) {
        while (isPlaying) {
            val pos = try {
                player.currentPosition
            } catch (_: Exception) {
                break
            }
            positionMs = pos
            delay(250)
        }
    }

    fun togglePlayPause() {
        try {
            if (player.isPlaying) {
                player.pause()
                isPlaying = false
            } else {
                player.start()
                isPlaying = true
                positionMs = player.currentPosition
            }
        } catch (_: Exception) {
            isPlaying = false
        }
    }

    val shownPositionMs = (dragPositionMs?.toInt() ?: positionMs).coerceIn(0, durationMs)
    val timeStyle = TextStyle(fontFeatureSettings = "tnum")

    MuesliSurfaceCard(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MuesliSpacing.s12),
            modifier = Modifier.padding(MuesliSpacing.s12)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.accent)
            ) {
                IconButton(onClick = { togglePlayPause() }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Slider(
                    value = shownPositionMs.toFloat(),
                    onValueChange = { dragPositionMs = it },
                    onValueChangeFinished = {
                        dragPositionMs?.let { target ->
                            val clamped = target.toInt().coerceIn(0, durationMs)
                            try {
                                player.seekTo(clamped)
                                positionMs = clamped
                            } catch (_: Exception) {
                            }
                        }
                        dragPositionMs = null
                    },
                    valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = colors.accent,
                        activeTrackColor = colors.accent,
                        inactiveTrackColor = colors.surfacePrimary
                    )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MuesliSpacing.s4)
                ) {
                    Text(
                        formatMillis(shownPositionMs),
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        style = timeStyle
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        formatMillis(durationMs),
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        style = timeStyle
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioUnavailableRow(modifier: Modifier = Modifier) {
    val colors = MuesliTheme.colors
    MuesliSurfaceCard(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MuesliSpacing.s8),
            modifier = Modifier.padding(MuesliSpacing.s12)
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                "Audio unavailable",
                color = colors.textSecondary,
                fontSize = 13.sp
            )
        }
    }
}

private fun formatMillis(ms: Int): String {
    val totalSeconds = ms / 1000
    return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
