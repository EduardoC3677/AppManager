// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import io.github.muntashirakon.AppManager.db.AppsDb
import io.github.muntashirakon.AppManager.logs.Log

class ArchiveResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (ACTION_ARCHIVE_RESULT == intent.action) {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
            if (packageName == null) {
                Log.e(TAG, "Package name is null in archive result")
                return
            }
            val archivedAppDao = AppsDb.getInstance().archivedAppDao()
            when (status) {
                PackageInstaller.STATUS_SUCCESS -> {
                    Log.d(TAG, "App archived successfully: $packageName")
                }
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    Log.d(TAG, "User action required for archiving: $packageName")
                    val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    confirmIntent?.let {
                        context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
                else -> {
                    Log.e(TAG, "App archiving failed for $packageName: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
                    archivedAppDao.deleteByPackageNameSync(packageName)
                }
            }
        }
    }

    companion object {
        const val ACTION_ARCHIVE_RESULT = "io.github.muntashirakon.AppManager.ACTION_ARCHIVE_RESULT"
        private const val TAG = "ArchiveResultReceiver"
    }
}
