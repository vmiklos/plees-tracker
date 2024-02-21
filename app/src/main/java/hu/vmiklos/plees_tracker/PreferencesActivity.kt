/*
 * Copyright 2023 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class PreferencesActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "PreferencesActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, Preferences())
            .commit()
        setContentView(R.layout.settings)
        DataModel.preferencesActivity = this
    }

    override fun onDestroy() {
        super.onDestroy()
        DataModel.preferencesActivity = null
    }

    private val backupActivityResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            var success = false
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                result.data?.data?.let { uri ->
                    contentResolver.takePersistableUriPermission(
                        uri,
                        flags
                    )
                    val editor = DataModel.preferences.edit()
                    editor.putString("auto_backup_path", uri.toString())
                    editor.apply()
                    success = true
                }

                if (!success) {
                    // Disable the bool setting when the user picked no folder.
                    val editor = DataModel.preferences.edit()
                    editor.putBoolean("auto_backup", false)
                    editor.apply()
                }

                // Refresh the view in case auto_backup or auto_backup_path changed.
                supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.settings_container, Preferences())
                    .commit()
            } catch (e: Exception) {
                Log.e(TAG, "onActivityResult: setting backup path failed")
            }
        }

    fun openFolderChooser() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        backupActivityResult.launch(intent)
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
