package com.phequals7.muesli.bubble

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.phequals7.muesli.MainActivity
import com.phequals7.muesli.R
import com.phequals7.muesli.data.SharedStore
import com.phequals7.muesli.data.entity.DictationResult
import com.phequals7.muesli.engine.MockTranscriptionEngine
import com.phequals7.muesli.engine.SherpaOnnxEngine
import com.phequals7.muesli.engine.SystemSpeechRecognizerEngine
import com.phequals7.muesli.engine.TranscriptionEngine
import com.phequals7.muesli.model.ModelManager
import com.phequals7.muesli.model.SpeechModels
import com.phequals7.muesli.theme.AppearanceController
import com.phequals7.muesli.utils.CustomWordMatcher
import com.phequals7.muesli.utils.FillerWordFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Floating quick-capture bubble — the Android-exclusive counterpart of
 * nothing on iOS (overlays are impossible there). A draggable Muesli-blue
 * bubble floats over other apps; tapping it expands a compact dictation
 * card (waveform + elapsed + stop/discard), and the finished transcript is
 * post-processed, saved as a voice note, and copied to the clipboard.
 *
 * Runs as a foreground service with the microphone type so dictation can
 * start later from the overlay while the app is in the background. The
 * service must be (re)started from a foreground context (Settings toggle)
 * for mic access to stick.
 */
