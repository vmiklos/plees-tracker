/*
 * Copyright 2020 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.TextView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.util.*
import kotlin.collections.ArrayList


/**
 * This is the view model of SleepActivity, providing coroutine scopes.
 */
class SleepViewModel : ViewModel() {

    private val sleepFactsList = ArrayList<String>()
    var rand: Random? = null


    fun showSleep(activity: SleepActivity, sid: Int) {
        viewModelScope.launch {
            val sleep = DataModel.getSleepById(sid)

            val start = activity.findViewById<TextView>(R.id.sleep_start)
            start.text = DataModel.formatTimestamp(Date(sleep.start))
            val stop = activity.findViewById<TextView>(R.id.sleep_stop)
            stop.text = DataModel.formatTimestamp(Date(sleep.stop))
            val sleepFacts = activity.findViewById<TextView>(R.id.sleep_facts_text)


            // init Sleep Facts Array
            initSleepFactsArray()

            // Generate Random Facts when Opening Sleep Activity
            val facts: String = randomFacts(sleepFactsList)
            sleepFacts.text = facts
        }
    }

    private fun randomFacts(sleepFactsList: ArrayList<String>): String {

        rand = Random()
        return sleepFactsList[rand!!.nextInt(sleepFactsList.size)]

    }

    private fun initSleepFactsArray() {
        sleepFactsList.add("Longer sleep has been shown to improve many aspects of athletic and physical performance.")
        sleepFactsList.add("Short sleep duration is associated with an increased risk of weight gain and obesity in both children and adults.")
        sleepFactsList.add("Poor sleep affects hormones that regulate appetite. Those who get adequate sleep tend to eat fewer calories than those who don’t.")
        sleepFactsList.add("Good sleep can maximize problem-solving skills and enhance memory. Poor sleep has been shown to impair brain function.")
        sleepFactsList.add("Sleeping less than 7–8 hours per night is linked to an increased risk of heart disease and stroke.")
        sleepFactsList.add("Poor sleeping patterns are strongly linked to depression, particularly for those with a sleeping disorder.")
        sleepFactsList.add("Getting at least 8 hours of sleep can improve your immune function and help fight the common cold.")
    }


    fun editSleep(activity: SleepActivity, sid: Int, isStart: Boolean) {
        viewModelScope.launch {
            val sleep = DataModel.getSleepById(sid)

            val dateTime = Calendar.getInstance()
            dateTime.time =
                if (isStart) {
                    Date(sleep.start)
                } else {
                    Date(sleep.stop)
                }
            DatePickerDialog(activity,
                    DatePickerDialog.OnDateSetListener { _/*view*/, year, monthOfYear, dayOfMonth ->
                dateTime.set(year, monthOfYear, dayOfMonth)
                TimePickerDialog(activity,
                        TimePickerDialog.OnTimeSetListener { _/*view*/, hourOfDay, minute ->
                    dateTime[Calendar.HOUR_OF_DAY] = hourOfDay
                    dateTime[Calendar.MINUTE] = minute
                    if (isStart) {
                        sleep.start = dateTime.time.time
                    } else {
                        sleep.stop = dateTime.time.time
                    }
                    updateSleep(activity, sleep)
                }, dateTime[Calendar.HOUR_OF_DAY], dateTime[Calendar.MINUTE], false).show()
            }, dateTime[Calendar.YEAR], dateTime[Calendar.MONTH], dateTime[Calendar.DATE]).show()
        }
    }

    private fun updateSleep(activity: SleepActivity, sleep: Sleep) {
        viewModelScope.launch {
            DataModel.updateSleep(sleep)
            showSleep(activity, sleep.sid)
        }
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
