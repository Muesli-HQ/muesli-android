package com.phequals7.muesli.engine

interface TranscriptionEngine {
    fun startListening(
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit
    )
    fun stopListening()
    fun cancel()

    /**
     * Optional live input level (0..1) for waveform rendering, published
     * roughly every 100 ms while recording. Engines that cannot meter
     * input leave this as a no-op.
     */
    fun setAmplitudeListener(listener: ((Float) -> Unit)?) {}
}
