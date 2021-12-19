/*
 * Copyright 2020 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

/**
 * This is the view model of MainActivity, providing coroutine scopes.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = PreferenceManager.getDefaultSharedPreferences(application)

    val durationSleepsLive: LiveData<List<Sleep>> =
        Transformations.switchMap(preferences.liveData("dashboard_duration", "0")) { durationStr ->
            val duration = durationStr?.toInt() ?: 0
            val date = if (duration == 0) {
                Date(0)
            } else {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DATE, duration)
                cal.time
            }
            DataModel.getSleepsAfterLive(date)
        }

    fun stopSleep(context: Context, cr: ContentResolver) {
        viewModelScope.launch {
            DataModel.storeSleep()
            DataModel.backupSleeps(context, cr)
        }
    }

    fun exportDataToFile(context: Context, cr: ContentResolver, uri: Uri, showToast: Boolean) {
        viewModelScope.launch {
            DataModel.exportDataToFile(context, cr, uri, showToast)
        }
    }

    fun importDataFromCalendar(context: Context, calendarId: String) {
        viewModelScope.launch {
            DataModel.importDataFromCalendar(context, calendarId)
        }
    }

    fun exportDataToCalendar(context: Context, calendarId: String) {
        viewModelScope.launch {
            DataModel.exportDataToCalendar(context, calendarId)
        }
    }

    fun importData(context: Context, cr: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            DataModel.importData(context, cr, uri)
        }
    }

    fun insertSleep(sleep: Sleep) {
        viewModelScope.launch {
            DataModel.insertSleep(sleep)
        }
    }

    fun insertSleeps(sleepList: List<Sleep>) {
        viewModelScope.launch {
            DataModel.insertSleeps(sleepList)
        }
    }

    fun deleteSleep(sleep: Sleep, context: Context, cr: ContentResolver) {
        viewModelScope.launch {
            DataModel.deleteSleep(sleep)
            DataModel.backupSleeps(context, cr)
        }
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
