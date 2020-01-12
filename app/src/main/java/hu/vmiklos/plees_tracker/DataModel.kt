/*
 * Copyright 2019 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.room.Room
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
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

    private var mStart: Date? = null
    private var mStop: Date? = null
    private var mContext: Context? = null
    private var mPreferences: SharedPreferences? = null
    private var mDatabase: AppDatabase? = null

    var start: Date?
        get() = mStart
        set(start) {
            mStart = start
            // Save start timestamp in case the foreground service is killed.
            val editor = mPreferences!!.edit()
            editor.putLong("start", mStart!!.getTime())
            editor.apply()
        }

    var stop: Date?
        get() = mStop
        set(stop) {
            mStop = stop
        }

    private val database: AppDatabase?
        get() {
            if (mDatabase == null) {
                mDatabase = Room.databaseBuilder(mContext!!, AppDatabase::class.java, "database")
                        .build()
            }
            return mDatabase
        }

    val sleepsLive: LiveData<List<Sleep>>
        get() = database!!.sleepDao().getAllLive()

    fun init(context: Context, preferences: SharedPreferences) {
        if (mContext !== context) {
            mContext = context
        }

        if (mPreferences !== preferences) {
            mPreferences = preferences
        }

        val start = mPreferences!!.getLong("start", 0)
        if (start > 0) {
            // Restore start timestamp in case the foreground service was
            // killed.
            mStart = Date(start)
        }
    }

    suspend fun storeSleep() {
        val sleep = Sleep()
        sleep.start = mStart!!.getTime()
        sleep.stop = stop!!.getTime()
        database!!.sleepDao().insert(sleep)

        // Drop start timestamp from preferences, it's in the database now.
        val editor = mPreferences!!.edit()
        editor.remove("start")
        editor.apply()
    }

    suspend fun insertSleep(sleep: Sleep) {
        database!!.sleepDao().insert(sleep)
    }

    suspend fun deleteSleeps() {
        database!!.sleepDao().deleteAll()
    }

    suspend fun deleteSleep(sleep: Sleep) {
        database!!.sleepDao().delete(sleep)
    }

    suspend fun importData(`is`: InputStream) {
        val br = BufferedReader(InputStreamReader(`is`))
        try {
            var first = true
            while (true) {
                var line = br.readLine()
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
                database!!.sleepDao().insert(sleep)
            }
        } catch (e: IOException) {
            Log.e(TAG, "importData: readLine() failed")
            return
        }

        val text = mContext!!.getString(R.string.import_success)
        val duration = Toast.LENGTH_SHORT
        val toast = Toast.makeText(mContext, text, duration)
        toast.show()
    }

    suspend fun exportData(os: OutputStream) {
        try {
            val sleeps = database!!.sleepDao().getAll()
            os.write("sid,start,stop\n".toByteArray())
            for (sleep in sleeps) {
                val row = sleep.sid.toString() + "," + sleep.start.toString() + "," +
                        sleep.stop.toString() + "\n"
                os.write(row.toByteArray())
            }
        } catch (e: IOException) {
            Log.e(TAG, "exportData: write() failed")
            return
        }

        val text = mContext!!.getString(R.string.export_success)
        val duration = Toast.LENGTH_SHORT
        val toast = Toast.makeText(mContext, text, duration)
        toast.show()
    }

    fun getString(resId: Int): String {
        return mContext!!.getString(resId)
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
