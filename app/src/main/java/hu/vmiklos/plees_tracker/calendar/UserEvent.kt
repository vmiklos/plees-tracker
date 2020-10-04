package hu.vmiklos.plees_tracker.calendar

import android.database.Cursor

class UserEvent {

    val id: String
    val calendarId: String
    val title: String
    val start: Long
    val end: Long

    constructor(id: String, calendarId: String, title: String, start: Long, end: Long){
        this.id = id
        this.calendarId = calendarId
        this.title = title
        this.start = start
        this.end = end
    }

    constructor(cursor: Cursor) {
        id = cursor.getString(CalendarImport.EVENT_PROJECTION_ID)
        calendarId = cursor.getString(CalendarImport.EVENT_PROJECTION_CAL_ID)
        title = cursor.getString(CalendarImport.EVENT_PROJECTION_TITLE)
        start = cursor.getLong(CalendarImport.EVENT_PROJECTION_START)
        end = cursor.getLong(CalendarImport.EVENT_PROJECTION_END)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UserEvent

        if (id != other.id) return false
        if (calendarId != other.calendarId) return false
        if (title != other.title) return false
        if (start != other.start) return false
        if (end != other.end) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + calendarId.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + start.hashCode()
        result = 31 * result + end.hashCode()
        return result
    }

    override fun toString(): String {
        return "UserEvent(id='$id', calendarId='$calendarId', title='$title', start='$start', end='$end')"
    }

}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
