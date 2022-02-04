/*
 * Copyright 2019 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LiveData
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import hu.vmiklos.plees_tracker.calendar.CalendarExport
import hu.vmiklos.plees_tracker.calendar.CalendarImport
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.apache.commons.csv.CSVRecord

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE sleep ADD COLUMN rating INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE sleep ADD COLUMN comment TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * Data model is the singleton shared state between the activity and the
 * service.
 */
object DataModel {

    lateinit var preferences: SharedPreferences

    var preferencesActivity: PreferencesActivity? = null

    var start: Date? = null
        set(start) {
            field = start
            // Save start timestamp in case the foreground service is killed.
            val editor = preferences.edit()
            field?.let {
                editor.putLong("start", it.time)
            }
            editor.apply()
        }

    var stop: Date? = null

    lateinit var database: AppDatabase

    val sleepsLive: LiveData<List<Sleep>>
        get() = database.sleepDao().getAllLive()

    var initialized: Boolean = false

    fun init(context: Context, preferences: SharedPreferences) {
        if (initialized) {
            return
        }

        this.preferences = preferences

        val start = preferences.getLong("start", 0)
        if (start > 0) {
            // Restore start timestamp in case the foreground service was
            // killed.
            this.start = Date(start)
        }
        database = Room.databaseBuilder(context, AppDatabase::class.java, "database")
            .addMigrations(MIGRATION_1_2)
            .addMigrations(MIGRATION_2_3)
            .build()
        initialized = true
    }

    suspend fun storeSleep() {
        val sleep = Sleep()
        start?.let {
            sleep.start = it.time
        }
        stop?.let {
            sleep.stop = it.time
        }
        database.sleepDao().insert(sleep)

        // Drop start timestamp from preferences, it's in the database now.
        val editor = preferences.edit()
        editor.remove("start")
        editor.apply()
    }

    suspend fun insertSleep(sleep: Sleep) {
        database.sleepDao().insert(sleep)
    }

    suspend fun insertSleeps(sleepList: List<Sleep>) {
        database.sleepDao().insert(sleepList)
    }

    suspend fun updateSleep(sleep: Sleep) {
        database.sleepDao().update(sleep)
    }

    suspend fun deleteSleep(sleep: Sleep) {
        database.sleepDao().delete(sleep)
    }

    suspend fun getSleepById(sid: Int): Sleep {
        return database.sleepDao().getById(sid)
    }

    fun getSleepsAfterLive(after: Date): LiveData<List<Sleep>> {
        return database.sleepDao().getAfterLive(after.time)
    }

