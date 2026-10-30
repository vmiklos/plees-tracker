/*
 * Copyright 2026 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.room.Room
import java.util.Calendar
import java.util.concurrent.Executor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * Tests for SleepActivity, run on the JVM with Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class SleepActivityTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        DataModel.init(context, PreferenceManager.getDefaultSharedPreferences(context))
        DataModel.database.close()
        // Run Room queries on the calling thread, so the activity's viewModelScope coroutines
        // can be driven deterministically by idling the main looper.
        val direct = Executor { it.run() }
        DataModel.database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(direct)
            .setTransactionExecutor(direct)
            .build()
        ShadowDialog.reset()
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun createSleep(): Int {
        val sleep = Sleep()
        val start = Calendar.getInstance()
        start.set(Calendar.HOUR_OF_DAY, 9)
        sleep.start = start.timeInMillis
        val stop = Calendar.getInstance()
        stop.set(Calendar.HOUR_OF_DAY, 23)
        stop.set(Calendar.MINUTE, 59)
        sleep.stop = stop.timeInMillis
        return runBlocking {
            DataModel.database.sleepDao().insert(sleep)
            DataModel.database.sleepDao().getAll().first().sid
        }
    }

    private fun setTime(activity: SleepActivity, viewId: Int, hour: Int, minute: Int) {
        activity.findViewById<TextView>(viewId).performClick()
        idle()
        val dialog = ShadowDialog.getLatestDialog() as TimePickerDialog
        dialog.updateTime(hour, minute)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idle()
    }

    @Test
    fun testUpdate() {
        val sid = createSleep()
        val intent = Intent(context, SleepActivity::class.java).putExtra("sid", sid)
        val activity = Robolectric.buildActivity(SleepActivity::class.java, intent).setup().get()
        idle()

        setTime(activity, R.id.sleep_start_time, 10, 0)
        setTime(activity, R.id.sleep_stop_time, 22, 0)

        assertEquals(
            "10:00",
            activity.findViewById<TextView>(R.id.sleep_start_time).text.toString()
        )
        assertEquals(
            "22:00",
            activity.findViewById<TextView>(R.id.sleep_stop_time).text.toString()
        )
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
