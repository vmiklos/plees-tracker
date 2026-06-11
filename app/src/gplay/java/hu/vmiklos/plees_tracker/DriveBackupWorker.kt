/*
 * Copyright 2026 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Uploads the sleep data to Google Drive for one specific account on a daily schedule. The target
 * account email is passed via [inputData] so each account has its own independently-named work
 * request (see [DriveBackend.scheduleDailyBackup]).
 */
class DriveBackupWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val email = inputData.getString("email") ?: return Result.failure()
        val context = applicationContext
        DataModel.init(context, PreferenceManager.getDefaultSharedPreferences(context))

        // If this account was removed from the destination list, stop retrying.
        val configured = DataModel.getDestinations()
            .filterIsInstance<BackupDestination.DriveAccount>()
            .any { it.email == email }
        if (!configured) {
            return Result.success()
        }

        // The master switch is off: stay scheduled, but don't upload.
        if (!DataModel.isAutomaticBackupEnabled()) {
            return Result.success()
        }

        return if (DataModel.uploadToDriveIfChanged(context, email, DataModel.serializeSleeps())) {
            Result.success()
        } else if (!DriveBackend.isAccountOnDevice(context, email)) {
            // Account removed from the device; no point retrying.
            Result.success()
        } else {
            Result.retry()
        }
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
