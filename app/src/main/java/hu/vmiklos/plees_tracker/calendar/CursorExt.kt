/*
 * Copyright 2023 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker.calendar

import android.database.Cursor

/**
 * Apply a transformation [block] to all records in a Cursor
 */
fun <T> Cursor.map(block: (Cursor) -> T): List<T> {
    val items = mutableListOf<T>()
    use {
        while (!it.isClosed && it.moveToNext()) {
            items += block(it)
        }
    }
    return items
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
