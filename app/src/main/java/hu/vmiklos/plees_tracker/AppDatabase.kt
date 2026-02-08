/*
 * Copyright 2023 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Contains the database holder and serves as the main access point for the
 * stored data.
 */
@Database(entities = [Sleep::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sleepDao(): SleepDao
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
