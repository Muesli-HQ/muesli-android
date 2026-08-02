package com.phequals7.muesli.ui.dashboard.voicenotes

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import com.phequals7.muesli.model.SpeechModels
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phequals7.muesli.R
import com.phequals7.muesli.audio.AudioInputRouteManager
import com.phequals7.muesli.data.SharedStore
import com.phequals7.muesli.data.entity.DictationResult
import com.phequals7.muesli.data.entity.RecordingSession
import com.phequals7.muesli.engine.MockTranscriptionEngine
import com.phequals7.muesli.engine.SherpaOnnxEngine
import com.phequals7.muesli.theme.MuesliCorners
import com.phequals7.muesli.theme.MuesliSpacing
import com.phequals7.muesli.theme.MuesliTheme
import com.phequals7.muesli.ui.components.MuesliElapsedBadge
import com.phequals7.muesli.ui.components.MuesliInlineWaveform
import com.phequals7.muesli.ui.components.MuesliWaveformMode
import com.phequals7.muesli.utils.CustomWordMatcher
import com.phequals7.muesli.utils.FillerWordFilter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Voice Notes home — port of the muesli-ios main screen (DictationView):
 * brand header, stats row, keep-mic-ready card, hero record card that
 * transforms into the live recording surface, and recent voice notes.
 */
