/*
 * Copyright 2023 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class Preferences : PreferenceFragmentCompat() {
    private fun padMinute(raw: String): String {
        val fro = ":([0-9])$".toRegex()
        val to = ":0$1"
        return raw.replace(fro, to)
    }
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        val autoBackupPath = findPreference<Preference>("auto_backup_path")
        autoBackupPath?.let {
            val preferences = DataModel.preferences
            val path = preferences.getString("auto_backup_path", "")
            it.summary = path
        }
        val wakeup = findPreference<Preference>("wakeup")
        wakeup?.let {
            val preferences = DataModel.preferences
            val value = preferences.getString("wakeup", "")
            if (value != null) {
                it.summary = padMinute(value)
            }
        }
        val bedtime = findPreference<Preference>("bedtime")
        bedtime?.let {
            val preferences = DataModel.preferences
            val value = preferences.getString("bedtime", "")
            if (value != null) {
                it.summary = padMinute(value)
            }
        }
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
