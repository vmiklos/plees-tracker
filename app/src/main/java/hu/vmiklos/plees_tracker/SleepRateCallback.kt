/*
 * Copyright 2023 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import android.content.ContentResolver
import android.content.Context
import android.widget.RatingBar

/**
 * This callback handles the rating of an individual sleep.
 */
class SleepRateCallback(
    private val viewModel: SleepViewModel,
    private val sleep: Sleep,
    private val context: Context,
    private val cr: ContentResolver
) : RatingBar.OnRatingBarChangeListener {
    override fun onRatingChanged(ratingBar: RatingBar?, rating: Float, fromUser: Boolean) {
        if (sleep.rating != rating.toLong()) {
            sleep.rating = rating.toLong()
            viewModel.updateSleep(sleep, context, cr)
        }
    }
}
