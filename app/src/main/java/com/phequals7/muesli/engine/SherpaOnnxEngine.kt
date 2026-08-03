package com.phequals7.muesli.engine

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.phequals7.muesli.audio.AudioInputRouteManager
import com.phequals7.muesli.data.SharedStore
import com.phequals7.muesli.model.ModelManager
import com.phequals7.muesli.model.SpeechModelKind
import com.phequals7.muesli.model.SpeechModels
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Holds the loaded recognizer for the currently selected speech model.
 * Loading a large int8 model takes a few seconds, so the instance is shared
 * and reused across dictations within the process. Switching models in
 * Settings must go through [reset] to drop the old recognizer.
 */
object SherpaRecognizerHolder {
    private const val TAG = "SherpaRecognizer"

    enum class WarmState { IDLE, WARMING, READY, FAILED }

    @Volatile
    var warmState = WarmState.IDLE
        private set

    private val prewarmExecutor = Executors.newSingleThreadExecutor()

    @Volatile
    private var recognizer: OfflineRecognizer? = null

    @Volatile
    private var loadedModelId: String? = null

    /**
     * Background model prewarm for the launch warmup screen (iOS
     * prewarmModelIfNeeded(reason: "launch_screen") parity). Idempotent.
     */
    fun prewarmAsync(context: Context) {
        if (warmState != WarmState.IDLE) return
        warmState = WarmState.WARMING
        prewarmExecutor.execute {
            try {
                get(context.applicationContext)
                warmState = WarmState.READY
            } catch (t: Throwable) {
                Log.w(TAG, "Prewarm failed: ${t.message}")
                warmState = WarmState.FAILED
            }
        }
    }

    /** Releases the loaded recognizer so the next [get] reloads the selected
     * model (called when the user switches models in Settings). */
    @Synchronized
    fun reset() {
        recognizer?.release()
        recognizer = null
        loadedModelId = null
        warmState = WarmState.IDLE
    }

    @Synchronized
    fun get(context: Context): OfflineRecognizer {
        val models = ModelManager(context.applicationContext)
        recognizer?.let {
            if (loadedModelId == models.model.id) return it
            // Selected model changed since the recognizer was loaded.
            it.release()
            recognizer = null
            loadedModelId = null
        }

        check(models.isDownloaded()) { "${models.model.shortName} model is not downloaded" }

        val modelConfig = when (models.model.kind) {
            SpeechModelKind.NEMO_TRANSDUCER -> OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = models.modelFile("encoder.int8.onnx").absolutePath,
                    decoder = models.modelFile("decoder.int8.onnx").absolutePath,
                    joiner = models.modelFile("joiner.int8.onnx").absolutePath,
                ),
                tokens = models.modelFile("tokens.txt").absolutePath,
                modelType = "nemo_transducer",
                numThreads = 4,
                provider = "cpu",
                debug = false,
            )
            SpeechModelKind.NEMO_CTC -> OfflineModelConfig(
                nemo = OfflineNemoEncDecCtcModelConfig(
                    model = models.modelFile("model.int8.onnx").absolutePath,
                ),
                tokens = models.modelFile("tokens.txt").absolutePath,
                modelType = "nemo_ctc",
                numThreads = 4,
                provider = "cpu",
                debug = false,
            )
        }
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = modelConfig,
            decodingMethod = "greedy_search",
        )

        Log.i(TAG, "Loading ${models.model.displayName} from ${models.modelDir}")
        val start = System.currentTimeMillis()
        val created = OfflineRecognizer(assetManager = null, config = config)
        Log.i(TAG, "Model loaded in ${System.currentTimeMillis() - start} ms")
        recognizer = created
        loadedModelId = models.model.id
        return created
    }
}

/**
 * On-device transcription engine backed by sherpa-onnx running the selected
 * catalog model (Parakeet v3 TDT or Parakeet 110M CTC) — the Android
 * counterpart of the FluidAudio/Parakeet runtime used by muesli-ios.
 *
 * Both models are non-streaming, so audio is captured via AudioRecord
 * and decoded on stop. While recording, the accumulated audio is re-decoded
 * every few seconds to produce pseudo-live partial results.
 */
class SherpaOnnxEngine(private val context: Context) : TranscriptionEngine {

