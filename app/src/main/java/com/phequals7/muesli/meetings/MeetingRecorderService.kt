package com.phequals7.muesli.meetings

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import com.phequals7.muesli.MainActivity
import com.phequals7.muesli.R
import com.phequals7.muesli.audio.AudioInputRouteManager
import com.phequals7.muesli.data.SharedStore
import com.phequals7.muesli.data.entity.RecordingSession
import com.phequals7.muesli.data.entity.Transcript
import com.phequals7.muesli.engine.SherpaRecognizerHolder
import com.phequals7.muesli.model.ModelManager
import com.phequals7.muesli.model.SpeechModels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground service that records a meeting and transcribes it on-device.
 *
 * Pipeline (Android counterpart of the iOS StreamingMeetingRecorder):
 *   AudioRecord 16 kHz float → WAV retention (16-bit PCM) → Silero VAD
 *   speech segmentation (bundled model, iOS 3–60 s FluidAudio-VAD parity)
 *   → sequential Parakeet decode per segment → merged live transcript in
 *   [MeetingRecordingController] → Transcript row on stop. When speaker
 *   labels are enabled, the finished WAV is diarized (bundled pyannote +
 *   TitaNet models) and chunks are prefixed with "Speaker N · m:ss".
 *
 * If the VAD cannot be created, chunking falls back to fixed 30 s rotation.
 */
class MeetingRecorderService : Service() {

    companion object {
        private const val TAG = "MeetingRecorder"
        const val ACTION_START = "com.phequals7.muesli.meetings.START"
        const val ACTION_STOP = "com.phequals7.muesli.meetings.STOP"
        const val ACTION_DISCARD = "com.phequals7.muesli.meetings.DISCARD"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEMPLATE_ID = "template_id"

        private const val SAMPLE_RATE = 16000
        private const val CHUNK_SECONDS = 30 // fallback when VAD is unavailable
        private const val PARTIAL_INTERVAL_MS = 5_000L
        private const val CHANNEL_ID = "meeting_recording"
        private const val NOTIFICATION_ID = 42
    }

    /** A finished audio chunk ready for decode, with its position in the recording. */
    private data class TimedChunk(val pcm: FloatArray, val startSec: Float, val endSec: Float)

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val decodeExecutor = Executors.newSingleThreadExecutor()
    private val capturing = AtomicBoolean(false)

    private lateinit var store: SharedStore

    private var sessionId: String? = null
    private var sessionTitle: String = ""
    private var startedAtMs: Long = 0

    private var audioRecord: AudioRecord? = null
    private var activeMicPref = AudioInputRouteManager.MicPreference.AUTO
    private var captureThread: Thread? = null
    private var wavFile: File? = null
    private var wavBytesWritten = 0L

