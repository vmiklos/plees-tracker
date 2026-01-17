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
        if (sleep.wakes != wakes) {
            // Basic validation will happen in UI/logic, here we just store valid integers
            // Ideally validation for 'max 10' should be enforced here or in ViewModel
            // For now, let's just update perfectly.
            // But wait, user requested Max 10.
            
            // We can check here, but TextWatcher shouldn't really revert changes easily without loops.
            // Let's just update validation in ViewModel or similar if needed.
            // Actually, the user asked for max 10.
            
            // If we enforce it strictly here:
            if (wakes <= 10) {
                 sleep.wakes = wakes
                 viewModel.updateSleep(sleep)
            }
        }
    }
}
