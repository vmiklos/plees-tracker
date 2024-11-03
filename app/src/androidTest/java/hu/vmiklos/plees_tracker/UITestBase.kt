/*
 * Copyright 2024 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.Calendar
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals

open class UITestBase {
    private val timeout: Long = 5000
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val pkg = instrumentation.processName
    protected val device = UiDevice.getInstance(instrumentation)

    protected fun findObjectByRes(resourceId: String): UiObject2 {
        return device.wait(Until.findObject(By.res(pkg, resourceId)), timeout)
    }

    protected fun findObjectByText(text: String): UiObject2 {
        return device.wait(Until.findObject(By.text(text)), timeout)
    }

    protected fun findObjectByDesc(desc: String): UiObject2 {
        return device.wait(Until.findObject(By.desc(desc)), timeout)
    }

    protected fun assertResText(resourceId: String, textValue: String) {
        device.wait(Until.findObject(By.res(pkg, resourceId).text(textValue)), timeout)
        val obj = device.findObject(By.res(pkg, resourceId))
        assertEquals(textValue, obj.text)
    }

    protected fun resetDatabase() {
        DataModel.database.clearAllTables()
        device.waitForIdle()
    }

    protected fun createSleep() {
        val sleep = Sleep()
        val start = Calendar.getInstance()
        start.set(Calendar.HOUR_OF_DAY, 9)
        sleep.start = start.timeInMillis
        val stop = Calendar.getInstance()
        start.set(Calendar.HOUR_OF_DAY, 23)
        sleep.stop = stop.timeInMillis
        runBlocking {
            DataModel.database.sleepDao().insert(sleep)
        }
        device.waitForIdle()
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
