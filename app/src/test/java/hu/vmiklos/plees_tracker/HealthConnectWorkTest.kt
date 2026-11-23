/*
 * Copyright 2026 Aleksandrs Serbajevs
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Tests for the WorkManager result bridge used by Health Connect operations. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class HealthConnectWorkTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @Test
    fun testActiveCancellationIsRethrown() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                HealthConnectWorker.runOperation(
                    HealthConnectBackend.Operation.WRITE,
                    userInitiated = false
                ) {
                    throw CancellationException("work replaced")
                }
            }
        }
    }

    @Test
    fun testAutomaticFailureIsRetried() = runBlocking {
        val result = HealthConnectWorker.runOperation(
            HealthConnectBackend.Operation.WRITE,
            userInitiated = false
        ) {
            throw IOException("temporarily unavailable")
        }

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun testAutomaticPermissionFailureIsNotRetried() = runBlocking {
        val result = HealthConnectWorker.runOperation(
            HealthConnectBackend.Operation.WRITE,
            userInitiated = false
        ) {
            throw SecurityException("permission unavailable")
        }

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun testUserInitiatedFailureIsReported() = runBlocking {
        val result = HealthConnectWorker.runOperation(
            HealthConnectBackend.Operation.RECONCILE,
            userInitiated = true
        ) {
            throw IOException("temporarily unavailable")
        }

        assertEquals(
            ListenableWorker.Result.success(
                workDataOf(HealthConnectBackend.FAILED_KEY to true)
            ),
            result
        )
    }

    @Test
    fun testSuccessfulWorkResultIsReported() = runBlocking {
        val preferences = HealthConnectBackend.localPreferences(context)
        val hadEnabled = preferences.contains(HealthConnectBackend.ENABLED_KEY)
        val wasEnabled = HealthConnectBackend.isEnabled(context)
        HealthConnectBackend.setEnabled(context, false)
        val request = OneTimeWorkRequestBuilder<HealthConnectWorker>()
            .setInputData(
                workDataOf(
                    HealthConnectBackend.OPERATION_KEY to
                        HealthConnectBackend.Operation.WRITE.name,
                    HealthConnectBackend.USER_INITIATED_KEY to true
                )
            )
            .build()
        val succeeded = try {
            WorkManager.getInstance(context).enqueue(request).result.get()
            val driver = WorkManagerTestInitHelper.getTestDriver(context)
            driver?.setAllConstraintsMet(request.id)
            HealthConnectBackend.awaitSuccess(context, request.id)
        } finally {
            preferences.edit().apply {
                if (hadEnabled) {
                    putBoolean(HealthConnectBackend.ENABLED_KEY, wasEnabled)
                } else {
                    remove(HealthConnectBackend.ENABLED_KEY)
                }
            }.commit()
        }

        assertTrue(succeeded == true)
    }

    @Test
    fun testCancelledWorkHasNoResult() = runBlocking {
        val request = OneTimeWorkRequestBuilder<HealthConnectWorker>()
            .setInitialDelay(1, TimeUnit.DAYS)
            .setInputData(
                workDataOf(
                    HealthConnectBackend.OPERATION_KEY to
                        HealthConnectBackend.Operation.WRITE.name,
                    HealthConnectBackend.USER_INITIATED_KEY to true
                )
            )
            .build()
        val workManager = WorkManager.getInstance(context)
        workManager.enqueue(request).result.get()
        workManager.getWorkInfoByIdFlow(request.id).filterNotNull().first()
        workManager.cancelWorkById(request.id).result.get()

        val succeeded = HealthConnectBackend.awaitSuccess(context, request.id)

        assertNull(succeeded)
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
