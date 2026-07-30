package com.phequals7.muesli.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.phequals7.muesli.ime.ui.KeyboardScreen
import com.phequals7.muesli.theme.MuesliTheme

class MuesliInputMethodService : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry by lazy { LifecycleRegistry(this) }
    private val savedStateRegistryController by lazy { SavedStateRegistryController.create(this) }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private val keyboardController by lazy {
        KeyboardController(
            context = this,
            textInserter = { text ->
                currentInputConnection?.commitText(text, 1)
            },
            textDeleter = { length ->
                currentInputConnection?.deleteSurroundingText(length, 0)
            }
        )
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onCreateInputView(): View {
        com.phequals7.muesli.theme.AppearanceController.ensureInitialized(this)

        // Newer Compose UI resolves the window recomposer from the IME window's
        // decor view (parentPanel), not just the ComposeView — the owners must
        // be present there too or the keyboard crashes on show.
        window?.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this@MuesliInputMethodService)
            decor.setViewTreeSavedStateRegistryOwner(this@MuesliInputMethodService)
        }

        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@MuesliInputMethodService)
            setViewTreeSavedStateRegistryOwner(this@MuesliInputMethodService)
            setContent {
                MuesliTheme {
                    KeyboardScreen(controller = keyboardController)
                }
            }
        }
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        // "Keep mic ready" (iOS session-mode parity): start dictation as soon
        // as the keyboard opens, if the user opted in and mic access is granted.
        val store = com.phequals7.muesli.data.SharedStore(this)
        val micGranted = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (store.keepMicReady && micGranted && keyboardController.state == KeyboardState.IDLE) {
            keyboardController.startDictation()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        keyboardController.cancelDictation()
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
