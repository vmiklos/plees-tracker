/*
 * Copyright 2021 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker

import android.content.SharedPreferences
import androidx.lifecycle.LiveData

/**
 * From https://gist.github.com/rharter/1df1cd72ce4e9d1801bd2d49f2a96810#gistcomment-3541759
 */
private class SharedPreferenceLiveData<T>(
    private val sharedPrefs: SharedPreferences,
    private val key: String,
    private val getPreferenceValue: () -> T,
) : LiveData<T>(getPreferenceValue()), SharedPreferences.OnSharedPreferenceChangeListener {
    override fun onActive() {
        sharedPrefs.registerOnSharedPreferenceChangeListener(this)
        updateIfChanged()
    }

    override fun onInactive() = sharedPrefs.unregisterOnSharedPreferenceChangeListener(this)

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == this.key || key == null) {
            // Note that we get here on every preference write, even if the value has not changed
            updateIfChanged()
        }
    }

    /** Update the live data value, but only if the value has changed. */
    private fun updateIfChanged() = with(getPreferenceValue()) { if (value != this) value = this }
}

fun SharedPreferences.liveData(key: String, default: Int): LiveData<Int> =
    SharedPreferenceLiveData(this, key) { getInt(key, default) }

fun SharedPreferences.liveData(key: String, default: Long): LiveData<Long> =
    SharedPreferenceLiveData(this, key) { getLong(key, default) }

fun SharedPreferences.liveData(key: String, default: Boolean): LiveData<Boolean> =
    SharedPreferenceLiveData(this, key) { getBoolean(key, default) }

fun SharedPreferences.liveData(key: String, default: Float): LiveData<Float> =
    SharedPreferenceLiveData(this, key) { getFloat(key, default) }

fun SharedPreferences.liveData(key: String, default: String?): LiveData<String?> =
    SharedPreferenceLiveData(this, key) { getString(key, default) }

fun SharedPreferences.liveData(key: String, default: Set<String>?): LiveData<Set<String>?> =
    SharedPreferenceLiveData(this, key) { getStringSet(key, default) }
