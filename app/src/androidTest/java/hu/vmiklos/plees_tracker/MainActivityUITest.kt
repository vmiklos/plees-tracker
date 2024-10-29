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
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
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
    private val timeout: Long = 5000
    val pkg = InstrumentationRegistry.getInstrumentation().processName

    private fun resetDatabase() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        device.pressMenu()
        val deleteAllSleep = device.wait(Until.findObject(By.text("Delete All Sleep")), timeout)
        deleteAllSleep.click()
        val yesButton = device.wait(Until.findObject(By.text("YES")), timeout)
        yesButton.click()
    }

    private fun getDevice(): UiDevice {
        return UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    private fun createSleep() {
        val device = getDevice()
        val startStop = device.wait(Until.findObject(By.res(pkg, "start_stop")), timeout)
        startStop.click()
        startStop.click()
    }

    private fun assertResText(resourceId: String, textValue: String) {
        val device = getDevice()
        device.wait(Until.findObject(By.res(pkg, resourceId).text(textValue)), timeout)
        val obj = device.findObject(By.res(pkg, resourceId))
        assertEquals(textValue, obj.text)
    }

    private fun findObjectByRes(resourceId: String): UiObject2 {
        val device = getDevice()
        return device.wait(Until.findObject(By.res(pkg, resourceId)), timeout)
    }

    private fun findObjectByText(text: String): UiObject2 {
        val device = getDevice()
        return device.wait(Until.findObject(By.text(text)), timeout)
    }

    private fun findObjectByDesc(desc: String): UiObject2 {
        val device = getDevice()
        return device.wait(Until.findObject(By.desc(desc)), timeout)
    }

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

        assertResText("sleep_start_time", "10:00")
        assertResText("sleep_stop_time", "22:00")
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
