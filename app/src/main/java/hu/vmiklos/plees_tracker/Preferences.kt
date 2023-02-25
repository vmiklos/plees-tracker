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
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        val autoBackupPath = findPreference<Preference>("auto_backup_path")
        autoBackupPath?.let {
            val preferences = DataModel.preferences
            val path = preferences.getString("auto_backup_path", "")
            it.summary = path
        }
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
