package com.phequals7.muesli.ui.dashboard.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phequals7.muesli.data.SharedStore
import com.phequals7.muesli.data.entity.CustomWord
import com.phequals7.muesli.meetings.MeetingTemplate
import com.phequals7.muesli.model.ModelManager
import com.phequals7.muesli.bubble.BubbleService
import com.phequals7.muesli.model.SpeechModel
import com.phequals7.muesli.model.SpeechModels
import com.phequals7.muesli.engine.SherpaRecognizerHolder
import com.phequals7.muesli.summaries.ChatGptAuthManager
import com.phequals7.muesli.summaries.MeetingSummaryClient
import com.phequals7.muesli.theme.AppearanceController
import com.phequals7.muesli.theme.MuesliAccentTheme
import com.phequals7.muesli.theme.MuesliCorners
import com.phequals7.muesli.theme.MuesliSpacing
import com.phequals7.muesli.theme.MuesliTheme
import com.phequals7.muesli.ui.dashboard.voicenotes.MuesliSurfaceCard
import kotlinx.coroutines.launch

/**
 * Settings hub — port of the muesli-ios SettingsView structure:
 * a grouped list of navigation rows leading to section screens.
 */
@Composable
fun SettingsTab(store: SharedStore) {
    var section by remember { mutableStateOf<SettingsSection?>(null) }

    when (section) {
        null -> SettingsHub(onOpenSection = { section = it })
        SettingsSection.VOICE_NOTES -> SettingsScaffold("Voice Notes", onBack = { section = null }) {
            VoiceNotesSettings(store)
        }
        SettingsSection.MEETINGS -> SettingsScaffold("Meetings", onBack = { section = null }) {
            MeetingsSettings(store)
        }
        SettingsSection.DICTIONARY -> SettingsScaffold("Dictionary", onBack = { section = null }) {
            DictionarySettings(store)
        }
        SettingsSection.MODELS -> SettingsScaffold("Models", onBack = { section = null }) {
            ModelsSettings(store)
        }
        SettingsSection.AI_SUMMARIES -> SettingsScaffold("AI Summaries", onBack = { section = null }) {
            AiSummariesSettings(store)
        }
        SettingsSection.APPEARANCE -> SettingsScaffold("Appearance", onBack = { section = null }) {
            AppearanceSettings()
        }
        SettingsSection.ABOUT -> SettingsScaffold("About", onBack = { section = null }) {
            AboutSettings(store)
        }
    }
}

private enum class SettingsSection(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
) {
    VOICE_NOTES("Voice Notes", "Speech engine, mic readiness, and keyboard setup.", Icons.Default.Keyboard),
    MEETINGS("Meetings", "Default template and audio retention.", Icons.Default.Groups),
    DICTIONARY("Dictionary", "Filler word removal, custom phrases, names, and acronyms.", Icons.Default.Book),
    MODELS("Models", "Choose and download on-device Parakeet models.", Icons.Default.Memory),
    AI_SUMMARIES("AI Summaries", "Meeting summary providers, auth, and model selection.", Icons.Default.AutoAwesome),
    APPEARANCE("Appearance", "Light and dark mode, and app accent.", Icons.Default.Palette),
    ABOUT("About", "Version, profile, and open-source acknowledgements.", Icons.Default.Info),
}

// ───────────────────────────── hub ─────────────────────────────