@Composable
fun VoiceNotesTab(
    store: SharedStore,
    onNavigateToSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MuesliTheme.colors

    var dictationsList by remember { mutableStateOf(emptyList<DictationResult>()) }
    var meetingsList by remember { mutableStateOf(emptyList<RecordingSession>()) }

    // Recording state (hero card transforms into the recording surface)
    var isRecording by remember { mutableStateOf(false) }
    var isTranscribing by remember { mutableStateOf(false) }
    var partialText by remember { mutableStateOf("") }
    var inputLevel by remember { mutableFloatStateOf(0f) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var keepMicReady by remember { mutableStateOf(store.keepMicReady) }

    var activeEngine by remember { mutableStateOf<com.phequals7.muesli.engine.TranscriptionEngine?>(null) }
    var recordingStartedAt by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        launch { store.getDictationResultsFlow().collect { dictationsList = it } }
        launch { store.getRecordingSessionsFlow().collect { meetingsList = it } }
    }

    LaunchedEffect(isRecording) {
        if (!isRecording) return@LaunchedEffect
        while (true) {
            elapsedSeconds = ((System.currentTimeMillis() - recordingStartedAt) / 1000L).toInt()
            delay(250)
        }
    }

    fun startDictation() {
        isRecording = true
        isTranscribing = false
        partialText = ""
        inputLevel = 0f
        elapsedSeconds = 0
        recordingStartedAt = System.currentTimeMillis()

        val engine = when (store.engineType) {
            "mock" -> MockTranscriptionEngine()
            else -> SherpaOnnxEngine(context)
        }
        engine.setAmplitudeListener { level -> inputLevel = level }
        activeEngine = engine

        engine.startListening(
            onPartialResult = { partial -> partialText = partial },
            onFinalResult = { rawText ->
                scope.launch {
                    var processed = rawText
                    if (store.isFillerWordRemovalEnabled) processed = FillerWordFilter.apply(processed)
                    if (store.isCustomDictionaryEnabled) {
                        processed = CustomWordMatcher.apply(processed, store.getCustomWords())
                    }
                    if (processed.isNotBlank()) {
                        store.insertDictationResult(
                            DictationResult(
                                text = processed,
                                engineIdentifier = store.engineType,
                                durationMs = System.currentTimeMillis() - recordingStartedAt
                            )
                        )
                    }
                    isRecording = false
                    isTranscribing = false
                    activeEngine = null
                }
            },
            onError = { err ->
                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                isRecording = false
                isTranscribing = false
                activeEngine = null
            }
        )
    }

    fun stopDictation() {
        isTranscribing = true
        activeEngine?.stopListening()
    }

    fun cancelDictation() {
        activeEngine?.cancel()
        activeEngine = null
        isRecording = false
        isTranscribing = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(MuesliSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(MuesliSpacing.s16)
    ) {
        // ── Brand header ────────────────────────────────────────────────
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MuesliSpacing.s12)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_muesli_logo),
                    contentDescription = "Muesli logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(MuesliCorners.medium))
                )
                Column {
                    Text(
                        "muesli",
                        color = colors.textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.4).sp
                    )
                    Text(
                        "Local-first voice notes",
                        color = colors.textSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // ── Stats row ───────────────────────────────────────────────────
        item {
            val stats = remember(dictationsList, meetingsList) {
                computeStats(dictationsList, meetingsList)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MuesliSpacing.s8)) {
                StatCard(
                    icon = Icons.Default.LocalFireDepartment,
                    iconTint = Color(0xFFFF9F43),
                    value = "${stats.streakDays}",
                    label = "streak",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    icon = Icons.Default.GraphicEq,
                    iconTint = colors.brandBlue,
                    value = formatWordCount(stats.totalWords),
                    label = "words",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    icon = Icons.Default.Speed,
                    iconTint = colors.syncGreen,
                    value = if (stats.wpm > 0) "${stats.wpm}" else "—",
                    label = "WPM",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    icon = Icons.Default.Groups,
                    iconTint = colors.accent,
                    value = "${stats.meetingCount}",
                    label = "meetings",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── Keep mic ready ──────────────────────────────────────────────
        item {
            MuesliSurfaceCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MuesliSpacing.s12),
                    modifier = Modifier.padding(MuesliSpacing.s16)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colors.brandBlueSubtle),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = colors.accent)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Keep mic ready", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "The keyboard prewarms when it opens — recording starts only when you tap the mic.",
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = keepMicReady,
                        onCheckedChange = {
                            keepMicReady = it
                            store.keepMicReady = it
                        }
                    )
                }
            }
        }

        // ── Hero record card ────────────────────────────────────────────
        item {
            MuesliSurfaceCard {
                if (!isRecording) {
                    // Idle hero (iOS "Voice Note / Ready" card)
                    Column(modifier = Modifier.padding(MuesliSpacing.s20)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Voice Note", color = colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
                                Text("Ready", color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                            MicSourcePill(store)
                        }

                        // Big gradient mic button with halo ring (iOS hero)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = MuesliSpacing.s24)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(104.dp)
                                    .clip(CircleShape)
                                    .background(colors.accent.copy(alpha = 0.10f))
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(84.dp)
                                        .clip(CircleShape)
                                        .background(colors.accent.copy(alpha = 0.16f))
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(68.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(colors.brandBlue, colors.accent)
                                                )
                                            )
                                            .clickable { startDictation() }
                                    ) {
                                        Icon(
                                            Icons.Default.Mic,
                                            contentDescription = "Start Voice Note",
                                            tint = Color.White,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            "Start Voice Note",
                            color = colors.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )

                        // Engine footer → Settings (iOS "Long mode" row slot)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MuesliSpacing.s8),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = MuesliSpacing.s20)
                                .clip(RoundedCornerShape(MuesliCorners.small))
                                .clickable { onNavigateToSettings() }
                                .padding(vertical = MuesliSpacing.s4)
                        ) {
                            Icon(
                                Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                engineDisplayName(store.engineType, SpeechModels.selected(context).shortName),
                                color = colors.textSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = colors.textTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else {
                    // Recording surface (hero card becomes the live view).
                    // Layout mirrors the iOS recording hero: status + elapsed,
                    // mic pill, waveform inside a raised container with the
                    // "Listening" caption, red halo stop button, discard pill.
                    Column(
                        modifier = Modifier.padding(MuesliSpacing.s20),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Voice Note", color = colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
                                Text(
                                    if (isTranscribing) "Transcribing" else "Recording",
                                    color = if (isTranscribing) colors.transcribing else colors.accent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            MuesliElapsedBadge(
                                elapsedSeconds = elapsedSeconds,
                                color = if (isTranscribing) colors.transcribing else colors.recording
                            )
                        }

                        // Mic source pill stays visible while recording (iOS parity)
                        MicSourcePill(
                            store = store,
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = MuesliSpacing.s8)
                        )

                        // Waveform inside a raised container, "Listening" caption below
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = MuesliSpacing.s16)
                                .clip(RoundedCornerShape(MuesliCorners.large))
                                .background(colors.surfacePrimary)
                                .padding(vertical = MuesliSpacing.s20, horizontal = MuesliSpacing.s12)
                        ) {
                            MuesliInlineWaveform(
                                mode = if (isTranscribing) MuesliWaveformMode.Waiting else MuesliWaveformMode.Level,
                                color = if (isTranscribing) colors.transcribing else colors.brandBlue,
                                level = if (isTranscribing) null else inputLevel,
                                barCount = 32,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                            )
                            Spacer(modifier = Modifier.height(MuesliSpacing.s12))
                            Text(
                                if (isTranscribing) partialText.ifEmpty { "Transcribing" } else "Listening",
                                color = colors.textSecondary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Red halo stop button (iOS recording hero parity)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = MuesliSpacing.s8)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(104.dp)
                                    .clip(CircleShape)
                                    .background(colors.destructive.copy(alpha = 0.10f))
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(84.dp)
                                        .clip(CircleShape)
                                        .background(colors.destructive.copy(alpha = 0.16f))
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(68.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(colors.destructive.copy(alpha = 0.9f), colors.destructive)
                                                )
                                            )
                                            .clickable { stopDictation() }
                                    ) {
                                        Icon(
                                            Icons.Default.Stop,
                                            contentDescription = "Stop Recording",
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            "Stop Recording",
                            color = colors.destructive,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = MuesliSpacing.s8)
                        )

                        // Discard Recording: full-width destructive-outlined pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = MuesliSpacing.s16)
                                .clip(RoundedCornerShape(MuesliCorners.medium))
                                .background(colors.destructiveSubtle)
                                .border(1.dp, colors.destructive.copy(alpha = 0.35f), RoundedCornerShape(MuesliCorners.medium))
                                .clickable { cancelDictation() }
                                .padding(vertical = MuesliSpacing.s12)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = colors.destructive,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(MuesliSpacing.s8))
                            Text(
                                "Discard Recording",
                                color = colors.destructive,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // ── Recent voice notes ──────────────────────────────────────────
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Recent Voice Notes", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
                    Text(
                        "${dictationsList.size} saved",
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (dictationsList.isEmpty()) {
            item {
                MuesliSurfaceCard {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MuesliSpacing.s24)
                    ) {
                        Icon(
                            Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = colors.textTertiary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(MuesliSpacing.s8))
                        Text("No voice notes yet", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Tap the microphone to record your first note.", color = colors.textSecondary, fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(dictationsList, key = { it.id }) { item ->
                VoiceNoteRow(
                    item = item,
                    onDelete = { id -> scope.launch { store.deleteDictationResult(id) } }
                )
            }
        }
    }
}

// ─────────────────────────── components ───────────────────────────

/**
 * Mic source pill shown on the hero card (idle and recording). Displays the
 * live input route from [AudioInputRouteManager] and opens a chooser for the
 * RecordingMicrophonePreference (iOS mic source pill parity).
 */
@Composable
private fun MicSourcePill(
    store: SharedStore,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = MuesliTheme.colors
    val routeManager = remember { AudioInputRouteManager(context) }
    var pref by remember { mutableStateOf(AudioInputRouteManager.MicPreference.fromId(store.micPreference)) }
    var showChooser by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(MuesliCorners.small))
            .background(colors.surfacePrimary)
            .clickable { showChooser = true }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            Icons.Default.Mic,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(13.dp)
        )
        Text(routeManager.currentRouteLabel(pref), color = colors.textSecondary, fontSize = 11.sp)
    }

    if (showChooser) {
        AlertDialog(
            onDismissRequest = { showChooser = false },
            containerColor = colors.backgroundRaised,
            title = { Text("Recording Microphone", color = colors.textPrimary) },
            text = {
                Column {
                    AudioInputRouteManager.MicPreference.entries.forEach { option ->
                        val available = routeManager.isAvailable(option)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(MuesliCorners.small))
                                .clickable(enabled = available) {
                                    pref = option
                                    store.micPreference = option.id
                                    showChooser = false
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    option.label,
                                    color = if (available) colors.textPrimary else colors.textTertiary,
                                    fontSize = 14.sp,
                                    fontWeight = if (option == pref) FontWeight.SemiBold else FontWeight.Normal
                                )
                                Text(
                                    micOptionSubtitle(routeManager, option),
                                    color = colors.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            if (option == pref) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChooser = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }
}

private fun micOptionSubtitle(
    routeManager: AudioInputRouteManager,
    option: AudioInputRouteManager.MicPreference,
): String = when (option) {
    AudioInputRouteManager.MicPreference.AUTO -> "Follows the system default route"
    AudioInputRouteManager.MicPreference.PHONE -> "Built-in phone microphone"
    AudioInputRouteManager.MicPreference.BLUETOOTH ->
        if (routeManager.isAvailable(option)) "Connected Bluetooth mic" else "Not connected"
    AudioInputRouteManager.MicPreference.USB ->
        if (routeManager.isAvailable(option)) "Connected USB microphone" else "Not connected"
}

@Composable
private fun StatCard(
    icon: ImageVector,
    iconTint: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = MuesliTheme.colors
    MuesliSurfaceCard(modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MuesliSpacing.s12, horizontal = MuesliSpacing.s4)
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                value,
                color = colors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp,
                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
            )
            Text(label, color = colors.textSecondary, fontSize = 11.sp, letterSpacing = 0.2.sp)
        }
    }
}

