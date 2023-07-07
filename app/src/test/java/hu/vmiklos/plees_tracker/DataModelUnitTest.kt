/*
 * Copyright 2023 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import java.util.ArrayList
import java.util.Calendar
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for DataModel.
 */
class DataModelUnitTest {
    @Test
    fun testFormatDuration() {
        val actual = DataModel.formatDuration(61, false)
        assertEquals("0:01:01", actual)
    }

    @Test
    fun testFormatTimestamp() {
        val actual = DataModel.formatTimestamp(Date(0), false)
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

        assertEquals(
            "0:00:15",
            DataModel.getSleepDurationStat(sleeps, false)
        )
    }

    @Test
    fun testGetSleepDurationDailyStat() {
        val sleeps = ArrayList<Sleep>()
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = 0

        // 1+1 seconds on the first day
        var sleep = Sleep()
        calendar.set(2020, 2, 12, 21, 26, 56)
        sleep.start = calendar.timeInMillis
        calendar.set(2020, 2, 12, 21, 26, 57)
        sleep.stop = calendar.timeInMillis
        sleeps.add(sleep)

        calendar.set(2020, 2, 12, 21, 26, 58)
        sleep.start = calendar.timeInMillis
        calendar.set(2020, 2, 12, 21, 26, 59)
        sleep.stop = calendar.timeInMillis
        sleeps.add(sleep)

        // 10 + 10 seconds on the second day
        sleep = Sleep()
        calendar.set(2020, 2, 13, 21, 26, 29)
        sleep.start = calendar.timeInMillis
        calendar.set(2020, 2, 13, 21, 26, 39)
        sleep.stop = calendar.timeInMillis
        sleeps.add(sleep)

        calendar.set(2020, 2, 13, 21, 26, 49)
        sleep.start = calendar.timeInMillis
        calendar.set(2020, 2, 13, 21, 26, 59)
        sleep.stop = calendar.timeInMillis
        sleeps.add(sleep)

        // Note how this is 11, not 5.5.
        assertEquals(
            "0:00:11",
            DataModel.getSleepDurationDailyStat(sleeps, false, false)
        )
    }

    @Test
    fun testGetSleepDurationDailyStatEmptyDays() {
        val sleeps = ArrayList<Sleep>()
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = 0

        // 12 hours on 12th
        var sleep = Sleep()
        calendar.set(2020, 2, 12, 1, 26, 56)
        sleep.start = calendar.timeInMillis
        calendar.set(2020, 2, 12, 13, 26, 56)
        sleep.stop = calendar.timeInMillis
        sleeps.add(sleep)

        // 12 hours on 14th
        sleep = Sleep()
        calendar.set(2020, 2, 14, 1, 26, 29)
        sleep.start = calendar.timeInMillis
        calendar.set(2020, 2, 14, 13, 26, 29)
        sleep.stop = calendar.timeInMillis
        sleeps.add(sleep)

        // Note how this is 8 hours per day, not 12.
        assertEquals(
            "8:00:00",
            DataModel.getSleepDurationDailyStat(sleeps, false, false)
        )
    }

    @Test
    fun testAvgOfDailySumsIgnoreEmptyDays() {
        val sleeps = ArrayList<Sleep>()
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = 0

        // 5+1 hours on 12th
        var sleep = Sleep()
        calendar.set(2020, 2, 12, 0, 0, 0)
        sleep.start = calendar.timeInMillis
        calendar.set(2020, 2, 12, 5, 0, 0)
        sleep.stop = calendar.timeInMillis
        sleeps.add(sleep)
        sleep = Sleep()
        calendar.set(2020, 2, 12, 6, 0, 0)
        sleep.start = calendar.timeInMillis
        calendar.set(2020, 2, 12, 7, 0, 0)
        sleep.stop = calendar.timeInMillis
        sleeps.add(sleep)

        // 7 hours on 14th
        sleep = Sleep()
        calendar.set(2020, 2, 14, 0, 0, 0)
        sleep.start = calendar.timeInMillis
        calendar.set(2020, 2, 14, 7, 0, 0)
        sleep.stop = calendar.timeInMillis
        sleeps.add(sleep)

        // Note how this is 6.5 hours per day, not 4.33.
        assertEquals(
            "6:30:00",
            DataModel.getSleepDurationDailyStat(sleeps, false, true)
        )
    }

    @Test
    fun testFilterSleeps() {
        val sleeps = mutableListOf<Sleep>()
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = 0

        // 12 hours on 12th
        var sleep = Sleep()
        calendar.set(2020, 2, 12, 1, 26, 56)
        sleep.start = calendar.timeInMillis
        calendar.set(2020, 2, 12, 13, 26, 56)
        sleep.stop = calendar.timeInMillis
        sleeps.add(sleep)

        // 12 hours on 14th
        sleep = Sleep()
        calendar.set(2020, 2, 14, 1, 26, 29)
        sleep.start = calendar.timeInMillis
        calendar.set(2020, 2, 14, 13, 26, 29)
        sleep.stop = calendar.timeInMillis
        sleeps.add(sleep)

        // Note how the first is filtered out but not the second.
        calendar.set(2020, 2, 14, 12, 0, 0)
        val filtered = DataModel.filterSleeps(sleeps, calendar.time)
        assertEquals(filtered.size, 1)
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
