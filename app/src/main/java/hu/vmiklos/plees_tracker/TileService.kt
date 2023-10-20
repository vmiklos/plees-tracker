/*
 * Copyright 2023 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.preference.PreferenceManager

/**
 * Provides a quick settings tile that opens the main activity and immediately toggles between
 * started/stopped sleep tracking.
 */
@RequiresApi(api = Build.VERSION_CODES.N)
class TileService : android.service.quicksettings.TileService() {

    override fun onStartListening() {
        refreshTile()
    }

    override fun onTileAdded() {
        refreshTile()
    }

    private fun refreshTile() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        DataModel.init(applicationContext, preferences)

        val active = DataModel.start != null && DataModel.stop == null

        if (active) {
            qsTile.state = Tile.STATE_ACTIVE
        } else {
            qsTile.state = Tile.STATE_INACTIVE
        }
        qsTile.updateTile()
    }

    override fun onClick() {
        try {
            if (qsTile.state == Tile.STATE_ACTIVE) {
                qsTile.state = Tile.STATE_INACTIVE
            } else {
                qsTile.state = Tile.STATE_ACTIVE
            }
            qsTile.updateTile()

            val intent = Intent(applicationContext, MainActivity::class.java)
            intent.putExtra("startStop", true)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val flags = PendingIntent.FLAG_IMMUTABLE
                val activity = PendingIntent.getActivity(this, 0, intent, flags)
                startActivityAndCollapse(activity)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "onClick: uncaught exception: $e")
        }
    }

    companion object {
        private const val TAG = "TileService"
    }
}