@Composable
private fun VoiceNoteRow(
    item: DictationResult,
    onDelete: (String) -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val colors = MuesliTheme.colors

    MuesliSurfaceCard {
        Row(modifier = Modifier.fillMaxWidth()) {
            // iOS-style accent rail on the leading edge
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .heightIn(min = 64.dp)
                    .fillMaxHeight()
                    .background(colors.accent)
            )
            Column(modifier = Modifier.padding(MuesliSpacing.s12).weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = SimpleDateFormat("d MMM yyyy 'at' h:mm a", Locale.getDefault()).format(Date(item.createdAt)),
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = colors.textSecondary,
                        modifier = Modifier
                            .size(17.dp)
                            .clickable {
                                clipboardManager.setText(AnnotatedString(item.text))
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                    )
                    Spacer(modifier = Modifier.width(MuesliSpacing.s12))
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = colors.destructive.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(17.dp)
                            .clickable { onDelete(item.id) }
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                var expanded by remember { mutableStateOf(false) }
                var hasOverflow by remember { mutableStateOf(false) }
                Text(
                    text = item.text,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!expanded) hasOverflow = it.hasVisualOverflow }
                )
                // iOS parity: expand affordance only when the text actually truncates
                if (hasOverflow || expanded) {
                    Text(
                        text = if (expanded) "Show less" else "Show more",
                        color = colors.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .padding(top = MuesliSpacing.s4)
                            .clip(RoundedCornerShape(MuesliCorners.small))
                            .clickable { expanded = !expanded }
                            .padding(vertical = MuesliSpacing.s4)
                    )
                }
            }
        }
    }
}

