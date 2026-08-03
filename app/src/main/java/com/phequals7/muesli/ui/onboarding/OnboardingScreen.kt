package com.phequals7.muesli.ui.onboarding

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phequals7.muesli.data.SharedStore
import com.phequals7.muesli.data.entity.DictationResult
import com.phequals7.muesli.engine.SherpaOnnxEngine
import com.phequals7.muesli.model.ModelManager
import com.phequals7.muesli.model.SpeechModel
import com.phequals7.muesli.model.SpeechModels
import com.phequals7.muesli.summaries.ChatGptAuthManager
import com.phequals7.muesli.theme.MuesliCorners
import com.phequals7.muesli.theme.MuesliSpacing
import com.phequals7.muesli.theme.MuesliTheme
import com.phequals7.muesli.ui.components.MuesliInlineWaveform
import com.phequals7.muesli.ui.components.MuesliWaveformMode
import com.phequals7.muesli.utils.CustomWordMatcher
import com.phequals7.muesli.utils.FillerWordFilter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * iOS-style multi-step onboarding (OnboardingView parity): Profile →
 * Microphone → Keyboard → Speech Model → Try It → AI Summaries, tailored by
 * the use case picked on the first page.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember { SharedStore(context) }

    var useCase by remember { mutableStateOf(OnboardingUseCase.EVERYTHING) }
    val steps = remember(useCase) { OnboardingStep.forUseCase(useCase) }
    var stepIndex by remember { mutableIntStateOf(0) }
    val step = steps[stepIndex]

    var profileName by remember { mutableStateOf("") }

    fun finish() {
        store.userProfileName = profileName.trim()
        store.onboardingUseCase = useCase.id
        store.isOnboardingCompleted = true
        onFinished()
    }

    val onNext: () -> Unit = { if (stepIndex < steps.lastIndex) stepIndex++ else finish() }
    val onBack: (() -> Unit)? = if (stepIndex > 0) ({ stepIndex-- }) else null

    Box(modifier = modifier) {
        when (step) {
            OnboardingStep.PROFILE -> ProfileStep(
                stepIndex = stepIndex, stepCount = steps.size, onBack = onBack,
                name = profileName, onNameChange = { profileName = it },
                useCase = useCase, onUseCaseChange = { useCase = it },
                onNext = onNext,
            )
            OnboardingStep.MICROPHONE -> MicrophoneStep(
                stepIndex = stepIndex, stepCount = steps.size, onBack = onBack, onNext = onNext,
            )
            OnboardingStep.KEYBOARD -> KeyboardStep(
                stepIndex = stepIndex, stepCount = steps.size, onBack = onBack, onNext = onNext,
            )
            OnboardingStep.MODEL -> ModelStep(
                store = store, stepIndex = stepIndex, stepCount = steps.size, onBack = onBack, onNext = onNext,
            )
            OnboardingStep.TEST -> TestDictationStep(
                store = store, stepIndex = stepIndex, stepCount = steps.size, onBack = onBack, onNext = onNext,
            )
            OnboardingStep.SUMMARY -> SummaryStep(
                store = store, stepIndex = stepIndex, stepCount = steps.size, onBack = onBack, onNext = onNext,
            )
        }
    }
}

// ── 1. Profile ──────────────────────────────────────────────────────────

