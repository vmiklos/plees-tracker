/*
 * Copyright 2020 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.launch

/**
 * This is the view model of MainActivity, providing coroutine scopes.
 */
class MainViewModel : ViewModel() {
    public fun stopSleep() {
        viewModelScope.launch {
            val dataModel = DataModel.dataModel
            dataModel.storeSleep()
        }
    }

    public fun exportData(os: OutputStream) {
        viewModelScope.launch {
            val dataModel = DataModel.dataModel
            dataModel.exportData(os)
        }
    }

    public fun importData(inputStream: InputStream) {
        viewModelScope.launch {
            val dataModel = DataModel.dataModel
            dataModel.importData(inputStream)
        }
    }

    public fun insertSleep(sleep: Sleep) {
        viewModelScope.launch {
            val dataModel = DataModel.dataModel
            dataModel.insertSleep(sleep)
        }
    }

    public fun deleteSleep(sleep: Sleep) {
        viewModelScope.launch {
            val dataModel = DataModel.dataModel
            dataModel.deleteSleep(sleep)
        }
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
