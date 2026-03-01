// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import io.github.muntashirakon.AppManager.self.filecache.InternalCacheCleanerService
import io.github.muntashirakon.AppManager.servermanager.WifiWaitService
import io.github.muntashirakon.AppManager.settings.Ops

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            if (Ops.getMode() == Ops.MODE_ADB_WIFI) {
                // Connect ADB
                val serviceIntent = Intent(context, WifiWaitService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
            // Schedule cache cleaning
            InternalCacheCleanerService.scheduleAlarm(context.applicationContext)
        }
    }
}
