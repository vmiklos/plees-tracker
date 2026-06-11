/*
 * Copyright 2026 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

/**
 * Outcome of the Google Drive sign-in flow. Cancelled (the user backed out of the account picker
 * on purpose) is kept separate from Failed so the UI only reports actual errors.
 */
sealed class DriveSignInResult {
    data class Success(val email: String) : DriveSignInResult()
    object Cancelled : DriveSignInResult()
    object Failed : DriveSignInResult()
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
