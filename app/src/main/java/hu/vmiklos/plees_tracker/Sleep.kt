/*
 * Copyright 2019 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker

import android.text.format.DateUtils
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents one tracked sleep.
 */
@Entity
class Sleep {
    @PrimaryKey(autoGenerate = true)
    var sid: Int = 0

    @ColumnInfo(name = "start_date")
    var start: Long = 0

    @ColumnInfo(name = "stop_date")
    var stop: Long = 0

    @ColumnInfo(name = "rating")
    var rating: Long = 0

    private val lengthMs
        get() = stop - start

    val lengthHours
        get() = lengthMs.toFloat() / DateUtils.HOUR_IN_MILLIS
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
