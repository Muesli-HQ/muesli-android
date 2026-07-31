package com.phequals7.muesli.bubble

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phequals7.muesli.theme.MuesliCorners
import com.phequals7.muesli.theme.MuesliSpacing
import com.phequals7.muesli.theme.MuesliTheme
import com.phequals7.muesli.ui.components.MuesliElapsedBadge
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
 * Overlay content: a draggable Muesli-blue bubble when collapsed, a compact
 * dictation card (header / waveform / elapsed / stop / discard) when
 * expanded. Colors and shapes mirror the in-app recording hero.
 */
@Composable
fun BubbleOverlay(
    state: BubbleUiState,
    onStop: () -> Unit,
    onDiscard: () -> Unit,
    onCollapse: () -> Unit,
    onDismissService: () -> Unit,
) {
    if (state.expanded.value) {
        BubbleCard(state, onStop, onDiscard, onCollapse, onDismissService)
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

@Composable
private fun BubbleCard(
    state: BubbleUiState,
    onStop: () -> Unit,
    onDiscard: () -> Unit,
    onCollapse: () -> Unit,
    onDismissService: () -> Unit,
) {
    val colors = MuesliTheme.colors
    val captureState = state.state.value

    Column(
        modifier = Modifier
            .width(300.dp)
            .clip(RoundedCornerShape(MuesliCorners.large))
            .background(colors.backgroundRaised)
            .border(1.dp, colors.surfaceBorder, RoundedCornerShape(MuesliCorners.large))
            .padding(MuesliSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(MuesliSpacing.s12),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "muesli",
                color = colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                modifier = Modifier.weight(1f),
            )
            if (captureState == BubbleState.RECORDING || captureState == BubbleState.TRANSCRIBING) {
                MuesliElapsedBadge(
                    elapsedSeconds = state.elapsedSeconds.value,
                    color = if (captureState == BubbleState.TRANSCRIBING) colors.transcribing else colors.recording,
                )
            }
            IconButton(onClick = onCollapse, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Collapse", tint = colors.textTertiary, modifier = Modifier.size(18.dp))
            }
        }

        // Waveform / status
        when (captureState) {
            BubbleState.RECORDING -> {
                MuesliInlineWaveform(
                    mode = MuesliWaveformMode.Level,
                    color = colors.brandBlue,
                    level = state.inputLevel.value,
                    barCount = 28,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                )
                if (state.partialText.value.isNotBlank()) {
                    Text(
                        state.partialText.value,
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            BubbleState.TRANSCRIBING -> {
                MuesliInlineWaveform(
                    mode = MuesliWaveformMode.Waiting,
                    color = colors.transcribing,
                    level = null,
                    barCount = 28,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                )
                Text("Transcribing…", color = colors.textSecondary, fontSize = 11.sp)
            }
            BubbleState.SAVED -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MuesliSpacing.s8),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(colors.syncGreenSubtle),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = colors.syncGreen, modifier = Modifier.size(14.dp))
                    }
                    Text("Saved & copied to clipboard", color = colors.syncGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
            BubbleState.ERROR -> {
                Text("Something went wrong", color = colors.destructive, fontSize = 12.sp)
            }
            BubbleState.IDLE -> {}
        }

        // Actions
        if (captureState == BubbleState.RECORDING) {
            Row(horizontalArrangement = Arrangement.spacedBy(MuesliSpacing.s8)) {
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.destructive),
                    shape = RoundedCornerShape(MuesliCorners.medium),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("Stop", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onDiscard) {
                    Text("Discard", color = colors.textSecondary, fontSize = 13.sp)
                }
            }
        }

        // Footer: dismiss service affordance
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "Quick capture",
                color = colors.textTertiary,
                fontSize = 10.sp,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismissService) {
                Text("Hide bubble", color = colors.textTertiary, fontSize = 10.sp)
            }
        }
    }
}
