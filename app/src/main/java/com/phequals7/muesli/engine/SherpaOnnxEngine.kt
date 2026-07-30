package com.phequals7.muesli.engine

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.phequals7.muesli.model.ModelManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Holds the loaded Parakeet V3 recognizer. Loading the ~620 MB int8 encoder
 * takes a few seconds, so the instance is shared and reused across dictations
 * within the process.
 */
object SherpaRecognizerHolder {
    private const val TAG = "SherpaRecognizer"

    @Volatile
    private var recognizer: OfflineRecognizer? = null

    @Synchronized
    fun get(context: Context): OfflineRecognizer {
        recognizer?.let { return it }

        val models = ModelManager(context.applicationContext)
        check(models.isDownloaded()) { "Parakeet V3 model is not downloaded" }

        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = OfflineModelConfig(
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
            ),
            decodingMethod = "greedy_search",
        )

        Log.i(TAG, "Loading Parakeet V3 model from ${models.modelDir}")
        val start = System.currentTimeMillis()
        val created = OfflineRecognizer(assetManager = null, config = config)
        Log.i(TAG, "Model loaded in ${System.currentTimeMillis() - start} ms")
        recognizer = created
        return created
    }
}

/**
 * On-device transcription engine backed by sherpa-onnx running NVIDIA
 * Parakeet TDT 0.6B v3 (multilingual, int8) — the Android counterpart of the
 * FluidAudio/Parakeet runtime used by muesli-ios.
 *
 * Parakeet TDT is a non-streaming model, so audio is captured via AudioRecord
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
    private val recording = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)

    private var audioRecord: AudioRecord? = null
    private val lock = Object()
    private var samples = FloatArray(0)

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
            onError("Parakeet V3 model is not downloaded. Download it from Settings.")
            return
        }

        executor.execute {
            try {
                // Warm the recognizer before opening the mic so no audio is missed.
                SherpaRecognizerHolder.get(context)
                startCapture()
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to start", t)
                if (!cancelled.get()) onErr?.invoke("Could not start on-device engine: ${t.message}")
            }
        }
    }

    override fun stopListening() {
        if (!recording.compareAndSet(true, false)) return
        executor.execute {
            val pcm = synchronized(lock) { samples }
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            if (cancelled.get()) return@execute
            if (pcm.size < SAMPLE_RATE / 2) {
                onFinal?.invoke("")
                return@execute
            }
            try {
                val text = decode(pcm)
                if (!cancelled.get()) onFinal?.invoke(text)
            } catch (t: Throwable) {
                Log.e(TAG, "Final decode failed", t)
                if (!cancelled.get()) onErr?.invoke("Transcription failed: ${t.message}")
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
        synchronized(lock) { samples = FloatArray(0) }
    }

    @SuppressLint("MissingPermission") // RECORD_AUDIO is granted before entry points reach here
    private fun startCapture() {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
            maxOf(minBuffer, SAMPLE_RATE) // ~1s buffer
        )
        check(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord failed to initialize" }

        audioRecord = record
        record.startRecording()
        recording.set(true)

        val chunk = FloatArray(SAMPLE_RATE / 10) // 100 ms reads
        var lastPartialAt = System.currentTimeMillis()

        while (recording.get()) {
            val read = record.read(chunk, 0, chunk.size, AudioRecord.READ_BLOCKING)
            if (read <= 0) continue
            synchronized(lock) {
                samples = samples.copyOf(samples.size + read).also {
                    System.arraycopy(chunk, 0, it, samples.size, read)
                }
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
        val snapshot = synchronized(lock) { samples }
        if (snapshot.size < SAMPLE_RATE) return
        executor.execute {
            if (!recording.get() || cancelled.get()) return@execute
            try {
                val text = decode(snapshot)
                if (!cancelled.get() && text.isNotBlank()) onPartial?.invoke(text)
            } catch (t: Throwable) {
                Log.w(TAG, "Partial decode failed: ${t.message}")
            }
        }
    }

    private fun decode(pcm: FloatArray): String {
        val recognizer = SherpaRecognizerHolder.get(context)
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(pcm, SAMPLE_RATE)
            recognizer.decode(stream)
            recognizer.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }
}
