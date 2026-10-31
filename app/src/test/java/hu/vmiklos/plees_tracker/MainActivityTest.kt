/*
 * Copyright 2026 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import android.content.Context
import android.os.Looper
import android.widget.LinearLayout
import androidx.preference.PreferenceManager
import androidx.room.Room
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
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
