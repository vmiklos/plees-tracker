/*
 * Copyright 2022 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker

import android.text.Editable
import android.text.TextWatcher

/**
 * This callback handles the comment of an individual sleep.
 */
class SleepCommentCallback(
    private val activity: SleepActivity,
    private val viewModel: SleepViewModel,
    private val sleep: Sleep
) : TextWatcher {
    override fun afterTextChanged(s: Editable?) {
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        sleep.comment = s.toString()
        viewModel.updateSleep(activity, sleep, true)
    }
}
