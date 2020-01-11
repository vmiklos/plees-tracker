/*
 * Copyright 2019 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

/**
 * Accesses the database of Sleep objects.
 */
@Dao
interface SleepDao {
    @Query("SELECT * FROM sleep")
    fun getAll(): List<Sleep>
    @Query("SELECT * FROM sleep ORDER BY sid DESC")
    fun getAllLive(): LiveData<List<Sleep>>

    @Insert
    fun insert(sleep: Sleep)

    @Delete
    fun delete(sleep: Sleep)

    @Query("DELETE FROM sleep")
    fun deleteAll()
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
