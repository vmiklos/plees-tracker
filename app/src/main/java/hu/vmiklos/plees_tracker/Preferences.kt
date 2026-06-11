/*
 * Copyright 2023 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import kotlinx.coroutines.launch

class Preferences : PreferenceFragmentCompat() {

    // Cached hasBackup state per email, loaded asynchronously on fragment start.
    private val driveHasBackup = mutableMapOf<String, Boolean>()

    private fun padMinute(raw: String): String {
        val fro = ":([0-9])$".toRegex()
        val to = ":0$1"
        return raw.replace(fro, to)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        setupBackupPreferences()
        val wakeup = findPreference<Preference>("wakeup")
        wakeup?.let {
            val preferences = DataModel.preferences
            val value = preferences.getString("wakeup", "")
            if (value != null) {
                it.summary = padMinute(value)
            }
        }
        val bedtime = findPreference<Preference>("bedtime")
        bedtime?.let {
            val preferences = DataModel.preferences
            val value = preferences.getString("bedtime", "")
            if (value != null) {
                it.summary = padMinute(value)
            }
        }
    }

    private fun setupBackupPreferences() {
        val category = findPreference<PreferenceCategory>("backup_destinations_category") ?: return
        val staticKeys = setOf("automatic_backup", "add_backup_destination", "pretty_backup")

        // Remove previously-added dynamic destination rows (preserves static items).
        val toRemove = (0 until category.preferenceCount)
            .map { category.getPreference(it) }
            .filter { it.key !in staticKeys }
        toRemove.forEach { category.removePreference(it) }

        val destinations = DataModel.getDestinations()
        findPreference<SwitchPreference>("automatic_backup")?.apply {
            // Automatic backup needs at least one destination: turning the switch on with none
            // configured starts the add flow (which re-runs this setup; the switch falls back to
            // off below when the flow ended without adding anything, e.g. it was cancelled).
            if (isChecked && destinations.isEmpty()) {
                isChecked = false
            }
            setOnPreferenceChangeListener { _, newValue ->
                if (newValue == true && DataModel.getDestinations().isEmpty()) {
                    (activity as? PreferencesActivity)?.promptAddDestination()
                }
                true
            }
        }
        for ((index, dest) in destinations.withIndex()) {
            val pref = Preference(requireContext()).apply {
                key = "dest_$index"
                order = index
                when (dest) {
                    is BackupDestination.LocalFolder -> {
                        title = getString(R.string.backup_target_folder)
                        summary = dest.path
                        setOnPreferenceClickListener {
                            showFolderOptions(dest)
                            true
                        }
                    }
                    is BackupDestination.DriveAccount -> {
                        title = getString(R.string.backup_target_drive)
                        // Placeholder until the async hasBackup result arrives.
                        summary = if (driveHasBackup.containsKey(dest.email)) {
                            buildDriveSummary(dest, driveHasBackup[dest.email])
                        } else {
                            "${dest.email} · ${getString(R.string.drive_checking_backup)}"
                        }
                        setOnPreferenceClickListener {
                            showDriveOptions(dest)
                            true
                        }
                    }
                }
            }
            category.addPreference(pref)
            // Grey the row out while the master switch is off; must be set after addPreference,
            // the dependency is only resolvable once the row is part of the hierarchy.
            pref.dependency = "automatic_backup"
        }

        findPreference<Preference>("add_backup_destination")?.apply {
            // One destination of each type: the label names what the button will actually add,
            // and the row disappears once both a folder and a Drive account are configured.
            val folderAddable = destinations.none { it is BackupDestination.LocalFolder }
            val driveAddable = DriveBackend.isSupported &&
                destinations.none { it is BackupDestination.DriveAccount }
            isVisible = folderAddable || driveAddable
            setTitle(
                when {
                    folderAddable && driveAddable -> R.string.settings_add_backup_destination
                    folderAddable -> R.string.settings_add_device_backup_destination
                    else -> R.string.settings_add_drive_backup_destination
                }
            )
            setOnPreferenceClickListener {
                (activity as? PreferencesActivity)?.promptAddDestination()
                true
            }
        }

        // Async: refresh hasBackup state and update summaries for Drive accounts.
        val ctx = requireContext().applicationContext
        for ((index, dest) in destinations.withIndex()) {
            if (dest !is BackupDestination.DriveAccount) continue
            lifecycleScope.launch {
                if (!DriveBackend.isAccountOnDevice(ctx, dest.email)) {
                    // The account was removed from the device's system settings: backups can't
                    // run until it is added back, or the user picks another via "Change account".
                    findPreference<Preference>("dest_$index")?.summary =
                        "${dest.email} · ${getString(R.string.drive_account_unavailable)}"
                    return@launch
                }
                val has = DriveBackend.hasBackup(ctx, dest.email)
                driveHasBackup[dest.email] = has
                findPreference<Preference>("dest_$index")
                    ?.summary = buildDriveSummary(dest, has)
            }
        }
    }

    private fun buildDriveSummary(
        dest: BackupDestination.DriveAccount,
        hasBackup: Boolean?
    ): String {
        val freqLabel = if (dest.frequency == "daily") {
            getString(R.string.drive_backup_frequency_daily)
        } else {
            getString(R.string.drive_backup_frequency_on_change)
        }
        return if (hasBackup == true) "${dest.email} · $freqLabel" else dest.email
    }

    private fun showDriveOptions(dest: BackupDestination.DriveAccount) {
        val cached = driveHasBackup[dest.email]
        if (cached != null) {
            buildDriveOptionsDialog(dest, cached)
            return
        }
        // Result not yet available (e.g. slow network): fetch on-demand before showing the
        // dialog so Restore and Delete appear or stay hidden based on the actual backup state.
        lifecycleScope.launch {
            val ctx = requireContext().applicationContext
            val has = DriveBackend.hasBackup(ctx, dest.email)
            driveHasBackup[dest.email] = has
            // Update the summary now that we have the real result.
            val index = DataModel.getDestinations().indexOf(dest)
            if (index >= 0) {
                findPreference<Preference>("dest_$index")?.summary = buildDriveSummary(dest, has)
            }
            buildDriveOptionsDialog(dest, has)
        }
    }

    private fun buildDriveOptionsDialog(dest: BackupDestination.DriveAccount, hasBackup: Boolean) {
        val items = buildList {
            add(getString(R.string.drive_backup_now))
            add(getString(R.string.drive_frequency_change))
            if (hasBackup) add(getString(R.string.drive_restore))
            if (hasBackup) add(getString(R.string.drive_delete))
            add(getString(R.string.drive_account_change))
            add(getString(R.string.drive_account_remove))
        }
        AlertDialog.Builder(requireContext())
            .setTitle(dest.email)
            .setItems(items.toTypedArray()) { _, which ->
                val activity = activity as? PreferencesActivity ?: return@setItems
                when (items[which]) {
                    getString(R.string.drive_backup_now) ->
                        activity.backupNow(dest.email)
                    getString(R.string.drive_frequency_change) ->
                        activity.changeFrequency(dest)
                    getString(R.string.drive_restore) ->
                        activity.confirmRestoreFromDrive(dest.email)
                    getString(R.string.drive_delete) ->
                        confirmDeleteFromDrive(dest.email)
                    getString(R.string.drive_account_change) ->
                        activity.changeDriveAccount(dest)
                    getString(R.string.drive_account_remove) ->
                        confirmRemoveDriveAccount(dest.email, hasBackup)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteFromDrive(email: String) {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.drive_delete_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                (activity as? PreferencesActivity)?.deleteFromDrive(email)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmRemoveDriveAccount(email: String, hasBackup: Boolean) {
        val builder = AlertDialog.Builder(requireContext())
        if (hasBackup) {
            // An AlertDialog list replaces the message area, so the question goes in the title.
            val deleteBackup = booleanArrayOf(false)
            builder
                .setTitle(R.string.destination_remove_confirm)
                .setMultiChoiceItems(
                    arrayOf(getString(R.string.drive_remove_also_delete)), deleteBackup
                ) { _, _, isChecked -> deleteBackup[0] = isChecked }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    (activity as? PreferencesActivity)
                        ?.removeDriveAccount(email, deleteBackup[0])
                }
        } else {
            builder
                .setMessage(R.string.destination_remove_confirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    (activity as? PreferencesActivity)
                        ?.removeDriveAccount(email, deleteBackup = false)
                }
        }
        builder.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showFolderOptions(dest: BackupDestination.LocalFolder) {
        // The existence check is local-only and fast, so resolve it before building the dialog;
        // the remove confirmation only offers backup deletion when there is a file to delete.
        lifecycleScope.launch {
            val ctx = requireContext().applicationContext
            val hasBackup = DataModel.hasFolderBackup(ctx, dest.path)
            val items = arrayOf(
                getString(R.string.folder_change_path),
                getString(R.string.folder_destination_remove)
            )
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.backup_target_folder)
                .setItems(items) { _, which ->
                    val activity = activity as? PreferencesActivity ?: return@setItems
                    when (items[which]) {
                        getString(R.string.folder_change_path) ->
                            activity.changeFolderPath(dest)
                        getString(R.string.folder_destination_remove) ->
                            confirmRemoveFolder(dest, hasBackup)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun confirmRemoveFolder(dest: BackupDestination.LocalFolder, hasBackup: Boolean) {
        val builder = AlertDialog.Builder(requireContext())
        if (hasBackup) {
            // An AlertDialog list replaces the message area, so the question goes in the title.
            val deleteBackup = booleanArrayOf(false)
            builder
                .setTitle(R.string.destination_remove_confirm)
                .setMultiChoiceItems(
                    arrayOf(getString(R.string.folder_remove_also_delete)), deleteBackup
                ) { _, _, isChecked -> deleteBackup[0] = isChecked }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    (activity as? PreferencesActivity)
                        ?.removeFolderDestination(dest, deleteBackup[0])
                }
        } else {
            builder
                .setMessage(R.string.destination_remove_confirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    (activity as? PreferencesActivity)
                        ?.removeFolderDestination(dest, deleteBackup = false)
                }
        }
        builder.setNegativeButton(android.R.string.cancel, null).show()
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