@Composable
private fun SettingsHub(onOpenSection: (SettingsSection) -> Unit) {
    val colors = MuesliTheme.colors

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(MuesliSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(MuesliSpacing.s16)
    ) {
        item {
            Column {
                Text("Settings", color = colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                Text("Voice notes, meetings, local models, and privacy.", color = colors.textSecondary, fontSize = 13.sp)
            }
        }

        item {
            MuesliSurfaceCard {
                Column {
                    SettingsSection.entries.forEachIndexed { index, section ->
                        SettingsHubRow(
                            section = section,
                            onClick = { onOpenSection(section) }
                        )
                        if (index < SettingsSection.entries.lastIndex) {
                            HorizontalDivider(
                                color = colors.surfaceBorder,
                                modifier = Modifier.padding(start = 64.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsHubRow(
    section: SettingsSection,
    onClick: () -> Unit,
) {
    val colors = MuesliTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MuesliSpacing.s12),
        modifier = Modifier
            .fillMaxWidth()
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
            Icon(section.icon, contentDescription = null, tint = colors.accent)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(section.title, color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(section.subtitle, color = colors.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = colors.textTertiary)
    }
}

@Composable
private fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = MuesliTheme.colors
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MuesliSpacing.s8, vertical = MuesliSpacing.s8)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
            }
            Text(title, color = colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = MuesliSpacing.s16, vertical = MuesliSpacing.s8),
            verticalArrangement = Arrangement.spacedBy(MuesliSpacing.s16)
        ) {
            item { content() }
        }
    }
}

// ─────────────────────── shared row components ───────────────────────

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = MuesliTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = colors.textPrimary, fontSize = 14.sp)
            Text(subtitle, color = colors.textSecondary, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    accentWhenSelected: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = MuesliTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MuesliCorners.small))
            .clickable { onClick() }
            .padding(vertical = MuesliSpacing.s8)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = if (selected && accentWhenSelected) colors.accent else colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(subtitle, color = colors.textSecondary, fontSize = 11.sp)
        }
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    MuesliSurfaceCard {
        Column(
            modifier = Modifier.padding(MuesliSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(MuesliSpacing.s12),
            content = { content() }
        )
    }
}

// ─────────────────────── Voice Notes ───────────────────────

@Composable
private fun VoiceNotesSettings(store: SharedStore) {
    val context = LocalContext.current
    val colors = MuesliTheme.colors
    var engineType by remember { mutableStateOf(store.engineType) }
    var keepMicReady by remember { mutableStateOf(store.keepMicReady) }
    var bubbleEnabled by remember { mutableStateOf(store.bubbleEnabled) }

    Column(verticalArrangement = Arrangement.spacedBy(MuesliSpacing.s16)) {
        SettingsCard {
            Text("Speech Engine", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            ChoiceRow(
                title = "On-Device (Parakeet)",
                subtitle = "Sherpa-ONNX, fully local & private",
                selected = engineType == "sherpa",
                onClick = { engineType = "sherpa"; store.engineType = "sherpa" }
            )
            ChoiceRow(
                title = "System Speech Recognizer",
                subtitle = "Uses the Android SDK recognizer",
                selected = engineType == "system",
                onClick = { engineType = "system"; store.engineType = "system" }
            )
            ChoiceRow(
                title = "Mock Engine (Offline Sandbox)",
                subtitle = "Simulates recognition for testing",
                selected = engineType == "mock",
                onClick = { engineType = "mock"; store.engineType = "mock" }
            )
        }

        SettingsCard {
            Text("Keyboard", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            ToggleRow(
                title = "Keep mic ready",
                subtitle = "Muesli Keyboard starts listening as soon as it opens.",
                checked = keepMicReady,
                onCheckedChange = { keepMicReady = it; store.keepMicReady = it }
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MuesliCorners.small))
                    .clickable { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
                    .padding(vertical = MuesliSpacing.s8)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Keyboard setup", color = colors.textPrimary, fontSize = 14.sp)
                    Text("Enable or switch to the Muesli keyboard.", color = colors.textSecondary, fontSize = 11.sp)
                }
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = colors.textTertiary)
            }
        }

        SettingsCard {
            Text("Quick Capture", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            ToggleRow(
                title = "Floating mic bubble",
                subtitle = "A draggable bubble floats over other apps. Tap it to dictate — the note is saved and copied to your clipboard.",
                checked = bubbleEnabled && Settings.canDrawOverlays(context),
                onCheckedChange = { enabled ->
                    if (enabled && !Settings.canDrawOverlays(context)) {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            )
                        )
                    } else {
                        bubbleEnabled = enabled
                        store.bubbleEnabled = enabled
                        val intent = Intent(context, BubbleService::class.java)
                        if (enabled) context.startService(intent) else context.stopService(intent)
                    }
                }
            )
        }
    }
}

// ─────────────────────── Meetings ───────────────────────

