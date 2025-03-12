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
                "Bedtime Reminders",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "Channel for bedtime and wakeup reminders"
            notificationManager.createNotificationChannel(channel)
        }

        val title = when (reminderType) {
            "bedtime" -> "Bedtime Reminder"
            "wakeup" -> "Wake Up Reminder"
            else -> "Reminder"
        }
        val message = when (reminderType) {
            "bedtime" -> "It's time to go to bed. Remember to start tracking your sleep!"
            "wakeup" -> "Good morning! Don't forget to stop tracking if you haven't already."
            else -> "Time for your reminder."
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
