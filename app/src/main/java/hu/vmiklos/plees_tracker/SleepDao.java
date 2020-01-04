/*
 * Copyright 2019 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/**
 * Accesses the database of Sleep objects.
 */
@Dao
public interface SleepDao {
    @Query("SELECT * FROM sleep") List<Sleep> getAll();
    @Query("SELECT * FROM sleep ORDER BY sid DESC")
    LiveData<List<Sleep>> getAllLive();

    @Insert void insert(Sleep sleep);

    @Delete void delete(Sleep sleep);

    @Query("DELETE FROM sleep") void deleteAll();
}
