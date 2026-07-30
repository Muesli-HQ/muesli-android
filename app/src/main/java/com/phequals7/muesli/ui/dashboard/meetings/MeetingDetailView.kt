package com.phequals7.muesli.ui.dashboard.meetings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phequals7.muesli.data.SharedStore
import com.phequals7.muesli.data.entity.RecordingSession
import com.phequals7.muesli.meetings.MeetingTemplate
import com.phequals7.muesli.theme.MuesliCorners
import com.phequals7.muesli.theme.MuesliSpacing
import com.phequals7.muesli.theme.MuesliTheme
import com.phequals7.muesli.ui.dashboard.voicenotes.MuesliSurfaceCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Meeting detail — transcript review + manual notes + share/delete,
 * the Android counterpart of the iOS meeting detail (Notes / Raw tabs).
 */
@Composable
fun MeetingDetailView(
    store: SharedStore,
    meeting: RecordingSession,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MuesliTheme.colors

    var transcriptText by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf(meeting.manualNotes) }
    var activeSection by remember {
        mutableStateOf(if (meeting.summaryText.isNotBlank()) MeetingSection.SUMMARY else MeetingSection.TRANSCRIPT)
    }

    LaunchedEffect(meeting.id) {
        launch {
            store.getTranscriptForSessionFlow(meeting.id).collect { transcriptText = it?.text }
        }
    }

    // Debounced notes persistence
    LaunchedEffect(notes) {
        if (notes == meeting.manualNotes) return@LaunchedEffect
        delay(600)
        store.updateRecordingSession(meeting.copy(manualNotes = notes))
    }

    val template = MeetingTemplate.fromId(meeting.templateId)
    val dateLine = buildString {
        append(SimpleDateFormat("d MMM yyyy 'at' h:mm a", Locale.getDefault()).format(Date(meeting.createdAt)))
        if (meeting.startedAt != null && meeting.endedAt != null) {
            val mins = (meeting.endedAt - meeting.startedAt) / 60_000L
            append(if (mins >= 1) " · $mins min" else " · <1 min")
        }
        append(" · ${template.label}")
    }

    fun shareMeeting() {
        val body = buildString {
            append(meeting.title).append("\n").append(dateLine).append("\n\n")
            if (meeting.summaryText.isNotBlank()) append(meeting.summaryText).append("\n\n")
            if (notes.isNotBlank()) append("## Written Notes\n").append(notes).append("\n\n")
            if (!transcriptText.isNullOrBlank()) append("## Transcript\n").append(transcriptText)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, meeting.title)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        context.startActivity(Intent.createChooser(intent, "Share meeting"))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar: back / share / delete
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MuesliSpacing.s8, vertical = MuesliSpacing.s8)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { shareMeeting() }) {
                Icon(Icons.Default.Share, contentDescription = "Share", tint = colors.accent)
            }
            IconButton(onClick = {
                scope.launch {
                    store.deleteRecordingSession(meeting)
                    onDeleted()
                }
            }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = colors.destructive)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = MuesliSpacing.s16, vertical = MuesliSpacing.s8),
            verticalArrangement = Arrangement.spacedBy(MuesliSpacing.s16)
        ) {
            item {
                Column {
                    Text(meeting.title, color = colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(dateLine, color = colors.textSecondary, fontSize = 12.sp)
                    if (meeting.errorMessage != null) {
                        Spacer(modifier = Modifier.height(MuesliSpacing.s8))
                        Text(
                            meeting.errorMessage,
                            color = colors.destructive,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(MuesliCorners.small))
                                .background(colors.destructiveSubtle)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Section picker: Transcript / Notes
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(MuesliCorners.medium))
                        .background(colors.surfacePrimary)
                        .padding(MuesliSpacing.s4),
                    horizontalArrangement = Arrangement.spacedBy(MuesliSpacing.s4)
                ) {
                    MeetingSection.entries.forEach { section ->
                        val selected = section == activeSection
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(MuesliCorners.small))
                                .background(if (selected) colors.accent.copy(alpha = 0.24f) else Color.Transparent)
                                .clickable { activeSection = section }
                                .padding(vertical = MuesliSpacing.s8)
                        ) {
                            Text(
                                section.label,
                                color = if (selected) colors.accent else colors.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            item {
                when (activeSection) {
                    MeetingSection.SUMMARY -> MuesliSurfaceCard {
                        Column(modifier = Modifier.padding(MuesliSpacing.s16)) {
                            when (meeting.summaryState) {
                                "completed", "failed" -> SelectionContainer {
                                    Text(
                                        meeting.summaryText,
                                        color = colors.textPrimary,
                                        fontSize = 14.sp,
                                        lineHeight = 21.sp
                                    )
                                }
                                "generating" -> Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(MuesliSpacing.s12)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.accent)
                                    Text("Generating meeting notes...", color = colors.textSecondary, fontSize = 13.sp)
                                }
                                else -> Text(
                                    "No AI summary for this meeting. Enable Meeting Summaries and sign in from Settings → AI Summaries before your next meeting.",
                                    color = colors.textSecondary,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }

                    MeetingSection.TRANSCRIPT -> MuesliSurfaceCard {
                        Column(modifier = Modifier.padding(MuesliSpacing.s16)) {
                            if (transcriptText.isNullOrBlank()) {
                                Text(
                                    if (meeting.phase == "completed") "No speech was transcribed." else "No transcript yet.",
                                    color = colors.textSecondary,
                                    fontSize = 13.sp
                                )
                            } else {
                                SelectionContainer {
                                    Text(
                                        transcriptText!!,
                                        color = colors.textPrimary,
                                        fontSize = 14.sp,
                                        lineHeight = 21.sp
                                    )
                                }
                            }
                        }
                    }

                    MeetingSection.NOTES -> MuesliSurfaceCard {
                        TextField(
                            value = notes,
                            onValueChange = { notes = it },
                            placeholder = {
                                Text(
                                    "Write notes for this meeting...",
                                    color = colors.textTertiary,
                                    fontSize = 14.sp
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                cursorColor = colors.accent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 200.dp)
                        )
                    }
                }
            }
        }
    }
}

private enum class MeetingSection(val label: String) {
    SUMMARY("Summary"),
    TRANSCRIPT("Transcript"),
    NOTES("Notes"),
}
