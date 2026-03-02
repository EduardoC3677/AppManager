// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.muntashirakon.AppManager.db.AppsDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application-level coroutine scope for background tasks that outlive components
 * Uses SupervisorJob to prevent failure propagation
 */
object AppScope : CoroutineScope by SupervisorJob() + Dispatchers.Default

class PackageInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_PACKAGE_ADDED == intent.action) {
            val data = intent.data
            if (data != null) {
                val packageName = data.schemeSpecificPart
                if (packageName != null) {
                    val pendingResult = goAsync()
                    // Use application scope instead of GlobalScope to prevent memory leaks
                    AppScope.launch {
                        try {
                            AppsDb.getInstance().archivedAppDao().deleteByPackageName(packageName)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }
}
