// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.muntashirakon.AppManager.db.AppsDb
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class PackageInstallReceiver : BroadcastReceiver() {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_PACKAGE_ADDED == intent.action) {
            val data = intent.data
            if (data != null) {
                val packageName = data.schemeSpecificPart
                if (packageName != null) {
                    val pendingResult = goAsync()
                    GlobalScope.launch {
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
