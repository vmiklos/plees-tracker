/*
 * Copyright 2023 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import android.text.Editable
import android.text.TextWatcher

/**
 * This callback handles the comment of an individual sleep.
 */
class SleepCommentCallback(
    private val viewModel: SleepViewModel,
    private val sleep: Sleep
) : TextWatcher {
    override fun afterTextChanged(s: Editable?) {
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        if (sleep.comment != s.toString()) {
            sleep.comment = s.toString()
            viewModel.updateSleep(sleep)
        }
    }
}
