package com.phequals7.muesli.ime.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phequals7.muesli.R
import com.phequals7.muesli.ime.KeyboardController
import com.phequals7.muesli.ime.KeyboardState
import com.phequals7.muesli.theme.MuesliCorners
import com.phequals7.muesli.theme.MuesliSpacing
import com.phequals7.muesli.theme.MuesliTheme
import com.phequals7.muesli.ui.components.MuesliInlineWaveform
import com.phequals7.muesli.ui.components.MuesliWaveformMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Muesli dictation keyboard. Layout mirrors the muesli-ios keyboard
 * extension: brand header, listening strip with live waveform,
 * Cancel/Stop actions, punctuation row, and utility keys.
 */
@Composable
fun KeyboardScreen(
    controller: KeyboardController,
    modifier: Modifier = Modifier
) {
    val state = controller.state
    val statusText = controller.statusText
    val partialText = controller.transcriptionText

    // Muesli brand tokens (dark by default in the IME process)
    val colors = MuesliTheme.colors
    val isRecording = state == KeyboardState.RECORDING
    val isTranscribing = state == KeyboardState.TRANSCRIBING
    val isActive = isRecording || isTranscribing

    val statusColor = when {
        isRecording -> colors.recording
        isTranscribing -> colors.transcribing
        else -> colors.accent
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.backgroundDeep)
            .padding(MuesliSpacing.s12)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(MuesliSpacing.s8),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MuesliCorners.medium))
                    .background(colors.backgroundRaised)
                    .border(1.dp, colors.surfaceBorder, RoundedCornerShape(MuesliCorners.medium))
                    .padding(MuesliSpacing.s12)
            ) {
                // Brand header: official icon + wordmark + status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MuesliSpacing.s8),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.muesli_app_icon),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(7.dp))
                    )
                    Text(
                        text = "muesli",
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (!isActive) {
                        Text(
                            text = statusText,
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Listening strip: status dot + label + live waveform (iOS parity)
                if (isActive) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MuesliSpacing.s8),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = MuesliSpacing.s8)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Text(
                            text = if (isRecording) "Listening" else "Transcribing",
                            color = statusColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        MuesliInlineWaveform(
                            mode = if (isRecording) MuesliWaveformMode.Level else MuesliWaveformMode.Waiting,
                            color = colors.brandBlue,
                            level = if (isRecording) controller.inputLevel else null,
                            barCount = 34, // iOS keyboard activeWaveformBarCount
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                        )
                    }
                }

                // Show partial results in real-time
                AnimatedVisibility(visible = partialText.isNotEmpty()) {
                    Text(
                        text = partialText,
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .padding(top = MuesliSpacing.s8)
                            .fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(MuesliSpacing.s8))

                // Primary actions: iOS shows Cancel + red Stop while recording
                if (isRecording) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(MuesliSpacing.s8),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { controller.cancelDictation() },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.surfacePrimary),
                            shape = RoundedCornerShape(MuesliCorners.small),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Text("Cancel", color = colors.destructive, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = { controller.stopDictation() },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.destructive),
                            shape = RoundedCornerShape(MuesliCorners.small),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            if (state == KeyboardState.IDLE || state == KeyboardState.ERROR || state == KeyboardState.FINISHED) {
                                controller.startDictation()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = statusColor),
                        shape = RoundedCornerShape(MuesliCorners.small),
                        enabled = !isTranscribing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(MuesliSpacing.s8))
                        Text(
                            text = if (isTranscribing) "Transcribing..." else "Start Dictation",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Punctuation row (iOS parity: . , ? ! ' backspace)
            Row(
                horizontalArrangement = Arrangement.spacedBy(MuesliSpacing.s8),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(".", ",", "?", "!", "'").forEach { mark ->
                    KeyboardKey(
                        title = mark,
                        onClick = { controller.insertPunctuation(mark) },
                        modifier = Modifier.weight(1f)
                    )
                }
                KeyboardKey(
                    title = "⌫",
                    onClick = { controller.backspace() },
                    modifier = Modifier.weight(1f),
                    repeatable = true
                )
            }

            // Utility row: Clear Last / space / return
            Row(
                horizontalArrangement = Arrangement.spacedBy(MuesliSpacing.s8),
                modifier = Modifier.fillMaxWidth()
            ) {
                KeyboardKey(
                    title = "Clear Last",
                    onClick = { controller.clearLastInserted() },
                    modifier = Modifier.weight(1f)
                )
                KeyboardKey(
                    title = "space",
                    onClick = { controller.insertSpace() },
                    modifier = Modifier.weight(1f),
                    repeatable = true
                )
                KeyboardKey(
                    title = "return ↵",
                    onClick = { controller.insertReturn() },
                    modifier = Modifier.weight(1f)
                )
            }

            // System row (iOS bottom bar parity): globe to switch keyboards
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MuesliSpacing.s4)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(MuesliCorners.small))
                        .clickable { controller.switchKeyboard() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = "Switch keyboard",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "dictation-only · switch for QWERTY",
                    color = colors.textTertiary,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun KeyboardKey(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    repeatable: Boolean = false,
) {
    val colors = MuesliTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(MuesliCorners.small))
            .background(colors.surfacePrimary)
            .border(1.dp, colors.surfaceBorder, RoundedCornerShape(MuesliCorners.small))
            .then(
                if (repeatable) {
                    Modifier.repeatableClickable(onClick = onClick)
                } else {
                    Modifier.clickable { onClick() }
                }
            )
    ) {
        Text(
            text = title,
            color = colors.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Fires [onClick] immediately, then repeats after a 400 ms hold at ~16/s,
 * matching how backspace/space behave on system keyboards.
 */
private fun Modifier.repeatableClickable(
    initialDelayMs: Long = 400,
    repeatDelayMs: Long = 60,
    onClick: () -> Unit,
): Modifier = composed {
    val scope = rememberCoroutineScope()
    pointerInput(Unit) {
        detectTapGestures(
            onPress = {
                onClick()
                val repeatJob = scope.launch {
                    delay(initialDelayMs)
                    while (true) {
                        onClick()
                        delay(repeatDelayMs)
                    }
                }
                tryAwaitRelease()
                repeatJob.cancel()
            }
        )
    }
}
