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
import org.junit.Assert.assertEquals

open class UITestBase {
    protected val timeout: Long = 5000
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    protected val pkg = instrumentation.processName
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
        device.pressMenu()
        findObjectByText("Delete All Sleep").click()
        findObjectByText("YES").click()
    }

    protected fun createSleep() {
        val startStop = findObjectByRes("start_stop")
        startStop.click()
        startStop.click()
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */