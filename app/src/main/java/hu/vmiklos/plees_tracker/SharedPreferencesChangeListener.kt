/*
 * Copyright 2023 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate

class SharedPreferencesChangeListener : SharedPreferences.OnSharedPreferenceChangeListener {
    companion object {
        private const val TAG = "SPChangeListener"
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        when (key) {
            "auto_backup" -> {
                val autoBackup = sharedPreferences.getBoolean("auto_backup", false)
                val autoBackupPath = sharedPreferences.getString("auto_backup_path", "")
                if (autoBackup) {
                    if (autoBackupPath.isNullOrEmpty()) {
                        val preferencesActivity = DataModel.preferencesActivity
                        if (preferencesActivity != null) {
                            Log.i(TAG, "onSharedPreferenceChanged: setting new backup path")
                            preferencesActivity.openFolderChooser()
                        }
                    }
                } else {
                    // Forget old path, so it's possible to set a different one later.
                    Log.i(TAG, "onSharedPreferenceChanged: clearing old backup path")
                    val editor = DataModel.preferences.edit()
                    editor.remove("auto_backup_path")
                    editor.apply()
                }
                return
            }
            "daily_reminder" -> {
                val autoReminder = sharedPreferences.getBoolean("daily_reminder", false)
                if (autoReminder) {
                    val preferencesActivity = DataModel.preferencesActivity
                    if (preferencesActivity != null) {
                        preferencesActivity.showBedtimeDialog()
                    }
                }
            }
            "enable_dnd" -> {
                if (sharedPreferences.getBoolean("enable_dnd", false)) {
                    val preferencesActivity = DataModel.preferencesActivity
                    val noteServ = Context.NOTIFICATION_SERVICE // 100 char limit
                    val dndManager = preferencesActivity?.getSystemService(noteServ)
                        as NotificationManager
                    val hasPermission = dndManager.isNotificationPolicyAccessGranted

                    if (!hasPermission) {
                        // If we don't have permissions for DND
                        AlertDialog.Builder(preferencesActivity)
                            .setTitle(R.string.settings_enable_dnd_q_title)
                            .setMessage(R.string.settings_enable_dnd_q_message)
                            .setPositiveButton(R.string.settings_enable_dnd_q_ok) { _, _ ->
                                val settingId = Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
                                val intent = Intent(settingId)
                                preferencesActivity.startActivity(intent)
                            }
                            .setNegativeButton(R.string.settings_enable_dnd_q_cancel) { _, _ -> }
                            .create()
                            .show()
                    }
                }
            }
        }

        applyTheme(sharedPreferences)
    }

    fun applyTheme(sharedPreferences: SharedPreferences) {
        val themeFollowSystem = sharedPreferences.getBoolean("follow_system_theme", true)
        val darkTheme = sharedPreferences.getBoolean("dark_mode", false)
        when {
            themeFollowSystem -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
            darkTheme -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
            else -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