    private val lock = Object()
    // Fixed-chunk fallback accumulation as append-only parts (concatenated
    // once at rotation — the old per-100ms copyOf churned quadratically and
    // contributed to OOM on low-heap devices). VAD mode leaves this empty.
    private val currentChunkParts = mutableListOf<FloatArray>()
    private var currentChunkSize = 0
    private var currentChunkStartSec = 0f
    private var samplesCaptured = 0L
    private var vad: Vad? = null
    private val finishedChunks = LinkedBlockingQueue<TimedChunk>()
    private val transcriptParts = mutableListOf<SpeakerLabeler.TimedText>()
    private val decodeBusy = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        store = SharedStore(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val id = intent.getStringExtra(EXTRA_SESSION_ID) ?: UUID.randomUUID().toString()
                val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                val templateId = intent.getStringExtra(EXTRA_TEMPLATE_ID) ?: MeetingTemplate.GENERAL.id
                startRecording(id, title, templateId)
            }
            ACTION_STOP -> stopRecording(discard = false)
            ACTION_DISCARD -> stopRecording(discard = true)
        }
        return START_NOT_STICKY
    }

    // ── lifecycle ────────────────────────────────────────────────────────

    private fun startRecording(id: String, title: String, templateId: String) {
        if (capturing.get()) return

        val selectedModel = SpeechModels.selected(this)
        if (!ModelManager(this, selectedModel).isDownloaded()) {
            failSession(id, title, templateId, "${selectedModel.shortName} model is not downloaded")
            return
        }

        sessionId = id
        sessionTitle = title
        startedAtMs = System.currentTimeMillis()
        transcriptParts.clear()
        synchronized(lock) {
            currentChunkParts.clear()
            currentChunkSize = 0
            currentChunkStartSec = 0f
            samplesCaptured = 0L
        }
        finishedChunks.clear()
        vad = try {
            Vad(
                assetManager = assets,
                config = VadModelConfig(
                    sileroVadModelConfig = SileroVadModelConfig(
                        model = "models/silero_vad.onnx",
                        threshold = 0.5f,
                        minSilenceDuration = 0.5f,
                        minSpeechDuration = 0.5f,
                        windowSize = 512,
                        // iOS rotates VAD chunks at 3–60 s; 30 s caps decode latency per chunk.
                        maxSpeechDuration = 30f,
                    ),
                    sampleRate = SAMPLE_RATE,
                    numThreads = 1,
                    provider = "cpu",
                ),
            )
        } catch (t: Throwable) {
            Log.w(TAG, "VAD unavailable, falling back to fixed ${CHUNK_SECONDS}s chunks: ${t.message}")
            null
        }

        serviceScope.launch {
            store.insertRecordingSession(
                RecordingSession(
                    id = id,
                    kind = "meeting",
                    title = title,
                    startedAt = startedAtMs,
                    phase = "recording",
                    engineIdentifier = selectedModel.id,
                    templateId = templateId,
                )
            )
        }

        startForegroundWithNotification(title)
        MeetingRecordingController.onCaptureStarted(id, title)

        try {
            // Warm the recognizer before opening the mic.
            decodeExecutor.execute {
                try {
                    SherpaRecognizerHolder.get(applicationContext)
                } catch (t: Throwable) {
                    Log.e(TAG, "Recognizer warm-up failed", t)
                }
            }
            startCaptureThread()
            capturing.set(true)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start capture", t)
            failSession(id, title, templateId, "Could not start microphone: ${t.message}")
            teardown()
        }
    }

    private fun stopRecording(discard: Boolean) {
        if (!capturing.compareAndSet(true, false)) return
        MeetingRecordingController.onFinalizing()

        decodeExecutor.execute {
            try {
                captureThread?.join(2_000)
            } catch (_: InterruptedException) {
            }
            AudioInputRouteManager(applicationContext).stopBluetoothSco(activeMicPref)

            if (!discard) {
                // Flush trailing speech out of the VAD, then rotate any
                // remaining fixed-fallback chunk.
                vad?.let { v ->
                    try {
                        v.flush()
                        drainVadSegments(v)
                    } catch (t: Throwable) {
                        Log.w(TAG, "VAD flush failed: ${t.message}")
                    }
                }
                synchronized(lock) {
                    if (currentChunkSize > 0) {
                        val endSec = samplesCaptured.toFloat() / SAMPLE_RATE
                        finishedChunks.offer(TimedChunk(concatParts(), currentChunkStartSec, endSec))
                        currentChunkParts.clear()
                        currentChunkSize = 0
                    }
                }
                drainChunkQueue()
            }

            closeWav()

            val id = sessionId
            val retainAudio = store.retainMeetingAudio
            var finalText = synchronized(transcriptParts) {
                transcriptParts.joinToString("\n\n") { it.text }.trim()
            }
            val endedAt = System.currentTimeMillis()
            val audioName = wavFile?.name

            if (!discard && store.speakerLabelsEnabled && wavFile != null && finalText.isNotBlank()) {
                finalText = try {
                    diarizeAndLabel(finalText)
                } catch (t: Throwable) {
                    Log.w(TAG, "Diarization failed, keeping unlabeled transcript: ${t.message}")
                    finalText
                }
            }

            // Audio retention is user-controlled; the temp WAV written for
            // diarization is removed when retention is off.
            if (!retainAudio) {
                wavFile?.delete()
            }
            releaseVad()

            serviceScope.launch {
                if (id != null) {
                    val existing = store.getRecordingSessionById(id)
                    if (existing != null) {
                        if (discard) {
                            store.updateRecordingSession(
                                existing.copy(phase = "cancelled", endedAt = endedAt)
                            )
                            wavFile?.delete()
                        } else {
                            val transcript = Transcript(
                                sessionID = id,
                                text = finalText,
                                engineIdentifier = existing.engineIdentifier
                                    ?: SpeechModels.selected(applicationContext).id
                            )
                            store.insertTranscript(transcript)
                            store.updateRecordingSession(
                                existing.copy(
                                    phase = "completed",
                                    endedAt = endedAt,
                                    audioFileName = if (retainAudio) audioName else null,
                                    transcriptID = transcript.id,
                                )
                            )
                            maybeRunSummary(id, finalText)
                        }
                    }
                }
                MeetingRecordingController.onFinished()
                teardown()
            }
        }
    }

    /** Runs diarization on the recorded WAV and renders a speaker-grouped
     * transcript. Falls back to the plain text on any inconsistency. */
    private fun diarizeAndLabel(plainText: String): String {
        val wav = wavFile ?: return plainText
        val pcm = readWavSamples(wav)
        if (pcm.size < SAMPLE_RATE) return plainText
        val segments = SpeakerDiarizer(applicationContext).diarize(pcm)
        if (segments.isEmpty()) return plainText
        val chunks = synchronized(transcriptParts) { transcriptParts.toList() }
        val labeled = SpeakerLabeler.render(chunks, segments)
        return labeled.ifBlank { plainText }
    }

    private fun releaseVad() {
        try {
            vad?.release()
        } catch (_: Throwable) {
        }
        vad = null
    }

    /**
     * AI meeting notes (iOS finalizeMeetingTranscript parity): runs after the
     * transcript is saved when summaries are enabled and the selected backend
     * has credentials. On failure the raw transcript is preserved inside
     * failureNotes, mirroring iOS.
     */
    private fun maybeRunSummary(sessionId: String, transcript: String) {
        if (!store.summariesEnabled) return

        val client = com.phequals7.muesli.summaries.MeetingSummaryClient(applicationContext)
        serviceScope.launch {
            val session = store.getRecordingSessionById(sessionId) ?: return@launch

            if (transcript.isBlank() || !client.isConfigured()) {
                store.updateRecordingSession(session.copy(summaryState = "unavailable"))
                return@launch
            }

            store.updateRecordingSession(session.copy(summaryState = "generating"))
            try {
                val result = client.summarize(
                    transcript = transcript,
                    meetingTitle = session.title,
                    template = MeetingTemplate.fromId(session.templateId),
                    manualNotesToRetain = session.manualNotes.ifBlank { null },
                )
                val latest = store.getRecordingSessionById(sessionId) ?: return@launch
                store.updateRecordingSession(
                    latest.copy(
                        summaryText = result.notes,
                        summaryState = "completed",
                        title = result.title,
                    )
                )
            } catch (t: Throwable) {
                Log.e(TAG, "summary generation failed", t)
                val latest = store.getRecordingSessionById(sessionId) ?: return@launch
                store.updateRecordingSession(
                    latest.copy(
                        summaryText = client.failureNotes(
                            transcript = transcript,
                            meetingTitle = latest.title,
                            error = t,
                            manualNotes = latest.manualNotes.ifBlank { null },
                        ),
                        summaryState = "failed",
                    )
                )
            }
        }
    }

    private fun failSession(id: String, title: String, templateId: String, message: String) {
        serviceScope.launch {
            store.insertRecordingSession(
                RecordingSession(
                    id = id,
                    kind = "meeting",
                    title = title.ifEmpty { "Untitled Meeting" },
                    phase = "failed",
                    errorMessage = message,
                    engineIdentifier = SpeechModels.selected(this@MeetingRecorderService).id,
                    templateId = templateId,
                )
            )
            MeetingRecordingController.onFinished()
        }
    }

    private fun teardown() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        capturing.set(false)
        try {
            audioRecord?.stop()
        } catch (_: Throwable) {
        }
        audioRecord?.release()
        audioRecord = null
        AudioInputRouteManager(applicationContext).stopBluetoothSco(activeMicPref)
        closeWav()
        releaseVad()
        decodeExecutor.shutdown()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ── capture ──────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission") // RECORD_AUDIO granted before reaching here
    private fun startCaptureThread() {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT
        )
        val pref = AudioInputRouteManager.MicPreference.fromId(store.micPreference)
        activeMicPref = pref
        val routeManager = AudioInputRouteManager(applicationContext)
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
            .setBufferSizeInBytes(maxOf(minBuffer, SAMPLE_RATE))
            .build()
        check(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord failed to initialize" }
        routeManager.resolveDevice(pref)?.let { record.setPreferredDevice(it) }
        audioRecord = record

        // The WAV is always written: it feeds diarization at stop. When the
        // user disabled audio retention it is written under a temp name and
        // deleted during finalizing.
        wavFile = if (store.retainMeetingAudio) {
            File(filesDir, "meetings/${sessionId}.wav")
        } else {
            File(filesDir, "meetings/.tmp-${sessionId}.wav")
        }.apply {
            parentFile?.mkdirs()
            writeWavHeader(this, 0)
        }

        record.startRecording()

        captureThread = Thread {
            val chunk = FloatArray(SAMPLE_RATE / 10) // 100 ms
            var lastPartialAt = System.currentTimeMillis()

            while (capturing.get()) {
                val read = record.read(chunk, 0, chunk.size, AudioRecord.READ_BLOCKING)
                if (read <= 0) continue

                appendToWav(chunk, read)
                publishLevel(chunk, read)

                val frame = chunk.copyOf(read)
                val frameStartSec: Float
                synchronized(lock) {
                    frameStartSec = samplesCaptured.toFloat() / SAMPLE_RATE
                    samplesCaptured += read
                }

                val v = vad
                if (v != null) {
                    try {
                        v.acceptWaveform(frame)
                        drainVadSegments(v)
                    } catch (t: Throwable) {
                        Log.w(TAG, "VAD failed mid-capture, using fixed chunks: ${t.message}")
                        try { v.release() } catch (_: Throwable) {}
                        vad = null
                    }
                } else {
                    synchronized(lock) {
                        if (currentChunkSize == 0) currentChunkStartSec = frameStartSec
                        currentChunkParts.add(frame)
                        currentChunkSize += read
                        if (currentChunkSize >= SAMPLE_RATE * CHUNK_SECONDS) {
                            finishedChunks.offer(
                                TimedChunk(
                                    concatParts(),
                                    currentChunkStartSec,
                                    currentChunkStartSec + currentChunkSize.toFloat() / SAMPLE_RATE
                                )
                            )
                            currentChunkParts.clear()
                            currentChunkSize = 0
                        }
                    }
                }
                scheduleChunkDecode()

                val elapsedSec = ((System.currentTimeMillis() - startedAtMs) / 1000L).toInt()
                val now = System.currentTimeMillis()
                if (now - lastPartialAt >= PARTIAL_INTERVAL_MS) {
                    lastPartialAt = now
                    schedulePartial()
                }
                MeetingRecordingController.onProgress(
                    elapsedSec,
                    lastLevel,
                    lastPartial,
                    synchronized(transcriptParts) { transcriptParts.joinToString("\n\n") { it.text } }
                )
            }
        }.also { it.start() }
    }

    /** Concatenates the fallback chunk parts once (call with [lock] held). */
    private fun concatParts(): FloatArray {
        val out = FloatArray(currentChunkSize)
        var offset = 0
        for (part in currentChunkParts) {
            System.arraycopy(part, 0, out, offset, part.size)
            offset += part.size
        }
        return out
    }

    /** Pops every completed speech segment out of the VAD into the decode
     * queue. Must be called after acceptWaveform/flush. */
    private fun drainVadSegments(v: Vad) {
        while (!v.empty()) {
            val segment = v.front()
            v.pop()
            if (segment.samples.size < SAMPLE_RATE / 2) continue
            val startSec = segment.start.toFloat() / SAMPLE_RATE
            val endSec = startSec + segment.samples.size.toFloat() / SAMPLE_RATE
            finishedChunks.offer(TimedChunk(segment.samples, startSec, endSec))
        }
        scheduleChunkDecode()
    }

    @Volatile
    private var lastLevel = 0f

    @Volatile
    private var lastPartial = ""

    private fun publishLevel(chunk: FloatArray, read: Int) {
        var sum = 0.0
        for (i in 0 until read) sum += chunk[i] * chunk[i]
        val rms = kotlin.math.sqrt(sum / read).toFloat()
        val db = 20f * kotlin.math.log10(rms + 1e-8f)
        lastLevel = ((db + 45f) / 35f).coerceIn(0f, 1f)
    }

    // ── transcription ────────────────────────────────────────────────────

    private fun scheduleChunkDecode() {
        if (!decodeBusy.compareAndSet(false, true)) return
        decodeExecutor.execute {
            try {
                drainChunkQueue()
            } finally {
                decodeBusy.set(false)
            }
        }
    }

    /** Decodes every finished chunk currently queued. Must run on decodeExecutor. */
    private fun drainChunkQueue() {
        while (true) {
            val chunk = finishedChunks.poll() ?: break
            if (chunk.pcm.size < SAMPLE_RATE / 2) continue
            try {
                val text = decode(chunk.pcm)
                if (text.isNotBlank()) {
                    synchronized(transcriptParts) {
                        transcriptParts.add(SpeakerLabeler.TimedText(chunk.startSec, chunk.endSec, text))
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Chunk decode failed", t)
            }
        }
    }

    /** Re-decodes the in-progress chunk for live feedback. Disabled: the
     * re-decode doubles CPU use and the flickering preview tested poorly. */
    private fun schedulePartial() = Unit

    private fun decode(pcm: FloatArray): String {
        val recognizer = SherpaRecognizerHolder.get(applicationContext)
        val stream = recognizer.createStream()
        val raw = try {
            stream.acceptWaveform(pcm, SAMPLE_RATE)
            recognizer.decode(stream)
            recognizer.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
        return postProcess(raw)
    }

    /** Filler-word removal + custom dictionary, same pipeline as dictation
     * (iOS applies TranscriptPostProcessor to meeting chunks too). */
    private fun postProcess(text: String): String {
        if (text.isBlank()) return text
        var processed = text
        if (store.isFillerWordRemovalEnabled) {
            processed = com.phequals7.muesli.utils.FillerWordFilter.apply(processed)
        }
        if (store.isCustomDictionaryEnabled) {
            processed = kotlinx.coroutines.runBlocking {
                com.phequals7.muesli.utils.CustomWordMatcher.apply(processed, store.getCustomWords())
            }
        }
        return processed.trim()
    }

    // ── WAV retention ────────────────────────────────────────────────────

    private fun appendToWav(chunk: FloatArray, read: Int) {
        val file = wavFile ?: return
        try {
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(file.length())
                val bytes = ByteArray(read * 2)
                for (i in 0 until read) {
                    val s = (chunk[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                    bytes[i * 2] = (s.toInt() and 0xFF).toByte()
                    bytes[i * 2 + 1] = (s.toInt() shr 8 and 0xFF).toByte()
                }
                raf.write(bytes)
                wavBytesWritten += bytes.size
            }
        } catch (t: Throwable) {
            Log.w(TAG, "WAV append failed: ${t.message}")
        }
    }

    private fun closeWav() {
        val file = wavFile ?: return
        try {
            writeWavHeader(file, wavBytesWritten)
        } catch (t: Throwable) {
            Log.w(TAG, "WAV header patch failed: ${t.message}")
        }
    }

    private fun writeWavHeader(file: File, dataBytes: Long) {
        RandomAccessFile(file, "rw").use { raf ->
            val byteRate = SAMPLE_RATE * 2
            val header = ByteArray(44)
            fun putStr(off: Int, s: String) {
                for (i in s.indices) header[off + i] = s[i].code.toByte()
            }
            fun putInt(off: Int, v: Long) {
                header[off] = (v and 0xFF).toByte()
                header[off + 1] = (v shr 8 and 0xFF).toByte()
                header[off + 2] = (v shr 16 and 0xFF).toByte()
                header[off + 3] = (v shr 24 and 0xFF).toByte()
            }
            fun putShort(off: Int, v: Int) {
                header[off] = (v and 0xFF).toByte()
                header[off + 1] = (v shr 8 and 0xFF).toByte()
            }
            putStr(0, "RIFF"); putInt(4, 36 + dataBytes); putStr(8, "WAVE")
            putStr(12, "fmt "); putInt(16, 16); putShort(20, 1); putShort(22, 1)
            putInt(24, SAMPLE_RATE.toLong()); putInt(28, byteRate.toLong())
            putShort(32, 2); putShort(34, 16)
            putStr(36, "data"); putInt(40, dataBytes)
            raf.seek(0)
            raf.write(header)
        }
    }

    /** Reads a 16 kHz mono 16-bit WAV (as written by [appendToWav]) back into
     * float samples for diarization. */
    private fun readWavSamples(file: File): FloatArray {
        val bytes = file.readBytes()
        if (bytes.size <= 44) return FloatArray(0)
        val count = (bytes.size - 44) / 2
        val out = FloatArray(count)
        for (i in 0 until count) {
            val lo = bytes[44 + i * 2].toInt() and 0xFF
            val hi = bytes[44 + i * 2 + 1].toInt()
            val sample = (hi shl 8) or lo
            out[i] = sample / 32768f
        }
        return out
    }

    // ── notification ─────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Meeting Recording",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shown while Muesli records a meeting" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startForegroundWithNotification(title: String) {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, MeetingRecorderService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val discardIntent = PendingIntent.getService(
            this, 2,
            Intent(this, MeetingRecorderService::class.java).setAction(ACTION_DISCARD),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Recording meeting")
            .setContentText(title.ifEmpty { "Muesli" })
            .setContentIntent(openApp)
            .setOngoing(true)
            .setWhen(startedAtMs)
            .setUsesChronometer(true)
            .addAction(0, "Stop", stopIntent)
            .addAction(0, "Discard", discardIntent)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}
