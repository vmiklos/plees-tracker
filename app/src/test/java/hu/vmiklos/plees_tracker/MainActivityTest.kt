/*
 * Copyright 2026 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import android.content.Context
import android.os.Looper
import android.widget.LinearLayout
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
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

/**
 * Tests for MainActivity, run on the JVM with Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class MainActivityTest {
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
        DataModel.start = null
        DataModel.stop = null
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun createSleep() {
        val sleep = Sleep()
        val start = Calendar.getInstance()
        start.set(Calendar.HOUR_OF_DAY, 9)
        sleep.start = start.timeInMillis
        val stop = Calendar.getInstance()
        stop.set(Calendar.HOUR_OF_DAY, 23)
        stop.set(Calendar.MINUTE, 59)
        sleep.stop = stop.timeInMillis
        runBlocking {
            DataModel.database.sleepDao().insert(sleep)
        }
    }

    @Test
    fun testCreate() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        idle()

        // Given no sleeps:
        DataModel.database.clearAllTables()

        // When creating one:
        val startStop = activity.findViewById<LinearLayout>(R.id.start_stop_layout)
        startStop.performClick()
        idle()
        startStop.performClick()
        idle()

        // Then make sure we have one sleep:
        val sleepCount = runBlocking {
            DataModel.database.sleepDao().getAll().count()
        }
        assertEquals(1, sleepCount)
    }

    @Test
    fun testRead() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        idle()

        // Given no sleeps and creating one:
        DataModel.database.clearAllTables()
        createSleep()
        idle()

        // Then make sure the sleep count widget reads the correct value:
        assertEquals(
            "1",
            activity.findViewById<TextView>(R.id.fragment_stats_sleeps).text.toString()
        )
    }

    @Test
    fun testDelete() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        idle()

        // Given a sleep:
        DataModel.database.clearAllTables()
        createSleep()
        idle()
        assertEquals(
            "1",
            activity.findViewById<TextView>(R.id.fragment_stats_sleeps).text.toString()
        )

        // When deleting one:
        val recyclerView = activity.findViewById<RecyclerView>(R.id.sleeps)
        val viewHolder = recyclerView.findViewHolderForAdapterPosition(0)
            ?: throw AssertionError("no view holder")
        // Simulate the swipe handled by ItemTouchHelper
        val adapter = recyclerView.adapter as SleepsAdapter
        val viewModelField = MainActivity::class.java.getDeclaredField("viewModel")
        viewModelField.isAccessible = true
        val viewModel = viewModelField.get(activity) as MainViewModel
        val callback = SleepTouchCallback(
            context,
            context.contentResolver,
            viewModel,
            adapter
        )
        callback.onSwiped(viewHolder, 4) // ItemTouchHelper.RIGHT
        idle()

        // Snackbar should be shown
        // ShadowSnackbar.latestSnackbar
        // Undo if desired? No, delete should remain - but click undo would restore it;
        // just ensure we didn't undo. The snackbar is fine.

        // Then make sure we have no sleeps:
        assertEquals(
            "0",
            activity.findViewById<TextView>(R.id.fragment_stats_sleeps).text.toString()
        )
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
