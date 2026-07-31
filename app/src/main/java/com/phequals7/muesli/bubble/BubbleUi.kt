package com.phequals7.muesli.bubble

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phequals7.muesli.theme.MuesliSpacing
import com.phequals7.muesli.theme.MuesliTheme
import com.phequals7.muesli.ui.components.MuesliInlineWaveform
import com.phequals7.muesli.ui.components.MuesliWaveformMode

enum class BubbleState { IDLE, RECORDING, TRANSCRIBING, SAVED, ERROR }

/** Observable UI state shared between BubbleService and the overlay Compose UI. */
class BubbleUiState {
    val expanded = mutableStateOf(false)
    val state = mutableStateOf(BubbleState.IDLE)
    val inputLevel = mutableFloatStateOf(0f)
    val elapsedSeconds = mutableIntStateOf(0)
    val partialText = mutableStateOf("")

    fun reset() {
        expanded.value = false
        state.value = BubbleState.IDLE
        inputLevel.value = 0f
        elapsedSeconds.value = 0
        partialText.value = ""
    }
}

/**
 * Overlay content: a draggable Muesli-blue bubble when collapsed; a slim
 * single-row capsule (stop · waveform · elapsed · cancel) while capturing.
 * Deliberately snug — it floats over content without blocking it.
 */
@Composable
fun BubbleOverlay(
    state: BubbleUiState,
    onStop: () -> Unit,
    onDiscard: () -> Unit,
) {
    if (state.expanded.value) {
        BubbleCapsule(state, onStop, onDiscard)
    } else {
        BubbleButton()
    }
}

@Composable
private fun BubbleButton() {
    val colors = MuesliTheme.colors
    Box(
        modifier = Modifier
            .size(56.dp)
            .shadow(8.dp, CircleShape, ambientColor = colors.accent, spotColor = colors.accent)
            .clip(CircleShape)
            .background(colors.accent)
            .border(1.dp, colors.accent.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Mic,
            contentDescription = "Record a voice note",
            tint = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.size(26.dp)
        )
    }
}

/** Slim floating capsule: [stop] [waveform] [elapsed] [cancel]. */
@Composable
private fun BubbleCapsule(
    state: BubbleUiState,
    onStop: () -> Unit,
    onDiscard: () -> Unit,
) {
    val colors = MuesliTheme.colors
    val captureState = state.state.value

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MuesliSpacing.s8),
        modifier = Modifier
            .shadow(12.dp, RoundedCornerShape(32.dp))
            .clip(RoundedCornerShape(32.dp))
            .background(colors.backgroundRaised)
            .border(1.dp, colors.surfaceBorder, RoundedCornerShape(32.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        when (captureState) {
            BubbleState.RECORDING -> {
                // Stop: red circle, white square (plain box — IconButton
                // enforces a 48dp min touch target and overflows the capsule)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(colors.destructive)
                        .clickable { onStop() },
                ) {
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(androidx.compose.ui.graphics.Color.White)
                    )
                }
                MuesliInlineWaveform(
                    mode = MuesliWaveformMode.Level,
                    color = colors.brandBlue,
                    level = state.inputLevel.value,
                    barCount = 20,
                    modifier = Modifier.width(120.dp).height(26.dp),
                )
                CapsuleElapsed(state.elapsedSeconds.value, colors.recording)
                CapsuleCancel(onDiscard, colors)
            }
            BubbleState.TRANSCRIBING -> {
                MuesliInlineWaveform(
                    mode = MuesliWaveformMode.Waiting,
                    color = colors.transcribing,
                    level = null,
                    barCount = 20,
                    modifier = Modifier.width(150.dp).height(26.dp),
                )
                CapsuleElapsed(state.elapsedSeconds.value, colors.transcribing)
            }
            BubbleState.SAVED -> {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(colors.syncGreenSubtle),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = colors.syncGreen, modifier = Modifier.size(14.dp))
                }
                Text(
                    "Saved & copied",
                    color = colors.syncGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            BubbleState.ERROR -> {
                Text("Something went wrong", color = colors.destructive, fontSize = 12.sp)
            }
            BubbleState.IDLE -> {}
        }
    }
}

@Composable
private fun CapsuleElapsed(seconds: Int, color: androidx.compose.ui.graphics.Color) {
    Text(
        "%d:%02d".format(seconds / 60, seconds % 60),
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
private fun CapsuleCancel(onDiscard: () -> Unit, colors: com.phequals7.muesli.theme.MuesliColors) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable { onDiscard() },
    ) {
        Icon(
            Icons.Default.Close,
            contentDescription = "Discard recording",
            tint = colors.textTertiary,
            modifier = Modifier.size(16.dp),
        )
    }
}
