/*
 * Copyright 2023 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import android.text.Editable
import android.text.TextWatcher

/**
 * This callback handles the wakes of an individual sleep.
 */
class SleepWakesCallback(
    private val viewModel: SleepViewModel,
    private val sleep: Sleep
) : TextWatcher {
    override fun afterTextChanged(s: Editable?) {
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        val wakesStr = s.toString()
        val wakes = if (wakesStr.isNotEmpty()) wakesStr.toIntOrNull() ?: 0 else 0
        if (sleep.wakes != wakes && wakes in 0..10) {
            sleep.wakes = wakes
            viewModel.updateSleep(sleep)
        }
    }
}
