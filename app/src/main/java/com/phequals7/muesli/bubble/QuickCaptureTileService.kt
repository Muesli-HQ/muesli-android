package com.phequals7.muesli.bubble

import android.content.Intent
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.phequals7.muesli.MainActivity
import com.phequals7.muesli.data.SharedStore
import com.phequals7.muesli.model.ModelManager

/**
 * Quick Settings tile for one-tap voice capture: pull down, tap "Voice Note",
 * and the floating dictation card starts recording immediately. Falls back
 * to opening the app when the overlay permission is missing.
 */
class QuickCaptureTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val store = SharedStore(applicationContext)
        qsTile?.apply {
            state = when {
                store.bubbleEnabled && Settings.canDrawOverlays(applicationContext) -> Tile.STATE_ACTIVE
                else -> Tile.STATE_INACTIVE
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (!Settings.canDrawOverlays(this)) {
            // No overlay permission: open the app so the user can capture there.
            startActivityAndCollapse(
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        if (!ModelManager(this).isDownloaded()) {
            startActivityAndCollapse(
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        startService(Intent(this, BubbleService::class.java).setAction(BubbleService.ACTION_START_DICTATION))
    }
}
