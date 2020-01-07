/*
 * Copyright 2019 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker

import org.junit.Test

import java.util.ArrayList
import java.util.Date

import org.junit.Assert.*

/**
 * Unit tests for DataModel.
 */
class DataModelUnitTest {
    @Test
    fun testFormatDuration() {
        val actual = DataModel.formatDuration(61)
        assertEquals("0:01:01", actual)
    }

    @Test
    fun testFormatTimestamp() {
        val actual = DataModel.formatTimestamp(Date(0))
        assertTrue(actual.startsWith("1970-01-01"))
    }

    @Test
    fun testGetSleepCountStat() {
        val sleeps = ArrayList<Sleep>()
        val sleep = Sleep()
        sleep.start = 10000
        sleep.stop = 20000
        sleeps.add(sleep)
        sleeps.add(sleep)
        assertEquals("2", DataModel.getSleepCountStat(sleeps))
    }

    @Test
    fun testGetSleepDurationStat() {
        val sleeps = ArrayList<Sleep>()
        // 10 seconds.
        var sleep = Sleep()
        sleep.start = 10000
        sleep.stop = 20000
        sleeps.add(sleep)

        // 20 seconds.
        sleep = Sleep()
        sleep.start = 10000
        sleep.stop = 30000
        sleeps.add(sleep)

        assertEquals("0:00:15",
                DataModel.getSleepDurationStat(sleeps))
    }
}