@Composable
private fun ProfileStep(
    stepIndex: Int, stepCount: Int, onBack: (() -> Unit)?,
    name: String, onNameChange: (String) -> Unit,
    useCase: OnboardingUseCase, onUseCaseChange: (OnboardingUseCase) -> Unit,
    onNext: () -> Unit,
) {
    val colors = MuesliTheme.colors
    OnboardingPage(
        step = OnboardingStep.PROFILE, stepIndex = stepIndex, stepCount = stepCount,
        subtitle = "What should Muesli call you, and what are you here for?",
        onBack = onBack,
        primaryLabel = "Continue",
        primaryEnabled = name.isNotBlank(),
        onPrimary = onNext,
    ) {
        OnboardingBrandHeader()
        OnboardingCard {
            Text("Your name", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            TextField(
                value = name,
                onValueChange = onNameChange,
                placeholder = { Text("e.g. Pranav", color = colors.textTertiary) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.surfacePrimary,
                    unfocusedContainerColor = colors.surfacePrimary,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    cursorColor = colors.accent,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                shape = RoundedCornerShape(MuesliCorners.medium),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OnboardingCard {
            Text("I want Muesli for…", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            OnboardingUseCase.entries.forEach { option ->
                OnboardingChoiceRow(
                    title = option.label,
                    subtitle = option.detail,
                    selected = option == useCase,
                    onClick = { onUseCaseChange(option) },
                )
            }
        }
    }
}

// ── 2. Microphone ───────────────────────────────────────────────────────

@Composable
private fun MicrophoneStep(
    stepIndex: Int, stepCount: Int, onBack: (() -> Unit)?, onNext: () -> Unit,
) {
    val context = LocalContext.current
    val colors = MuesliTheme.colors
    var granted by remember {
        mutableStateOf(
            context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    OnboardingPage(
        step = OnboardingStep.MICROPHONE, stepIndex = stepIndex, stepCount = stepCount,
        subtitle = "Muesli transcribes on this phone, so it needs the microphone. Audio never leaves the device for transcription.",
        onBack = onBack,
        primaryLabel = "Continue",
        primaryEnabled = granted,
        onPrimary = onNext,
    ) {
        OnboardingCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MuesliSpacing.s12),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OnboardingStatusTile(done = granted, icon = Icons.Default.Mic)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Microphone access", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (granted) "Granted" else "Required for dictation and meetings",
                        color = if (granted) colors.syncGreen else colors.textSecondary,
                        fontSize = 11.sp,
                    )
                }
                if (!granted) {
                    Button(
                        onClick = { launcher.launch(android.Manifest.permission.RECORD_AUDIO) },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                        shape = RoundedCornerShape(MuesliCorners.medium),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text("Grant", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ── 3. Keyboard ─────────────────────────────────────────────────────────

@Composable
private fun KeyboardStep(
    stepIndex: Int, stepCount: Int, onBack: (() -> Unit)?, onNext: () -> Unit,
) {
    val context = LocalContext.current
    val colors = MuesliTheme.colors

    fun isEnabled(): Boolean {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.any { it.packageName == context.packageName }
    }

    var enabled by remember { mutableStateOf(isEnabled()) }
    LaunchedEffect(Unit) {
        while (!enabled) {
            delay(1_500)
            enabled = isEnabled()
        }
    }

    OnboardingPage(
        step = OnboardingStep.KEYBOARD, stepIndex = stepIndex, stepCount = stepCount,
        subtitle = "The Muesli keyboard lets you dictate into any text field, in any app. Enable it once in system settings.",
        onBack = onBack,
        primaryLabel = "Continue",
        primaryEnabled = enabled,
        onPrimary = onNext,
        skipLabel = "Skip for now",
        onSkip = onNext,
    ) {
        OnboardingCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MuesliSpacing.s12),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OnboardingStatusTile(done = enabled, icon = Icons.Default.Keyboard)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Muesli Dictation Keyboard", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (enabled) "Enabled" else "Settings → Keyboards → Muesli",
                        color = if (enabled) colors.syncGreen else colors.textSecondary,
                        fontSize = 11.sp,
                    )
                }
                if (!enabled) {
                    Button(
                        onClick = { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                        shape = RoundedCornerShape(MuesliCorners.medium),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text("Setup", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ── 4. Speech model ─────────────────────────────────────────────────────

@Composable
private fun ModelStep(
    store: SharedStore,
    stepIndex: Int, stepCount: Int, onBack: (() -> Unit)?, onNext: () -> Unit,
) {
    val context = LocalContext.current
    val colors = MuesliTheme.colors
    val scope = rememberCoroutineScope()

    var selected by remember { mutableStateOf(SpeechModels.byId(store.selectedModelId)) }
    val manager = remember(selected.id) { ModelManager(context.applicationContext, selected) }
    var downloaded by remember(selected.id) { mutableStateOf(manager.isDownloaded()) }
    var downloading by remember(selected.id) { mutableStateOf(false) }
    var progress by remember(selected.id) { mutableStateOf<ModelManager.DownloadProgress?>(null) }
    var error by remember(selected.id) { mutableStateOf<String?>(null) }
    var downloadJob by remember(selected.id) { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    OnboardingPage(
        step = OnboardingStep.MODEL, stepIndex = stepIndex, stepCount = stepCount,
        subtitle = "Pick the on-device model that transcribes your voice. It downloads once and runs fully offline.",
        onBack = onBack,
        primaryLabel = "Continue",
        primaryEnabled = downloaded,
        onPrimary = onNext,
    ) {
        OnboardingCard {
            SpeechModels.all.forEach { model: SpeechModel ->
                OnboardingChoiceRow(
                    title = model.displayName,
                    subtitle = "${model.capabilityLabel} · ${manager.formatBytes(model.totalSizeBytes)}",
                    selected = model.id == selected.id,
                    onClick = {
                        selected = model
                        store.selectedModelId = model.id
                    },
                )
            }
        }

        OnboardingCard {
            Text(
                when {
                    downloaded -> "${selected.shortName} is ready"
                    downloading && progress?.extracting == true -> "Extracting…"
                    downloading && progress != null ->
                        "${(progress!!.fraction * 100).toInt()}% · ${manager.formatBytes(progress!!.totalBytesDone)} of ${manager.formatBytes(progress!!.totalBytes)}"
                    else -> "${selected.shortName} needs a one-time download"
                },
                color = if (downloaded) colors.syncGreen else colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (downloading && progress != null) {
                LinearProgressIndicator(
                    progress = { progress!!.fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = colors.accent,
                    trackColor = colors.surfacePrimary,
                )
            }
            error?.let { Text(it, color = colors.destructive, fontSize = 11.sp) }
            if (!downloaded) {
                if (downloading) {
                    TextButton(onClick = {
                        downloadJob?.cancel()
                        downloading = false
                        progress = null
                    }) {
                        Text("Cancel download", color = colors.textSecondary)
                    }
                } else {
                    Button(
                        onClick = {
                            error = null
                            downloading = true
                            downloadJob = scope.launch {
                                try {
                                    manager.download { p -> progress = p }
                                    downloaded = true
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (t: Throwable) {
                                    error = "Download failed: ${t.message}"
                                } finally {
                                    downloading = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                        shape = RoundedCornerShape(MuesliCorners.medium),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (manager.downloadedBytes() > 0) "Resume download" else "Download ${selected.shortName}",
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}

// ── 5. Try it ───────────────────────────────────────────────────────────

@Composable
private fun TestDictationStep(
    store: SharedStore,
    stepIndex: Int, stepCount: Int, onBack: (() -> Unit)?, onNext: () -> Unit,
) {
    val context = LocalContext.current
    val colors = MuesliTheme.colors
    val scope = rememberCoroutineScope()
    val engine = remember { SherpaOnnxEngine(context.applicationContext) }

    var recording by remember { mutableStateOf(false) }
    var transcribing by remember { mutableStateOf(false) }
    var level by remember { mutableFloatStateOf(0f) }
    var transcript by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { engine.cancel() }
    }

    OnboardingPage(
        step = OnboardingStep.TEST, stepIndex = stepIndex, stepCount = stepCount,
        subtitle = "Say something — watch it become text, entirely on this phone.",
        onBack = onBack,
        primaryLabel = "Looks good",
        primaryEnabled = true,
        onPrimary = onNext,
        skipLabel = "Skip",
        onSkip = onNext,
    ) {
        OnboardingCard {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = MuesliSpacing.s8),
            ) {
                MuesliInlineWaveform(
                    mode = when {
                        transcribing -> MuesliWaveformMode.Waiting
                        recording -> MuesliWaveformMode.Level
                        else -> MuesliWaveformMode.Waiting
                    },
                    color = if (transcribing) colors.transcribing else colors.brandBlue,
                    level = if (recording) level else null,
                    barCount = 32,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                )
            }
            if (transcript.isNotBlank()) {
                Text(transcript, color = colors.textPrimary, fontSize = 14.sp, lineHeight = 20.sp)
            }
            errorText?.let { Text(it, color = colors.destructive, fontSize = 11.sp) }
            Button(
                onClick = {
                    if (recording) {
                        recording = false
                        transcribing = true
                        engine.stopListening()
                    } else {
                        transcript = ""
                        errorText = null
                        engine.setAmplitudeListener { level = it }
                        engine.startListening(
                            onPartialResult = { },
                            onFinalResult = { text ->
                                transcribing = false
                                if (text.isBlank()) {
                                    errorText = "No speech detected — try again."
                                } else {
                                    scope.launch {
                                        var processed = text
                                        if (store.isFillerWordRemovalEnabled) {
                                            processed = FillerWordFilter.apply(processed)
                                        }
                                        if (store.isCustomDictionaryEnabled) {
                                            processed = CustomWordMatcher.apply(processed, store.getCustomWords())
                                        }
                                        transcript = processed
                                        store.insertDictationResult(
                                            DictationResult(
                                                text = processed,
                                                engineIdentifier = SpeechModels.selected(context).id,
                                            )
                                        )
                                    }
                                }
                            },
                            onError = { err ->
                                transcribing = false
                                errorText = err
                            },
                        )
                        recording = true
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (recording) colors.destructive else colors.accent,
                ),
                shape = RoundedCornerShape(MuesliCorners.medium),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    if (recording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(MuesliSpacing.s8))
                Text(
                    when {
                        recording -> "Stop"
                        transcribing -> "Transcribing…"
                        else -> "Start talking"
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (transcript.isNotBlank()) {
            Text(
                "Saved as your first voice note — it will be waiting in Recents.",
                color = colors.syncGreen,
                fontSize = 12.sp,
            )
        }
    }
}

// ── 6. AI summaries ─────────────────────────────────────────────────────

@Composable
private fun SummaryStep(
    store: SharedStore,
    stepIndex: Int, stepCount: Int, onBack: (() -> Unit)?, onNext: () -> Unit,
) {
    val context = LocalContext.current
    val colors = MuesliTheme.colors
    val scope = rememberCoroutineScope()
    val authManager = remember { ChatGptAuthManager(context.applicationContext) }

    var signedIn by remember { mutableStateOf(authManager.isAuthenticated) }
    var signInInProgress by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf(store.openRouterApiKey) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    OnboardingPage(
        step = OnboardingStep.SUMMARY, stepIndex = stepIndex, stepCount = stepCount,
        subtitle = "Optionally connect a provider to turn meeting transcripts into structured notes. You can do this later in Settings.",
        onBack = onBack,
        primaryLabel = if (signedIn || apiKey.isNotBlank()) "Finish" else "Finish without summaries",
        primaryEnabled = !signInInProgress,
        onPrimary = {
            store.openRouterApiKey = apiKey.trim()
            store.summariesEnabled = signedIn || apiKey.isNotBlank()
            onNext()
        },
    ) {
        OnboardingCard {
            Text("ChatGPT (recommended)", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (signedIn) colors.syncGreen else colors.textTertiary),
                )
                Spacer(modifier = Modifier.width(MuesliSpacing.s8))
                Text(
                    if (signedIn) "Signed in" else "Not signed in",
                    color = if (signedIn) colors.syncGreen else colors.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                if (signInInProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.accent)
                }
            }
            if (!signedIn) {
                Button(
                    onClick = {
                        signInInProgress = true
                        statusMessage = null
                        scope.launch {
                            val error = authManager.signIn { intent -> context.startActivity(intent) }
                            signInInProgress = false
                            if (error == null) signedIn = true else statusMessage = error
                        }
                    },
                    enabled = !signInInProgress,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                    shape = RoundedCornerShape(MuesliCorners.medium),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Sign in with ChatGPT", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        OnboardingCard {
            Text("Or bring an OpenRouter key", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            TextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                placeholder = { Text("sk-or-…", color = colors.textTertiary) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.surfacePrimary,
                    unfocusedContainerColor = colors.surfacePrimary,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    cursorColor = colors.accent,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                shape = RoundedCornerShape(MuesliCorners.medium),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        statusMessage?.let { Text(it, color = colors.destructive, fontSize = 11.sp) }
    }
}
