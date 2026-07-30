package com.phequals7.muesli.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.phequals7.muesli.data.SharedStore

/**
 * Process-wide appearance state (accent theme + light/dark mode), backed by
 * SharedPreferences. Read by [MuesliTheme]; written from the Appearance
 * settings screen. Initialized lazily from the first context available.
 */
object AppearanceController {

    var accentThemeId by mutableStateOf("blue")
        private set

    var appearanceMode by mutableStateOf("system")
        private set

    private var initialized = false

    @Synchronized
    fun ensureInitialized(context: Context) {
        if (initialized) return
        val store = SharedStore(context.applicationContext)
        accentThemeId = store.accentTheme
        appearanceMode = store.appearanceMode
        initialized = true
    }

    fun setAccentTheme(context: Context, id: String) {
        accentThemeId = id
        SharedStore(context.applicationContext).accentTheme = id
    }

    fun setAppearanceMode(context: Context, mode: String) {
        appearanceMode = mode
        SharedStore(context.applicationContext).appearanceMode = mode
    }
}
