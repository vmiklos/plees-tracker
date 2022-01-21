/*
 * Copyright 2020 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider

/**
 * The activity is the editing UI of a single sleep.
 */
class SleepActivity : AppCompatActivity(), View.OnClickListener {
    private lateinit var viewModel: SleepViewModel
    private var sid: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider.NewInstanceFactory().create(SleepViewModel::class.java)

        setContentView(R.layout.activity_sleep)
        val start = findViewById<TextView>(R.id.sleep_start)
        start.setOnClickListener(this)
        val stop = findViewById<TextView>(R.id.sleep_stop)
        stop.setOnClickListener(this)

        val bundle = intent.extras ?: return
        sid = bundle.getInt("sid")

        title = String.format(getString(R.string.sleep_id), sid)
        // Show a back button.
        actionBar?.setDisplayHomeAsUpEnabled(true)

        viewModel.showSleep(this, sid)
    }

    override fun onClick(view: View?) {
        val isStart = view == findViewById(R.id.sleep_start)
        viewModel.editSleep(this, sid, isStart, applicationContext, contentResolver)
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
