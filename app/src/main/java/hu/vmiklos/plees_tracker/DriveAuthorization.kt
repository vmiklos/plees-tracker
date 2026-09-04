/*
 * Copyright 2026 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import android.app.PendingIntent

/**
 * Outcome of asking Play Services for the Drive appDataFolder scope on an account's behalf.
 * Consent is not an error: it just means Play Services wants to show its consent screen before
 * granting, which only the activity can launch.
 */
sealed class DriveAuthorization {
    /** The account already granted the scope, no user interaction is needed. */
    object Granted : DriveAuthorization()
    data class Consent(val pendingIntent: PendingIntent) : DriveAuthorization()
    object Failed : DriveAuthorization()
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