    companion object {
        private const val TAG = "SherpaOnnxEngine"
        private const val SAMPLE_RATE = 16000
        private const val PARTIAL_INTERVAL_MS = 4_000L
    }

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val recording = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)
    /** Prevents partial re-decode backlog: on slow chips a full re-decode of
     * the accumulated audio takes longer than the 4s interval, so queued
     * partials would starve the final decode indefinitely. */
    private val partialBusy = AtomicBoolean(false)

    /** Engine callbacks always land on the main thread (callers may Toasts/UI). */
    private fun emitPartial(text: String) = mainHandler.post { onPartial?.invoke(text) }
    private fun emitFinal(text: String) = mainHandler.post { onFinal?.invoke(text) }
    private fun emitError(message: String) = mainHandler.post { onErr?.invoke(message) }

    private var audioRecord: AudioRecord? = null
    private val lock = Object()

    /**
     * Captured audio as an append-only list of 100 ms chunks. The previous
     * single-buffer + copyOf approach copied the entire recording every
     * 100 ms (quadratic churn) and OOMed on devices with small heap caps
     * (POCO C71, ~256 MB large-heap). sherpa's stream accepts multiple
     * acceptWaveform calls, so no large concatenation is ever needed.
     */
    private val chunks = mutableListOf<FloatArray>()
    private var activeMicPref = AudioInputRouteManager.MicPreference.AUTO
    private val routeManager by lazy { AudioInputRouteManager(context) }

    private var onPartial: ((String) -> Unit)? = null
    private var onFinal: ((String) -> Unit)? = null
    private var onErr: ((String) -> Unit)? = null
    private var amplitudeListener: ((Float) -> Unit)? = null

    override fun setAmplitudeListener(listener: ((Float) -> Unit)?) {
        amplitudeListener = listener
    }

    override fun startListening(
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        onPartial = onPartialResult
        onFinal = onFinalResult
        onErr = onError

        if (!ModelManager(context).isDownloaded()) {
            onError("The selected speech model is not downloaded. Download it from Settings.")
            return
        }

        executor.execute {
            try {
                // Warm the recognizer before opening the mic so no audio is missed.
                SherpaRecognizerHolder.get(context)
                startCapture()
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to start", t)
                if (!cancelled.get()) emitError("Could not start on-device engine: ${t.message}")
            }
        }
    }

    override fun stopListening() {
        if (!recording.compareAndSet(true, false)) return
        executor.execute {
            val audio = synchronized(lock) { chunks.toList() }
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            routeManager.stopBluetoothSco(activeMicPref)

            if (cancelled.get()) return@execute
            if (audio.sumOf { it.size } < SAMPLE_RATE / 2) {
                emitFinal("")
                return@execute
            }
            try {
                val text = decode(audio)
                if (!cancelled.get()) emitFinal(text)
            } catch (t: Throwable) {
                Log.e(TAG, "Final decode failed", t)
                if (!cancelled.get()) emitError("Transcription failed: ${t.message}")
            }
        }
    }

    override fun cancel() {
        cancelled.set(true)
        recording.set(false)
        try {
            audioRecord?.stop()
        } catch (_: Throwable) {
        }
        audioRecord?.release()
        audioRecord = null
        routeManager.stopBluetoothSco(activeMicPref)
        synchronized(lock) { chunks.clear() }
    }

    @SuppressLint("MissingPermission") // RECORD_AUDIO is granted before entry points reach here
    private fun startCapture() {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        val pref = AudioInputRouteManager.MicPreference.fromId(SharedStore(context).micPreference)
        activeMicPref = pref
        // Best-effort BT SCO bring-up for the Bluetooth preference; SCO
        // support varies by OEM, so failures fall back to the default route.
        routeManager.startBluetoothSco(pref)
        val record = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuffer, SAMPLE_RATE)) // ~1s buffer
            .build()
        check(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord failed to initialize" }
        routeManager.resolveDevice(pref)?.let { record.setPreferredDevice(it) }

        audioRecord = record
        record.startRecording()
        recording.set(true)

        val chunk = FloatArray(SAMPLE_RATE / 10) // 100 ms reads
        var lastPartialAt = System.currentTimeMillis()

        while (recording.get()) {
            val read = record.read(chunk, 0, chunk.size, AudioRecord.READ_BLOCKING)
            if (read <= 0) continue
            synchronized(lock) {
                chunks.add(chunk.copyOf(read))
            }
            publishLevel(chunk, read)

            val now = System.currentTimeMillis()
            if (now - lastPartialAt >= PARTIAL_INTERVAL_MS) {
                lastPartialAt = now
                schedulePartial()
            }
        }
    }

    /** RMS of the latest capture chunk mapped to a 0..1 waveform level. */
    private fun publishLevel(chunk: FloatArray, read: Int) {
        val listener = amplitudeListener ?: return
        var sum = 0.0
        for (i in 0 until read) sum += chunk[i] * chunk[i]
        val rms = kotlin.math.sqrt(sum / read).toFloat()
        // Map -45 dB .. -10 dB RMS to 0..1 (typical speech near the mic)
        val db = 20f * kotlin.math.log10(rms + 1e-8f)
        listener(((db + 45f) / 35f).coerceIn(0f, 1f))
    }

    /** Re-decodes the audio captured so far to emulate a live partial result. */
    private fun schedulePartial() {
        if (!partialBusy.compareAndSet(false, true)) return
        val snapshot = synchronized(lock) { chunks.toList() }
        if (snapshot.sumOf { it.size } < SAMPLE_RATE) {
            partialBusy.set(false)
            return
        }
        executor.execute {
            try {
                if (!recording.get() || cancelled.get()) return@execute
                try {
                    val text = decode(snapshot)
                    if (!cancelled.get() && text.isNotBlank()) emitPartial(text)
                } catch (t: Throwable) {
                    Log.w(TAG, "Partial decode failed: ${t.message}")
                }
            } finally {
                partialBusy.set(false)
            }
        }
    }

    /** Feeds chunks into the recognizer stream one by one (no concatenation)
     * and decodes. */
    private fun decode(audio: List<FloatArray>): String {
        val recognizer = SherpaRecognizerHolder.get(context)
        val stream = recognizer.createStream()
        return try {
            for (chunk in audio) {
                stream.acceptWaveform(chunk, SAMPLE_RATE)
            }
            recognizer.decode(stream)
            recognizer.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }
}
