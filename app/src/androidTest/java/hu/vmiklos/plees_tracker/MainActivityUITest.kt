/*
 * Copyright 2024 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for MainActivity.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityUITest {
    @JvmField
    @Rule
    var activityScenarioRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun testCreateAndRead() {
        // Given no sleeps:
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val pkg = instrumentation.processName
        val timeout: Long = 5000
        device.pressMenu()
        val deleteAllSleep = device.wait(Until.findObject(By.text("Delete All Sleep")), timeout)
        deleteAllSleep.click()
        val yesButton = device.wait(Until.findObject(By.text("YES")), timeout)
        yesButton.click()

        // When creating one:
        val startStop = device.wait(Until.findObject(By.res(pkg, "start_stop")), timeout)
        startStop.click()
        startStop.click()

        // Then make sure we have one sleep:
        val sleeps = device.wait(
            Until.findObject(By.res(pkg, "fragment_stats_sleeps").text("1")),
            timeout
        )
        assertNotNull(sleeps)
    }

    @Test
    fun testDelete() {
        // Given a sleep:
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val pkg = instrumentation.processName
        val timeout: Long = 5000
        device.pressMenu()
        val deleteAllSleep = device.wait(Until.findObject(By.text("Delete All Sleep")), timeout)
        deleteAllSleep.click()
        val yesButton = device.wait(Until.findObject(By.text("YES")), timeout)
        yesButton.click()
        val startStop = device.wait(Until.findObject(By.res(pkg, "start_stop")), timeout)
        startStop.click()
        startStop.click()

        // When deleting one:
        val sleepSwipeable = device.wait(Until.findObject(By.res(pkg, "sleep_swipeable")), timeout)
        sleepSwipeable.swipe(Direction.RIGHT, 1F)

        // Then make sure we have no sleeps:
        val sleeps = device.wait(
            Until.findObject(By.res(pkg, "fragment_stats_sleeps").text("0")),
            timeout
        )
        assertNotNull(sleeps)
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
