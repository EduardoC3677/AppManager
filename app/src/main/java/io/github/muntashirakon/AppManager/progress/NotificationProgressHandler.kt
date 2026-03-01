// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.progress

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.types.ForegroundService
import io.github.muntashirakon.AppManager.utils.NotificationUtils

class NotificationProgressHandler(
    private val mContext: Context,
    private val mProgressNotificationManagerInfo: NotificationManagerInfo,
    private val mCompletionNotificationManagerInfo: NotificationManagerInfo,
    private val mQueueNotificationManagerInfo: NotificationManagerInfo?
) : QueuedProgressHandler() {

    private val mProgressNotificationManager: NotificationManagerCompat = getNotificationManager(mContext, mProgressNotificationManagerInfo)!!
    private val mCompletionNotificationManager: NotificationManagerCompat = getNotificationManager(mContext, mCompletionNotificationManagerInfo)!!
    private val mQueueNotificationManager: NotificationManagerCompat? = getNotificationManager(mContext, mQueueNotificationManagerInfo)
    private val mProgressNotificationId: Int = NotificationUtils.nextNotificationId(null)

    private var mLastProgressNotification: NotificationInfo? = null
    private var mLastMax = MAX_INDETERMINATE
    private var mLastProgress = 0f
    private var mAttachedToService = false

    override fun onQueue(message: Any?) {
        if (mQueueNotificationManager == null || mQueueNotificationManagerInfo == null || message == null) return
        val info = message as NotificationInfo
        val notification = info.getBuilder(mContext, mQueueNotificationManagerInfo).setLocalOnly(true).build()
        notify(mContext, mQueueNotificationManager, "queue", NotificationUtils.nextNotificationId("queue"), notification)
    }

    override fun onAttach(service: Service?, message: Any) {
        mLastProgressNotification = message as NotificationInfo
        if (service != null) {
            mAttachedToService = true
            val notification = mLastProgressNotification!!.getBuilder(mContext, mProgressNotificationManagerInfo)
                .setLocalOnly(true).setOngoing(true).setOnlyAlertOnce(true).setProgress(0, 0, false).build()
            ForegroundService.start(service, mProgressNotificationId, notification,
                ForegroundService.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ForegroundService.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        }
    }

    override fun onProgressStart(max: Int, current: Float, message: Any?) {
        onProgressUpdate(max, current, message)
    }

    override fun onProgressUpdate(max: Int, current: Float, message: Any?) {
        if (message != null) {
            mLastProgressNotification = message as NotificationInfo
        }
        mLastMax = max
        mLastProgress = current
        val progressText = progressTextInterface.getProgressText(this)
        val indeterminate = max == -1
        val newMax = Math.max(max, 0)
        val newCurrent = if (max < 0) 0 else current.toInt()
        val builder = mLastProgressNotification!!.getBuilder(mContext, mProgressNotificationManagerInfo)
            .setLocalOnly(true).setOngoing(true).setOnlyAlertOnce(true).setProgress(newMax, newCurrent, indeterminate)
        if (progressText != null) {
            if (max > 0) {
                builder.setContentText(progressText)
            } else if (max == MAX_FINISHED) {
                builder.setContentText(mContext.getString(R.string.done))
            } else {
                builder.setContentText(mContext.getString(R.string.operation_running))
            }
        }
        notify(mContext, mProgressNotificationManager, null, mProgressNotificationId, builder.build())
    }

    override fun onResult(message: Any?) {
        if (!mAttachedToService) {
            mProgressNotificationManager.cancel(null, mProgressNotificationId)
        } else {
            onProgressUpdate(MAX_FINISHED, 0f, null)
        }
        if (message == null) return
        val info = message as NotificationInfo
        val notification = info.getBuilder(mContext, mCompletionNotificationManagerInfo).build()
        notify(mContext, mCompletionNotificationManager, "alert", NotificationUtils.nextNotificationId("alert"), notification)
    }

    override fun onDetach(service: Service?) {
        if (service != null) {
            mAttachedToService = false
            mProgressNotificationManager.cancel(null, mProgressNotificationId)
        }
    }

    override fun newSubProgressHandler(): ProgressHandler {
        return NotificationProgressHandler(mContext, mProgressNotificationManagerInfo, mCompletionNotificationManagerInfo, null)
    }

    override fun getLastMax(): Int = mLastMax
    override fun getLastProgress(): Float = mLastProgress
    override fun getLastMessage(): Any? = mLastProgressNotification

    override fun postUpdate(max: Int, current: Float, message: Any?) {
        mLastMax = max
        mLastProgress = current
        super.postUpdate(max, current, message)
    }

    class NotificationManagerInfo(val channelId: String, val channelName: CharSequence, @NotificationUtils.NotificationImportance val importance: Int)

    class NotificationInfo {
        @DrawableRes private var icon = R.drawable.ic_default_notification
        private var level: Int = 0
        private var time: Long = 0L
        private var operationName: CharSequence? = null
        private var title: CharSequence? = null
        private var body: CharSequence? = null
        private var statusBarText: CharSequence? = null
        private var style: NotificationCompat.Style? = null
        private var autoCancel: Boolean = false
        private var defaultAction: PendingIntent? = null
        private var groupId: String? = null
        private val actions = mutableListOf<NotificationCompat.Action>()

        constructor()
        constructor(other: NotificationInfo) {
            icon = other.icon; level = other.level; time = other.time; operationName = other.operationName; title = other.title; body = other.body; statusBarText = other.statusBarText; style = other.style; autoCancel = other.autoCancel; defaultAction = other.defaultAction; groupId = other.groupId; actions.addAll(other.actions)
        }

        fun setIcon(icon: Int): NotificationInfo { this.icon = icon; return this }
        fun setLevel(level: Int): NotificationInfo { this.level = level; return this }
        fun setTitle(title: CharSequence?): NotificationInfo { this.title = title; return this }
        fun setBody(body: CharSequence?): NotificationInfo { this.body = body; return this }
        fun setStatusBarText(text: CharSequence?): NotificationInfo { this.statusBarText = text; return this }
        fun setDefaultAction(action: PendingIntent?): NotificationInfo { this.defaultAction = action; return this }
        fun setOperationName(name: CharSequence?): NotificationInfo { this.operationName = name; return this }
        fun setStyle(style: NotificationCompat.Style?): NotificationInfo { this.style = style; return this }
        fun setAutoCancel(auto: Boolean): NotificationInfo { this.autoCancel = auto; return this }
        fun setTime(time: Long): NotificationInfo { this.time = time; return this }
        fun setGroupId(id: String?) { this.groupId = id }
        fun addAction(icon: Int, title: CharSequence?, intent: PendingIntent?): NotificationInfo { actions.add(NotificationCompat.Action(icon, title, intent)); return this }

        fun getBuilder(context: Context, info: NotificationManagerInfo): NotificationCompat.Builder {
            val contentIntent = if (autoCancel && defaultAction == null) PendingIntentCompat.getActivity(context, 0, Intent(), 0, false) else defaultAction
            val builder = NotificationCompat.Builder(context, info.channelId)
                .setLocalOnly(!Prefs.Misc.sendNotificationsToConnectedDevices())
                .setPriority(NotificationUtils.importanceToPriority(info.importance))
                .setDefaults(Notification.DEFAULT_ALL)
                .setSmallIcon(icon, level)
                .setSubText(operationName)
                .setTicker(statusBarText)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(contentIntent)
                .setAutoCancel(autoCancel)
                .setGroup(groupId)
                .setStyle(style)
            if (groupId == null) {
                builder.setGroupSummary(false).setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            }
            actions.forEach { builder.addAction(it) }
            if (time > 0L) {
                builder.setWhen(time).setShowWhen(true)
            }
            return builder
        }
    }

    companion object {
        private fun notify(context: Context, manager: NotificationManagerCompat, tag: String?, id: Int, notification: Notification) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                manager.notify(tag, id, notification)
            }
        }

        private fun getNotificationManager(context: Context, info: NotificationManagerInfo?): NotificationManagerCompat? {
            if (info == null) return null
            val channel = NotificationChannelCompat.Builder(info.channelId, info.importance).setName(info.channelName).build()
            val manager = NotificationManagerCompat.from(context)
            manager.createNotificationChannel(channel)
            return manager
        }
    }
}
