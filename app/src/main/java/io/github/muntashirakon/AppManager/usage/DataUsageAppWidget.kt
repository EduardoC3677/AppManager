// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import android.widget.RemoteViews
import androidx.core.app.PendingIntentCompat
import com.google.android.material.color.MaterialColors
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.settings.FeatureController
import io.github.muntashirakon.AppManager.utils.appearance.AppearanceUtils
import io.github.muntashirakon.util.UiUtils

class DataUsageAppWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    companion object {
        @JvmStatic
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            var themedContext = context
            if (!FeatureController.isUsageAccessEnabled() || !SelfPermissions.checkUsageStatsPermission()) {
                return
            }
            // Fetch colors
            themedContext = AppearanceUtils.getThemedWidgetContext(themedContext, false)
            // Fetch data
            val interval = UsageUtils.getToday()
            val mobileData = AppUsageStatsManager.getMobileData(interval)
            val wifiData = AppUsageStatsManager.getWifiData(interval)
            var mobileTx = 0L
            var mobileRx = 0L
            var wifiTx = 0L
            var wifiRx = 0L
            for (i in 0 until mobileData.size()) {
                val usage = mobileData.valueAt(i)!!
                mobileRx += usage.getRx()
                mobileTx += usage.getTx()
            }
            for (i in 0 until wifiData.size()) {
                val usage = wifiData.valueAt(i)!!
                wifiRx += usage.getRx()
                wifiTx += usage.getTx()
            }
            val totalTx = mobileTx + wifiTx
            val totalRx = mobileRx + wifiRx
            // Construct the RemoteViews object
            val views = RemoteViews(themedContext.packageName, R.layout.app_widget_data_usage_small)
            // Set data usage
            views.setTextViewText(R.id.data_usage, String.format("↑ %1\$s ↓ %2\$s",
                Formatter.formatShortFileSize(themedContext, totalTx),
                Formatter.formatShortFileSize(themedContext, totalRx)))
            // Set colors
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val isNight = UiUtils.isDarkMode(themedContext)
                val colorSurface = MaterialColors.getColor(themedContext, com.google.android.material.R.attr.colorSurface, "colorSurface")
                val colorSurfaceInverse = MaterialColors.getColor(themedContext, com.google.android.material.R.attr.colorSurfaceInverse, "colorSurfaceInverse")
                if (isNight) {
                    views.setColorInt(R.id.widget_background, "setBackgroundColor", colorSurfaceInverse, colorSurface)
                } else {
                    views.setColorInt(R.id.widget_background, "setBackgroundColor", colorSurface, colorSurfaceInverse)
                }
            }
            // Get PendingIntent for App Usage page
            val appUsageIntent = Intent(themedContext, AppUsageActivity::class.java)
            val appUsagePendingIntent = PendingIntentCompat.getActivity(themedContext, 0,
                appUsageIntent, PendingIntent.FLAG_UPDATE_CURRENT, false)
            views.setOnClickPendingIntent(R.id.widget_background, appUsagePendingIntent)
            // Get PendingIntent for widget update
            val appWidgetIntent = Intent(themedContext, DataUsageAppWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            }
            val appWidgetPendingIntent = PendingIntentCompat.getBroadcast(themedContext, 0,
                appWidgetIntent, PendingIntent.FLAG_UPDATE_CURRENT, false)
            views.setOnClickPendingIntent(R.id.screen_time_refresh, appWidgetPendingIntent)
            // Instruct the widget manager to update the widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
