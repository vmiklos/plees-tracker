/*
 * Copyright 2026 Aleksandrs Serbajevs
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import android.content.Context
import android.content.SharedPreferences
import android.os.RemoteException
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.InsertRecordsResponse
import androidx.health.connect.client.response.ReadRecordsResponse
import androidx.health.connect.client.testing.FakeHealthConnectClient
import androidx.health.connect.client.testing.FakePermissionController
import androidx.preference.PreferenceManager
import androidx.room.Room
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.reflect.KClass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Reconciliation tests using the official in-memory Health Connect client. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class HealthConnectSyncTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val packageName = "hu.vmiklos.plees_tracker"
    private lateinit var database: AppDatabase
    private lateinit var client: FakeHealthConnectClient

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        DataModel.database = database
        client = FakeHealthConnectClient(
            packageName,
            Clock.systemUTC(),
            FakePermissionController()
        )
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun testNoForegroundWork() = runBlocking {
        val hasWork = HealthConnectBackend.hasForegroundWork(Instant.EPOCH)

        assertFalse(hasWork)
    }

    @Test
    fun testPendingWriteRequiresForegroundWork() = runBlocking {
        database.sleepDao().insert(sleep(1))

        val hasWork = HealthConnectBackend.hasForegroundWork(Instant.EPOCH)

        assertTrue(hasWork)
    }

    @Test
    fun testInvalidPendingWriteDoesNotRequireForegroundWork() = runBlocking {
        database.sleepDao().insert(sleep(1).apply { stop = start })

        val hasWork = HealthConnectBackend.hasForegroundWork(Instant.EPOCH)

        assertFalse(hasWork)
    }

    @Test
    fun testReadableDeletionRequiresForegroundWork() = runBlocking {
        database.healthConnectDao().insertDeletions(
            listOf(HealthConnectDeletion(sleep(1).healthConnectId, 3_000))
        )

        val hasWork = HealthConnectBackend.hasForegroundWork(Instant.ofEpochMilli(3_000))

        assertTrue(hasWork)
    }

    @Test
    fun testUnreadableDeletionDoesNotRequireForegroundWork() = runBlocking {
        database.healthConnectDao().insertDeletions(
            listOf(HealthConnectDeletion(sleep(1).healthConnectId, 2_999))
        )

        val hasWork = HealthConnectBackend.hasForegroundWork(Instant.ofEpochMilli(3_000))

        assertFalse(hasWork)
    }

    @Test
    fun testDeleteUsesCurrentDatabaseHealthConnectId() = runBlocking {
        database.sleepDao().insert(sleep(1).apply { healthConnectId = "" })
        val staleSleep = database.sleepDao().getAll().single()
        val assignedId = UUID.randomUUID().toString()
        database.healthConnectDao().assignId(staleSleep.sid, assignedId)

        DataModel.deleteSleepFromDatabase(staleSleep)

        assertEquals(
            listOf(HealthConnectDeletion(assignedId, staleSleep.start)),
            database.healthConnectDao().getDeletions()
        )
    }

    @Test
    fun testDeletedSleepIsNotWrittenAfterIdAssignmentFails() = runBlocking {
        database.sleepDao().insert(sleep(1).apply { healthConnectId = "" })
        val pending = database.sleepDao().getPendingHealthConnectWrites()
        database.sleepDao().delete(pending.single())

        HealthConnectBackend.write(client, pending)

        assertEquals(0, HealthConnectBackend.readOwnRecords(client, packageName).size)
    }

    @Test
    fun testCreate() = runBlocking {
        val sleep = sleep(1).apply {
            comment = "edited"
        }
        database.sleepDao().insert(sleep)

        HealthConnectBackend.sync(client, packageName)
        val records = HealthConnectBackend.readOwnRecords(client, packageName)
        assertEquals(1, records.size)
        assertEquals(HealthConnectNotes.encode(sleep), records.single().notes)
    }

    @Test
    fun testDeleteClearsTombstone() = runBlocking {
        val sleep = sleep(1)
        database.sleepDao().insert(sleep)
        HealthConnectBackend.sync(client, packageName)

        database.healthConnectDao().insertDeletions(
            listOf(HealthConnectDeletion(sleep.healthConnectId))
        )
        database.sleepDao().delete(sleep)
        HealthConnectBackend.sync(client, packageName)
        // FakeHealthConnectClient does not remove records like the platform provider does; the
        // record-ID arguments are verified separately below.
        assertEquals(0, database.healthConnectDao().getDeletions().size)
    }

    @Test
    fun testBackgroundWritePathDoesNotRead() = runBlocking {
        val sleep = sleep(1)
        database.sleepDao().insert(sleep)
        val noReadClient = object : HealthConnectClient by client {
            override suspend fun <T : Record> readRecords(
                request: ReadRecordsRequest<T>
            ): ReadRecordsResponse<T> {
                throw AssertionError("write-only synchronization must not read")
            }
        }

        HealthConnectBackend.write(noReadClient)

        assertEquals(1, HealthConnectBackend.readOwnRecords(client, packageName).size)
        assertEquals(0, database.sleepDao().getPendingHealthConnectWrites().size)
    }

    @Test
    fun testWriteAssignsMissingIds() = runBlocking {
        val first = Sleep().apply {
            start = 1_000
            stop = 2_000
        }
        val second = Sleep().apply {
            start = 3_000
            stop = 4_000
        }
        database.sleepDao().insert(listOf(first, second))

        HealthConnectBackend.write(client)

        val sleeps = database.sleepDao().getAll()
        assertTrue(sleeps.all { it.healthConnectId.isNotEmpty() })
        assertNotEquals(sleeps[0].healthConnectId, sleeps[1].healthConnectId)
        assertEquals(0, database.sleepDao().getPendingHealthConnectWrites().size)
    }

    @Test
    fun testWriteBatchesCheckpointProgressAfterFailure() = runBlocking {
        failSecondWriteBatch()

        assertEquals(1, database.sleepDao().getPendingHealthConnectWrites().size)
    }

    @Test
    fun testWriteBatchesResumeAfterFailure() = runBlocking {
        failSecondWriteBatch()

        HealthConnectBackend.write(client)

        assertEquals(0, database.sleepDao().getPendingHealthConnectWrites().size)
    }

    @Test
    fun testForceReconcileRepairsCancelledWrite() = runBlocking {
        val sleepCount = HealthConnectBackend.HEALTH_CONNECT_BATCH_SIZE + 1
        database.sleepDao().insert((1..sleepCount).map(::sleep))
        var calls = 0
        val cancelledClient = object : HealthConnectClient by client {
            override suspend fun insertRecords(records: List<Record>): InsertRecordsResponse {
                calls++
                if (calls == 2) {
                    throw CancellationException("replaced by force reconciliation")
                }
                return client.insertRecords(records)
            }
        }
        try {
            HealthConnectBackend.write(cancelledClient)
        } catch (_: CancellationException) {
            // WorkManager REPLACE can cancel an automatic write after a completed batch.
        }

        HealthConnectBackend.sync(client, packageName)

        assertEquals(
            Pair(sleepCount, 0),
            Pair(
                HealthConnectBackend.readOwnRecords(client, packageName).size,
                database.sleepDao().getPendingHealthConnectWrites().size
            )
        )
    }

    @Test
    fun testRapidWritesAndForceReconciliationsConverge() = runBlocking {
        repeat(12) { round ->
            val firstIndex = round * 4 + 1
            database.sleepDao().insert((firstIndex until firstIndex + 4).map(::sleep))
            database.sleepDao().getAll().forEachIndexed { index, stored ->
                if (index % 3 == round % 3) {
                    stored.comment = "round $round, sleep $index"
                    stored.healthConnectVersion++
                    database.sleepDao().update(stored)
                }
            }
            if (round % 3 == 2) {
                HealthConnectBackend.sync(client, packageName)
            } else {
                HealthConnectBackend.write(client)
            }
        }
        repeat(3) {
            HealthConnectBackend.sync(client, packageName)
        }

        val local = database.sleepDao().getAll()
        val expected = local.map { stored ->
            Triple(
                HealthConnectBackend.CLIENT_ID_PREFIX + stored.healthConnectId,
                stored.healthConnectVersion,
                HealthConnectNotes.encode(stored)
            )
        }.sortedBy { it.first }
        val actual = HealthConnectBackend.readOwnRecords(client, packageName).map { record ->
            Triple(
                record.metadata.clientRecordId,
                record.metadata.clientRecordVersion,
                record.notes
            )
        }.sortedBy { it.first }
        assertEquals(
            Pair(expected, 0),
            Pair(actual, database.sleepDao().getPendingHealthConnectWrites().size)
        )
    }

    @Test
    fun testConcurrentEditIsNotCheckpointedAsWritten() = runBlocking {
        val sleep = sleep(1)
        database.sleepDao().insert(sleep)
        val editingClient = object : HealthConnectClient by client {
            override suspend fun insertRecords(records: List<Record>): InsertRecordsResponse {
                val response = client.insertRecords(records)
                val edited = database.sleepDao().getAll().single()
                edited.healthConnectVersion = 1
                database.sleepDao().update(edited)
                return response
            }
        }

        HealthConnectBackend.write(editingClient)

        val pending = database.sleepDao().getPendingHealthConnectWrites()
        assertEquals(1, pending.size)
        assertEquals(1, pending.single().healthConnectVersion)
        assertEquals(-1, pending.single().healthConnectSyncedVersion)
    }

    @Test
    fun testMatchingRemoteRecordClearsPendingWriteWithoutUpsert() = runBlocking {
        val sleep = sleep(1)
        database.sleepDao().insert(sleep)
        client.insertRecords(listOf(HealthConnectBackend.toRecord(sleep)))
        val noInsertClient = object : HealthConnectClient by client {
            override suspend fun insertRecords(records: List<Record>): InsertRecordsResponse {
                throw AssertionError("matching records must not be rewritten")
            }
        }

        HealthConnectBackend.sync(noInsertClient, packageName)

        assertEquals(0, database.sleepDao().getPendingHealthConnectWrites().size)
    }

    @Test
    fun testReconciliationOnlyProcessesReadablePeriod() = runBlocking {
        val readablePeriodStart = Instant.now().minusSeconds(60)
        val historical = sleep(1).apply {
            start = readablePeriodStart.minusSeconds(60).toEpochMilli()
            stop = start + 1_000
        }
        val current = sleep(2).apply {
            start = readablePeriodStart.plusSeconds(1).toEpochMilli()
            stop = start + 1_000
        }
        database.sleepDao().insert(listOf(historical, current))

        HealthConnectBackend.sync(client, packageName, readablePeriodStart)

        assertEquals(
            listOf(historical.healthConnectId),
            database.sleepDao().getPendingHealthConnectWrites().map { it.healthConnectId }
        )
        assertEquals(1, HealthConnectBackend.readOwnRecords(client, packageName).size)
    }

    @Test
    fun testReadCutoffIsPersistedAtPermissionGrant() {
        val preferences = context.getSharedPreferences(
            HealthConnectBackend.LOCAL_PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        val hadCutoff = preferences.contains(HealthConnectBackend.READ_CUTOFF_KEY)
        val previousCutoff = preferences.getLong(HealthConnectBackend.READ_CUTOFF_KEY, 0)
        preferences.edit().remove(HealthConnectBackend.READ_CUTOFF_KEY).commit()
        try {
            val grantedAt = Instant.parse("2026-07-14T12:00:00Z").toEpochMilli()
            HealthConnectBackend.recordPermissionGrant(context, grantedAt, 33)
            HealthConnectBackend.recordPermissionGrant(context, grantedAt + 86_400_000, 33)

            assertEquals(
                Instant.parse("2026-06-14T12:00:00Z"),
                HealthConnectBackend.readablePeriodStart(
                    context,
                    grantedAt + 86_400_000,
                    33
                )
            )
        } finally {
            val editor = preferences.edit()
            if (hadCutoff) {
                editor.putLong(HealthConnectBackend.READ_CUTOFF_KEY, previousCutoff)
            } else {
                editor.remove(HealthConnectBackend.READ_CUTOFF_KEY)
            }
            editor.commit()
        }
    }

    @Test
    fun testDisabledStateUsesBackupExcludedPreferences() {
        withRestoredEnabledPreferences { backedUpPreferences ->
            backedUpPreferences.edit()
                .putBoolean(HealthConnectBackend.ENABLED_KEY, true)
                .commit()
            HealthConnectBackend.setEnabled(context, false)

            assertFalse(HealthConnectBackend.isEnabled(context))
        }
    }

    @Test
    fun testEnabledStateUsesBackupExcludedPreferences() {
        withRestoredEnabledPreferences { backedUpPreferences ->
            backedUpPreferences.edit()
                .putBoolean(HealthConnectBackend.ENABLED_KEY, false)
                .commit()
            HealthConnectBackend.setEnabled(context, true)

            assertTrue(HealthConnectBackend.isEnabled(context))
        }
    }

    @Test
    fun testLimitedReconciliationRetainsOnlyUnreadableDeletion() = runBlocking {
        val readablePeriodStart = Instant.now().minusSeconds(60)
        val historical = sleep(1).apply {
            start = readablePeriodStart.minusSeconds(60).toEpochMilli()
            stop = start + 1_000
        }
        val current = sleep(2).apply {
            start = readablePeriodStart.plusSeconds(1).toEpochMilli()
            stop = start + 1_000
        }
        client.insertRecords(
            listOf(
                HealthConnectBackend.toRecord(historical),
                HealthConnectBackend.toRecord(current)
            )
        )
        database.healthConnectDao().insertDeletions(
            listOf(
                HealthConnectDeletion(historical.healthConnectId, historical.start),
                HealthConnectDeletion(current.healthConnectId, current.start),
                HealthConnectDeletion(UUID.randomUUID().toString(), current.start)
            )
        )

        HealthConnectBackend.sync(client, packageName, readablePeriodStart)

        assertEquals(
            listOf(HealthConnectDeletion(historical.healthConnectId, historical.start)),
            database.healthConnectDao().getDeletions()
        )
    }

    @Test
    fun testImportOfferWithExistingLocalSleeps() = runBlocking {
        val local = sleep(1)
        database.sleepDao().insert(local)
        val sameIdWithRemoteContents = sleep(2).apply {
            healthConnectId = local.healthConnectId
        }
        val sameContentsWithDifferentId = sleep(1).apply {
            healthConnectId = UUID.randomUUID().toString()
        }
        val remoteOnly = sleep(3)

        val candidates = DataModel.healthConnectImportCandidates(
            listOf(sameIdWithRemoteContents, sameContentsWithDifferentId, remoteOnly)
        )

        assertEquals(listOf(remoteOnly), candidates)
    }

    @Test
    fun testWipeDeletesAllOwnedRecordsInBatches() = runBlocking {
        val recordCount = HealthConnectBackend.HEALTH_CONNECT_BATCH_SIZE + 1
        val records = (1..recordCount).map { HealthConnectBackend.toRecord(sleep(it)) }
        client.insertRecords(records)
        val batchSizes = mutableListOf<Int>()
        val clientRecordIdBatches = mutableListOf<List<String>>()
        val recordingClient = object : HealthConnectClient by client {
            override suspend fun deleteRecords(
                recordType: KClass<out Record>,
                recordIdsList: List<String>,
                clientRecordIdsList: List<String>
            ) {
                batchSizes.add(recordIdsList.size)
                clientRecordIdBatches.add(clientRecordIdsList)
            }
        }

        HealthConnectBackend.wipe(recordingClient, packageName)

        assertEquals(listOf(1000, 1), batchSizes)
        assertTrue(clientRecordIdBatches.all { it.isEmpty() })
    }

    @Test
    fun testRateLimitedWriteEventuallyCompletes() = runBlocking {
        val sleep = sleep(1)
        database.sleepDao().insert(sleep)
        val rateLimitedClient = AlternatingRateLimitClient(client)

        val attempts = syncUntilSuccess(rateLimitedClient)

        assertTrue(attempts > 1)
        assertEquals(1, HealthConnectBackend.readOwnRecords(client, packageName).size)
    }

    @Test
    fun testRateLimitedDeletionEventuallyCompletes() = runBlocking {
        val sleep = sleep(1)
        database.sleepDao().insert(sleep)
        client.insertRecords(listOf(HealthConnectBackend.toRecord(sleep)))

        database.healthConnectDao().insertDeletions(
            listOf(HealthConnectDeletion(sleep.healthConnectId))
        )
        database.sleepDao().delete(sleep)
        val rateLimitedClient = AlternatingRateLimitClient(client)

        val attempts = syncUntilSuccess(rateLimitedClient)

        assertTrue(attempts > 1)
        assertEquals(0, database.healthConnectDao().getDeletions().size)
    }

    @Test
    fun testFailedDeletionRetainsTombstone() = runBlocking {
        val remoteSleep = sleep(1)
        client.insertRecords(listOf(HealthConnectBackend.toRecord(remoteSleep)))
        val deletion = HealthConnectDeletion(remoteSleep.healthConnectId)
        database.healthConnectDao().insertDeletions(listOf(deletion))
        val failingClient = object : HealthConnectClient by client {
            override suspend fun deleteRecords(
                recordType: KClass<out Record>,
                recordIdsList: List<String>,
                clientRecordIdsList: List<String>
            ) {
                throw RemoteException("temporarily unavailable")
            }
        }

        try {
            HealthConnectBackend.deletePending(failingClient, packageName)
        } catch (_: RemoteException) {
            // The next foreground session retries the retained tombstone.
        }

        assertEquals(listOf(deletion), database.healthConnectDao().getDeletions())
    }

    @Test
    fun testDeletionUsesRemoteRecordId() = runBlocking {
        val remoteSleep = sleep(1)
        client.insertRecords(listOf(HealthConnectBackend.toRecord(remoteSleep)))
        val remoteRecordId = HealthConnectBackend.readOwnRecords(client, packageName)
            .single().metadata.id
        val deletion = HealthConnectDeletion(remoteSleep.healthConnectId)
        database.healthConnectDao().insertDeletions(listOf(deletion))
        var recordIds: List<String>? = null
        var clientRecordIds: List<String>? = null
        val recordingClient = object : HealthConnectClient by client {
            override suspend fun deleteRecords(
                recordType: KClass<out Record>,
                recordIdsList: List<String>,
                clientRecordIdsList: List<String>
            ) {
                recordIds = recordIdsList
                clientRecordIds = clientRecordIdsList
            }
        }

        HealthConnectBackend.deletePending(recordingClient, packageName)

        assertEquals(listOf(remoteRecordId), recordIds)
        assertEquals(emptyList<String>(), clientRecordIds)
        assertEquals(0, database.healthConnectDao().getDeletions().size)
    }

    @Test
    fun testAlreadyAbsentDeletionClearsTombstone() = runBlocking {
        val deletion = HealthConnectDeletion(sleep(1).healthConnectId)
        database.healthConnectDao().insertDeletions(listOf(deletion))

        HealthConnectBackend.deletePending(client, packageName)

        assertEquals(0, database.healthConnectDao().getDeletions().size)
    }

    @Test
    fun testDeletionBatchesCheckpointProgressAfterFailure() = runBlocking {
        failSecondDeletionBatch()

        assertEquals(1300, database.healthConnectDao().getDeletions().size)
    }

    @Test
    fun testDeletionBatchesResumeAfterFailure() = runBlocking {
        val remoteByClientId = failSecondDeletionBatch()

        val succeedingClient = object : HealthConnectClient by client {
            override suspend fun deleteRecords(
                recordType: KClass<out Record>,
                recordIdsList: List<String>,
                clientRecordIdsList: List<String>
            ) = Unit
        }
        HealthConnectBackend.deletePending(succeedingClient, remoteByClientId)

        assertEquals(0, database.healthConnectDao().getDeletions().size)
    }

    @Test
    fun testUnmatchedHistoricalRecordSurvivesSync() = runBlocking {
        val historical = sleep(1)
        client.insertRecords(listOf(HealthConnectBackend.toRecord(historical)))

        val current = sleep(2)
        database.sleepDao().insert(current)
        HealthConnectBackend.sync(client, packageName)

        val records = HealthConnectBackend.readOwnRecords(client, packageName)
        assertEquals(
            setOf(historical.healthConnectId, current.healthConnectId),
            records.mapNotNull { it.metadata.clientRecordId }
                .map { it.removePrefix(HealthConnectBackend.CLIENT_ID_PREFIX) }
                .toSet()
        )
    }

    @Test
    fun testUnmatchedHistoricalRecordSurvivesUnrelatedDeletion() = runBlocking {
        val historical = sleep(1)
        client.insertRecords(listOf(HealthConnectBackend.toRecord(historical)))

        val current = sleep(2)
        database.sleepDao().insert(current)
        HealthConnectBackend.sync(client, packageName)
        val records = HealthConnectBackend.readOwnRecords(client, packageName)

        database.healthConnectDao().insertDeletions(
            listOf(HealthConnectDeletion(current.healthConnectId))
        )
        database.sleepDao().delete(current)
        val remoteByClientId = records.associateBy { it.metadata.clientRecordId }
        var deletedRecordIds: List<String>? = null
        val recordingClient = object : HealthConnectClient by client {
            override suspend fun deleteRecords(
                recordType: KClass<out Record>,
                recordIdsList: List<String>,
                clientRecordIdsList: List<String>
            ) {
                deletedRecordIds = recordIdsList
            }
        }
        HealthConnectBackend.sync(recordingClient, packageName)

        assertEquals(
            listOf(
                remoteByClientId[HealthConnectBackend.CLIENT_ID_PREFIX + current.healthConnectId]
                    ?.metadata?.id
            ),
            deletedRecordIds
        )
        assertEquals(0, database.healthConnectDao().getDeletions().size)
    }

    @Test
    fun testBatchedDeletionStateCleanup() = runBlocking {
        val deletions = (1..2300).map { index ->
            HealthConnectDeletion(
                UUID.nameUUIDFromBytes(index.toString().toByteArray()).toString()
            )
        }
        val dao = database.healthConnectDao()
        dao.insertDeletions(deletions)

        dao.deleteDeletionsBatched(deletions.map { it.healthConnectId })

        assertEquals(0, dao.getDeletions().size)
    }

    @Test
    fun testBatchingAndPagination() = runBlocking {
        val sleepCount = HealthConnectBackend.HEALTH_CONNECT_BATCH_SIZE + 1
        database.sleepDao().insert((1..sleepCount).map(::sleep))

        HealthConnectBackend.sync(client, packageName)

        assertEquals(sleepCount, HealthConnectBackend.readOwnRecords(client, packageName).size)
        assertEquals(0, database.sleepDao().getPendingHealthConnectWrites().size)
    }

    @Test
    fun testReadStopsOnBlankPageToken() = runBlocking {
        val requestedPageTokens = mutableListOf<String?>()
        val blankTerminalTokenClient = object : HealthConnectClient by client {
            override suspend fun <T : Record> readRecords(
                request: ReadRecordsRequest<T>
            ): ReadRecordsResponse<T> {
                requestedPageTokens.add(request.pageToken)
                return ReadRecordsResponse(emptyList(), "")
            }
        }

        HealthConnectBackend.readOwnRecords(blankTerminalTokenClient, packageName)

        assertEquals(listOf<String?>(null), requestedPageTokens)
    }

    @Test
    fun testReadStopsOnRepeatedPageToken() = runBlocking {
        val requestedPageTokens = mutableListOf<String?>()
        val repeatedTokenClient = object : HealthConnectClient by client {
            override suspend fun <T : Record> readRecords(
                request: ReadRecordsRequest<T>
            ): ReadRecordsResponse<T> {
                requestedPageTokens.add(request.pageToken)
                return ReadRecordsResponse(emptyList(), "repeated")
            }
        }

        HealthConnectBackend.readOwnRecords(repeatedTokenClient, packageName)

        assertEquals(listOf(null, "repeated"), requestedPageTokens)
    }

    private fun sleep(index: Int): Sleep = Sleep().apply {
        start = index * 3_000L
        stop = start + 1_000
        healthConnectId = UUID.nameUUIDFromBytes(index.toString().toByteArray()).toString()
    }

    private suspend fun failSecondWriteBatch() {
        val sleepCount = HealthConnectBackend.HEALTH_CONNECT_BATCH_SIZE + 1
        database.sleepDao().insert((1..sleepCount).map(::sleep))
        var calls = 0
        val secondBatchFailureClient = object : HealthConnectClient by client {
            override suspend fun insertRecords(records: List<Record>): InsertRecordsResponse {
                calls++
                if (calls == 2) {
                    throw RemoteException("rate limited")
                }
                return client.insertRecords(records)
            }
        }
        try {
            HealthConnectBackend.write(secondBatchFailureClient)
        } catch (_: RemoteException) {
            // The successful first chunk must stay checkpointed.
        }
    }

    private fun withRestoredEnabledPreferences(block: (SharedPreferences) -> Unit) {
        val localPreferences = HealthConnectBackend.localPreferences(context)
        val backedUpPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val hadLocalValue = localPreferences.contains(HealthConnectBackend.ENABLED_KEY)
        val previousLocalValue = localPreferences.getBoolean(
            HealthConnectBackend.ENABLED_KEY,
            false
        )
        val hadBackedUpValue = backedUpPreferences.contains(HealthConnectBackend.ENABLED_KEY)
        val previousBackedUpValue = backedUpPreferences.getBoolean(
            HealthConnectBackend.ENABLED_KEY,
            false
        )
        try {
            block(backedUpPreferences)
        } finally {
            localPreferences.edit().apply {
                if (hadLocalValue) {
                    putBoolean(HealthConnectBackend.ENABLED_KEY, previousLocalValue)
                } else {
                    remove(HealthConnectBackend.ENABLED_KEY)
                }
            }.commit()
            backedUpPreferences.edit().apply {
                if (hadBackedUpValue) {
                    putBoolean(HealthConnectBackend.ENABLED_KEY, previousBackedUpValue)
                } else {
                    remove(HealthConnectBackend.ENABLED_KEY)
                }
            }.commit()
        }
    }

    private suspend fun failSecondDeletionBatch(): Map<String, SleepSessionRecord> {
        val deletions = (1..2300).map { index ->
            HealthConnectDeletion(
                UUID.nameUUIDFromBytes(index.toString().toByteArray()).toString()
            )
        }
        database.healthConnectDao().insertDeletions(deletions)
        val remoteByClientId = (1..2300).associate { index ->
            val sleep = sleep(index)
            HealthConnectBackend.CLIENT_ID_PREFIX + sleep.healthConnectId to
                HealthConnectBackend.toRecord(sleep)
        }
        var calls = 0
        val secondBatchFailureClient = object : HealthConnectClient by client {
            override suspend fun deleteRecords(
                recordType: KClass<out Record>,
                recordIdsList: List<String>,
                clientRecordIdsList: List<String>
            ) {
                calls++
                if (calls == 2) {
                    throw RemoteException("rate limited")
                }
            }
        }
        try {
            HealthConnectBackend.deletePending(secondBatchFailureClient, remoteByClientId)
        } catch (_: RemoteException) {
            // The successful first chunk must stay checkpointed.
        }
        return remoteByClientId
    }

    private suspend fun syncUntilSuccess(client: HealthConnectClient): Int {
        for (attempt in 1..MAX_SYNC_ATTEMPTS) {
            try {
                HealthConnectBackend.sync(client, packageName)
                return attempt
            } catch (_: RemoteException) {
                // A later foreground session repeats reconciliation after a transient error.
            }
        }
        throw AssertionError("Health Connect synchronization did not complete")
    }

    private class AlternatingRateLimitClient(
        private val delegate: HealthConnectClient
    ) : HealthConnectClient by delegate {
        private var inserts = 0
        private var reads = 0
        private var deletions = 0

        override suspend fun insertRecords(records: List<Record>): InsertRecordsResponse {
            rateLimit(++inserts)
            return delegate.insertRecords(records)
        }

        override suspend fun <T : Record> readRecords(
            request: ReadRecordsRequest<T>
        ): ReadRecordsResponse<T> {
            rateLimit(++reads)
            return delegate.readRecords(request)
        }

        override suspend fun deleteRecords(
            recordType: KClass<out Record>,
            recordIdsList: List<String>,
            clientRecordIdsList: List<String>
        ) {
            rateLimit(++deletions)
            delegate.deleteRecords(recordType, recordIdsList, clientRecordIdsList)
        }

        private fun rateLimit(operation: Int) {
            if (operation % 2 == 1) {
                throw RemoteException("rate limited")
            }
        }
    }

    companion object {
        private const val MAX_SYNC_ATTEMPTS = 10
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
