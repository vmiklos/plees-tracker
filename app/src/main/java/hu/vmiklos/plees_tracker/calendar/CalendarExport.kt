/*
 * Copyright 2023 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker.calendar

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import hu.vmiklos.plees_tracker.R
import hu.vmiklos.plees_tracker.Sleep
import java.util.TimeZone

/**
 * Singleton helper for exporting sleeps to a user's local calendars
 */
object CalendarExport {

    /**
     * Exports a list of Sleep [sleepList] to the selected user calendar
     */
    fun exportSleep(context: Context, calendarId: String, sleepList: List<Sleep>) {
        sleepList.forEach { sleep ->
            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, sleep.start)
                put(CalendarContract.Events.DTEND, sleep.stop)
                put(CalendarContract.Events.TITLE, context.getString(R.string.exported_event_title))
                val description = context.getString(R.string.exported_event_description)
                put(CalendarContract.Events.DESCRIPTION, description)
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        }
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
