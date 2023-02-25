/*
 * Copyright 2023 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
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
import java.time.Duration
import java.util.Calendar
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * Instrumented tests for MainActivity.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityInstrumentedTest {

    @get:Rule
    var activityScenarioRule = ActivityScenarioRule(MainActivity::class.java)

    @get:Rule
    var tempFolder = TemporaryFolder()

    private lateinit var exportFile: File
    private lateinit var database: AppDatabase
    private lateinit var context: Context

    @Before
    fun setup() {
        exportFile = tempFolder.newFile("sleeps.csv")
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        DataModel.database = database
        Intents.init()
    }

    @After
    fun cleanup() {
        database.close()
        Intents.release()
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
        val sleep = Sleep()
        sleep.start = Calendar.getInstance().timeInMillis - Duration.ofHours(1).toMillis()
        sleep.stop = Calendar.getInstance().timeInMillis
        database.sleepDao().insert(sleep)
        assertEquals(1, database.sleepDao().getAll().size)

        // Export.
        exportToFile()

        // Clear the database.
        database.clearAllTables()
        assertEquals(0, database.sleepDao().getAll().size)

        // Import.
        importFromFile()
        assertEquals(1, database.sleepDao().getAll().size)
    }

    @Test
    fun testDoesNotImportDuplicate() = runBlocking {
        // Create one sleep.
        val sleep = Sleep()
        sleep.start = Calendar.getInstance().timeInMillis
        sleep.stop = Calendar.getInstance().timeInMillis + Duration.ofHours(1).toMillis()
        database.sleepDao().insert(sleep)
        assertEquals(1, database.sleepDao().getAll().size)

        // Export.
        exportToFile()

        // Import.
        importFromFile()
        assertEquals(1, database.sleepDao().getAll().size)
    }

    private fun exportToFile() {
        val intent = Intent()
        intent.data = Uri.fromFile(exportFile)
        val result = ActivityResult(Activity.RESULT_OK, intent)
        intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(result)
        openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)
        onView(withText(context.getString(R.string.export_file_item))).perform(click())
    }

    private fun importFromFile() {
        val intent = Intent()
        intent.data = Uri.fromFile(exportFile)
        val result = ActivityResult(Activity.RESULT_OK, intent)
        intending(hasAction(Intent.ACTION_GET_CONTENT)).respondWith(result)
        openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)
        onView(withText(context.getString(R.string.import_file_item))).perform(click())
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
