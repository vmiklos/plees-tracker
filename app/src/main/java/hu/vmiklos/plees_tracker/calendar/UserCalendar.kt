/*
 * Copyright 2020 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker.calendar

import android.database.Cursor

/**
 * Represents a single calendar record, as found within the user's connected calendar
 */
class UserCalendar(cursor: Cursor) {

    val id: String
    val name: String
    val owner: String

    init {
        id = cursor.getString(CalendarImport.CALENDAR_PROJECTION_ID)
        name = cursor.getString(CalendarImport.CALENDAR_PROJECTION_DISPLAY_NAME)
        owner = cursor.getString(CalendarImport.CALENDAR_PROJECTION_ACCOUNT_NAME)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UserCalendar

        if (id != other.id) return false
        if (name != other.name) return false
        if (owner != other.owner) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + owner.hashCode()
        return result
    }

    override fun toString(): String {
        return "UserCalendar(id='$id', name='$name', owner='$owner')"
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
