/*
 * Copyright 2023 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager

class PreferencesActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "PreferencesActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.root, Preferences())
            .commit()
        setContentView(R.layout.activity_settings)

        DataModel.handleWindowInsets(this)

        DataModel.preferencesActivity = this
    }

    override fun onDestroy() {
        super.onDestroy()
        DataModel.preferencesActivity = null
    }

    private val backupActivityResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            var success = false
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                result.data?.data?.let { uri ->
                    contentResolver.takePersistableUriPermission(
                        uri,
                        flags
                    )
                    val editor = DataModel.preferences.edit()
                    editor.putString("auto_backup_path", uri.toString())
                    editor.apply()
                    success = true
                }

                if (!success) {
                    // Disable the bool setting when the user picked no folder.
                    val editor = DataModel.preferences.edit()
                    editor.putBoolean("auto_backup", false)
                    editor.apply()
                }

                // Refresh the view in case auto_backup or auto_backup_path changed.
                supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.root, Preferences())
                    .commit()
            } catch (e: Exception) {
                Log.e(TAG, "onActivityResult: setting backup path failed")
            }
        }

    fun openFolderChooser() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        backupActivityResult.launch(intent)
    }

    // Show a dialog to set bedtime and wakeup times (using two TimePickerDialogs sequentially)
    fun showBedtimeDialog() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        // Get current values or use defaults (22:00 for bedtime, 07:00 for wakeup)
        val currentBedHour = DataModel.getBedtimeHour(preferences)
        val currentBedMinute = DataModel.getBedtimeMinute(preferences)
        val currentWakeHour = DataModel.getWakeupHour(preferences)
        val currentWakeMinute = DataModel.getWakeupMinute(preferences)

        // First, pick bedtime
        TimePickerDialog(
            this,
            { _, bedHour, bedMinute ->
                // Save bedtime values
                val editor = preferences.edit()
                editor.putString("bedtime", "$bedHour:$bedMinute")
                editor.apply()
                // Then, pick wakeup time
                TimePickerDialog(
                    this,
                    { _, wakeHour, wakeMinute ->
                        // Save wakeup values
                        editor.putString("wakeup", "$wakeHour:$wakeMinute")
                        editor.apply()
                        Toast.makeText(
                            this,
                            getString(R.string.bedtime_toast),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    currentWakeHour,
                    currentWakeMinute,
                    true
                ).show()
            },
            currentBedHour,
            currentBedMinute,
            true
        ).show()
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
