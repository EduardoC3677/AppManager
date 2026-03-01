// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.PendingIntentCompat
import com.google.android.material.color.MaterialColors
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.settings.FeatureController
import io.github.muntashirakon.AppManager.users.Users
import io.github.muntashirakon.AppManager.utils.DateUtils
import io.github.muntashirakon.AppManager.utils.ExUtils
import io.github.muntashirakon.AppManager.utils.appearance.AppearanceUtils
import io.github.muntashirakon.util.UiUtils
import java.util.*

class ScreenTimeAppWidget : AppWidgetProvider() {
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
            // Fetch screens time
            val userIds = Users.getUsersIds()
            val packageUsageInfoList = mutableListOf<PackageUsageInfo>()
            val usageStatsManager = AppUsageStatsManager.getInstance()
            val interval = UsageUtils.getToday()
            for (userId in userIds) {
                ExUtils.exceptionAsIgnored { packageUsageInfoList.addAll(usageStatsManager.getUsageStats(interval, userId)) }
            }
            packageUsageInfoList.sortWith { o1, o2 -> -o1.screenTime.compareTo(o2.screenTime) }
            var totalScreenTime = 0L
            for (appItem in packageUsageInfoList) {
                totalScreenTime += appItem.screenTime
            }
            // Construct the RemoteViews object
            val appWidgetSize = getAppWidgetSize(themedContext, appWidgetManager, appWidgetId)
            val views = when {
                appWidgetSize.height <= 200 -> RemoteViews(themedContext.packageName, R.layout.app_widget_screen_time_small)
                appWidgetSize.width <= 250 -> RemoteViews(themedContext.packageName, R.layout.app_widget_screen_time)
                else -> RemoteViews(themedContext.packageName, R.layout.app_widget_screen_time_large)
            }
            // Set screen time
            views.setTextViewText(R.id.screen_time, DateUtils.getFormattedDurationShort(totalScreenTime, false, true, false))
            val len = Math.min(packageUsageInfoList.size, 3)
            // Set visibility
            val app3_visibility = if (len == 3) View.VISIBLE else View.INVISIBLE
            val app2_visibility = if (len >= 2) View.VISIBLE else View.INVISIBLE
            val app1_visibility = if (len >= 1) View.VISIBLE else View.INVISIBLE
            views.setViewVisibility(R.id.app3_circle, app3_visibility)
            views.setViewVisibility(R.id.app3_time, app3_visibility)
            views.setViewVisibility(R.id.app3_label, app3_visibility)
            views.setViewVisibility(R.id.app2_circle, app2_visibility)
            views.setViewVisibility(R.id.app2_time, app2_visibility)
            views.setViewVisibility(R.id.app2_label, app2_visibility)
            views.setViewVisibility(R.id.app1_circle, app1_visibility)
            views.setViewVisibility(R.id.app1_time, app1_visibility)
            views.setViewVisibility(R.id.app1_label, app1_visibility)
            // Set app info
            if (app3_visibility == View.VISIBLE) {
                val item3 = packageUsageInfoList[2]
                views.setTextViewText(R.id.app3_label, item3.appLabel)
                views.setTextViewText(R.id.app3_time, DateUtils.getFormattedDurationSingle(item3.screenTime, false))
            }
            if (app2_visibility == View.VISIBLE) {
                val item2 = packageUsageInfoList[1]
                views.setTextViewText(R.id.app2_label, item2.appLabel)
                views.setTextViewText(R.id.app2_time, DateUtils.getFormattedDurationSingle(item2.screenTime, false))
            }
            if (app1_visibility == View.VISIBLE) {
                val item1 = packageUsageInfoList[0]
                views.setTextViewText(R.id.app1_label, item1.appLabel)
                views.setTextViewText(R.id.app1_time, DateUtils.getFormattedDurationSingle(item1.screenTime, false))
            }
            // Set colors
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val isNight = UiUtils.isDarkMode(themedContext)
                val colorSurface = MaterialColors.getColor(themedContext, com.google.android.material.R.attr.colorSurface, "colorSurface")
                val colorSurfaceInverse = MaterialColors.getColor(themedContext, com.google.android.material.R.attr.colorSurfaceInverse, "colorSurfaceInverse")
                val color1 = ColorStateList.valueOf(MaterialColors.harmonizeWithPrimary(themedContext, Color.parseColor("#1b1b1b")))
                val color2 = ColorStateList.valueOf(MaterialColors.harmonizeWithPrimary(themedContext, Color.parseColor("#565e71")))
                val color3 = ColorStateList.valueOf(MaterialColors.harmonizeWithPrimary(themedContext, Color.parseColor("#d4e3ff")))
                views.setColorStateList(R.id.app1_time, "setBackgroundTintList", color1)
                views.setColorStateList(R.id.app1_circle, "setBackgroundTintList", color1)
                views.setColorStateList(R.id.app2_time, "setBackgroundTintList", color2)
                views.setColorStateList(R.id.app2_circle, "setBackgroundTintList", color2)
                views.setColorStateList(R.id.app3_time, "setBackgroundTintList", color3)
                views.setColorStateList(R.id.app3_circle, "setBackgroundTintList", color3)
                if (isNight) {
                    views.setColorInt(R.id.widget_background, "setBackgroundColor", colorSurfaceInverse, colorSurface)
                } else views.setColorInt(R.id.widget_background, "setBackgroundColor", colorSurface, colorSurfaceInverse)
            }
            // Get PendingIntent for App Usage page
            val appUsageIntent = Intent(themedContext, AppUsageActivity::class.java)
            val appUsagePendingIntent = PendingIntentCompat.getActivity(themedContext, 0,
                appUsageIntent, PendingIntent.FLAG_UPDATE_CURRENT, false)
            views.setOnClickPendingIntent(R.id.widget_background, appUsagePendingIntent)
            // Get PendingIntent for widget update
            val appWidgetIntent = Intent(themedContext, ScreenTimeAppWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            }
            val appWidgetPendingIntent = PendingIntentCompat.getBroadcast(themedContext, 0,
                appWidgetIntent, PendingIntent.FLAG_UPDATE_CURRENT, false)
            views.setOnClickPendingIntent(R.id.screen_time_refresh, appWidgetPendingIntent)
            // Instruct the widget manager to update the widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        @JvmStatic
        private fun getAppWidgetSize(context: Context, manager: AppWidgetManager, appWidgetId: Int): Size {
            val appWidgetOptions = manager.getAppWidgetOptions(appWidgetId)
            val isPortrait = context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            val width = appWidgetOptions.getInt(if (isPortrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            val height = appWidgetOptions.getInt(if (isPortrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            return Size(width, height)
        }
    }
}