class BubbleService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        private const val TAG = "BubbleService"
        const val ACTION_SHOW = "com.phequals7.muesli.bubble.SHOW"
        const val ACTION_START_DICTATION = "com.phequals7.muesli.bubble.START_DICTATION"
        const val ACTION_HIDE = "com.phequals7.muesli.bubble.HIDE"
        private const val CHANNEL_ID = "quick_capture"
        private const val NOTIFICATION_ID = 77
    }

    private val lifecycleRegistry by lazy { LifecycleRegistry(this) }
    private val savedStateRegistryController by lazy { SavedStateRegistryController.create(this) }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var store: SharedStore
    private lateinit var wm: WindowManager

    private var overlayView: DraggableOverlayLayout? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private val uiState = BubbleUiState()

    private var engine: TranscriptionEngine? = null
    private var recordingStartedAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        // The overlay ComposeView's window recomposer only runs while the
        // lifecycle is RESUMED — without this the bubble draws once but never
        // recomposes (tap → card stays invisible).
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        store = SharedStore(applicationContext)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        AppearanceController.ensureInitialized(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> {
                hideBubble()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_DICTATION -> {
                showBubble()
                expandCard(startDictation = true)
            }
            else -> showBubble()
        }
        return START_STICKY
    }

    // ── overlay ──────────────────────────────────────────────────────────

    private fun showBubble() {
        if (overlayView != null) return
        if (!Settings.canDrawOverlays(this)) {
            toast("Overlay permission is required — enable it in Muesli Settings")
            stopSelf()
            return
        }
        startForegroundWithNotification()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            x = 24
            y = 420
        }

        val view = DraggableOverlayLayout(this).apply {
            onTap = {
                Log.i(TAG, "Bubble tapped, expanded=${uiState.expanded.value}")
                if (uiState.expanded.value) Unit else expandCard(startDictation = true)
            }
            onDrag = { dx, dy ->
                params.x += dx
                params.y += dy
                try { wm.updateViewLayout(this, params) } catch (_: Throwable) {}
            }
            // Newer Compose resolves the window recomposer from the overlay
            // window's root view, so the owners must live here too (same fix
            // as the IME decor view) or setContent crashes.
            setViewTreeLifecycleOwner(this@BubbleService)
            setViewTreeSavedStateRegistryOwner(this@BubbleService)
            val compose = ComposeView(context).apply {
                setViewTreeLifecycleOwner(this@BubbleService)
                setViewTreeSavedStateRegistryOwner(this@BubbleService)
                setContent {
                    com.phequals7.muesli.theme.MuesliTheme {
                        BubbleOverlay(
                            state = uiState,
                            onStop = { stopDictation() },
                            onDiscard = { discardDictation() },
                            onCollapse = { collapseCard() },
                            onDismissService = { hideBubble(); store.bubbleEnabled = false; stopSelf() },
                        )
                    }
                }
            }
            addView(compose)
        }

        overlayView = view
        overlayParams = params
        try {
            wm.addView(view, params)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to add overlay", t)
            stopSelf()
        }
    }

    private fun hideBubble() {
        engine?.cancel()
        engine = null
        overlayView?.let {
            try { wm.removeView(it) } catch (_: Throwable) {}
        }
        overlayView = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun expandCard(startDictation: Boolean) {
        uiState.expanded.value = true
        // Overlay windows don't reliably re-measure WRAP_CONTENT on Compose
        // recomposition alone — force a relayout so the card becomes visible.
        requestRelayout()
        if (startDictation) startDictation()
    }

    private fun collapseCard() {
        engine?.cancel()
        engine = null
        uiState.reset()
        requestRelayout()
    }

    private fun requestRelayout() {
        val view = overlayView ?: return
        val params = overlayParams ?: return
        view.post {
            try { wm.updateViewLayout(view, params) } catch (_: Throwable) {}
        }
    }

    // ── dictation ────────────────────────────────────────────────────────

    private fun startDictation() {
        if (uiState.state.value == BubbleState.RECORDING) return
        Log.i(TAG, "startDictation: model downloaded=${ModelManager(this).isDownloaded()}")
        if (!ModelManager(this).isDownloaded()) {
            toast("${SpeechModels.selected(this).shortName} model is not downloaded — open Muesli Settings")
            collapseCard()
            return
        }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            toast("Microphone permission is required")
            collapseCard()
            return
        }

        val newEngine = when (store.engineType) {
            "system" -> SystemSpeechRecognizerEngine(this)
            "sherpa" -> SherpaOnnxEngine(this)
            else -> MockTranscriptionEngine()
        }.also { engine = it }
        newEngine.setAmplitudeListener { level -> uiState.inputLevel.value = level }

        uiState.state.value = BubbleState.RECORDING
        uiState.partialText.value = ""
        uiState.inputLevel.value = 0f
        recordingStartedAt = System.currentTimeMillis()
        tickElapsed()

        newEngine.startListening(
            onPartialResult = { uiState.partialText.value = it },
            onFinalResult = { text -> finishDictation(text) },
            onError = { err ->
                Log.e(TAG, "Dictation error: $err")
                uiState.state.value = BubbleState.ERROR
                toast(err)
                serviceScope.launch {
                    delay(1_500)
                    collapseCard()
                }
            },
        )
    }

    private fun stopDictation() {
        if (uiState.state.value != BubbleState.RECORDING) return
        uiState.state.value = BubbleState.TRANSCRIBING
        uiState.inputLevel.value = 0f
        engine?.stopListening()
    }

    private fun discardDictation() {
        collapseCard()
    }

    private fun finishDictation(rawText: String) {
        serviceScope.launch {
            if (rawText.isBlank()) {
                toast("No speech detected")
                collapseCard()
                return@launch
            }
            var processed = rawText
            if (store.isFillerWordRemovalEnabled) processed = FillerWordFilter.apply(processed)
            if (store.isCustomDictionaryEnabled) {
                processed = CustomWordMatcher.apply(processed, store.getCustomWords())
            }
            store.insertDictationResult(
                DictationResult(
                    text = processed,
                    engineIdentifier = SpeechModels.selected(applicationContext).id,
                    durationMs = System.currentTimeMillis() - recordingStartedAt,
                )
            )
            copyToClipboard(processed)
            uiState.state.value = BubbleState.SAVED
            delay(1_200)
            collapseCard()
        }
    }

    private fun tickElapsed() {
        serviceScope.launch {
            while (uiState.state.value == BubbleState.RECORDING) {
                uiState.elapsedSeconds.value =
                    ((System.currentTimeMillis() - recordingStartedAt) / 1000L).toInt()
                delay(250)
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Muesli voice note", text))
    }

    private fun toast(message: String) {
        android.os.Handler(mainLooper).post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    // ── lifecycle / notification ─────────────────────────────────────────

    override fun onDestroy() {
        hideBubble()
        serviceScope.cancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Quick Capture",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shown while the Muesli floating bubble is active" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startForegroundWithNotification() {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val hideIntent = PendingIntent.getService(
            this, 1,
            Intent(this, BubbleService::class.java).setAction(ACTION_HIDE),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.muesli_app_icon)
            .setContentTitle("Muesli quick capture")
            .setContentText("Floating bubble is active")
            .setContentIntent(openApp)
            .setOngoing(true)
            .addAction(0, "Hide bubble", hideIntent)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}

/**
 * FrameLayout that distinguishes taps from drags: movement past the touch
 * slop is intercepted and reported as drag deltas; otherwise the event
 * flows to the Compose child as a normal click.
 */
private class DraggableOverlayLayout(context: Context) : FrameLayout(context) {

    var onTap: () -> Unit = {}
    var onDrag: (Int, Int) -> Unit = { _, _ -> }

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var dragging = false

    /**
     * Single choke point for tap-vs-drag. dispatchTouchEvent sees every event
     * of the gesture even when the Compose child consumes the stream (unlike
     * onInterceptTouchEvent, which stops being consulted once the child takes
     * the gesture — the bug that made taps dead).
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = ev.rawX; downRawY = ev.rawY
                lastRawX = ev.rawX; lastRawY = ev.rawY
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging &&
                    (kotlin.math.abs(ev.rawX - downRawX) > slop ||
                        kotlin.math.abs(ev.rawY - downRawY) > slop)
                ) {
                    dragging = true
                }
                if (dragging) {
                    onDrag((ev.rawX - lastRawX).toInt(), (ev.rawY - lastRawY).toInt())
                    lastRawX = ev.rawX; lastRawY = ev.rawY
                    return true // swallow so the card doesn't click through drags
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) onTap()
                dragging = false
            }
            MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return super.dispatchTouchEvent(ev)
    }
}
