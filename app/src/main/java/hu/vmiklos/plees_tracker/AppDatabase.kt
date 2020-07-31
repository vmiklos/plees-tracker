/*
 * Copyright 2019 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Contains the database holder and serves as the main access point for the
 * stored data.
 */
@Database(entities = [Sleep::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sleepDao(): SleepDao
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
