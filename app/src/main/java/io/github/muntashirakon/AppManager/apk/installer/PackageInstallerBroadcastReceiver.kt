// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.app.PendingIntentCompat
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.intercept.IntentCompat
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.AppManager.utils.NotificationUtils
import io.github.muntashirakon.AppManager.utils.Utils

class PackageInstallerBroadcastReceiver : BroadcastReceiver() {
    private var mPackageName: String? = null
    private var mAppLabel: CharSequence? = null
    private var mConfirmNotificationId = 0

    fun setPackageName(packageName: String?) {
        mPackageName = packageName
    }

    fun setAppLabel(appLabel: CharSequence?) {
        mAppLabel = appLabel
    }

    override fun onReceive(nullableContext: Context?, intent: Intent) {
        val context = nullableContext ?: ContextUtils.getContext()
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        Log.d(TAG, "Session ID: $sessionId")
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                Log.d(TAG, "Requesting user confirmation...")
                val broadcastIntent2 = Intent(PackageInstallerCompat.ACTION_INSTALL_INTERACTION_BEGIN).apply {
                    `package` = context.packageName
                    putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, mPackageName)
                    putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId)
                }
                context.sendBroadcast(broadcastIntent2)
                val confirmIntent = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                val intent2 = Intent(context, PackageInstallerActivity::class.java).apply {
                    action = PackageInstallerActivity.ACTION_PACKAGE_INSTALLED
                    putExtra(Intent.EXTRA_INTENT, confirmIntent)
                    putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, mPackageName)
                    putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val appInForeground = Utils.isAppInForeground()
                if (appInForeground) {
                    context.startActivity(intent2)
                }
                val broadcastCancel = Intent(PackageInstallerCompat.ACTION_INSTALL_COMPLETED).apply {
                    `package` = context.packageName
                    putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, mPackageName)
                    putExtra(PackageInstaller.EXTRA_STATUS, PackageInstallerCompat.STATUS_FAILURE_ABORTED)
                    putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId)
                }
                mConfirmNotificationId = NotificationUtils.displayInstallConfirmNotification(context) { builder ->
                    builder.setAutoCancel(false)
                        .setSilent(appInForeground)
                        .setDefaults(Notification.DEFAULT_ALL)
                        .setWhen(System.currentTimeMillis())
                        .setSmallIcon(R.drawable.ic_default_notification)
                        .setTicker(mAppLabel)
                        .setContentTitle(mAppLabel)
                        .setSubText(context.getString(R.string.package_installer))
                        .setContentText(context.getString(if (sessionId == -1) R.string.confirm_uninstallation else R.string.confirm_installation))
                        .setContentIntent(PendingIntentCompat.getActivity(context, 0, intent2, PendingIntent.FLAG_UPDATE_CURRENT, false))
                        .setDeleteIntent(PendingIntentCompat.getBroadcast(context, 0, broadcastCancel, PendingIntent.FLAG_UPDATE_CURRENT, false))
                        .build()
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Log.d(TAG, "Install success!")
                NotificationUtils.cancelInstallConfirmNotification(context, mConfirmNotificationId)
                PackageInstallerCompat.sendCompletedBroadcast(context, mPackageName!!, PackageInstallerCompat.STATUS_SUCCESS, sessionId)
            }
            else -> {
                NotificationUtils.cancelInstallConfirmNotification(context, mConfirmNotificationId)
                val broadcastError = Intent(PackageInstallerCompat.ACTION_INSTALL_COMPLETED).apply {
                    `package` = context.packageName
                    val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, statusMessage)
                    putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, mPackageName)
                    putExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME, intent.getStringExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME))
                    putExtra(PackageInstaller.EXTRA_STATUS, status)
                    putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId)
                }
                context.sendBroadcast(broadcastError)
                Log.d(TAG, "Install failed! ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
            }
        }
    }

    companion object {
        val TAG: String = PackageInstallerBroadcastReceiver::class.java.simpleName
        const val ACTION_PI_RECEIVER = "${BuildConfig.APPLICATION_ID}.action.PI_RECEIVER"
    }
}
