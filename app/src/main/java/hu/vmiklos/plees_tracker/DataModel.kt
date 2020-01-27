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
import androidx.lifecycle.LiveData
import androidx.room.Room
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Data model is the singleton shared state between the activity and the
 * service.
 */
class DataModel private constructor() {

    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences

    var start: Date? = null
        set(start) {
            field = start
            // Save start timestamp in case the foreground service is killed.
            val editor = this.preferences.edit()
            field?.let {
                editor.putLong("start", it.time)
            }
            editor.apply()
        }

    var stop: Date? = null

    lateinit var database: AppDatabase

    val sleepsLive: LiveData<List<Sleep>>
        get() = this.database.sleepDao().getAllLive()

    fun init(context: Context, preferences: SharedPreferences) {
        this.context = context

        this.preferences = preferences

        val start = this.preferences.getLong("start", 0)
        if (start > 0) {
            // Restore start timestamp in case the foreground service was
            // killed.
            this.start = Date(start)
        }
        this.database = Room.databaseBuilder(this.context, AppDatabase::class.java, "database")
                .build()
    }

    suspend fun storeSleep() {
        val sleep = Sleep()
        this.start?.let {
            sleep.start = it.time
        }
        this.stop?.let {
            sleep.stop = it.time
        }
        this.database.sleepDao().insert(sleep)

        // Drop start timestamp from preferences, it's in the database now.
        val editor = this.preferences.edit()
        editor.remove("start")
        editor.apply()
    }

    suspend fun insertSleep(sleep: Sleep) {
        this.database.sleepDao().insert(sleep)
    }

    suspend fun deleteSleep(sleep: Sleep) {
        this.database.sleepDao().delete(sleep)
    }

    suspend fun importData(cr: ContentResolver, uri: Uri) {
        var inputStream = cr.openInputStream(uri)
        val br = BufferedReader(InputStreamReader(inputStream))
        try {
            var first = true
            while (true) {
                val line = br.readLine()
                if (line == null) {
                    break
                }
                if (first) {
                    // Ignore the header.
                    first = false
                    continue
                }
                val cells = line.split(",")
                if (cells.size < 3) {
                    continue
                }
                val sleep = Sleep()
                sleep.start = cells[1].toLong()
                sleep.stop = cells[2].toLong()
                this.database.sleepDao().insert(sleep)
            }
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

        val text = this.context.getString(R.string.import_success)
        val duration = Toast.LENGTH_SHORT
        val toast = Toast.makeText(this.context, text, duration)
        toast.show()
    }

    suspend fun exportData(cr: ContentResolver, uri: Uri) {
        val sleeps = this.database.sleepDao().getAll()

        try {
            cr.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (e: SecurityException) {
            Log.e(TAG, "exportData: takePersistableUriPermission() failed for write")
        }

        var os: OutputStream? = cr.openOutputStream(uri)
        if (os == null) {
            Log.e(TAG, "exportData: openOutputStream() failed")
            return
        }
        try {
            os.write("sid,start,stop\n".toByteArray())
            for (sleep in sleeps) {
                val row = sleep.sid.toString() + "," + sleep.start.toString() + "," +
                        sleep.stop.toString() + "\n"
                os.write(row.toByteArray())
            }
        } catch (e: IOException) {
            Log.e(TAG, "exportData: write() failed")
            return
        } finally {
            try {
                os.close()
            } catch (e: Exception) {
            }
        }

        val text = this.context.getString(R.string.export_success)
        val duration = Toast.LENGTH_SHORT
        val toast = Toast.makeText(this.context, text, duration)
        toast.show()
    }

    fun getString(resId: Int): String {
        return this.context.getString(resId)
    }

    companion object {
        private val TAG = "DataModel"
        val dataModel = DataModel()

        fun getSleepCountStat(sleeps: List<Sleep>): String {
            return sleeps.size.toString()
        }

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

        fun formatDuration(seconds: Long): String {
            return String.format(Locale.getDefault(), "%d:%02d:%02d",
                    seconds / 3600, seconds % 3600 / 60,
                    seconds % 60)
        }

        fun formatTimestamp(date: Date): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            return sdf.format(date)
        }
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
