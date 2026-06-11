/*
 * Copyright 2026 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for BackupDestination: JSON (de)serialization of the multi-account backup destination
 * list, and the legacy single-destination migration logic.
 */
class BackupDestinationUnitTest {

    @Test
    fun testLocalFolderRoundTrip() {
        val original = listOf<BackupDestination>(
            BackupDestination.LocalFolder("content://com.android.externalstorage/tree/primary")
        )
        val restored = BackupDestination.listFromJson(BackupDestination.listToJson(original))
        assertEquals(original, restored)
    }

    @Test
    fun testDriveAccountRoundTrip() {
        val original = listOf<BackupDestination>(
            BackupDestination.DriveAccount("user@example.com", "daily")
        )
        val restored = BackupDestination.listFromJson(BackupDestination.listToJson(original))
        assertEquals(original, restored)
    }

    @Test
    fun testMixedListPreservesOrder() {
        val original = listOf(
            BackupDestination.DriveAccount("a@example.com", "on_change"),
            BackupDestination.LocalFolder("content://folder"),
            BackupDestination.DriveAccount("b@example.com", "daily")
        )
        val restored = BackupDestination.listFromJson(BackupDestination.listToJson(original))
        assertEquals(original, restored)
    }

    @Test
    fun testEmptyListSerializesToEmptyJsonArray() {
        assertEquals("[]", BackupDestination.listToJson(emptyList()))
    }

    @Test
    fun testPathWithJsonSpecialCharactersRoundTrips() {
        // Folder URIs can contain characters that must be escaped in JSON.
        val original = listOf<BackupDestination>(
            BackupDestination.LocalFolder("""content://tree/a"b\c/üñîç""")
        )
        val restored = BackupDestination.listFromJson(BackupDestination.listToJson(original))
        assertEquals(original, restored)
    }

    @Test
    fun testEmptyJsonArrayParsesToEmptyList() {
        assertTrue(BackupDestination.listFromJson("[]").isEmpty())
    }

    @Test
    fun testMalformedJsonReturnsEmptyList() {
        assertTrue(BackupDestination.listFromJson("not json at all").isEmpty())
        assertTrue(BackupDestination.listFromJson("").isEmpty())
        assertTrue(BackupDestination.listFromJson("{}").isEmpty())
    }

    @Test
    fun testUnknownTypeIsSkipped() {
        val json = """[{"type":"ftp","host":"example.com"}]"""
        assertTrue(BackupDestination.listFromJson(json).isEmpty())
    }

    @Test
    fun testFolderWithoutPathIsSkipped() {
        val json = """[{"type":"folder"}]"""
        assertTrue(BackupDestination.listFromJson(json).isEmpty())
    }

    @Test
    fun testDriveWithoutEmailIsSkipped() {
        val json = """[{"type":"drive","frequency":"daily"}]"""
        assertTrue(BackupDestination.listFromJson(json).isEmpty())
    }

    @Test
    fun testDriveWithoutFrequencyDefaultsToDaily() {
        val json = """[{"type":"drive","email":"user@example.com"}]"""
        val restored = BackupDestination.listFromJson(json)
        assertEquals(
            listOf(BackupDestination.DriveAccount("user@example.com", "daily")),
            restored
        )
    }

    @Test
    fun testValidEntriesSurviveAlongsideInvalidOnes() {
        val json = """[
            {"type":"drive","email":"good@example.com","frequency":"daily"},
            {"type":"folder"},
            {"type":"folder","path":"content://valid"}
        ]"""
        val restored = BackupDestination.listFromJson(json)
        assertEquals(
            listOf(
                BackupDestination.DriveAccount("good@example.com", "daily"),
                BackupDestination.LocalFolder("content://valid")
            ),
            restored
        )
    }

    @Test
    fun testMigrationAutoBackupOffYieldsEmpty() {
        val result = BackupDestination.fromLegacyPreferences(
            autoBackup = false,
            folderPath = "content://x"
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun testMigrationFolder() {
        val result = BackupDestination.fromLegacyPreferences(
            autoBackup = true,
            folderPath = "content://primary/backup"
        )
        assertEquals(listOf(BackupDestination.LocalFolder("content://primary/backup")), result)
    }

    @Test
    fun testMigrationBlankPathYieldsEmpty() {
        val result = BackupDestination.fromLegacyPreferences(
            autoBackup = true,
            folderPath = ""
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun testMigrationNullPathYieldsEmpty() {
        val result = BackupDestination.fromLegacyPreferences(
            autoBackup = true,
            folderPath = null
        )
        assertTrue(result.isEmpty())
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
