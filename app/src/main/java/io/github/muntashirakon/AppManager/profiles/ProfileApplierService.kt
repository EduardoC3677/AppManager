// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.app.ServiceCompat
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.batchops.BatchOpsResultsActivity
import io.github.muntashirakon.AppManager.batchops.BatchOpsService
import io.github.muntashirakon.AppManager.history.ops.OpHistoryManager
import io.github.muntashirakon.AppManager.intercept.IntentCompat
import io.github.muntashirakon.AppManager.progress.NotificationProgressHandler
import io.github.muntashirakon.AppManager.progress.NotificationProgressHandler.NotificationManagerInfo
import io.github.muntashirakon.AppManager.progress.ProgressHandler
import io.github.muntashirakon.AppManager.progress.QueuedProgressHandler
import io.github.muntashirakon.AppManager.types.ForegroundService
import io.github.muntashirakon.AppManager.utils.CpuUtils
import io.github.muntashirakon.AppManager.utils.NotificationUtils
import java.io.IOException

class ProfileApplierService : ForegroundService("ProfileApplierService") {
    private var mProgressHandler: QueuedProgressHandler? = null
    private var mNotificationInfo: NotificationProgressHandler.NotificationInfo? = null
    private var mWakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        mWakeLock = CpuUtils.getPartialWakeLock("profile_applier")
        mWakeLock!!.acquire()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isWorking) return super.onStartCommand(intent, flags, startId)
        val notificationManagerInfo = NotificationManagerInfo(CHANNEL_ID,
            "Profile Applier", NotificationManagerCompat.IMPORTANCE_LOW)
        mProgressHandler = NotificationProgressHandler(this,
            notificationManagerInfo,
            NotificationUtils.HIGH_PRIORITY_NOTIFICATION_INFO,
            NotificationUtils.HIGH_PRIORITY_NOTIFICATION_INFO)
        mProgressHandler!!.setProgressTextInterface(ProgressHandler.PROGRESS_REGULAR)
        mNotificationInfo = NotificationProgressHandler.NotificationInfo()
            .setBody(getString(R.string.operation_running))
            .setOperationName(getText(R.string.profiles))
        mProgressHandler!!.onAttach(this, mNotificationInfo!!)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onHandleIntent(intent: Intent?) {
        val item = getQueueItem(intent) ?: return
        val notify = intent?.getBooleanExtra(EXTRA_NOTIFY, true) ?: true
        val tempProfilePath = item.tempProfilePath
        try {
            val profileManager = ProfileManager(item.profileId, tempProfilePath)
            profileManager.applyProfile(item.state, mProgressHandler)
            profileManager.conclude()
            OpHistoryManager.addHistoryItem(OpHistoryManager.HISTORY_TYPE_PROFILE, item, true)
            sendNotification(item.profileName, Activity.RESULT_OK, notify, profileManager.requiresRestart())
        } catch (e: IOException) {
            sendNotification(item.profileName, Activity.RESULT_CANCELED, notify, false)
        } finally {
            tempProfilePath?.delete()
        }
    }

    override fun onQueued(intent: Intent?) {
        val item = getQueueItem(intent) ?: return
        val notificationInfo = NotificationProgressHandler.NotificationInfo()
            .setAutoCancel(true)
            .setTime(System.currentTimeMillis())
            .setOperationName(getText(R.string.profiles))
            .setTitle(item.profileName)
            .setBody(getString(R.string.added_to_queue))
        mProgressHandler!!.onQueue(notificationInfo)
    }

    override fun onStartIntent(intent: Intent?) {
        val item = getQueueItem(intent)
        if (item != null) {
            val notificationIntent = ProfileManager.getProfileIntent(this, item.profileType, item.profileId)
            val pendingIntent = PendingIntentCompat.getActivity(this, 0, notificationIntent, 0, false)
            mNotificationInfo!!.setDefaultAction(pendingIntent)
        }
        mNotificationInfo!!.title = item?.profileName
        mProgressHandler!!.onProgressStart(-1, 0, mNotificationInfo!!)
    }

    override fun onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        mProgressHandler?.onDetach(this)
        CpuUtils.releaseWakeLock(mWakeLock)
        super.onDestroy()
    }

    private fun getQueueItem(intent: Intent?): ProfileQueueItem? {
        if (intent == null) return null
        return IntentCompat.getUnwrappedParcelableExtra(intent, EXTRA_QUEUE_ITEM, ProfileQueueItem::class.java)
    }

    private fun sendNotification(profileName: String, result: Int, notify: Boolean, requiresRestart: Boolean) {
        val notificationInfo = NotificationProgressHandler.NotificationInfo()
            .setAutoCancel(true)
            .setTime(System.currentTimeMillis())
            .setOperationName(getText(R.string.profiles))
            .setTitle(profileName)
        when (result) {
            Activity.RESULT_CANCELED -> notificationInfo.setBody(getString(R.string.error))
            Activity.RESULT_OK -> notificationInfo.setBody(getString(R.string.the_operation_was_successful))
        }
        if (requiresRestart) {
            val intent = Intent(this, BatchOpsResultsActivity::class.java)
            intent.putExtra(BatchOpsService.EXTRA_REQUIRES_RESTART, true)
            val pendingIntent = PendingIntentCompat.getActivity(this, 0, intent, PendingIntent.FLAG_ONE_SHOT, false)
            notificationInfo.addAction(0, getString(R.string.restart_device), pendingIntent)
        }
        mProgressHandler!!.onResult(if (notify) notificationInfo else null)
    }

    companion object {
        private const val EXTRA_QUEUE_ITEM = "queue_item"
        private const val EXTRA_NOTIFY = "notify"
        private val CHANNEL_ID = "${BuildConfig.APPLICATION_ID}.channel.PROFILE_APPLIER"

        @JvmStatic
        fun getIntent(context: Context, queueItem: ProfileQueueItem, notify: Boolean): Intent {
            val intent = Intent(context, ProfileApplierService::class.java)
            IntentCompat.putWrappedParcelableExtra(intent, EXTRA_QUEUE_ITEM, queueItem)
            intent.putExtra(EXTRA_NOTIFY, notify)
            return intent
        }
    }
}
