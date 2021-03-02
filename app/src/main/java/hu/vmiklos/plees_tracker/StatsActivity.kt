/*
 * Copyright 2020 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import java.util.Calendar

/**
 * This activity provides additional stats for a limited period of time. This is in contrast with
 * the main activity, which considers all sleeps within the specified duration.
 */
class StatsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_stats)

        title = getString(R.string.stats)
        // Show a back button.
        actionBar?.setDisplayHomeAsUpEnabled(true)

        DataModel.sleepsLive.observe(
            this,
            { sleeps ->
                if (sleeps != null) {
                    val fragments = supportFragmentManager

                    // Last week
                    val lastWeek = Calendar.getInstance()
                    lastWeek.add(Calendar.DATE, -7)
                    val lastWeekSleeps = DataModel.filterSleeps(sleeps, lastWeek.time)
                    val lastWeekFragment = fragments.findFragmentById(R.id.last_week_body)
                    populateFragment(lastWeekFragment, lastWeekSleeps)

                    // Last two weeks
                    val lastTwoWeeks = Calendar.getInstance()
                    lastTwoWeeks.add(Calendar.DATE, -14)
                    val lastTwoWeekSleeps = DataModel.filterSleeps(sleeps, lastTwoWeeks.time)
                    val lastTwoWeeksFragment = fragments.findFragmentById(R.id.last_two_weeks_body)
                    populateFragment(lastTwoWeeksFragment, lastTwoWeekSleeps)

                    // Last month
                    val lastMonth = Calendar.getInstance()
                    lastMonth.add(Calendar.DATE, -30)
                    val lastMonthSleeps = DataModel.filterSleeps(sleeps, lastMonth.time)
                    val lastMonthFragments = fragments.findFragmentById(R.id.last_month_body)
                    populateFragment(lastMonthFragments, lastMonthSleeps)

                    // Last year
                    val lastYear = Calendar.getInstance()
                    lastYear.add(Calendar.DATE, -365)
                    val lastYearSleeps = DataModel.filterSleeps(sleeps, lastYear.time)
                    val lastYearFragment = fragments.findFragmentById(R.id.last_year_body)
                    populateFragment(lastYearFragment, lastYearSleeps)

                    // All time, i.e. no filter
                    val allTimeFragment = fragments.findFragmentById(R.id.all_time_body)
                    populateFragment(allTimeFragment, sleeps)
                }
            }
        )
    }

    private fun populateFragment(fragment: Fragment?, sleeps: List<Sleep>) {
        val view = fragment?.view
        val count = view?.findViewById<TextView>(R.id.fragment_stats_sleeps)
        count?.text = DataModel.getSleepCountStat(sleeps)
        val average = view?.findViewById<TextView>(R.id.fragment_stats_average)
        average?.text = DataModel.getSleepDurationStat(sleeps)
        val daily = view?.findViewById<TextView>(R.id.fragment_stats_daily)
        daily?.text = DataModel.getSleepDurationDailyStat(sleeps)
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
