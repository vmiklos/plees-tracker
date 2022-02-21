/*
 * Copyright 2020 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker

import android.widget.RatingBar

/**
 * This callback handles the rating of an individual sleep.
 */
class SleepRateCallback(
    private val activity: SleepActivity,
    private val viewModel: SleepViewModel,
    private val sleep: Sleep
) : RatingBar.OnRatingBarChangeListener {
    override fun onRatingChanged(ratingBar: RatingBar?, rating: Float, fromUser: Boolean) {
        if (!fromUser) {
            return
        }
        if (sleep.rating != rating.toLong()) {
            sleep.rating = rating.toLong()
            viewModel.updateSleep(activity, sleep, false)
        }
    }
}