    suspend fun importData(context: Context, cr: ContentResolver, uri: Uri) {
        val inputStream = cr.openInputStream(uri)
        val records: Iterable<CSVRecord> = CSVFormat.DEFAULT.parse(InputStreamReader(inputStream))
        // We have a speed vs memory usage trade-off here. Pay the cost of keeping all sleeps in
        // memory: the benefit is that inserting all of them once triggers a single notification of
        // observers. This means that importing 100s of sleeps is still ~instant, while it used to
        // take ~forever.
        val importedSleeps = mutableListOf<Sleep>()
        try {
            var first = true
            for (cells in records) {
                if (first) {
                    // Ignore the header.
                    first = false
                    continue
                }
                val sleep = Sleep()
                sleep.start = cells[1].toLong()
                sleep.stop = cells[2].toLong()
                if (cells.isSet(3)) {
                    sleep.rating = cells[3].toLong()
                }
                if (cells.isSet(4)) {
                    sleep.comment = cells[4]
                }
                importedSleeps.add(sleep)
            }
            val oldSleeps = database.sleepDao().getAll()
            val newSleeps = importedSleeps.subtract(oldSleeps.toSet())
            database.sleepDao().insert(newSleeps.toList())
        } catch (e: IOException) {
            Log.e(TAG, "importData: readLine() failed")
            return
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close()
                } catch (e: Exception) {
                }
            }
        }

        val text = context.getString(R.string.import_success)
        val duration = Toast.LENGTH_SHORT
        val toast = Toast.makeText(context, text, duration)
        toast.show()
    }

    suspend fun importDataFromCalendar(context: Context, calendarId: String) {
        // Query the calendar for events
        var importedSleeps = CalendarImport.queryForEvents(
            context, calendarId
        ).map(CalendarImport::mapEventToSleep)
        val oldSleeps = database.sleepDao().getAll()
        val newSleeps = importedSleeps.subtract(oldSleeps.toSet())

        // Insert the list of Sleep into DB
        insertSleeps(newSleeps.toList())

        // Show how many sleeps were imported.
        val text = context.resources.getQuantityString(
            R.plurals.imported_items,
            newSleeps.size,
            newSleeps.size
        )
        val duration = Toast.LENGTH_SHORT
        val toast = Toast.makeText(context, text, duration)
        toast.show()
    }

    suspend fun exportDataToCalendar(context: Context, calendarId: String) {
        val calendarSleeps = CalendarImport.queryForEvents(
            context, calendarId
        ).map(CalendarImport::mapEventToSleep)
        val sleeps = database.sleepDao().getAll()
        val exportedSleeps = sleeps.subtract(calendarSleeps.toSet())

        CalendarExport.exportSleep(context, calendarId, exportedSleeps.toList())

        // Show how many sleeps were exported.
        val text = context.resources.getQuantityString(
            R.plurals.exported_items,
            exportedSleeps.size,
            exportedSleeps.size
        )
        val duration = Toast.LENGTH_SHORT
        val toast = Toast.makeText(context, text, duration)
        toast.show()
    }

    suspend fun backupSleeps(context: Context, cr: ContentResolver) {
        val autoBackup = preferences.getBoolean("auto_backup", false)
        val autoBackupPath = preferences.getString("auto_backup_path", "")
        if (!autoBackup || autoBackupPath == null || autoBackupPath.isEmpty()) {
            return
        }

        val folder = DocumentFile.fromTreeUri(context, Uri.parse(autoBackupPath))
            ?: return

        // Make sure that we don't create "backup (1).csv", etc.
        val oldBackup = folder.findFile("backup.csv")
        if (oldBackup != null && oldBackup.exists()) {
            oldBackup.delete()
        }

        val backup = folder.createFile("text/csv", "backup.csv") ?: return
        exportDataToFile(
            context, cr, backup.uri, showToast = false
        )
    }

    suspend fun exportDataToFile(
        context: Context,
        cr: ContentResolver,
        uri: Uri,
        showToast: Boolean
    ) {
        val sleeps = database.sleepDao().getAll()

        try {
            cr.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "exportData: takePersistableUriPermission() failed for write")
        }

        val os: OutputStream? = cr.openOutputStream(uri)
        if (os == null) {
            Log.e(TAG, "exportData: openOutputStream() failed")
            return
        }
        try {
            val writer = CSVPrinter(OutputStreamWriter(os, "UTF-8"), CSVFormat.DEFAULT)
            writer.printRecord("sid", "start", "stop", "rating", "comment")
            for (sleep in sleeps) {
                writer.printRecord(sleep.sid, sleep.start, sleep.stop, sleep.rating, sleep.comment)
            }
            writer.close()
        } catch (e: IOException) {
            Log.e(TAG, "exportData: write() failed")
            return
        } finally {
            try {
                os.close()
            } catch (e: Exception) {
            }
        }

        if (!showToast) {
            return
        }
        val text = context.getString(R.string.export_success)
        val duration = Toast.LENGTH_SHORT
        val toast = Toast.makeText(context, text, duration)
        toast.show()
    }

    private const val TAG = "DataModel"

    fun getSleepCountStat(sleeps: List<Sleep>): String {
        return sleeps.size.toString()
    }

    /**
     * Calculates the avg of sleeps.
     */
    fun getSleepDurationStat(sleeps: List<Sleep>): String {
        var sum: Long = 0
        for (sleep in sleeps) {
            var diff = sleep.stop - sleep.start
            diff /= 1000
            sum += diff
        }
        val count = sleeps.size
        return if (count == 0) {
            ""
        } else formatDuration(sum / count)
    }

    /**
     * Sums up sleeps per day, and then calculate the avg of those sums.
     */
    fun getSleepDurationDailyStat(sleeps: List<Sleep>): String {
        // Day -> sum (in seconds) map.
        val sums = HashMap<Long, Long>()
        var minKey: Long = Long.MAX_VALUE
        var maxKey: Long = 0
        for (sleep in sleeps) {
            var diff = sleep.stop - sleep.start
            diff /= 1000

            // Calculate stop day
            val stopDate = Calendar.getInstance()
            stopDate.timeInMillis = sleep.stop

            val day = Calendar.getInstance()
            day.timeInMillis = 0
            val startYear = stopDate.get(Calendar.YEAR)
            day.set(Calendar.YEAR, startYear)
            val startMonth = stopDate.get(Calendar.MONTH)
            day.set(Calendar.MONTH, startMonth)
            val startDay = stopDate.get(Calendar.DAY_OF_MONTH)
            day.set(Calendar.DAY_OF_MONTH, startDay)
            val key = day.timeInMillis
            minKey = minOf(minKey, key)
            maxKey = maxOf(maxKey, key)

            val sum = sums[key]
            if (sum != null) {
                sums[key] = sum + diff
            } else {
                sums[key] = diff
            }
        }

        if (sums.size == 0) {
            return ""
        }

        // Now determine the number of covered days. This is usually just the number of keys, but it
        // can be more, in case a whole 24h period was left out.
        val msPerDay = 86400 * 1000
        val count = (maxKey - minKey) / msPerDay + 1
        return formatDuration(sums.values.sum() / count)
    }

    fun formatDuration(seconds: Long): String {
        return String.format(
            Locale.getDefault(), "%d:%02d:%02d",
            seconds / 3600, seconds % 3600 / 60,
            seconds % 60
        )
    }

    fun formatTimestamp(date: Date): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(date)
    }

    /**
     * Returns the subset of [sleeps] which stop after [after].
     */
    fun filterSleeps(sleeps: List<Sleep>, after: Date): List<Sleep> {
        return sleeps.filter { it.stop > after.time }
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
