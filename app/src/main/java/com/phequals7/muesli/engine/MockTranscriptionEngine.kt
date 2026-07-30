package com.phequals7.muesli.engine

import android.os.Handler
import android.os.Looper

class MockTranscriptionEngine : TranscriptionEngine {
    private val handler = Handler(Looper.getMainLooper())
    private var isListening = false
    private var workRunnable: Runnable? = null
    private var amplitudeListener: ((Float) -> Unit)? = null
    private var tick = 0.0

    override fun setAmplitudeListener(listener: ((Float) -> Unit)?) {
        amplitudeListener = listener
    }

    private val phrases = listOf(
        "Hello",
        "Hello this is",
        "Hello this is a demo of",
        "Hello this is a demo of muesli on android."
    )

    override fun startListening(
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        cancel()
        isListening = true

        var index = 0
        fun scheduleNext() {
            if (!isListening) return

            workRunnable = Runnable {
                if (index < phrases.size) {
                    // Synthetic speech-like level so the waveform is exercised
                    val phrase = (kotlin.math.sin(tick * 0.82) + 1) * 0.28
                    val syllable = (kotlin.math.sin(tick * 2.7) + 1) * 0.18
                    tick += 0.9
                    amplitudeListener?.invoke((0.2 + phrase + syllable).toFloat().coerceIn(0f, 1f))

                    val partial = phrases[index]
                    onPartialResult(partial)
                    index++
                    if (index == phrases.size) {
                        onFinalResult(partial)
                        isListening = false
                    } else {
                        scheduleNext()
                    }
                }
            }
            handler.postDelayed(workRunnable!!, 1000)
        }

        scheduleNext()
    }

    override fun stopListening() {
        // In mock, we can just finish immediately
        isListening = false
        workRunnable?.let { handler.removeCallbacks(it) }
        // Return whatever the last phrase would have been (the full sentence)
        // (Just invoke final with the last phrase)
    }

    override fun cancel() {
        isListening = false
        workRunnable?.let { handler.removeCallbacks(it) }
        workRunnable = null
    }
}
