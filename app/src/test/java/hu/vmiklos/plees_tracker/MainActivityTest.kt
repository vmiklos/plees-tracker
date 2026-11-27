/*
 * Copyright 2026 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import android.app.NotificationManager
import android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS
import android.app.NotificationManager.INTERRUPTION_FILTER_ALL
import android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import java.util.Calendar
import java.util.concurrent.Executor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/**
 * Tests for MainActivity, run on the JVM with Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class MainActivityTest {
    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        preferences = PreferenceManager.getDefaultSharedPreferences(context)
        notificationManager = context.getSystemService(NotificationManager::class.java)
        DataModel.init(context, preferences)
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

    @Test
    fun testNoteIndicator() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean("show_rating", true)
            .commit()

        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        idle()

        // Given no sleeps and creating one:
        DataModel.database.clearAllTables()
        createSleep()
        idle()

        // When editing the comment:
        val sleep = runBlocking { DataModel.database.sleepDao().getAll().first() }
        sleep.comment = "my note"
        runBlocking { DataModel.database.sleepDao().update(sleep) }
        idle()

        // Then make sure the note indicator is visible:
        val recyclerView = activity.findViewById<RecyclerView>(R.id.sleeps)
        val viewHolder = recyclerView.findViewHolderForAdapterPosition(0)
            ?: throw AssertionError("no view holder")
        val indicator = viewHolder.itemView.findViewById<View>(R.id.sleep_item_has_comment)
        assertNotEquals(View.GONE, indicator.visibility)
    }

    private fun setupDnd(filter: Int, enabled: Boolean = true) {
        preferences.edit().putBoolean("enable_dnd", enabled).commit()
        shadowOf(notificationManager).setNotificationPolicyAccessGranted(true)
        notificationManager.setInterruptionFilter(filter)
    }

    private fun toggleSleep(activity: MainActivity) {
        activity.findViewById<LinearLayout>(R.id.start_stop_layout).performClick()
        idle()
    }

    @Test
    fun testDndRestoredAfterRecreate() {
        // Given DND enabled on Android 14 with alarms-only mode active:
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 34)
        setupDnd(INTERRUPTION_FILTER_ALARMS)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        idle()

        // When starting, recreating the activity, and stopping sleep:
        toggleSleep(controller.get())
        controller.recreate()
        idle()
        toggleSleep(controller.get())

        // Then the pre-sleep interruption filter is restored:
        assertEquals(INTERRUPTION_FILTER_ALARMS, notificationManager.currentInterruptionFilter)
    }

    @Test
    fun testDndClearedOnStop() {
        // Given DND enabled with alarms-only mode active on API 37:
        setupDnd(INTERRUPTION_FILTER_ALARMS)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        idle()

        // When starting sleep, disabling enable_dnd, and stopping sleep:
        toggleSleep(activity)
        preferences.edit().putBoolean("enable_dnd", false).commit()
        toggleSleep(activity)

        // Then DND is cleared on stop:
        assertEquals(INTERRUPTION_FILTER_ALL, notificationManager.currentInterruptionFilter)
    }

    @Test
    fun testDndDropsStaleSavedFilter() {
        // Given enable_dnd disabled on Android 14 with a stale saved filter:
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 34)
        setupDnd(INTERRUPTION_FILTER_ALL, enabled = false)
        preferences.edit().putInt("current_dnd", INTERRUPTION_FILTER_PRIORITY).commit()
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        idle()

        // When starting and stopping sleep:
        toggleSleep(activity)
        toggleSleep(activity)

        // Then the stale saved filter is dropped and DND stays off:
        assertEquals(INTERRUPTION_FILTER_ALL, notificationManager.currentInterruptionFilter)
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
