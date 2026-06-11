/*
 * Copyright 2026 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import org.json.JSONArray
import org.json.JSONObject

/** A single configured backup target: either a local folder or a Google Drive account. */
sealed class BackupDestination {

    data class LocalFolder(val path: String) : BackupDestination()

    data class DriveAccount(
        val email: String,
        val frequency: String = "daily"
    ) : BackupDestination()

    fun toJson(): JSONObject = when (this) {
        is LocalFolder -> JSONObject().put("type", "folder").put("path", path)
        is DriveAccount -> JSONObject()
            .put("type", "drive")
            .put("email", email)
            .put("frequency", frequency)
    }

    companion object {
        private fun fromJson(json: JSONObject): BackupDestination? = when (json.optString("type")) {
            "folder" -> json.optString("path").takeIf { it.isNotEmpty() }
                ?.let { LocalFolder(it) }
            "drive" -> json.optString("email").takeIf { it.isNotEmpty() }
                ?.let { DriveAccount(it, json.optString("frequency", "daily")) }
            else -> null
        }

        fun listFromJson(jsonStr: String): List<BackupDestination> = try {
            val arr = JSONArray(jsonStr)
            (0 until arr.length()).mapNotNull { fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }

        fun listToJson(destinations: List<BackupDestination>): String =
            JSONArray().also { arr -> destinations.forEach { arr.put(it.toJson()) } }.toString()

        /**
         * Builds the destination list for the one-time migration from the legacy
         * single-destination preference keys, which only supported a folder.
         */
        fun fromLegacyPreferences(
            autoBackup: Boolean,
            folderPath: String?
        ): List<BackupDestination> {
            if (!autoBackup) {
                return emptyList()
            }
            return folderPath?.takeIf { it.isNotEmpty() }
                ?.let { listOf(LocalFolder(it)) }
                ?: emptyList()
        }
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
