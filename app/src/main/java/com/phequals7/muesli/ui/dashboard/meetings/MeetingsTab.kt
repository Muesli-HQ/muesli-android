package com.phequals7.muesli.ui.dashboard.meetings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phequals7.muesli.data.SharedStore
import com.phequals7.muesli.data.entity.RecordingSession
import com.phequals7.muesli.meetings.MeetingRecordingController
import com.phequals7.muesli.meetings.MeetingTemplate
import com.phequals7.muesli.model.ModelManager
import com.phequals7.muesli.theme.MuesliCorners
import com.phequals7.muesli.theme.MuesliSpacing
import com.phequals7.muesli.theme.MuesliTheme
import com.phequals7.muesli.ui.components.MuesliElapsedBadge
import com.phequals7.muesli.ui.components.MuesliInlineWaveform
import com.phequals7.muesli.ui.components.MuesliWaveformMode
import com.phequals7.muesli.ui.dashboard.voicenotes.MuesliSurfaceCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Meetings home — port of the muesli-ios MeetingsView:
 * start-a-new-meeting card (title + template), the live recording surface,
 * and searchable recent meetings with status badges.
 */
@Composable
fun MeetingsTab(
    store: SharedStore,
    onNavigateToSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MuesliTheme.colors

    var meetings by remember { mutableStateOf(emptyList<RecordingSession>()) }
    var selectedMeetingId by remember { mutableStateOf<String?>(null) }

    var titleInput by remember { mutableStateOf("") }
    var selectedTemplate by remember { mutableStateOf(MeetingTemplate.fromId(store.defaultMeetingTemplate)) }
    var searchQuery by remember { mutableStateOf("") }
    val titleFocusRequester = remember { FocusRequester() }

    // Put the cursor in the title field when the start card is visible
    LaunchedEffect(MeetingRecordingController.isActive) {
        if (!MeetingRecordingController.isActive) titleFocusRequester.requestFocus()
    }

    val modelReady = remember { ModelManager(context.applicationContext).isDownloaded() }

    LaunchedEffect(Unit) {
        store.getRecordingSessionsFlow().collect { meetings = it.filter { s -> s.kind == "meeting" } }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun startMeeting() {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val title = titleInput.ifBlank {
            "Meeting ${SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date())}"
        }
        MeetingRecordingController.start(context, UUID.randomUUID().toString(), title, selectedTemplate.id)
        titleInput = ""
    }

    val selectedMeeting = selectedMeetingId?.let { id -> meetings.firstOrNull { it.id == id } }
    if (selectedMeeting != null) {
        MeetingDetailView(
            store = store,
            meeting = selectedMeeting,
            onBack = { selectedMeetingId = null },
            onDeleted = { selectedMeetingId = null }
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(MuesliSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(MuesliSpacing.s16)
    ) {
        // ── Header ──────────────────────────────────────────────────────
        item {
            Column {
                Text("Meetings", color = colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Record, write, review.", color = colors.textSecondary, fontSize = 13.sp)
            }
        }

        // ── Active recording surface ────────────────────────────────────
        if (MeetingRecordingController.isActive) {
            item {
                ActiveMeetingCard(
                    onStop = { MeetingRecordingController.stop(context) },
                    onDiscard = { MeetingRecordingController.discard(context) },
                )
            }
        }

        // ── Start a new meeting ─────────────────────────────────────────
        if (!MeetingRecordingController.isActive) {
            item {
                MuesliSurfaceCard {
                    Column(
                        modifier = Modifier.padding(MuesliSpacing.s16),
                        verticalArrangement = Arrangement.spacedBy(MuesliSpacing.s12)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MuesliSpacing.s12)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(MuesliCorners.small))
                                    .background(colors.brandBlueSubtle),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null, tint = colors.accent)
                            }
                            Text(
                                "Start a new meeting",
                                color = colors.accent,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        TextField(
                            value = titleInput,
                            onValueChange = { titleInput = it },
                            placeholder = { Text("Meeting title", color = colors.textTertiary) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = colors.surfacePrimary,
                                unfocusedContainerColor = colors.surfacePrimary,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                cursorColor = colors.accent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(MuesliCorners.small),
                            // Auto-focus so titling is the first thing you do (user feedback)
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(titleFocusRequester)
                        )

                        TemplatePicker(
                            selected = selectedTemplate,
                            onSelected = { selectedTemplate = it }
                        )

                        if (!modelReady) {
                            Text(
                                "Requires the Parakeet V3 model — download it from Settings first.",
                                color = colors.destructive,
                                fontSize = 11.sp
                            )
                        }

                        Button(
                            onClick = { startMeeting() },
                            enabled = modelReady,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                disabledContainerColor = colors.accent.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(MuesliCorners.medium),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(MuesliSpacing.s8))
                            Text("Start Meeting", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ── Recent meetings ─────────────────────────────────────────────
        item {
            Column {
                Text("Recent Meetings", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("${meetings.size} saved", color = colors.textSecondary, fontSize = 12.sp)
            }
        }

        item {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search meetings", color = colors.textTertiary) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.surfacePrimary,
                    unfocusedContainerColor = colors.surfacePrimary,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    cursorColor = colors.accent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(MuesliCorners.medium),
                modifier = Modifier.fillMaxWidth()
            )
        }

        val filtered = meetings.filter {
            searchQuery.isBlank() ||
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.manualNotes.contains(searchQuery, ignoreCase = true)
        }

        if (filtered.isEmpty()) {
            item {
                MuesliSurfaceCard {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MuesliSpacing.s24)
                    ) {
                        Icon(Icons.Default.Groups, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(MuesliSpacing.s8))
                        Text(
                            if (meetings.isEmpty()) "No meetings recorded" else "No matches",
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        if (meetings.isEmpty()) {
                            Text("Start a meeting above to capture notes.", color = colors.textSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            items(filtered, key = { it.id }) { meeting ->
                MeetingRow(
                    meeting = meeting,
                    onClick = { selectedMeetingId = meeting.id }
                )
            }
        }
    }
}

// ─────────────────────────── components ───────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplatePicker(
    selected: MeetingTemplate,
    onSelected: (MeetingTemplate) -> Unit,
) {
    val colors = MuesliTheme.colors
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(MuesliCorners.small))
                .background(colors.surfacePrimary)
                .clickable { expanded = true }
                .padding(MuesliSpacing.s12)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(selected.label, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(selected.detail, color = colors.textSecondary, fontSize = 12.sp)
            }
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = "Choose template",
                tint = colors.textSecondary
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(colors.backgroundRaised)
        ) {
            MeetingTemplate.entries.forEach { template ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(template.label, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(template.detail, color = colors.textSecondary, fontSize = 11.sp)
                        }
                    },
                    onClick = {
                        onSelected(template)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ActiveMeetingCard(
    onStop: () -> Unit,
    onDiscard: () -> Unit,
) {
    val colors = MuesliTheme.colors

    MuesliSurfaceCard {
        Column(
            modifier = Modifier.padding(MuesliSpacing.s20),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        MeetingRecordingController.meetingTitle,
                        color = colors.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (MeetingRecordingController.isFinalizing) "Finishing" else "Recording",
                        color = if (MeetingRecordingController.isFinalizing) colors.transcribing else colors.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                MuesliElapsedBadge(
                    elapsedSeconds = MeetingRecordingController.elapsedSeconds,
                    color = colors.recording
                )
            }

            // Waveform in raised container (Voice Notes parity)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = MuesliSpacing.s16)
                    .clip(RoundedCornerShape(MuesliCorners.large))
                    .background(colors.surfacePrimary)
                    .padding(vertical = MuesliSpacing.s16, horizontal = MuesliSpacing.s12)
            ) {
                MuesliInlineWaveform(
                    mode = if (MeetingRecordingController.isFinalizing) MuesliWaveformMode.Waiting else MuesliWaveformMode.Level,
                    color = colors.brandBlue,
                    level = if (MeetingRecordingController.isFinalizing) null else MeetingRecordingController.inputLevel,
                    barCount = 32,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                )
                Spacer(modifier = Modifier.height(MuesliSpacing.s8))
                Text(
                    text = if (MeetingRecordingController.isFinalizing) "Transcribing remaining audio" else "Listening",
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            }

            // Stop + Discard
            Row(
                horizontalArrangement = Arrangement.spacedBy(MuesliSpacing.s12),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(MuesliCorners.medium))
                        .background(colors.destructiveSubtle)
                        .border(1.dp, colors.destructive.copy(alpha = 0.35f), RoundedCornerShape(MuesliCorners.medium))
                        .clickable(enabled = !MeetingRecordingController.isFinalizing) { onDiscard() }
                        .padding(vertical = MuesliSpacing.s12)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = colors.destructive, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Discard", color = colors.destructive, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(2f)
                        .clip(RoundedCornerShape(MuesliCorners.medium))
                        .background(colors.destructive)
                        .clickable(enabled = !MeetingRecordingController.isFinalizing) { onStop() }
                        .padding(vertical = MuesliSpacing.s12)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (MeetingRecordingController.isFinalizing) "Finishing..." else "Stop Meeting",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun MeetingRow(
    meeting: RecordingSession,
    onClick: () -> Unit,
) {
    val colors = MuesliTheme.colors

    val (badgeText, badgeColor) = when (meeting.phase) {
        "completed" -> "Complete" to colors.syncGreen
        "recording" -> "Recording" to colors.accent
        "transcribing" -> "Transcribing" to colors.transcribing
        "failed" -> "Failed" to colors.destructive
        "cancelled" -> "Cancelled" to colors.textTertiary
        else -> meeting.phase to colors.textSecondary
    }

    val duration = if (meeting.startedAt != null && meeting.endedAt != null) {
        val mins = (meeting.endedAt - meeting.startedAt) / 60_000L
        if (mins >= 1) " · ${mins} min" else " · <1 min"
    } else ""

    MuesliSurfaceCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MuesliSpacing.s12),
            modifier = Modifier
                .clickable { onClick() }
                .padding(MuesliSpacing.s16)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(MuesliCorners.small))
                    .background(colors.brandBlueSubtle),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Groups, contentDescription = null, tint = colors.accent)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    meeting.title,
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    SimpleDateFormat("d MMM yyyy 'at' h:mm a", Locale.getDefault()).format(Date(meeting.createdAt)) + duration,
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (meeting.errorMessage != null) {
                    Text(meeting.errorMessage, color = colors.destructive, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(
                badgeText,
                color = badgeColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(MuesliCorners.small))
                    .background(badgeColor.copy(alpha = 0.13f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = colors.textTertiary)
        }
    }
}
