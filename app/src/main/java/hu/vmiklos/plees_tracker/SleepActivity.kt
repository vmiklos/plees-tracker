/*
 * Copyright 2023 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import android.os.Bundle
import android.view.MenuItem
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
        val startDate = findViewById<TextView>(R.id.sleep_start_date)
        startDate.setOnClickListener(this)
        val startTime = findViewById<TextView>(R.id.sleep_start_time)
        startTime.setOnClickListener(this)
        val stopDate = findViewById<TextView>(R.id.sleep_stop_date)
        stopDate.setOnClickListener(this)
        val stopTime = findViewById<TextView>(R.id.sleep_stop_time)
        stopTime.setOnClickListener(this)

        val bundle = intent.extras ?: return
        sid = bundle.getInt("sid")

        title = String.format(getString(R.string.sleep_id), sid.toString())
        // Show a back button.
        actionBar?.setDisplayHomeAsUpEnabled(true)

        viewModel.showSleep(this, sid)
    }

    override fun onClick(view: View?) {
        when {
            view == findViewById(R.id.sleep_start_date) ->
                viewModel.editSleepDate(this, sid, true, applicationContext, contentResolver)
            view == findViewById(R.id.sleep_start_time) ->
                viewModel.editSleepTime(this, sid, true, applicationContext, contentResolver)
            view == findViewById(R.id.sleep_stop_date) ->
                viewModel.editSleepDate(this, sid, false, applicationContext, contentResolver)
            view == findViewById(R.id.sleep_stop_time) ->
                viewModel.editSleepTime(this, sid, false, applicationContext, contentResolver)
            else -> {}
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            this.finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
