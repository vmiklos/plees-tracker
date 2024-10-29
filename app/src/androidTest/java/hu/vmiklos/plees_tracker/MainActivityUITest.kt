/*
 * Copyright 2024 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.Direction
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
    fun testCreateAndRead() {
        // Given no sleeps:
        resetDatabase()

        // When creating one:
        createSleep()

        // Then make sure we have one sleep:
        assertResText("fragment_stats_sleeps", "1")
    }

    @Test
    fun testDelete() {
        // Given a sleep:
        resetDatabase()
        createSleep()

        // When deleting one:
        val sleepSwipeable = findObjectByRes("sleep_swipeable")
        sleepSwipeable.swipe(Direction.RIGHT, 1F)

        // Then make sure we have no sleeps:
        assertResText("fragment_stats_sleeps", "0")
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
