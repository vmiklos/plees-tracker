/*
 * Copyright 2020 Miklos Vajna and contributors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class PreferencesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, Preferences())
                .commit()
        setContentView(R.layout.settings)
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
