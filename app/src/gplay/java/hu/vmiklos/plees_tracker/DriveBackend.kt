/*
 * Copyright 2026 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Real Google Drive backend for the gplay flavor: backs up and restores the sleep data in Drive's
 * hidden per-app appDataFolder. Each Google account on the device gets its own independent backup
 * file identified by that account's email. The foss flavor ships a no-op with the same signatures.
 */
object DriveBackend {
    private const val TAG = "DriveBackend"

    private const val BACKUP_NAME = "backup.csv"

    private const val ACCOUNT_TYPE = "com.google"

    // WorkManager work-name prefixes; a per-account suffix (the email) makes them unique per
    // account.
    private const val WORK_NAME_PREFIX = "drive_backup_"
    private const val ONESHOT_WORK_NAME_PREFIX = "drive_backup_oneshot_"

    // Not a const val on purpose: see the foss flavor's DriveBackend for the rationale.
    val isSupported = true

    /**
     * Intent for the system Google account picker, which always prompts, so there is no sign-in
     * state to clear before adding or changing an account.
     *
     * Naming the account and authorizing Drive are two separate steps: the picker gives back the
     * email that keys the whole destination and that [driveService] needs as a device account,
     * then [authorize] asks Play Services for the appDataFolder scope on its behalf.
     */
    fun createAccountPickerIntent(): Intent? = AccountManager.newChooseAccountIntent(
        null, null, arrayOf(ACCOUNT_TYPE), null, null, null, null
    )

