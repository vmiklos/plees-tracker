/*
 * Copyright 2024 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for SleepActivity.
 */
@RunWith(AndroidJUnit4::class)
class SleepActivityUITest : UITestBase() {
    @JvmField
    @Rule
    var activityScenarioRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun testUpdate() {
        resetDatabase()
        createSleep()

        findObjectByRes("sleep_swipeable").click()
        findObjectByRes("sleep_start_time").click()
        findObjectByText("AM").click()
        findObjectByDesc("10").click()
        findObjectByDesc("0").click()
        findObjectByText("OK").click()
        findObjectByRes("sleep_stop_time").click()
        findObjectByText("PM").click()
        findObjectByDesc("10").click()
        findObjectByDesc("0").click()
        findObjectByText("OK").click()
        // In case the start was not updated (to prevent negative sleep length), do it again:
        findObjectByRes("sleep_start_time").click()
        findObjectByText("AM").click()
        findObjectByDesc("10").click()
        findObjectByDesc("0").click()
        findObjectByText("OK").click()

        assertResText("sleep_start_time", "10:00")
        assertResText("sleep_stop_time", "22:00")
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
