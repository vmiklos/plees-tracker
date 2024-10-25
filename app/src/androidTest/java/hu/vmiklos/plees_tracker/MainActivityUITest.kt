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
    fun testCreate() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val pkg = instrumentation.processName
        val timeout: Long = 5000

        val startStop = device.wait(Until.findObject(By.res(pkg, "start_stop")), timeout)
        startStop.click()
        startStop.click()

        val sleeps = device.wait(
            Until.findObject(By.res(pkg, "fragment_stats_sleeps").text("1")),
            timeout
        )
        assertNotNull(sleeps)
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
