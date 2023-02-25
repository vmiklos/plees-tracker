/*
 * Copyright 2023 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews

/**
 * Provides a widget that opens the main activity and immediately toggles between started/stopped
 * sleep tracking.
 */
class ToggleWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context?,
        appWidgetManager: AppWidgetManager?,
        appWidgetIds: IntArray?
    ) {
        Log.d(TAG, "ToggleWidget.onUpdate")
        if (context == null) {
            return
        }

        if (appWidgetManager == null) {
            return
        }

        if (appWidgetIds == null) {
            return
        }

        for (appWidgetId in appWidgetIds) {
            val intent = Intent(context, MainActivity::class.java)
            intent.putExtra("startStop", true)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            // Needed for Android 12+
            flags = flags or PendingIntent.FLAG_IMMUTABLE
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent, flags
            )
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_layout_toggle)
            remoteViews.setOnClickPendingIntent(R.id.widget_toggle, pendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        }
    }

    companion object {
        private const val TAG = "ToggleWidget"
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
