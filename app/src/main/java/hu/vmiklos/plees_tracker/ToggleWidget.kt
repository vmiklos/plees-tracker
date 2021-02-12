/*
 * Copyright 2021 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
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
        for (appWidgetId in appWidgetIds!!) {
            val intent = Intent(context, MainActivity::class.java)
            intent.putExtra("startStop", true)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT
            )
            val remoteViews = RemoteViews(context!!.packageName, R.layout.widget_layout_toggle)
            remoteViews.setOnClickPendingIntent(R.id.widget_toggle, pendingIntent)
            appWidgetManager!!.updateAppWidget(appWidgetId, remoteViews)
        }
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
