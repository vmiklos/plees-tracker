/*
 * Copyright 2019 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for DataModel.
 */
public class DataModelUnitTest
{
    @Test public void testFormatDuration()
    {
        String actual = DataModel.formatDuration(61);
        assertEquals("0:01:01", actual);
    }

    @Test public void testFormatTimestamp()
    {
        String actual = DataModel.formatTimestamp(new Date(0));
        assertTrue(actual.startsWith("1970-01-01"));
    }

    @Test public void testGetSleepCountStat()
    {
        List<Sleep> sleeps = new ArrayList<>();
        Sleep sleep = new Sleep();
        sleep.start = 10000;
        sleep.stop = 20000;
        sleeps.add(sleep);
        sleeps.add(sleep);
        assertEquals("2", DataModel.getSleepCountStat(sleeps));
    }

    @Test public void testGetSleepDurationStat()
    {
        List<Sleep> sleeps = new ArrayList<>();
        // 10 seconds.
        Sleep sleep = new Sleep();
        sleep.start = 10000;
        sleep.stop = 20000;
        sleeps.add(sleep);

        // 20 seconds.
        sleep = new Sleep();
        sleep.start = 10000;
        sleep.stop = 30000;
        sleeps.add(sleep);

        assertEquals("0:00:15", DataModel.getSleepDurationStat(sleeps));
    }
}