/*
 * Copyright 2019 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import java.io.File
import java.util.Calendar
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for MainActivity.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityInstrumentedTest {

    @get:Rule
    var activityScenarioRule = ActivityScenarioRule(MainActivity::class.java)

    private lateinit var database: AppDatabase
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        DataModel.database = database
    }

    @Test
    fun testCountStat() = runBlocking {
        onView(withId(R.id.start_stop)).perform(click())
        onView(withId(R.id.start_stop)).perform(click())
        assertEquals(1, database.sleepDao().getAll().size)
    }

    @Test
    fun testImportExport() = runBlocking {
        // Create one sleep.
        DataModel.start = Calendar.getInstance().time
        DataModel.stop = Calendar.getInstance().time
        DataModel.storeSleep()
        assertEquals(1, database.sleepDao().getAll().size)

        // Export.
        Intents.init()
        val resultData = Intent()
        val file = File(context.filesDir, "sleeps.csv")
        resultData.data = Uri.fromFile(file)
        val result = ActivityResult(Activity.RESULT_OK, resultData)
        intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(result)
        openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)
        onView(withText(context.getString(R.string.export_file_item))).perform(click())

        // Import.
        intending(hasAction(Intent.ACTION_GET_CONTENT)).respondWith(result)
        openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)
        onView(withText(context.getString(R.string.import_file_item))).perform(click())
        assertEquals(2, database.sleepDao().getAll().size)
        Intents.release()
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
