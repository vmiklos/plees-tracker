/*
 * Copyright 2019 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

/**
 * A foreground service that just keeps the app alive, so the state is not lost
 * while tracking is on.
 */
public class MainService extends Service
{
    private static final String NOTIFICATION_CHANNEL_ID = "Notification";
    @Override public int onStartCommand(Intent intent, int flags, int startId)
    {
        Log.d("plees", "MainService.onStartCommand");

        NotificationManager notificationManager =
            (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.O)
        {
            NotificationChannel channel =
                new NotificationChannel(NOTIFICATION_CHANNEL_ID, "channel",
                                        NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent =
            PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Notification notification =
            new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentText("Sleep tracking is in progress")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .build();

        startForeground(42, notification);

        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent)
    {
        // We don't provide binding, so return null
        return null;
    }
}
