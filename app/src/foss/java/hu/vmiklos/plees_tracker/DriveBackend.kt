/*
 * Copyright 2026 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import android.content.Context
import android.content.Intent

/**
 * No-op Google Drive backend for the foss flavor: the F-Droid build must stay free of proprietary
 * Google Play Services. The gplay flavor provides a real implementation with the same signatures.
 */
object DriveBackend {
    // Not a const val on purpose: a constant would turn isSupported checks in shared code into
    // constant conditions, which the warnings-as-errors build rejects.
    val isSupported = false

    fun createSignInIntent(context: Context): Intent? = null

    suspend fun handleSignInResult(context: Context, data: Intent?): DriveSignInResult =
        DriveSignInResult.Cancelled

    fun signOut(context: Context) {}

    fun isAccountOnDevice(context: Context, email: String): Boolean = false

    suspend fun upload(context: Context, email: String, data: ByteArray): Boolean = false

    suspend fun download(context: Context, email: String): ByteArray? = null

    suspend fun hasBackup(context: Context, email: String): Boolean = false

    suspend fun delete(context: Context, email: String): Boolean = false

    fun scheduleDailyBackup(context: Context, email: String) {}

    fun cancelDailyBackup(context: Context, email: String) {}

    fun scheduleBackup(context: Context, email: String) {}
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
