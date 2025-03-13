/*
 * Copyright 2023 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import android.os.Build

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderType = intent.getStringExtra("reminder_type") ?: "unknown"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for API 26+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "bedtime_reminder_channel",
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = context.getString(R.string.notification_channel_description)
            notificationManager.createNotificationChannel(channel)
        }

        val title = when (reminderType) {
            "bedtime" -> context.getString(R.string.bedtime_reminder_title)
            "wakeup" -> context.getString(R.string.wakeup_reminder_title)
            else -> context.getString(R.string.default_reminder_title)
        }
        val message = when (reminderType) {
            "bedtime" -> context.getString(R.string.bedtime_reminder_message)
            "wakeup" -> context.getString(R.string.wakeup_reminder_message)
            else -> context.getString(R.string.default_reminder_message)
        }

        val notification = NotificationCompat.Builder(context, "bedtime_reminder_channel")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(reminderType.hashCode(), notification)
    }
}

