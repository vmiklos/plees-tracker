/*
 * Copyright 2019 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/**
 * Accesses the database of Sleep objects.
 */
@Dao
public interface SleepDao {
    @Query("SELECT * FROM sleep") List<Sleep> getAll();

    @Insert void insert(Sleep sleep);

    @Query("DELETE FROM sleep") public void deleteAll();
}
