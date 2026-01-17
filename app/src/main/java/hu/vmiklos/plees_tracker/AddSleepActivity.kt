/*
 * Copyright 2023 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.EditText
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.Calendar

class AddSleepActivity : AppCompatActivity() {
    private val startDateTime: Calendar = Calendar.getInstance()
    private val stopDateTime: Calendar = Calendar.getInstance().apply {
        add(Calendar.MINUTE, 10)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_sleep)
        title = getString(R.string.add_sleep)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        DataModel.handleWindowInsets(this)

        updateLabels()

        findViewById<TextView>(R.id.add_sleep_start_date).setOnClickListener {
            pickDate(startDateTime)
        }
        findViewById<TextView>(R.id.add_sleep_start_time).setOnClickListener {
            pickTime(startDateTime)
        }
        findViewById<TextView>(R.id.add_sleep_stop_date).setOnClickListener {
            pickDate(stopDateTime)
        }
        findViewById<TextView>(R.id.add_sleep_stop_time).setOnClickListener {
            pickTime(stopDateTime)
        }



        findViewById<View>(R.id.add_sleep_save).setOnClickListener {
            saveSleep()
        }
    }

    private fun updateLabels() {
        val compactView = DataModel.getCompactView()
        findViewById<TextView>(R.id.add_sleep_start_date).text =
            DataModel.formatDateTime(startDateTime.time, asTime = false, compactView)
        findViewById<TextView>(R.id.add_sleep_start_time).text =
            DataModel.formatDateTime(startDateTime.time, asTime = true, compactView)
        findViewById<TextView>(R.id.add_sleep_stop_date).text =
            DataModel.formatDateTime(stopDateTime.time, asTime = false, compactView)
        findViewById<TextView>(R.id.add_sleep_stop_time).text =
            DataModel.formatDateTime(stopDateTime.time, asTime = true, compactView)
    }

    private fun pickDate(calendar: Calendar) {
        DatePickerDialog(
            this,
            { _, year, month, day ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, day)
                updateLabels()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun pickTime(calendar: Calendar) {
        TimePickerDialog(
            this,
            { _, hour, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                updateLabels()
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            DateFormat.is24HourFormat(this)
        ).show()
    }

    private fun saveSleep() {
        if (!stopDateTime.after(startDateTime)) {
            Toast.makeText(this, getString(R.string.negative_duration), Toast.LENGTH_SHORT).show()
            return
        }

        val rating = findViewById<RatingBar>(R.id.add_sleep_rating).rating.toLong()
        val comment = findViewById<EditText>(R.id.add_sleep_comment).text.toString()
        val wakesStr = findViewById<EditText>(R.id.add_sleep_wakes).text.toString()
        val wakes = if (wakesStr.isNotEmpty()) wakesStr.toInt() else 0
        if (wakes > 10) {
            Toast.makeText(this, "The maximum number of times woken up is 10.", Toast.LENGTH_SHORT).show()
            return
        }

        val sleep = Sleep()
        sleep.start = startDateTime.timeInMillis
        sleep.stop = stopDateTime.timeInMillis
        sleep.rating = rating
        sleep.comment = comment
        sleep.wakes = wakes

        lifecycleScope.launch {
            DataModel.insertSleep(sleep)
            finish()
        }
    }


    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
