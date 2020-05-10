/*
 * Copyright 2020 Miklos Vajna and contributors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker

import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

class SharedPreferencesChangeListener : SharedPreferences.OnSharedPreferenceChangeListener {
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        applyTheme(sharedPreferences)
    }

    fun applyTheme(sharedPreferences: SharedPreferences) {
        val themeFollowSystem = sharedPreferences.getBoolean("follow_system_theme", true)
        val darkTheme = sharedPreferences.getBoolean("dark_mode", false)
        if (themeFollowSystem) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        } else if (darkTheme) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
