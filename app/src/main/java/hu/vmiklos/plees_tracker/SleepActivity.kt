/*
 * Copyright 2020 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider

/**
 * The activity is the editing UI of a single sleep.
 */
class SleepActivity : AppCompatActivity() {
    private lateinit var viewModel: SleepViewModel
    private var sid: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sleep)
        val dataModel = DataModel.dataModel
        viewModel = ViewModelProvider.NewInstanceFactory().create(SleepViewModel::class.java)

        val bundle = intent.extras
        if (bundle == null) {
            return
        }
        this.sid = bundle.getInt("sid")
        viewModel.showSleep(this, sid)
    }

    fun editDateTime(@Suppress("UNUSED_PARAMETER") view: View) {
        val isStart = view == findViewById(R.id.sleep_start)
        viewModel.editSleep(this, sid, isStart)
    }

    companion object {
        private val TAG = "SleepActivity"
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
