// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.behavior

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.IBinder
import android.os.PowerManager
import android.os.RemoteException
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.DummyActivity
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat
import io.github.muntashirakon.AppManager.misc.ScreenLockChecker
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.types.ForegroundService
import io.github.muntashirakon.AppManager.utils.CpuUtils
import io.github.muntashirakon.AppManager.utils.FreezeUtils
import io.github.muntashirakon.AppManager.utils.NotificationUtils
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.AppManager.utils.UIUtils.getBitmapFromDrawable
import io.github.muntashirakon.AppManager.utils.UIUtils.getDimmedBitmap
import java.util.concurrent.Future

class FreezeUnfreezeService : Service() {
    private val mPackagesToShortcut = mutableMapOf<String, FreezeUnfreezeShortcutInfo>()
    private val mPackagesToNotificationTag = mutableMapOf<String, String>()
    private var mScreenLockChecker: ScreenLockChecker? = null
    private val mScreenLockedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                mCheckLockResult?.cancel(true)
                mCheckLockResult = ThreadUtils.postOnBackgroundThread {
                    if (mScreenLockChecker == null) {
                        mScreenLockChecker = ScreenLockChecker(this@FreezeUnfreezeService) { freezeAllPackages() }
                    }
                    mScreenLockChecker!!.checkLock()
                }
            } catch (ignore: Throwable) {}
        }
    }

    private var mIsWorking = false
    private var mCheckLockResult: Future<*>? = null
    private var mWakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        mWakeLock = CpuUtils.getPartialWakeLock("freeze_unfreeze")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP_ACTION) {
            stopSelf()
            return START_NOT_STICKY
        }
        onHandleIntent(intent)
        if (mIsWorking) return START_NOT_STICKY
        mIsWorking = true
        NotificationUtils.getNewNotificationManager(this, CHANNEL_ID, "Freeze/unfreeze Monitor", NotificationManagerCompat.IMPORTANCE_LOW)
        val stopIntent = Intent(this, FreezeUnfreezeService::class.java).apply { action = STOP_ACTION }
        val pendingIntent = PendingIntentCompat.getService(this, 0, stopIntent, PendingIntent.FLAG_ONE_SHOT, false)
        val stopServiceAction = NotificationCompat.Action.Builder(null, getString(R.string.action_stop_service), pendingIntent)
            .setAuthenticationRequired(true).build()
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setLocalOnly(true).setOngoing(true).setContentTitle(null)
            .setContentText(getString(R.string.waiting_for_the_phone_to_be_locked))
            .setSmallIcon(R.drawable.ic_default_notification).setSubText(getText(R.string.freeze_unfreeze))
            .setPriority(NotificationCompat.PRIORITY_LOW).addAction(stopServiceAction)
        ForegroundService.start(this, NotificationUtils.nextNotificationId(null), builder.build(),
            ForegroundService.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ForegroundService.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT) }
        ContextCompat.registerReceiver(this, mScreenLockedReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        startActivity(Intent(this, DummyActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }

    override fun onDestroy() {
        unregisterReceiver(mScreenLockedReceiver)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        mCheckLockResult?.cancel(true)
        mWakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun onHandleIntent(intent: Intent?) {
        val shortcutInfo = intent?.let { FreezeUnfreeze.getShortcutInfo(it) } ?: return
        mPackagesToShortcut[shortcutInfo.packageName] = shortcutInfo
        mPackagesToNotificationTag[shortcutInfo.packageName] = shortcutInfo.hashCode().toString()
    }

    @WorkerThread
    private fun freezeAllPackages() {
        for (packageName in mPackagesToShortcut.keys) {
            val shortcutInfo = mPackagesToShortcut[packageName]
            val notificationTag = mPackagesToNotificationTag[packageName]
            if (shortcutInfo != null) {
                try {
                    val ai = PackageManagerCompat.getApplicationInfo(shortcutInfo.packageName,
                        PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES or PackageManagerCompat.MATCH_DISABLED_COMPONENTS or PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, shortcutInfo.userId)
                    val pm = application.packageManager
                    val icon = getBitmapFromDrawable(ai.loadIcon(pm))
                    shortcutInfo.name = ai.loadLabel(pm)
                    val freezeType = FreezeUtils.loadFreezeMethod(shortcutInfo.packageName) ?: Prefs.Blocking.getDefaultFreezingMethod()
                    FreezeUtils.freeze(shortcutInfo.packageName, shortcutInfo.userId, freezeType)
                    shortcutInfo.icon = getDimmedBitmap(icon)
                    updateShortcuts(shortcutInfo)
                } catch (ignore: Exception) {}
            }
            notificationTag?.let { NotificationUtils.getFreezeUnfreezeNotificationManager(this).cancel(it, 1) }
        }
        stopSelf()
    }

    private fun updateShortcuts(shortcutInfo: FreezeUnfreezeShortcutInfo) {
        val intent = FreezeUnfreeze.getShortcutIntent(this, shortcutInfo).apply { action = Intent.ACTION_CREATE_SHORTCUT }
        val sInfo = ShortcutInfoCompat.Builder(this, shortcutInfo.id!!)
            .setShortLabel(shortcutInfo.name!!).setLongLabel(shortcutInfo.name!!)
            .setIcon(IconCompat.createWithBitmap(shortcutInfo.icon!!)).setIntent(intent).build()
        ShortcutManagerCompat.updateShortcuts(this, listOf(sInfo))
    }

    companion object {
        val TAG: String = FreezeUnfreezeService::class.java.simpleName
        val CHANNEL_ID = "${BuildConfig.APPLICATION_ID}.channel.FREEZE_UNFREEZE_MONITOR"
        private const val STOP_ACTION = "${BuildConfig.APPLICATION_ID}.action.STOP_FREEZE_UNFREEZE_MONITOR"
    }
}
