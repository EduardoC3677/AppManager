// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops

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
import io.github.muntashirakon.AppManager.history.ops.OpHistoryManager
import io.github.muntashirakon.AppManager.intercept.IntentCompat
import io.github.muntashirakon.AppManager.main.MainActivity
import io.github.muntashirakon.AppManager.progress.NotificationProgressHandler
import io.github.muntashirakon.AppManager.progress.QueuedProgressHandler
import io.github.muntashirakon.AppManager.types.ForegroundService
import io.github.muntashirakon.AppManager.utils.CpuUtils
import io.github.muntashirakon.AppManager.utils.NotificationUtils
import java.util.*

class BatchOpsService : ForegroundService("BatchOpsService") {
    companion object {
        const val EXTRA_QUEUE_ITEM = "queue_item"\nconst val EXTRA_OP = "EXTRA_OP"\nconst val EXTRA_OP_PKG = "EXTRA_OP_PKG"\nconst val EXTRA_FAILED_PKG = "EXTRA_FAILED_PKG_ARR"\nconst val EXTRA_FAILURE_MESSAGE = "EXTRA_FAILURE_MESSAGE"\nconst val EXTRA_REQUIRES_RESTART = "requires_restart"\nconst val ACTION_BATCH_OPS_COMPLETED = BuildConfig.APPLICATION_ID + ".action.BATCH_OPS_COMPLETED"\nconst val ACTION_BATCH_OPS_STARTED = BuildConfig.APPLICATION_ID + ".action.BATCH_OPS_STARTED"\nconst val CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.BATCH_OPS"\n@JvmStatic
        fun getServiceIntent(context: Context, queueItem: BatchQueueItem): Intent {
            val intent = Intent(context, BatchOpsService::class.java)
            IntentCompat.putWrappedParcelableExtra(intent, EXTRA_QUEUE_ITEM, queueItem)
            return intent
        }
    }

    private var mProgressHandler: QueuedProgressHandler? = null
    private var mNotificationInfo: NotificationProgressHandler.NotificationInfo? = null
    private var mWakeLock: PowerManager.WakeLock? = null

    override fun onHandleIntent(intent: Intent?) {
        if (intent == null) return
        val queueItem: BatchQueueItem = IntentCompat.getWrappedParcelableExtra(intent, EXTRA_QUEUE_ITEM, BatchQueueItem::class.java) ?: return
        val info = BatchOpsManager.BatchOpsInfo.fromQueue(queueItem)

        // Wake lock
        mWakeLock = CpuUtils.getWakeLock(this, "BatchOpsService")
        mWakeLock?.acquire(30 * 60 * 1000L /*30 minutes*/)

        // Notification channel
        NotificationUtils.createNotificationChannel(this, CHANNEL_ID, getString(R.string.batch_ops), NotificationManagerCompat.IMPORTANCE_LOW)

        val managerInfo = NotificationProgressHandler.NotificationManagerInfo(this, CHANNEL_ID, 100)
        mNotificationInfo = NotificationProgressHandler.NotificationInfo(managerInfo)
            .setOperationName(getString(R.string.batch_ops))
            .setTitle(getString(R.string.processing))
            .setSmallIcon(R.drawable.ic_batch_ops)
        
        mProgressHandler = QueuedProgressHandler(this, managerInfo)
        mProgressHandler?.onProgressStart(info.size, 0, mNotificationInfo)

        val logger = BatchOpsLogger()
        val manager = BatchOpsManager(logger, mProgressHandler)
        val result = manager.run(info)

        mProgressHandler?.onResult(null)

        // Broadcast completion
        val completeIntent = Intent(ACTION_BATCH_OPS_COMPLETED)
        completeIntent.putExtra(EXTRA_OP, info.op)
        completeIntent.putStringArrayListExtra(EXTRA_OP_PKG, ArrayList(info.packages))
        completeIntent.putStringArrayListExtra(EXTRA_FAILED_PKG, result.failedPackages)
        completeIntent.putExtra(EXTRA_REQUIRES_RESTART, result.requiresRestart)
        completeIntent.setPackage(BuildConfig.APPLICATION_ID)
        sendBroadcast(completeIntent)

        // History
        OpHistoryManager.addToHistory(OpHistoryManager.HISTORY_TYPE_BATCH_OPS, info.op, info.packages, result.failedPackages)

        mWakeLock?.release()
    }

    override fun onDestroy() {
        mProgressHandler?.onResult(null)
        super.onDestroy()
    }
}