@Composable
fun MuesliSurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = MuesliTheme.colors
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.backgroundRaised),
        shape = RoundedCornerShape(MuesliCorners.large),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, colors.surfaceBorder, RoundedCornerShape(MuesliCorners.large)),
        content = { content() }
    )
}

// ─────────────────────────── stats ───────────────────────────

private data class VoiceNoteStats(
    val streakDays: Int,
    val totalWords: Int,
    val wpm: Int,
    val meetingCount: Int,
)

private fun computeStats(
    dictations: List<DictationResult>,
    meetings: List<RecordingSession>,
): VoiceNoteStats {
    val totalWords = dictations.sumOf { wordCount(it.text) }

    val timedWords = dictations.filter { it.durationMs > 0 }.sumOf { wordCount(it.text) }
    val timedMinutes = dictations.sumOf { it.durationMs } / 60_000.0
    val wpm = if (timedMinutes > 0) (timedWords / timedMinutes).roundToInt() else 0

    // Streak: consecutive day buckets with >= 1 note, ending today or yesterday
    val dayMs = 86_400_000L
    val days = dictations.map { it.createdAt / dayMs }.toSet()
    var streak = 0
    var cursor = System.currentTimeMillis() / dayMs
    if (cursor !in days) cursor-- // allow "yesterday" as streak anchor
    while (cursor in days) {
        streak++
        cursor--
    }

    return VoiceNoteStats(
        streakDays = streak,
        totalWords = totalWords,
        wpm = wpm,
        meetingCount = meetings.count { it.kind == "meeting" },
    )
}

private fun wordCount(text: String): Int =
    text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }

private fun formatWordCount(words: Int): String = when {
    words >= 1_000_000 -> "%.1fM".format(words / 1_000_000.0)
    words >= 10_000 -> "%.1fk".format(words / 1_000.0)
    words >= 1_000 -> "%,d".format(words)
    else -> "$words"
}

private fun engineDisplayName(engineType: String, modelName: String): String = when (engineType) {
    "mock" -> "Mock Engine (sandbox)"
    else -> "$modelName · on-device"
}
