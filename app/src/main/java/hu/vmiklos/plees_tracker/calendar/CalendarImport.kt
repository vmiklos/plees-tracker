/*
 * Copyright 2023 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker.calendar

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import hu.vmiklos.plees_tracker.Sleep

/**
 * Singleton helper for importing sleeps from a user's local calendars
 */
object CalendarImport {

    // Default event title to find within calendars
    private const val DEFAULT_EVENT_TITLE = "Sleep"

    const val CALENDAR_PROJECTION_ID = 0
    const val CALENDAR_PROJECTION_ACCOUNT_NAME = 1
    const val CALENDAR_PROJECTION_DISPLAY_NAME = 2

    private val CALENDAR_PROJECTION: Array<String> = arrayOf(
        Calendars._ID, // 0
        Calendars.ACCOUNT_NAME, // 1
        Calendars.CALENDAR_DISPLAY_NAME, // 2
    )

    const val EVENT_PROJECTION_CAL_ID = 0
    const val EVENT_PROJECTION_TITLE = 1
    const val EVENT_PROJECTION_ID = 2
    const val EVENT_PROJECTION_START = 3
    const val EVENT_PROJECTION_END = 4

    private val EVENT_PROJECTION: Array<String> = arrayOf(
        Events.CALENDAR_ID, // 0
        Events.TITLE, // 1
        Events._ID, // 2
        Events.DTSTART, // 3
        Events.DTEND, // 4
    )

    fun queryForCalendars(context: Context): List<UserCalendar> {
        val uri: Uri = Calendars.CONTENT_URI
        val contentResolver: ContentResolver = context.contentResolver
        contentResolver.query(
            uri,
            CALENDAR_PROJECTION,
            null,
            null,
            null
        ).use { cursor ->
            // Map to UserCalendar
            return cursor?.map(::UserCalendar).orEmpty()
        }
    }

    fun queryForEvents(
        context: Context,
        calendarId: String,
        title: String = DEFAULT_EVENT_TITLE
    ): List<UserEvent> {

        val uri = Events.CONTENT_URI
        val contentResolver: ContentResolver = context.contentResolver

        // Select events with a title containing the supplied param (case insensitive)
        // and where there is a non-null start time as well as a non-null (or zero) end time
        val selection = """
            (${Events.CALENDAR_ID} = ?) AND 
            (${Events.TITLE} LIKE ? COLLATE NOCASE) AND 
            (${Events.DTSTART} IS NOT NULL) AND 
            (${Events.DTEND} IS NOT NULL AND ${Events.DTEND} != 0)
        """.trimIndent()

        val selectionArgs = arrayOf(calendarId, "%$title%")

        contentResolver.query(
            uri,
            EVENT_PROJECTION,
            selection,
            selectionArgs,
            null
        ).use { cursor ->
            // Map to UserEvent and avoid any 'all-day' events
            return cursor?.map(::UserEvent).orEmpty()
        }
    }

    fun mapEventToSleep(event: UserEvent): Sleep {
        return Sleep().apply {
            start = event.start
            stop = event.end
        }
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
