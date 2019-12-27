/*
 * Copyright 2019 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker;

import org.junit.Test;

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
}