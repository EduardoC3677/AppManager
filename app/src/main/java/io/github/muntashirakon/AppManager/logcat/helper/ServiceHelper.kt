// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.helper

import android.app.ActivityManager
import android.content.Context
import io.github.muntashirakon.AppManager.logcat.LogcatRecordingService

/**
 * Copyright 2012 Nolan Lawson
 */
object ServiceHelper {
    @JvmStatic
    fun isServiceRunning(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (LogcatRecordingService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun stopService(context: Context) {
        context.stopService(LogcatRecordingService.getServiceIntent(context))
    }
}
