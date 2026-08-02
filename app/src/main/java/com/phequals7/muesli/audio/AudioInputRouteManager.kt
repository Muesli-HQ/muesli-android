package com.phequals7.muesli.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log

/**
 * Android counterpart of the iOS AudioInputRouteManager: maps the user's
 * RecordingMicrophonePreference to a concrete [AudioDeviceInfo] (when one is
 * currently connected) and provides the live route label shown on the
 * recording hero pill and in Settings.
 *
 * Recording itself applies the preference via AudioRecord.Builder
 * .setPreferredDevice(); when the preferred device is not connected, null is
 * returned and capture falls back to the system default (phone mic).
 */
class AudioInputRouteManager(context: Context) {

    /** Mirrors iOS RecordingMicrophonePreference ids ("auto" is the default). */
    enum class MicPreference(val id: String, val label: String) {
        AUTO("auto", "Automatic"),
        PHONE("phone", "This Phone"),
        BLUETOOTH("bluetooth", "Bluetooth"),
        USB("usb", "External (USB)");

        companion object {
            fun fromId(id: String?): MicPreference =
                entries.firstOrNull { it.id == id } ?: AUTO
        }
    }

    private val audioManager = context.getSystemService(AudioManager::class.java)

    private fun inputDevices(): List<AudioDeviceInfo> =
        audioManager?.getDevices(AudioManager.GET_DEVICES_INPUTS)?.toList().orEmpty()

    /** Whether a device matching this preference is currently connected. */
    fun isAvailable(pref: MicPreference): Boolean = when (pref) {
        // The built-in mic is always present; "auto" always has a route.
        MicPreference.AUTO, MicPreference.PHONE -> true
        MicPreference.BLUETOOTH, MicPreference.USB -> resolveDevice(pref) != null
    }

    /**
     * Resolves the concrete input device for [pref], or null when the system
     * default route should be used — either because the preference is
     * "auto" or because the preferred device is not currently connected.
     */
    fun resolveDevice(pref: MicPreference): AudioDeviceInfo? {
        val devices = inputDevices()
        return when (pref) {
            MicPreference.AUTO -> null
            MicPreference.PHONE ->
                devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
            MicPreference.BLUETOOTH ->
                devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            MicPreference.USB ->
                devices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                        it.type == AudioDeviceInfo.TYPE_USB_HEADSET
                }
        }
    }

    /** Human label for the route a recording would take right now. */
    fun currentRouteLabel(pref: MicPreference): String = when (pref) {
        MicPreference.AUTO -> "Automatic"
        MicPreference.PHONE -> "Phone Microphone"
        MicPreference.BLUETOOTH ->
            if (isAvailable(pref)) "Bluetooth Mic" else "Phone Microphone (Bluetooth unavailable)"
        MicPreference.USB ->
            if (isAvailable(pref)) "USB Mic" else "Phone Microphone (USB unavailable)"
    }

    /**
     * Best-effort Bluetooth SCO bring-up before recording with a Bluetooth
     * preference. BT SCO support varies widely by OEM — failures are ignored
     * and AudioRecord still falls back to the default route.
     */
    fun startBluetoothSco(pref: MicPreference) {
        if (pref != MicPreference.BLUETOOTH || !isAvailable(pref)) return
        try {
            audioManager?.startBluetoothSco()
            audioManager?.isBluetoothScoOn = true
        } catch (t: Throwable) {
            Log.w(TAG, "startBluetoothSco failed: ${t.message}")
        }
    }

    /** Tears down SCO started via [startBluetoothSco]; best-effort. */
    fun stopBluetoothSco(pref: MicPreference) {
        if (pref != MicPreference.BLUETOOTH) return
        try {
            audioManager?.isBluetoothScoOn = false
            audioManager?.stopBluetoothSco()
        } catch (t: Throwable) {
            Log.w(TAG, "stopBluetoothSco failed: ${t.message}")
        }
    }

    private companion object {
        const val TAG = "AudioInputRoute"
    }
}