@Composable
private fun MeetingsSettings(store: SharedStore) {
    val colors = MuesliTheme.colors
    var defaultTemplate by remember { mutableStateOf(MeetingTemplate.fromId(store.defaultMeetingTemplate)) }
    var retainAudio by remember { mutableStateOf(store.retainMeetingAudio) }
    var speakerLabels by remember { mutableStateOf(store.speakerLabelsEnabled) }

    Column(verticalArrangement = Arrangement.spacedBy(MuesliSpacing.s16)) {
        SettingsCard {
            Text("Default Template", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            MeetingTemplate.entries.forEach { template ->
                ChoiceRow(
                    title = template.label,
                    subtitle = template.detail,
                    selected = template == defaultTemplate,
                    onClick = {
                        defaultTemplate = template
                        store.defaultMeetingTemplate = template.id
                    }
                )
            }
        }

        SettingsCard {
            Text("Recording", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            ToggleRow(
                title = "Speaker labels",
                subtitle = "Diarizes the meeting on-device after you stop and groups the transcript by speaker. Adds a short processing step.",
                checked = speakerLabels,
                onCheckedChange = { speakerLabels = it; store.speakerLabelsEnabled = it }
            )
            ToggleRow(
                title = "Retain meeting audio",
                subtitle = "Keeps the WAV file on device for future playback and speaker separation.",
                checked = retainAudio,
                onCheckedChange = { retainAudio = it; store.retainMeetingAudio = it }
            )
        }
    }
}

// ─────────────────────── Dictionary ───────────────────────

@Composable
private fun DictionarySettings(store: SharedStore) {
    val colors = MuesliTheme.colors
    val scope = rememberCoroutineScope()

    var fillerEnabled by remember { mutableStateOf(store.isFillerWordRemovalEnabled) }
    var customDictEnabled by remember { mutableStateOf(store.isCustomDictionaryEnabled) }
    var customWords by remember { mutableStateOf(emptyList<CustomWord>()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var entryWord by remember { mutableStateOf("") }
    var entryReplacement by remember { mutableStateOf("") }
    var entryThreshold by remember { mutableStateOf(0.85f) }

    LaunchedEffect(Unit) {
        store.getCustomWordsFlow().collect { customWords = it }
    }

    Column(verticalArrangement = Arrangement.spacedBy(MuesliSpacing.s16)) {
        SettingsCard {
            Text("Text Processing", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            ToggleRow(
                title = "Filter filler words",
                subtitle = "Removes 'uh', 'um', 'you know', etc.",
                checked = fillerEnabled,
                onCheckedChange = { fillerEnabled = it; store.isFillerWordRemovalEnabled = it }
            )
            ToggleRow(
                title = "Custom dictionary",
                subtitle = "Replace matched words on the fly.",
                checked = customDictEnabled,
                onCheckedChange = { customDictEnabled = it; store.isCustomDictionaryEnabled = it }
            )
        }

        SettingsCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Custom Words", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                    shape = RoundedCornerShape(MuesliCorners.medium),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add", fontSize = 12.sp)
                }
            }

            if (customWords.isEmpty()) {
                Text(
                    "No custom words yet. Add terms to match fuzzy inputs using Jaro-Winkler similarity.",
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            } else {
                customWords.forEach { word ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(word.word, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            if (word.replacement != null) {
                                Text("→ ${word.replacement}", color = colors.accent, fontSize = 12.sp)
                            }
                            Text(
                                "Threshold: ${"%.2f".format(word.matchingThreshold)}",
                                color = colors.textSecondary,
                                fontSize = 10.sp
                            )
                        }
                        IconButton(onClick = { scope.launch { store.deleteCustomWordById(word.id) } }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = colors.destructive.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = colors.backgroundRaised,
            title = { Text("Add Custom Word", color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextField(value = entryWord, onValueChange = { entryWord = it }, label = { Text("Word/Phrase") })
                    TextField(value = entryReplacement, onValueChange = { entryReplacement = it }, label = { Text("Replacement (Optional)") })
                    Column {
                        Text("Matching Threshold: ${"%.2f".format(entryThreshold)}", color = colors.textSecondary, fontSize = 12.sp)
                        Slider(value = entryThreshold, onValueChange = { entryThreshold = it }, valueRange = 0.5f..1.0f)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (entryWord.isNotBlank()) {
                            scope.launch {
                                store.insertCustomWord(
                                    CustomWord(
                                        word = entryWord,
                                        replacement = entryReplacement.ifBlank { null },
                                        matchingThreshold = entryThreshold.toDouble()
                                    )
                                )
                            }
                            showAddDialog = false
                            entryWord = ""
                            entryReplacement = ""
                            entryThreshold = 0.85f
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel", color = colors.textSecondary) }
            }
        )
    }
}

// ─────────────────────── Models ───────────────────────

@Composable
private fun ModelsSettings(store: SharedStore) {
    val colors = MuesliTheme.colors
    var selectedId by remember { mutableStateOf(SpeechModels.byId(store.selectedModelId).id) }

    Column(verticalArrangement = Arrangement.spacedBy(MuesliSpacing.s16)) {
        SpeechModels.all.forEach { model ->
            SpeechModelCard(
                model = model,
                selected = model.id == selectedId,
                onSelect = {
                    selectedId = model.id
                    store.selectedModelId = model.id
                    SherpaRecognizerHolder.reset()
                },
            )
        }
        SettingsCard {
            Text("About on-device models", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                "All models run fully on-device via sherpa-onnx — audio never leaves this phone. The selected model is used for voice notes, the keyboard, and meetings.",
                color = colors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}

// ─────────────────────── AI Summaries ───────────────────────

@Composable
private fun AiSummariesSettings(store: SharedStore) {
    val context = LocalContext.current
    val colors = MuesliTheme.colors
    val scope = rememberCoroutineScope()
    val authManager = remember { ChatGptAuthManager(context.applicationContext) }

    var summariesEnabled by remember { mutableStateOf(store.summariesEnabled) }
    var backend by remember { mutableStateOf(store.summaryBackend) }
    var chatGptModel by remember { mutableStateOf(store.chatGptModel) }
    var openRouterModel by remember { mutableStateOf(store.openRouterModel) }
    var apiKeyInput by remember { mutableStateOf(store.openRouterApiKey) }
    var isSignedIn by remember { mutableStateOf(authManager.isAuthenticated) }
    var signInInProgress by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(MuesliSpacing.s16)) {
        SettingsCard {
            ToggleRow(
                title = "Meeting Summaries",
                subtitle = "Structured notes after local transcription. Transcript text is sent to the selected provider only when this is on.",
                checked = summariesEnabled,
                onCheckedChange = {
                    summariesEnabled = it
                    store.summariesEnabled = it
                }
            )
        }

        SettingsCard {
            Text("Summary Backend", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            ChoiceRow(
                title = "ChatGPT",
                subtitle = "Sign in with your ChatGPT account (primary)",
                selected = backend == "chatgpt",
                onClick = { backend = "chatgpt"; store.summaryBackend = "chatgpt" }
            )
            ChoiceRow(
                title = "OpenRouter",
                subtitle = "Bring your own API key",
                selected = backend == "openrouter",
                onClick = { backend = "openrouter"; store.summaryBackend = "openrouter" }
            )
        }

        if (backend == "chatgpt") {
            SettingsCard {
                Text("ChatGPT Account", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isSignedIn) colors.syncGreen else colors.textTertiary)
                    )
                    Spacer(modifier = Modifier.width(MuesliSpacing.s8))
                    Text(
                        if (isSignedIn) "Signed in" else "Not signed in",
                        color = if (isSignedIn) colors.syncGreen else colors.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (signInInProgress) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.accent)
                    }
                }

                if (!isSignedIn) {
                    Button(
                        onClick = {
                            signInInProgress = true
                            statusMessage = null
                            scope.launch {
                                val error = authManager.signIn { intent -> context.startActivity(intent) }
                                signInInProgress = false
                                if (error == null) {
                                    isSignedIn = true
                                    statusMessage = null
                                } else {
                                    statusMessage = error
                                }
                            }
                        },
                        enabled = !signInInProgress,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                        shape = RoundedCornerShape(MuesliCorners.medium),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Sign in with ChatGPT", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "Opens auth.openai.com in your browser. Times out after 5 minutes. Tokens stay on this device.",
                        color = colors.textSecondary,
                        fontSize = 11.sp
                    )
                } else {
                    TextButton(onClick = {
                        authManager.signOut()
                        isSignedIn = false
                    }) {
                        Text("Sign Out", color = colors.destructive)
                    }
                }

                statusMessage?.let {
                    Text(it, color = colors.destructive, fontSize = 11.sp)
                }
            }

            SettingsCard {
                Text("Summary Model", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                MeetingSummaryClient.CHATGPT_MODEL_PRESETS.forEach { preset ->
                    ChoiceRow(
                        title = preset,
                        subtitle = if (preset == "gpt-5.5") "Default" else "Faster, cheaper",
                        selected = chatGptModel == preset,
                        onClick = { chatGptModel = preset; store.chatGptModel = preset }
                    )
                }
            }
        } else {
            SettingsCard {
                Text("OpenRouter API Key", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = {
                        apiKeyInput = it
                        store.openRouterApiKey = it.trim()
                    },
                    placeholder = { Text("sk-or-...", color = colors.textTertiary) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (store.openRouterApiKey.isNotBlank()) "Key saved on this device." else "Get a key at openrouter.ai/keys",
                    color = colors.textSecondary,
                    fontSize = 11.sp
                )
            }

            SettingsCard {
                Text("Summary Model", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                MeetingSummaryClient.OPENROUTER_MODEL_PRESETS.forEach { preset ->
                    ChoiceRow(
                        title = preset.substringAfter('/').substringBefore(':'),
                        subtitle = preset,
                        selected = openRouterModel == preset,
                        onClick = { openRouterModel = preset; store.openRouterModel = preset }
                    )
                }
            }
        }
    }
}

// ─────────────────────── Appearance ───────────────────────

@Composable
private fun AppearanceSettings() {
    val context = LocalContext.current
    val colors = MuesliTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(MuesliSpacing.s16)) {
        SettingsCard {
            Text("Theme", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (mode, label) ->
                ChoiceRow(
                    title = label,
                    subtitle = when (mode) {
                        "system" -> "Follow the phone's dark mode setting"
                        "light" -> "Always use the light appearance"
                        else -> "Always use the dark appearance"
                    },
                    selected = AppearanceController.appearanceMode == mode,
                    onClick = { AppearanceController.setAppearanceMode(context, mode) }
                )
            }
        }

        SettingsCard {
            Text("Accent", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            MuesliAccentTheme.entries.forEach { theme ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MuesliSpacing.s12),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(MuesliCorners.small))
                        .clickable { AppearanceController.setAccentTheme(context, theme.id) }
                        .padding(vertical = MuesliSpacing.s8)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(theme.color(dark = true))
                    )
                    Text(
                        theme.label,
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = if (AppearanceController.accentThemeId == theme.id) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    if (AppearanceController.accentThemeId == theme.id) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ─────────────────────── About ───────────────────────

@Composable
private fun AboutSettings(store: SharedStore) {
    val colors = MuesliTheme.colors
    val context = LocalContext.current
    var profileName by remember { mutableStateOf(store.userProfileName) }

    Column(verticalArrangement = Arrangement.spacedBy(MuesliSpacing.s16)) {
        SettingsCard {
            Text("Profile", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            TextField(
                value = profileName,
                onValueChange = {
                    profileName = it
                    store.userProfileName = it
                },
                label = { Text("Your name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        SettingsCard {
            Text("Muesli for Android", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Version", color = colors.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                Text("${info.versionName} (${info.longVersionCode})", color = colors.textPrimary, fontSize = 13.sp)
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Speech runtime", color = colors.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("sherpa-onnx 1.13.4", color = colors.textPrimary, fontSize = 13.sp)
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Model", color = colors.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(SpeechModels.selected(context).displayName, color = colors.textPrimary, fontSize = 13.sp)
            }
        }

        SettingsCard {
            Text("Acknowledgements", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                "sherpa-onnx (k2-fsa, Apache-2.0) · ONNX Runtime (Microsoft, MIT) · Parakeet models (NVIDIA NeMo) · Silero VAD. Companion to Muesli for iOS and macOS.",
                color = colors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}

// ─────────────────────── shared cards ───────────────────────

/**
 * Model management card for the sherpa-onnx Parakeet V3 runtime: download
 * (resumable, with progress), status display, and deletion.
 */
@Composable
internal fun SpeechModelCard(
    model: SpeechModel,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val colors = MuesliTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val modelManager = remember(model.id) { ModelManager(context.applicationContext, model) }

    var isDownloaded by remember(model.id) { mutableStateOf(modelManager.isDownloaded()) }
    var isDownloading by remember(model.id) { mutableStateOf(false) }
    var progress by remember(model.id) { mutableStateOf<ModelManager.DownloadProgress?>(null) }
    var statusMessage by remember(model.id) { mutableStateOf<String?>(null) }
    var downloadJob by remember(model.id) { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    MuesliSurfaceCard {
        Column(
            modifier = Modifier.padding(MuesliSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(MuesliSpacing.s12)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MuesliSpacing.s12),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(MuesliCorners.small))
                        .background(
                            when {
                                selected -> colors.accentSubtle
                                isDownloaded -> colors.syncGreenSubtle
                                else -> colors.brandBlueSubtle
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            selected -> Icons.Default.CheckCircle
                            isDownloaded -> Icons.Default.CheckCircle
                            else -> Icons.Default.CloudDownload
                        },
                        contentDescription = null,
                        tint = when {
                            selected -> colors.accent
                            isDownloaded -> colors.syncGreen
                            else -> colors.accent
                        }
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(model.displayName, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(model.capabilityLabel, color = colors.accent, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    Text(
                        text = when {
                            isDownloading && progress?.extracting == true -> "Extracting ${progress!!.currentFile}…"
                            isDownloading && progress != null ->
                                "Downloading · ${modelManager.formatBytes(progress!!.totalBytesDone)} / ${modelManager.formatBytes(progress!!.totalBytes)}"
                            isDownloaded -> "Downloaded · ${modelManager.formatBytes(modelManager.downloadedBytes())} on disk"
                            modelManager.downloadedBytes() > 0 ->
                                "Partially downloaded · ${modelManager.formatBytes(modelManager.downloadedBytes())} / ${modelManager.formatBytes(model.totalSizeBytes)}"
                            else -> "Not downloaded · ${modelManager.formatBytes(model.totalSizeBytes)} required"
                        },
                        color = colors.textSecondary,
                        fontSize = 10.sp
                    )
                }
            }

            Text(model.detail, color = colors.textSecondary, fontSize = 11.sp, lineHeight = 15.sp)

            if (isDownloading && progress != null) {
                LinearProgressIndicator(
                    progress = { progress!!.fraction },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(MuesliCorners.small)),
                    color = colors.accent,
                    trackColor = colors.surfacePrimary,
                )
            }

            statusMessage?.let {
                Text(it, color = colors.destructive, fontSize = 11.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(MuesliSpacing.s8)) {
                if (isDownloading) {
                    TextButton(onClick = {
                        downloadJob?.cancel()
                        downloadJob = null
                        isDownloading = false
                        progress = null
                    }) {
                        Text("Cancel", color = colors.textSecondary)
                    }
                } else if (!isDownloaded) {
                    Button(
                        onClick = {
                            statusMessage = null
                            isDownloading = true
                            downloadJob = scope.launch {
                                try {
                                    modelManager.download { p -> progress = p }
                                    isDownloaded = true
                                    statusMessage = null
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (t: Throwable) {
                                    statusMessage = "Download failed: ${t.message}"
                                } finally {
                                    isDownloading = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                        shape = RoundedCornerShape(MuesliCorners.medium)
                    ) {
                        Text(
                            if (modelManager.downloadedBytes() > 0) "Resume Download" else "Download",
                            fontSize = 13.sp
                        )
                    }
                } else {
                    if (!selected) {
                        Button(
                            onClick = onSelect,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                            shape = RoundedCornerShape(MuesliCorners.medium)
                        ) {
                            Text("Use This Model", fontSize = 13.sp)
                        }
                    } else {
                        Text("Active", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = MuesliSpacing.s8, vertical = MuesliSpacing.s8))
                    }
                    TextButton(onClick = {
                        modelManager.deleteModel()
                        isDownloaded = false
                    }) {
                        Text("Delete", color = colors.destructive)
                    }
                }
            }
        }
    }
}
