package com.phequals7.muesli.ime

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.phequals7.muesli.data.SharedStore
import com.phequals7.muesli.data.entity.DictationResult
import com.phequals7.muesli.engine.MockTranscriptionEngine
import com.phequals7.muesli.engine.SherpaOnnxEngine
import com.phequals7.muesli.engine.TranscriptionEngine
import com.phequals7.muesli.utils.CustomWordMatcher
import com.phequals7.muesli.utils.FillerWordFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

enum class KeyboardState {
    IDLE,
    RECORDING,
    TRANSCRIBING,
    FINISHED,
    ERROR
}

class KeyboardController(
    private val context: Context,
    private val textInserter: (String) -> Unit,
    private val textDeleter: (Int) -> Unit
) {
    private val store = SharedStore(context)
    private val scope = CoroutineScope(Dispatchers.Main)

    var state by mutableStateOf(KeyboardState.IDLE)
        private set

    var statusText by mutableStateOf("Tap mic to record")
        private set

    var transcriptionText by mutableStateOf("")
        private set

    var errorMessage by mutableStateOf("")
        private set

    /** Live mic input level 0..1 for the waveform strip. */
    var inputLevel by mutableFloatStateOf(0f)
        private set

    private var currentEngine: TranscriptionEngine? = null
    private var lastInsertedLength = 0
    private var recordingStartedAt = 0L

    fun startDictation() {
        val engine = when (store.engineType) {
            "mock" -> MockTranscriptionEngine()
            else -> SherpaOnnxEngine(context)
        }.also { currentEngine = it }
        engine.setAmplitudeListener { level -> inputLevel = level }

        state = KeyboardState.RECORDING
        statusText = "Listening..."
        transcriptionText = ""
        errorMessage = ""
        inputLevel = 0f
        recordingStartedAt = System.currentTimeMillis()

        engine.startListening(
            onPartialResult = { text ->
                transcriptionText = text
            },
            onFinalResult = { text ->
                processAndCommitResult(text)
            },
            onError = { err ->
                state = KeyboardState.ERROR
                errorMessage = err
                statusText = "Error occurred"
            }
        )
    }

    fun stopDictation() {
        state = KeyboardState.TRANSCRIBING
        statusText = "Transcribing..."
        inputLevel = 0f
        currentEngine?.stopListening()
    }

    fun cancelDictation() {
        currentEngine?.cancel()
        state = KeyboardState.IDLE
        statusText = "Tap mic to record"
        transcriptionText = ""
    }

    /** "Keep mic ready" state: the model is prewarmed and recording would
     * start instantly, but capture only begins on an explicit tap. */
    fun showReadyStatus() {
        if (state == KeyboardState.IDLE) statusText = "Ready — tap mic to record"
    }

    fun clearLastInserted() {
        if (lastInsertedLength > 0) {
            textDeleter(lastInsertedLength)
            lastInsertedLength = 0
            statusText = "Deleted"
        }
    }

    fun insertReturn() {
        textInserter("\n")
        lastInsertedLength = 0 // Don't allow clearing formatting keys
    }

    fun insertSpace() {
        textInserter(" ")
        lastInsertedLength = 0
    }

    /** Punctuation keys, mirroring the iOS keyboard's punctuation row. */
    fun insertPunctuation(mark: String) {
        textInserter(mark)
        lastInsertedLength = 0
    }

    /** Single-character backspace from the punctuation row. */
    fun backspace() {
        textDeleter(1)
        lastInsertedLength = 0
    }

    /** Shows the system input-method picker (the iOS "globe key" equivalent),
     * so the user can switch back to Gboard/Samsung Keyboard. */
    fun switchKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showInputMethodPicker()
    }

    private fun processAndCommitResult(rawText: String) {
        if (rawText.isBlank()) {
            state = KeyboardState.IDLE
            statusText = "No speech detected"
            return
        }

        scope.launch {
            var processedText = rawText

            // 1. Filler word removal
            if (store.isFillerWordRemovalEnabled) {
                processedText = FillerWordFilter.apply(processedText)
            }

            // 2. Custom dictionary replacements
            if (store.isCustomDictionaryEnabled) {
                val customWords = store.getCustomWords()
                processedText = CustomWordMatcher.apply(processedText, customWords)
            }

            // Save to database
            val engineId = store.engineType
            val result = DictationResult(
                text = processedText,
                engineIdentifier = engineId,
                durationMs = if (recordingStartedAt > 0) System.currentTimeMillis() - recordingStartedAt else 0
            )
            store.insertDictationResult(result)

            // Insert into active input field
            textInserter(processedText)
            lastInsertedLength = processedText.length

            state = KeyboardState.FINISHED
            statusText = "Inserted successfully"
            transcriptionText = processedText

            // Reset back to idle after a short delay
            kotlinx.coroutines.delay(1500)
            if (state == KeyboardState.FINISHED) {
                state = KeyboardState.IDLE
                statusText = "Tap mic to record"
                transcriptionText = ""
            }
        }
    }
}