    /** Email of the account picked in the account picker, null when the user backed out of it. */
    fun readPickedAccount(data: Intent?): String? =
        data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)

    /**
     * Requests the Drive appDataFolder scope for [email]. Returns [DriveAuthorization.Consent]
     * when Play Services wants the user to confirm the grant first -- the normal case for an
     * account authorized for the first time -- carrying the intent only an activity can launch.
     */
    suspend fun authorize(context: Context, email: String): DriveAuthorization =
        withContext(Dispatchers.IO) {
            val request = AuthorizationRequest.Builder()
                .setRequestedScopes(listOf(appDataScope()))
                .setAccount(Account(email, ACCOUNT_TYPE))
                .build()
            try {
                // Blocking await: this already runs off the main thread, and it keeps the
                // callers plain suspending code without a coroutines-play-services dependency.
                val result = Tasks.await(
                    Identity.getAuthorizationClient(context).authorize(request)
                )
                val pendingIntent = result.pendingIntent
                when {
                    result.hasResolution() && pendingIntent != null ->
                        DriveAuthorization.Consent(pendingIntent)
                    isAppDataGranted(result) -> DriveAuthorization.Granted
                    else -> {
                        Log.e(TAG, "authorize($email): drive.appdata scope not granted")
                        DriveAuthorization.Failed
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "authorize($email): $e")
                DriveAuthorization.Failed
            }
        }

    /**
     * Processes the result of the consent activity launched for [DriveAuthorization.Consent].
     * [email] is the account the consent was asked for, so that a success can name it.
     */
    suspend fun handleAuthorizationResult(
        context: Context,
        email: String,
        data: Intent?
    ): DriveSignInResult = withContext(Dispatchers.IO) {
        if (data == null) {
            // No result payload at all: the user backed out of the consent screen.
            return@withContext DriveSignInResult.Cancelled
        }
        try {
            val result = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(data)
            if (!isAppDataGranted(result)) {
                Log.e(TAG, "handleAuthorizationResult: drive.appdata scope not granted")
                return@withContext DriveSignInResult.Failed
            }
            DriveSignInResult.Success(email)
        } catch (e: ApiException) {
            if (e.statusCode == CommonStatusCodes.CANCELED) {
                DriveSignInResult.Cancelled
            } else {
                Log.e(TAG, "handleAuthorizationResult: failed, status=${e.statusCode}")
                DriveSignInResult.Failed
            }
        }
    }

    /** True when the given email corresponds to a Google account still present on the device. */
    fun isAccountOnDevice(context: Context, email: String): Boolean = try {
        AccountManager.get(context).getAccountsByType(ACCOUNT_TYPE).any { it.name == email }
    } catch (_: Exception) {
        true // Can't verify; assume valid so callers don't silently drop the account.
    }

    suspend fun upload(context: Context, email: String, data: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            val drive = driveService(context, email) ?: return@withContext false
            try {
                val content = ByteArrayContent("text/csv", data)
                val existingId = findBackupId(drive)
                if (existingId != null) {
                    drive.files().update(existingId, null, content).execute()
                } else {
                    val metadata = DriveFile().apply {
                        name = BACKUP_NAME
                        parents = listOf("appDataFolder")
                    }
                    drive.files().create(metadata, content).setFields("id").execute()
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "upload($email): $e")
                false
            }
        }

    suspend fun download(context: Context, email: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val drive = driveService(context, email) ?: return@withContext null
            try {
                val id = findBackupId(drive) ?: return@withContext null
                val os = ByteArrayOutputStream()
                drive.files().get(id).executeMediaAndDownloadTo(os)
                os.toByteArray()
            } catch (e: Exception) {
                Log.e(TAG, "download($email): $e")
                null
            }
        }

    /**
     * Returns true when a backup file exists for [email] in the appDataFolder. Returns false when
     * the account is not on the device, when the network is unavailable, or on any other error:
     * callers treat "unknown" as "no backup".
     */
    suspend fun hasBackup(context: Context, email: String): Boolean =
        withContext(Dispatchers.IO) {
            val drive = driveService(context, email) ?: return@withContext false
            try {
                findBackupId(drive) != null
            } catch (e: Exception) {
                Log.e(TAG, "hasBackup($email): $e")
                false
            }
        }

    /**
     * Deletes the backup file for [email]. Returns true when the backup is gone afterwards
     * (deleted just now or never existed), false only on a real error.
     */
    suspend fun delete(context: Context, email: String): Boolean =
        withContext(Dispatchers.IO) {
            val drive = driveService(context, email) ?: return@withContext false
            try {
                val id = findBackupId(drive)
                if (id != null) drive.files().delete(id).execute()
                true
            } catch (e: Exception) {
                Log.e(TAG, "delete($email): $e")
                false
            }
        }

    /**
     * Schedules a daily backup worker for [email]. The work name is unique per account so multiple
     * accounts each get their own independent schedule.
     */
    fun scheduleDailyBackup(context: Context, email: String) {
        val request = PeriodicWorkRequestBuilder<DriveBackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setInputData(workDataOf("email" to email))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "$WORK_NAME_PREFIX$email", ExistingPeriodicWorkPolicy.KEEP, request
        )
    }

    fun cancelDailyBackup(context: Context, email: String) {
        WorkManager.getInstance(context).cancelUniqueWork("$WORK_NAME_PREFIX$email")
    }

    /**
     * Enqueues a one-shot Drive upload for [email] via WorkManager, used for "on_change" backups.
     * Because WorkManager persists the request, the upload survives the app being closed or the
     * device rebooting, only runs once the network is available, and retries with backoff on
     * failure -- so a backup triggered while offline or during a Drive outage still completes once
     * the problem clears. The worker stops itself when the account is no longer a configured
     * destination, so no cancel counterpart is needed.
     */
    fun scheduleBackup(context: Context, email: String) {
        val request = OneTimeWorkRequestBuilder<DriveBackupWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setInputData(workDataOf("email" to email))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "$ONESHOT_WORK_NAME_PREFIX$email", ExistingWorkPolicy.KEEP, request
        )
    }

    private fun appDataScope() = Scope(DriveScopes.DRIVE_APPDATA)

    private fun isAppDataGranted(result: AuthorizationResult): Boolean =
        DriveScopes.DRIVE_APPDATA in result.grantedScopes.orEmpty()

    /**
     * Builds a Drive client for [email], or null when the account is no longer on the device.
     * The token comes from the device account itself, so every configured account keeps working
     * independently of which one was authorized last.
     */
    private fun driveService(context: Context, email: String): Drive? {
        if (!isAccountOnDevice(context, email)) {
            Log.w(TAG, "driveService: account $email not on device")
            return null
        }
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_APPDATA)
        )
        credential.selectedAccount = Account(email, ACCOUNT_TYPE)
        return Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName(context.getString(R.string.app_name))
            .build()
    }

    private fun findBackupId(drive: Drive): String? {
        val result = drive.files().list()
            .setSpaces("appDataFolder")
            .setQ("name = '$BACKUP_NAME'")
            .setFields("files(id)")
            .execute()
        return result.files?.firstOrNull()?.id
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
