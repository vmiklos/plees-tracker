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
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager

class Preferences : PreferenceFragmentCompat() {
    companion object {
        private const val TAG = "PreferencesActivity"
    }
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        val autoBackupPath = findPreference<Preference>("auto_backup_path")
        autoBackupPath?.let {
            val preferences = DataModel.preferences
            val path = preferences.getString("auto_backup_path", "")
            it.summary = path
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        val sharedPreferences = context?.let { PreferenceManager.getDefaultSharedPreferences(it) }

        if (preference.key == "enable_dnd") { // If someone toggled the enable_dnd preference
            if (sharedPreferences != null) {
                if (sharedPreferences.getBoolean("enable_dnd", false)) {
                    val activity = activity ?: return super.onPreferenceTreeClick(preference)
                    val dndManager = activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    val hasPermission = dndManager.isNotificationPolicyAccessGranted

                    if (!hasPermission) {
                        // If we don't have permissions for DND
                        AlertDialog.Builder(context)
                            .setTitle(R.string.settings_enable_dnd_question_title)
                            .setMessage(R.string.settings_enable_dnd_question_message)
                            .setPositiveButton(R.string.settings_enable_dnd_question_ok) { _, _ ->
                                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                startActivity(intent)
                            }
                            .setNegativeButton(R.string.settings_enable_dnd_question_cancel) { _, _ ->
                                val editor = sharedPreferences.edit()
                                if (editor != null) {
                                    editor.putBoolean("enable_dnd", false)
                                    editor.apply()
                                } else {
                                    Log.wtf(TAG, "editor is null")
                                }
                                checkDnd()
                            }
                            .create()
                            .show()
                    }
                }
            } else {
                Log.wtf(TAG, "sharedPreferences is null")
            }
        }
        return super.onPreferenceTreeClick(preference)
    }

    override fun onResume() {
        super.onResume()
        checkDnd()
    }

    fun checkDnd() {
        val activity = activity ?: return
        val dndManager = activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val hasPermission = dndManager.isNotificationPolicyAccessGranted

        val sharedPreferences = context?.let { PreferenceManager.getDefaultSharedPreferences(it) }
        val editor = sharedPreferences?.edit()

        if (!hasPermission) {
            if (editor != null) {
                editor.putBoolean("enable_dnd", false)
                editor.apply()
                Log.d(TAG, "Set enable_dnd to false!")
                // Refresh screen, not the "cleanest way" but it works
                preferenceScreen.removeAll()
                addPreferencesFromResource(R.xml.preferences)
            } else {
                Log.wtf(TAG, "editor is null")
            }
        }
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
