/*
 * Copyright 2023 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.launch

class PreferencesActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "PreferencesActivity"
        private const val STATE_ADD_DRIVE_AFTER_FOLDER = "addDriveAfterFolder"
        private const val STATE_REPLACE_FOLDER_ON_SIGN_IN = "replaceFolderOnSignIn"
        private const val STATE_REPLACE_DRIVE_ON_FOLDER_PICKED = "replaceDriveOnFolderPicked"
        private const val STATE_CHANGE_PATH_FROM = "changePathFrom"
        private const val STATE_CHANGE_ACCOUNT_EMAIL = "changeAccountEmail"
        private const val STATE_CHANGE_ACCOUNT_FREQUENCY = "changeAccountFrequency"
    }

    // Pending state of an in-flight folder-picker or Drive sign-in flow. The system activities
    // can recreate this activity (rotation, low memory), so these survive via the saved instance
    // state; cancelling the external activity changes nothing.

    // When the user ticks both "This device" and "Google Drive" in the add dialog, the folder
    // picker runs first; this flag makes its result chain into the Drive sign-in afterwards.
    private var addDriveAfterFolder = false

    // The folder destination to retire once a "back up to Drive instead" sign-in succeeds.
    private var replaceFolderOnSignIn: BackupDestination.LocalFolder? = null

    // The Drive account whose backup gets deleted once a "back up locally instead" folder is
    // actually picked.
    private var replaceDriveOnFolderPicked: String? = null

    // The folder destination being repointed by "Change path".
    private var changePathFrom: BackupDestination.LocalFolder? = null

    // The destination whose account is being replaced by "Change account". Null means the
    // sign-in result adds a new destination instead.
    private var changeAccountFrom: BackupDestination.DriveAccount? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { state ->
            addDriveAfterFolder = state.getBoolean(STATE_ADD_DRIVE_AFTER_FOLDER)
            replaceFolderOnSignIn = state.getString(STATE_REPLACE_FOLDER_ON_SIGN_IN)
                ?.let { BackupDestination.LocalFolder(it) }
            replaceDriveOnFolderPicked = state.getString(STATE_REPLACE_DRIVE_ON_FOLDER_PICKED)
            changePathFrom = state.getString(STATE_CHANGE_PATH_FROM)
                ?.let { BackupDestination.LocalFolder(it) }
            val email = state.getString(STATE_CHANGE_ACCOUNT_EMAIL)
            val frequency = state.getString(STATE_CHANGE_ACCOUNT_FREQUENCY)
            if (email != null && frequency != null) {
                changeAccountFrom = BackupDestination.DriveAccount(email, frequency)
            }
        }
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.root, Preferences())
            .commit()
        setContentView(R.layout.activity_settings)

        DataModel.handleWindowInsets(this)

        DataModel.preferencesActivity = this
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_ADD_DRIVE_AFTER_FOLDER, addDriveAfterFolder)
        outState.putString(STATE_REPLACE_FOLDER_ON_SIGN_IN, replaceFolderOnSignIn?.path)
        outState.putString(STATE_REPLACE_DRIVE_ON_FOLDER_PICKED, replaceDriveOnFolderPicked)
        outState.putString(STATE_CHANGE_PATH_FROM, changePathFrom?.path)
        outState.putString(STATE_CHANGE_ACCOUNT_EMAIL, changeAccountFrom?.email)
        outState.putString(STATE_CHANGE_ACCOUNT_FREQUENCY, changeAccountFrom?.frequency)
    }

    override fun onDestroy() {
        super.onDestroy()
        DataModel.preferencesActivity = null
    }

    private val folderPickerResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val replaceDrive = replaceDriveOnFolderPicked
            replaceDriveOnFolderPicked = null
            val changeFrom = changePathFrom
            changePathFrom = null
            var added = false
            try {
                result.data?.data?.let { uri ->
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, flags)
                    val newDest = BackupDestination.LocalFolder(uri.toString())
                    if (changeFrom != null) {
                        // "Change path": swap the old folder for the new one. Re-picking the
                        // same folder must not remove-by-equality the freshly added entry.
                        if (changeFrom.path != newDest.path) {
                            DataModel.replaceDestination(changeFrom, newDest)
                            releaseFolderPermission(changeFrom.path)
                        }
                    } else {
                        DataModel.addDestination(newDest)
                    }
                    added = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "folderPickerResult: $e")
            }
            when {
                // "Instead of Google Drive": the folder is in place, retire the Drive account
                // along with its remote backup. Handles the refresh and the toast itself.
                added && replaceDrive != null ->
                    removeDriveAccount(replaceDrive, deleteBackup = true)
                // Continue to Drive sign-in if the user asked for both destinations; the Drive
                // callback owns the final refresh then.
                added && addDriveAfterFolder -> {
                    addDriveAfterFolder = false
                    addDriveAccount()
                }
                else -> {
                    addDriveAfterFolder = false
                    refreshFragment()
                }
            }
        }

    private fun openFolderChooser() {
        folderPickerResult.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
    }

    fun removeFolderDestination(dest: BackupDestination.LocalFolder, deleteBackup: Boolean) {
        lifecycleScope.launch {
            if (deleteBackup) {
                // Delete while the persisted URI permission is still held.
                val success = DataModel.deleteFolderBackup(applicationContext, dest.path)
                toast(
                    if (success) {
                        R.string.folder_delete_backup_success
                    } else {
                        R.string.folder_delete_backup_failure
                    }
                )
            }
            DataModel.removeDestination(dest)
            releaseFolderPermission(dest.path)
            refreshFragment()
        }
    }

    /** Repoints the folder destination [dest] to a folder picked in a fresh picker run. */
    fun changeFolderPath(dest: BackupDestination.LocalFolder) {
        changePathFrom = dest
        openFolderChooser()
    }

    fun deleteFolderBackup(dest: BackupDestination.LocalFolder) {
        lifecycleScope.launch {
            val success = DataModel.deleteFolderBackup(applicationContext, dest.path)
            toast(
                if (success) {
                    R.string.folder_delete_backup_success
                } else {
                    R.string.folder_delete_backup_failure
                }
            )
        }
    }

    /** Gives back the persisted permission grant of a no longer used folder destination. */
    private fun releaseFolderPermission(path: String) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.releasePersistableUriPermission(Uri.parse(path), flags)
        } catch (e: Exception) {
            Log.e(TAG, "releaseFolderPermission: $e")
        }
    }

    private val driveSignInResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val changeFrom = changeAccountFrom
            changeAccountFrom = null
            val replaceFolder = replaceFolderOnSignIn
            replaceFolderOnSignIn = null
            lifecycleScope.launch {
                val signIn = DriveBackend.handleSignInResult(applicationContext, result.data)
                when (signIn) {
                    is DriveSignInResult.Success -> if (changeFrom != null) {
                        applyAccountChange(changeFrom, signIn.email)
                    } else {
                        addDriveDestination(signIn.email)
                        toast(R.string.drive_sign_in_success)
                        if (replaceFolder != null) {
                            // "Instead of the device folder": Drive is in place, retire the
                            // folder destination and offer to clean up its backup file.
                            DataModel.removeDestination(replaceFolder)
                            releaseFolderPermission(replaceFolder.path)
                            confirmDeleteRetiredFolderBackup(replaceFolder)
                        }
                    }
                    // Backing out of the account picker is a deliberate choice, not an error.
                    DriveSignInResult.Cancelled -> {}
                    DriveSignInResult.Failed -> toast(R.string.drive_sign_in_failure)
                }
                refreshFragment()
            }
        }

    private fun addDriveDestination(email: String) {
        // The result can arrive twice for the same account, e.g. when the activity was
        // recreated while the picker was open: don't create a duplicate destination then.
        val alreadyConfigured = DataModel.getDestinations()
            .filterIsInstance<BackupDestination.DriveAccount>()
            .any { it.email == email }
        if (alreadyConfigured) {
            return
        }
        DataModel.addDestination(BackupDestination.DriveAccount(email, "daily"))
        DriveBackend.scheduleDailyBackup(applicationContext, email)
    }

    /**
     * Starts the flow for adding a backup destination, phrased as a migration from the current
     * setup. One destination of each type is supported, so: with nothing configured this shows
     * the clean-slate "back up to" checkbox dialog (ticking both runs the folder picker first and
     * then chains into Drive sign-in); with one type configured the remaining type is offered "in
     * addition to" or "instead of" the existing one. The add row is hidden when both types are
     * configured (see [Preferences.setupBackupPreferences]).
     */
    fun promptAddDestination() {
        val destinations = DataModel.getDestinations()
        val folder = destinations.filterIsInstance<BackupDestination.LocalFolder>().firstOrNull()
        val drive = destinations.filterIsInstance<BackupDestination.DriveAccount>().firstOrNull()
        when {
            // Only the folder type exists in the foss flavor; the row is hidden once configured.
            !DriveBackend.isSupported -> if (folder == null) openFolderChooser()
            folder == null && drive == null -> promptCleanSlate()
            drive == null && folder != null -> promptAddDriveTo(folder)
            folder == null && drive != null -> promptAddFolderTo(drive)
            // Both configured: unreachable, the add row is hidden.
        }
    }

    private fun promptCleanSlate() {
        val options = arrayOf(
            getString(R.string.backup_target_folder),
            getString(R.string.backup_target_drive)
        )
        val checked = booleanArrayOf(false, false)
        AlertDialog.Builder(this)
            .setTitle(R.string.backup_target_title)
            .setMultiChoiceItems(options, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val wantFolder = checked[0]
                val wantDrive = checked[1]
                when {
                    wantFolder && wantDrive -> {
                        addDriveAfterFolder = true
                        openFolderChooser()
                    }
                    wantFolder -> openFolderChooser()
                    wantDrive -> addDriveAccount()
                    // Nothing ticked: refresh so the automatic backup switch resets when this
                    // flow was started by toggling it on.
                    else -> refreshFragment()
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                refreshFragment()
            }
            .setOnCancelListener {
                refreshFragment()
            }
            .show()
    }

    /** A folder backup exists: offer Drive on top of it, or as its replacement. */
    private fun promptAddDriveTo(folder: BackupDestination.LocalFolder) {
        val options = arrayOf(
            getString(R.string.backup_drive_in_addition),
            getString(R.string.backup_drive_instead)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.backup_to_drive_title)
            .setItems(options) { _, which ->
                if (which == 1) {
                    replaceFolderOnSignIn = folder
                }
                addDriveAccount()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** A Drive backup exists: offer a folder on top of it, or as its replacement. */
    private fun promptAddFolderTo(drive: BackupDestination.DriveAccount) {
        val options = arrayOf(
            getString(R.string.backup_folder_in_addition),
            getString(R.string.backup_folder_instead)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.backup_to_folder_title)
            .setItems(options) { _, which ->
                if (which == 1) {
                    replaceDriveOnFolderPicked = drive.email
                }
                openFolderChooser()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Asks whether to also delete the backup.csv from a just-retired folder destination. Opt-in
     * because the folder may be synchronized elsewhere (e.g. Nextcloud); declining keeps the
     * file.
     */
    private fun confirmDeleteRetiredFolderBackup(folder: BackupDestination.LocalFolder) {
        AlertDialog.Builder(this)
            .setMessage(R.string.folder_retired_delete_backup_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                deleteFolderBackup(folder)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun addDriveAccount() {
        // Sign out first so GoogleSignIn shows the account picker rather than silently reusing
        // whichever account last completed the flow (e.g. one removed just before).
        DriveBackend.signOut(this)
        val intent = DriveBackend.createSignInIntent(this) ?: return
        driveSignInResult.launch(intent)
    }

    fun backupNow(email: String) {
        lifecycleScope.launch {
            // Always uploads (no unchanged-skip): an explicit user action doubles as the way to
            // recreate a backup that disappeared remotely. Still records the payload hash so the
            // next automatic backup can skip when nothing changed since.
            val data = DataModel.serializeSleeps()
            val success = DriveBackend.upload(applicationContext, email, data)
            if (success) {
                DataModel.setDriveBackupHash(email, DataModel.sha256(data))
            }
            toast(if (success) R.string.drive_backup_success else R.string.drive_backup_failure)
            refreshFragment()
        }
    }

    fun confirmRestoreFromDrive(email: String) {
        lifecycleScope.launch {
            if (DataModel.hasSleeps()) {
                showRestoreModeDialog(email)
            } else {
                restoreFromDrive(email, override = false)
            }
        }
    }

    private fun showRestoreModeDialog(email: String) {
        val options = arrayOf(
            getString(R.string.restore_merge),
            getString(R.string.restore_override)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.drive_restore)
            .setItems(options) { _, which -> restoreFromDrive(email, override = which == 1) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun restoreFromDrive(email: String, override: Boolean) {
        lifecycleScope.launch {
            val success = DataModel.restoreFromDrive(applicationContext, email, override)
            toast(if (success) R.string.import_success else R.string.drive_restore_failure)
        }
    }

    fun deleteFromDrive(email: String) {
        lifecycleScope.launch {
            val success = DriveBackend.delete(applicationContext, email)
            if (success) {
                // The remote file is gone; forget its hash so the next automatic backup uploads.
                DataModel.setDriveBackupHash(email, null)
            }
            toast(if (success) R.string.drive_delete_success else R.string.drive_delete_failure)
            refreshFragment()
        }
    }

    fun changeFrequency(dest: BackupDestination.DriveAccount) {
        val options = arrayOf(
            getString(R.string.drive_backup_frequency_daily),
            getString(R.string.drive_backup_frequency_on_change)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.drive_frequency)
            .setItems(options) { _, which ->
                val freq = if (which == 0) "daily" else "on_change"
                DataModel.replaceDestination(dest, dest.copy(frequency = freq))
                if (freq == "daily") {
                    DriveBackend.scheduleDailyBackup(applicationContext, dest.email)
                } else {
                    DriveBackend.cancelDailyBackup(applicationContext, dest.email)
                }
                refreshFragment()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Replaces the Google account behind [dest] with one picked in a fresh sign-in flow, keeping
     * the configured backup frequency. The existing destination stays untouched when the sign-in
     * fails or is cancelled.
     */
    fun changeDriveAccount(dest: BackupDestination.DriveAccount) {
        // Sign out first so the picker is shown instead of silently reusing the last account.
        DriveBackend.signOut(this)
        val intent = DriveBackend.createSignInIntent(this) ?: return
        changeAccountFrom = dest
        driveSignInResult.launch(intent)
    }

    private fun applyAccountChange(old: BackupDestination.DriveAccount, newEmail: String) {
        if (newEmail == old.email) {
            toast(R.string.drive_sign_in_success)
            return
        }
        val alreadyConfigured = DataModel.getDestinations()
            .filterIsInstance<BackupDestination.DriveAccount>()
            .any { it.email == newEmail }
        if (alreadyConfigured) {
            DataModel.removeDestination(old)
        } else {
            DataModel.replaceDestination(old, old.copy(email = newEmail))
            if (old.frequency == "daily") {
                DriveBackend.scheduleDailyBackup(applicationContext, newEmail)
            }
        }
        DriveBackend.cancelDailyBackup(applicationContext, old.email)
        DataModel.setDriveBackupHash(old.email, null)
        toast(R.string.drive_sign_in_success)
    }

    fun removeDriveAccount(email: String, deleteBackup: Boolean) {
        val dest = DataModel.getDestinations()
            .filterIsInstance<BackupDestination.DriveAccount>()
            .firstOrNull { it.email == email } ?: return
        lifecycleScope.launch {
            if (deleteBackup) {
                val success = DriveBackend.delete(applicationContext, email)
                toast(if (success) R.string.drive_delete_success else R.string.drive_delete_failure)
            }
            DataModel.removeDestination(dest)
            DriveBackend.cancelDailyBackup(applicationContext, email)
            DataModel.setDriveBackupHash(email, null)
            refreshFragment()
        }
    }

    fun refreshFragment() {
        // Several callers refresh after network I/O, which can finish while the activity is
        // already stopped (Home press, sign-in activity on top): allow the state loss instead of
        // crashing, the fragment is rebuilt from preferences anyway.
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.root, Preferences())
            .commitAllowingStateLoss()
    }

    private fun toast(resId: Int) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
    }

    // Show a dialog to set bedtime and wakeup times (using two TimePickerDialogs sequentially)
    fun showBedtimeDialog() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        // Get current values or use defaults (22:00 for bedtime, 07:00 for wakeup)
        val currentBedHour = DataModel.getBedtimeHour(preferences)
        val currentBedMinute = DataModel.getBedtimeMinute(preferences)
        val currentWakeHour = DataModel.getWakeupHour(preferences)
        val currentWakeMinute = DataModel.getWakeupMinute(preferences)

        // First, pick bedtime
        TimePickerDialog(
            this,
            { _, bedHour, bedMinute ->
                // Save bedtime values
                val editor = preferences.edit()
                editor.putString("bedtime", "$bedHour:$bedMinute")
                editor.apply()
                // Then, pick wakeup time
                TimePickerDialog(
                    this,
                    { _, wakeHour, wakeMinute ->
                        // Save wakeup values
                        editor.putString("wakeup", "$wakeHour:$wakeMinute")
                        editor.apply()
                        Toast.makeText(
                            this,
                            getString(R.string.bedtime_toast),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    currentWakeHour,
                    currentWakeMinute,
                    true
                ).show()
            },
            currentBedHour,
            currentBedMinute,
            true
        ).show()
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
