/*
 * Copyright 2020 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import java.util.Calendar

/**
 * This activity provides additional stats for a limited period of time. This is in contrast with
 * the main activity, which considers all sleeps.
 */
class StatsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_stats)

        title = getString(R.string.stats)
        // Show a back button.
        actionBar?.setDisplayHomeAsUpEnabled(true)

        DataModel.sleepsLive.observe(this, Observer { sleeps ->
            if (sleeps != null) {
                // Last 7 days
                val sevenDaysAgo = Calendar.getInstance()
                sevenDaysAgo.add(Calendar.DATE, -7)
                val last7days = DataModel.filterSleeps(sleeps, sevenDaysAgo.time)

                var count = findViewById<TextView>(R.id.last7days_sleeps)
                count.text = DataModel.getSleepCountStat(last7days)
                var average = findViewById<TextView>(R.id.last7days_average)
                average.text = DataModel.getSleepDurationStat(last7days)
                var daily = findViewById<TextView>(R.id.last7days_daily)
                daily.text = DataModel.getSleepDurationDailyStat(last7days)

                // This year
                val startOfYear = Calendar.getInstance()
                startOfYear.set(Calendar.DAY_OF_YEAR, 1)
                val thisYear = DataModel.filterSleeps(sleeps, startOfYear.time)

                count = findViewById<TextView>(R.id.thisyear_sleeps)
                count.text = DataModel.getSleepCountStat(thisYear)
                average = findViewById<TextView>(R.id.thisyear_average)
                average.text = DataModel.getSleepDurationStat(thisYear)
                daily = findViewById<TextView>(R.id.thisyear_daily)
                daily.text = DataModel.getSleepDurationDailyStat(thisYear)

                // All time, i.e. no filter
                count = findViewById<TextView>(R.id.alltime_sleeps)
                count.text = DataModel.getSleepCountStat(sleeps)
                average = findViewById<TextView>(R.id.alltime_average)
                average.text = DataModel.getSleepDurationStat(sleeps)
                daily = findViewById<TextView>(R.id.alltime_daily)
                daily.text = DataModel.getSleepDurationDailyStat(sleeps)
            }
        })
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
