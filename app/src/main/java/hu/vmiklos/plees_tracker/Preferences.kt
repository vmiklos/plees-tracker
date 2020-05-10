/*
 * Copyright 2020 Miklos Vajna and contributors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat

class Preferences : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
