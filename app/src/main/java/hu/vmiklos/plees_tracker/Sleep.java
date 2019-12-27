/*
 * Copyright 2019 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Represents one tracked sleep.
 */
@Entity class Sleep
{
    @PrimaryKey(autoGenerate = true) public int sid;

    @ColumnInfo(name = "start_date") public long start;

    @ColumnInfo(name = "stop_date") public long stop;
}