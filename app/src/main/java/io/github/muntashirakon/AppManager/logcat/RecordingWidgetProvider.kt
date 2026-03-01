// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.logcat.helper.PreferenceHelper
import io.github.muntashirakon.AppManager.logcat.helper.ServiceHelper
import io.github.muntashirakon.AppManager.main.MainActivity

/**
 * Copyright 2012 Nolan Lawson
 */
class RecordingWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == MainActivity.ACTION_STOP_RECORDING_FROM_NOTIFICATION) {
            ServiceHelper.stopService(context)
            updateWidget(context)
        } else {
            super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            PreferenceHelper.setWidgetExistsPreference(context, intArrayOf(appWidgetId))
            updateWidget(context, appWidgetId)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val editor = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE).edit()
        for (appWidgetId in appWidgetIds) {
            val key = "widget_" + appWidgetId
            editor.remove(key)
        }
        editor.apply()
        super.onDeleted(context, appWidgetIds)
    }

    companion object {
        @JvmStatic
        fun updateWidget(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = android.content.ComponentName(context, RecordingWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            updateWidget(context, *appWidgetIds)
        }

        @JvmStatic
        fun updateWidget(context: Context, vararg appWidgetIds: Int) {
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_recording)
            val running = ServiceHelper.isServiceRunning(context)

            remoteViews.setViewVisibility(R.id.start_button, if (running) View.GONE else View.VISIBLE)
            remoteViews.setViewVisibility(R.id.stop_button, if (running) View.VISIBLE else View.GONE)

            val intent: Intent
            val action: String
            val pendingIntent: PendingIntent

            if (running) {
                intent = Intent(MainActivity.ACTION_STOP_RECORDING_FROM_NOTIFICATION)
                action = MainActivity.ACTION_STOP_RECORDING_FROM_NOTIFICATION
            } else {
                intent = Intent(context, RecordLogDialogActivity::class.java)
                action = Intent.ACTION_MAIN
            }
            intent.addCategory(Intent.CATEGORY_LAUNCHER)

            pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            remoteViews.setOnClickPendingIntent(R.id.start_button, pendingIntent)
            remoteViews.setOnClickPendingIntent(R.id.stop_button, PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

            val appWidgetManager = AppWidgetManager.getInstance(context)
            for (appWidgetId in appWidgetIds) {
                appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
            }
        }
    }
}
