/*
 * Copyright 2023 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import android.app.TimePickerDialog
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class PreferencesActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "PreferencesActivity"
        private const val STATE_ADD_DRIVE_AFTER_FOLDER = "addDriveAfterFolder"
        private const val STATE_REPLACE_FOLDER_ON_SIGN_IN = "replaceFolderOnSignIn"
        private const val STATE_REPLACE_DRIVE_ON_FOLDER_PICKED = "replaceDriveOnFolderPicked"
        private const val STATE_CHANGE_PATH_FROM = "changePathFrom"
        private const val STATE_CHANGE_ACCOUNT_EMAIL = "changeAccountEmail"
        private const val STATE_CHANGE_ACCOUNT_FREQUENCY = "changeAccountFrequency"
        private const val STATE_AUTHORIZE_EMAIL = "authorizeEmail"
        private const val STATE_HEALTH_SETTINGS_PENDING = "healthSettingsPending"
        private const val STATE_HEALTH_CHECK_PENDING = "healthCheckPending"
        private const val HEALTH_CONNECT_WIPE_DELAY_SECONDS = 5
        private const val HEALTH_CONNECT_READ_TIMEOUT_MS = 30_000L
        private const val HEALTH_CONNECT_RETRY_INITIAL_DELAY_MS = 1_000L
        private const val HEALTH_CONNECT_RETRY_MAX_DELAY_MS = 30_000L
        internal const val HEALTH_CONNECT_CHECK_ACTIONS_KEY = "health_connect_check_actions"
        internal const val HEALTH_CONNECT_FORCE_SYNC_KEY = "health_connect_force_sync"
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

    // The picked account whose Drive consent screen is on screen. The consent result only says
    // which scopes were granted, so the email it belongs to is remembered here.
    private var authorizeEmail: String? = null

    // True while Health Connect's app-specific settings were opened from the enable flow. This
    // lets a permission granted there complete the opt-in when the user returns to Plees.
    private var healthSettingsPending = false

    // The initial history inspection is foreground-only. Its pending state survives activity and
    // process recreation; the actual read job runs only while this settings activity is started.
    internal var healthConnectCheckPending = false
        private set
    private var healthConnectCheckJob: Job? = null
    private var healthConnectCheckGeneration = 0
    private var healthConnectImportDialogShowing = false
    private var healthConnectRetryDelayMs = HEALTH_CONNECT_RETRY_INITIAL_DELAY_MS

    // The permission result is authoritative for this activity lifetime. Re-querying the
    // provider immediately after it returns needlessly consumes the APK provider's quota.
    internal var healthConnectPermissionKnownGranted = false
        private set

    private var healthPermissionLauncher: ActivityResultLauncher<Set<String>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            healthPermissionLauncher = registerForActivityResult(
                HealthConnectBackend.permissionContract()
            ) { granted ->
                HealthConnectBackend.localPreferences(applicationContext).edit {
                    putBoolean(HealthConnectBackend.PERMISSION_REQUESTED_KEY, true)
                }
                if (granted.containsAll(HealthConnectBackend.requestedPermissions())) {
                    healthConnectPermissionKnownGranted = true
                    completeHealthConnectEnable()
                } else {
                    refreshFragment()
                    showHealthConnectPermissionDeniedDialog()
                }
            }
        }
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
            authorizeEmail = state.getString(STATE_AUTHORIZE_EMAIL)
            healthSettingsPending = state.getBoolean(STATE_HEALTH_SETTINGS_PENDING)
        }
        val persistedCheckPending = HealthConnectBackend.localPreferences(applicationContext)
            .getBoolean(HealthConnectBackend.CHECK_PENDING_KEY, false)
        healthConnectCheckPending = savedInstanceState?.getBoolean(
            STATE_HEALTH_CHECK_PENDING,
            persistedCheckPending
        ) ?: persistedCheckPending
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.root, Preferences())
            .commit()
        setContentView(R.layout.activity_settings)

        DataModel.handleWindowInsets(this)

        // Show a back button.
        actionBar?.setDisplayHomeAsUpEnabled(true)

        DataModel.preferencesActivity = this
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            this.finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    fun requestHealthConnectPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return
        }
        lifecycleScope.launch {
            val requested = HealthConnectBackend.localPreferences(applicationContext)
                .getBoolean(HealthConnectBackend.PERMISSION_REQUESTED_KEY, false)
            if (!requested) {
                healthPermissionLauncher?.launch(HealthConnectBackend.requestedPermissions())
                return@launch
            }
            when (hasHealthConnectWritePermission()) {
                true -> completeHealthConnectEnable()
                false -> showHealthConnectPermissionDeniedDialog()
                null -> {
                    refreshFragment()
                    toast(R.string.health_connect_temporarily_unavailable)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private suspend fun hasHealthConnectWritePermission(): Boolean? =
        try {
            HealthConnectBackend.hasWritePermission(applicationContext).also { granted ->
                healthConnectPermissionKnownGranted = granted
            }
        } catch (e: Exception) {
            Log.e(TAG, "hasHealthConnectWritePermission: $e")
            null
        }

    private fun showHealthConnectPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.health_connect_permission_denied_title)
            .setMessage(R.string.health_connect_permission_denied_message)
            .setPositiveButton(R.string.health_connect_open_settings) { _, _ ->
                healthSettingsPending = true
                try {
                    startActivity(HealthConnectBackend.permissionSettingsIntent(this))
                } catch (e: Exception) {
                    Log.e(TAG, "openHealthConnectPermissions: $e")
                    if (!openHealthConnectSettings()) {
                        healthSettingsPending = false
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openHealthConnectSettings(): Boolean =
        try {
            startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
            true
        } catch (e: Exception) {
            Log.e(TAG, "openHealthConnectSettings: $e")
            false
        }

    fun openHealthConnectProvider() {
        for (intent in HealthConnectProviderInstaller.updateIntents()) {
            try {
                startActivity(intent)
                return
            } catch (e: Exception) {
                Log.e(TAG, "openHealthConnectProvider: $e")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun completeHealthConnectEnable() {
        HealthConnectBackend.recordPermissionGrant(applicationContext)
        val preferences = HealthConnectBackend.localPreferences(applicationContext)
        if (preferences.getBoolean(HealthConnectBackend.INITIALIZED_KEY, false)) {
            finishHealthConnectEnable()
            return
        }
        healthSettingsPending = false
        if (!healthConnectCheckPending) {
            healthConnectRetryDelayMs = HEALTH_CONNECT_RETRY_INITIAL_DELAY_MS
            setHealthConnectCheckPending(true)
            refreshFragment()
        }
        startHealthConnectHistoryCheck()
    }

    private fun setHealthConnectCheckPending(pending: Boolean) {
        healthConnectCheckPending = pending
        HealthConnectBackend.localPreferences(applicationContext).edit {
            putBoolean(HealthConnectBackend.CHECK_PENDING_KEY, pending)
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun startHealthConnectHistoryCheck() {
        if (!healthConnectCheckPending || healthConnectImportDialogShowing) {
            return
        }
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            return
        }
        if (healthConnectCheckJob?.isActive == true) {
            return
        }
        val generation = ++healthConnectCheckGeneration
        healthConnectCheckJob = lifecycleScope.launch {
            try {
                while (isActive &&
                    healthConnectCheckPending &&
                    generation == healthConnectCheckGeneration
                ) {
                    val previous = try {
                        withTimeout(HEALTH_CONNECT_READ_TIMEOUT_MS) {
                            DataModel.healthConnectImportCandidates(
                                HealthConnectBackend.previousSleeps(applicationContext)
                            )
                        }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Health Connect permission lost during history check: $e")
                        healthConnectPermissionKnownGranted = false
                        stopHealthConnectHistoryCheck(cancelJob = false)
                        HealthConnectBackend.localPreferences(applicationContext).edit {
                            putBoolean(HealthConnectBackend.ENABLED_KEY, false)
                            putBoolean(HealthConnectBackend.INITIALIZED_KEY, false)
                        }
                        refreshFragment()
                        showHealthConnectPermissionDeniedDialog()
                        return@launch
                    } catch (e: TimeoutCancellationException) {
                        Log.e(
                            TAG,
                            "Health Connect history check timed out after " +
                                "$HEALTH_CONNECT_READ_TIMEOUT_MS ms; retrying"
                        )
                        delay(healthConnectRetryDelayMs)
                        healthConnectRetryDelayMs = (healthConnectRetryDelayMs * 2).coerceAtMost(
                            HEALTH_CONNECT_RETRY_MAX_DELAY_MS
                        )
                        continue
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Quota and provider failures are transient. Keep the pending state visible
                        // and retry only while this activity remains in the foreground.
                        Log.e(TAG, "Health Connect history check failed; retrying: $e")
                        delay(healthConnectRetryDelayMs)
                        healthConnectRetryDelayMs = (healthConnectRetryDelayMs * 2).coerceAtMost(
                            HEALTH_CONNECT_RETRY_MAX_DELAY_MS
                        )
                        continue
                    }
                    if (!healthConnectCheckPending ||
                        generation != healthConnectCheckGeneration
                    ) {
                        return@launch
                    }
                    if (previous.isNotEmpty()) {
                        showHealthConnectImportDialog(previous)
                    } else {
                        finishHealthConnectEnable()
                    }
                    return@launch
                }
            } finally {
                if (generation == healthConnectCheckGeneration) {
                    healthConnectCheckJob = null
                }
            }
        }
    }

    private fun pauseHealthConnectHistoryCheck() {
        healthConnectCheckGeneration++
        healthConnectCheckJob?.cancel()
        healthConnectCheckJob = null
    }

    private fun stopHealthConnectHistoryCheck(cancelJob: Boolean = true) {
        setHealthConnectCheckPending(false)
        healthConnectCheckGeneration++
        if (cancelJob) {
            healthConnectCheckJob?.cancel()
        }
        healthConnectCheckJob = null
        healthConnectRetryDelayMs = HEALTH_CONNECT_RETRY_INITIAL_DELAY_MS
    }

    fun skipHealthConnectHistoryCheck() {
        if (healthConnectCheckPending) {
            finishHealthConnectEnable()
        }
    }

    fun cancelHealthConnectHistoryCheck() {
        cancelHealthConnectHistoryCheck(refresh = true)
    }

    private fun cancelHealthConnectHistoryCheck(refresh: Boolean) {
        if (!healthConnectCheckPending) {
            return
        }
        stopHealthConnectHistoryCheck()
        healthSettingsPending = false
        HealthConnectBackend.localPreferences(applicationContext).edit {
            putBoolean(HealthConnectBackend.ENABLED_KEY, false)
            putBoolean(HealthConnectBackend.INITIALIZED_KEY, false)
        }
        HealthConnectBackend.cancelSync(applicationContext)
        if (refresh) {
            refreshFragment()
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun showHealthConnectImportDialog(sleeps: List<Sleep>) {
        if (!healthConnectCheckPending || healthConnectImportDialogShowing) {
            return
        }
        healthConnectImportDialogShowing = true
        val canReadAllHistory = HealthConnectBackend.canReadAllHistory(applicationContext)
        val message = if (canReadAllHistory) {
            R.plurals.health_connect_import_message_all_history
        } else {
            R.plurals.health_connect_import_message
        }
        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.health_connect_import_title)
            .setMessage(
                resources.getQuantityString(
                    message,
                    sleeps.size,
                    sleeps.size
                )
            )
            .setPositiveButton(R.string.health_connect_import) { _, _ ->
                lifecycleScope.launch {
                    DataModel.importHealthConnectSleeps(sleeps)
                    finishHealthConnectEnable()
                }
            }
            .setNegativeButton(R.string.health_connect_skip) { _, _ ->
                finishHealthConnectEnable()
            }
            .setCancelable(false)
        if (canReadAllHistory) {
            builder.setNeutralButton(R.string.health_connect_wipe, null)
        }
        val dialog = builder.create()
        dialog.setOnDismissListener {
            healthConnectImportDialogShowing = false
        }
        dialog.setOnShowListener {
            if (!canReadAllHistory) {
                return@setOnShowListener
            }
            val importButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            val skipButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)
            val wipeButton = dialog.getButton(DialogInterface.BUTTON_NEUTRAL)
            wipeButton.isEnabled = false
            lifecycleScope.launch {
                for (remaining in HEALTH_CONNECT_WIPE_DELAY_SECONDS downTo 1) {
                    wipeButton.text = getString(
                        R.string.health_connect_wipe_countdown,
                        remaining
                    )
                    delay(1000)
                    if (!dialog.isShowing) {
                        return@launch
                    }
                }
                wipeButton.text = getString(R.string.health_connect_wipe)
                wipeButton.isEnabled = true
            }
            wipeButton.setOnClickListener {
                importButton.isEnabled = false
                skipButton.isEnabled = false
                wipeButton.isEnabled = false
                lifecycleScope.launch {
                    val work = HealthConnectBackend.scheduleWipe(applicationContext)
                    if (work != null &&
                        HealthConnectBackend.awaitSuccess(applicationContext, work) == true
                    ) {
                        dialog.dismiss()
                        finishHealthConnectEnable()
                    } else {
                        Log.e(TAG, "wipeHealthConnect: the wipe did not complete")
                        toast(R.string.health_connect_temporarily_unavailable)
                        importButton.isEnabled = true
                        skipButton.isEnabled = true
                        wipeButton.isEnabled = true
                    }
                }
            }
        }
        dialog.show()
    }

    private fun finishHealthConnectEnable() {
        stopHealthConnectHistoryCheck()
        healthSettingsPending = false
        val preferences = HealthConnectBackend.localPreferences(applicationContext)
        preferences.edit {
            putBoolean(HealthConnectBackend.ENABLED_KEY, true)
            putBoolean(HealthConnectBackend.INITIALIZED_KEY, true)
        }
        forceHealthConnectSync(showResult = false)
        lifecycleScope.launch {
            if (DataModel.hasSleeps() ||
                DataModel.database.healthConnectDao().getDeletions().isNotEmpty()
            ) {
                HealthConnectBackend.scheduleSync(applicationContext)
            }
        }
        refreshFragment()
    }

    fun forceHealthConnectSync() {
        forceHealthConnectSync(showResult = true)
    }

    private fun forceHealthConnectSync(showResult: Boolean) {
        val work = HealthConnectBackend.forceReconcile(applicationContext) ?: return
        if (!showResult) {
            return
        }
        lifecycleScope.launch {
            val succeeded =
                HealthConnectBackend.awaitSuccess(applicationContext, work) ?: return@launch
            toast(
                if (succeeded) {
                    R.string.health_connect_force_sync_finished
                } else {
                    R.string.health_connect_temporarily_unavailable
                }
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_ADD_DRIVE_AFTER_FOLDER, addDriveAfterFolder)
        outState.putString(STATE_REPLACE_FOLDER_ON_SIGN_IN, replaceFolderOnSignIn?.path)
        outState.putString(STATE_REPLACE_DRIVE_ON_FOLDER_PICKED, replaceDriveOnFolderPicked)
        outState.putString(STATE_CHANGE_PATH_FROM, changePathFrom?.path)
        outState.putString(STATE_CHANGE_ACCOUNT_EMAIL, changeAccountFrom?.email)
        outState.putString(STATE_CHANGE_ACCOUNT_FREQUENCY, changeAccountFrom?.frequency)
        outState.putString(STATE_AUTHORIZE_EMAIL, authorizeEmail)
        outState.putBoolean(STATE_HEALTH_SETTINGS_PENDING, healthSettingsPending)
        outState.putBoolean(STATE_HEALTH_CHECK_PENDING, healthConnectCheckPending)
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && healthConnectCheckPending) {
            startHealthConnectHistoryCheck()
        }
    }

    override fun onStop() {
        if (healthConnectCheckPending) {
            if (isFinishing) {
                // Leaving settings is an implicit cancel; backgrounding merely pauses the read.
                cancelHealthConnectHistoryCheck(refresh = false)
            } else {
                pauseHealthConnectHistoryCheck()
            }
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return
        }
        lifecycleScope.launch {
            if (healthSettingsPending) {
                when (hasHealthConnectWritePermission()) {
                    true -> completeHealthConnectEnable()
                    false -> {
                        healthConnectPermissionKnownGranted = false
                        healthSettingsPending = false
                    }
                    null -> return@launch
                }
            } else {
                val preferences = HealthConnectBackend.localPreferences(applicationContext)
                if (preferences.getBoolean(HealthConnectBackend.ENABLED_KEY, false) &&
                    !preferences.getBoolean(HealthConnectBackend.INITIALIZED_KEY, false)
                ) {
                    completeHealthConnectEnable()
                }
            }
        }
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

    // Second step of adding a Drive account: the Play Services consent screen, shown only when
    // the account has not granted the appDataFolder scope to this app yet.
    private val driveConsentResult =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val email = authorizeEmail
            authorizeEmail = null
            lifecycleScope.launch {
                finishDriveSignIn(
                    if (email == null) {
                        DriveSignInResult.Failed
                    } else {
                        DriveBackend.handleAuthorizationResult(
                            applicationContext, email, result.data
                        )
                    }
                )
            }
        }

    // First step of adding a Drive account: the system account picker, which names the account
    // the destination is keyed by.
    private val driveAccountPickerResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val email = DriveBackend.readPickedAccount(result.data)
            if (email == null) {
                // Backing out of the account picker is a deliberate choice, not an error.
                finishDriveSignIn(DriveSignInResult.Cancelled)
                return@registerForActivityResult
            }
            lifecycleScope.launch {
                when (val authorization = DriveBackend.authorize(applicationContext, email)) {
                    is DriveAuthorization.Consent -> {
                        authorizeEmail = email
                        driveConsentResult.launch(
                            IntentSenderRequest.Builder(authorization.pendingIntent).build()
                        )
                    }
                    DriveAuthorization.Granted ->
                        finishDriveSignIn(DriveSignInResult.Success(email))
                    DriveAuthorization.Failed -> finishDriveSignIn(DriveSignInResult.Failed)
                }
            }
        }

    /**
     * Applies the outcome of a Drive sign-in and consumes the pending state of the flow that
     * started it, no matter which of the two steps the flow ended at.
     */
    private fun finishDriveSignIn(signIn: DriveSignInResult) {
        val changeFrom = changeAccountFrom
        changeAccountFrom = null
        val replaceFolder = replaceFolderOnSignIn
        replaceFolderOnSignIn = null
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
            // Backing out of the flow is a deliberate choice, not an error.
            DriveSignInResult.Cancelled -> {}
            DriveSignInResult.Failed -> toast(R.string.drive_sign_in_failure)
        }
        refreshFragment()
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
        val intent = DriveBackend.createAccountPickerIntent() ?: return
        driveAccountPickerResult.launch(intent)
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
        val intent = DriveBackend.createAccountPickerIntent() ?: return
        changeAccountFrom = dest
        driveAccountPickerResult.launch(intent)
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
