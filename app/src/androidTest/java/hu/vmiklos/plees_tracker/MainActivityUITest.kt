/*
 * Copyright 2024 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for MainActivity.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityUITest : UITestBase() {
    @JvmField
    @Rule
    var activityScenarioRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun testRead() {
        // Given no sleeps and creating one:
        resetDatabase()
        createSleep()

        // When waiting for 'SELECT' to be executed:
        device.waitForIdle()

        // Then make sure the sleep count widget reads the correct value:
        assertResText("fragment_stats_sleeps", "1")
    }

    @Test
    fun testDelete() {
        // Given a sleep:
        resetDatabase()
        createSleep()

        // When deleting one:
        while (true) {
            try {
                val sleepSwipeable = findObjectByRes("sleep_swipeable")
                sleepSwipeable.swipe(Direction.RIGHT, 1F)
                break
            } catch (e: StaleObjectException) {
            }
        }

        // Then make sure we have no sleeps:
        assertResText("fragment_stats_sleeps", "0")
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
