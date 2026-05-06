// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.app.RemoteInput
import androidx.core.app.ServiceCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.servermanager.ServerConfig
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.types.ForegroundService
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.adb.android.AdbMdns

// This works as follows:
// 1. Start searching for a pairing port
// 2. A port is found, ask to enter a pairing code
// 3. Start pairing
// 4. Exit with result, or ask to retry
@RequiresApi(Build.VERSION_CODES.R)
class AdbPairingService : Service() {
    private lateinit var mNotificationBuilder: NotificationCompat.Builder
    private var mStartedSearching = false
    private var mAdbMdnsPairing: AdbMdns? = null
    private val mAdbPairingPort = MutableLiveData<Int>()
    private val mAdbPairingPortObserver = Observer<Int> { port ->
        Log.i(TAG, "Found port %d", port)
        inputPairingCode(port)
    }

    @MainThread
    private fun launchDeveloperOptions() {
        val intent = Intent("android.settings.APPLICATION_DEVELOPMENT_SETTINGS")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    override fun onCreate() {
        super.onCreate()
        val notificationManager = NotificationManagerCompat.from(this)
        val notificationChannel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName("ADB Pairing")
            .setSound(null, null)
            .setShowBadge(false)
            .build()
        notificationManager.createNotificationChannel(notificationChannel)
        mNotificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setDefaults(Notification.DEFAULT_ALL)
            .setLocalOnly(!Prefs.Misc.sendNotificationsToConnectedDevices())
            .setContentTitle(getString(R.string.wireless_debugging))
            .setSubText(getText(R.string.wireless_debugging))
            .setSmallIcon(R.drawable.ic_default_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == null) {
            // Invalid intent
            return START_NOT_STICKY
        }
        when (intent.action) {
            ACTION_START_SEARCHING -> {
                startSearching()
                return START_REDELIVER_INTENT
            }
            ACTION_START_PAIRING -> {
                val port = intent.getIntExtra(EXTRA_PORT, -1)
                val remoteInputs = RemoteInput.getResultsFromIntent(intent)
                if (port != -1 && remoteInputs != null) {
                    val code = remoteInputs.getCharSequence(INPUT_CODE, "").toString().trim()
                    startPairing(port, code)
                } else {
                    // Wrong inputs, continue searching
                    startSearching()
                }
                return START_REDELIVER_INTENT
            }
            ACTION_STOP_SEARCHING -> {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_LAUNCH_DEVELOPER_OPTIONS -> {
                launchDeveloperOptions()
                return START_NOT_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mStartedSearching) {
            // Still looking for a port, hence the pairing wasn't successful
            // Fail intentionally to avoid looping forever
            Log.i(TAG, "Stop searching for an active port...")
            ThreadUtils.postOnBackgroundThread {
                try {
                    AdbConnectionManager.getInstance().pairLiveData(ServerConfig.getAdbHost(this), -1, "")
                } catch (ignore: Exception) {
                }
            }
            stopSearching()
        }
    }

    @MainThread
    private fun startSearching() {
        if (mStartedSearching) {
            return
        }
        mStartedSearching = true
        if (mAdbMdnsPairing == null) {
            mAdbMdnsPairing = AdbMdns(application, AdbMdns.SERVICE_TYPE_TLS_PAIRING) { _, port ->
                if (port != -1) {
                    mAdbPairingPort.postValue(port)
                }
            }
        }
        mAdbPairingPort.observeForever(mAdbPairingPortObserver)
        val stopPendingIntent = getStopIntent()
        val stopAction = NotificationCompat.Action.Builder(null, getString(R.string.adb_pairing_stop_searching), stopPendingIntent).build()
        mNotificationBuilder.setContentText(getText(R.string.adb_pairing_searching_for_port))
            .clearActions()
            .addAction(stopAction)
        ServiceCompat.startForeground(
            this, 1, mNotificationBuilder.build(),
            ForegroundService.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ForegroundService.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        mAdbMdnsPairing!!.start()
    }

    @MainThread
    private fun inputPairingCode(port: Int) {
        val inputIntent = Intent(this, javaClass)
            .setAction(ACTION_START_PAIRING)
            .putExtra(EXTRA_PORT, port)
        val inputPendingIntent = PendingIntentCompat.getForegroundService(this, 2, inputIntent, PendingIntent.FLAG_UPDATE_CURRENT, true)
        val pairingCodeInput = RemoteInput.Builder(INPUT_CODE)
            .setLabel(getString(R.string.adb_pairing_pairing_code))
            .build()
        val inputAction = NotificationCompat.Action.Builder(null, getString(R.string.adb_pairing_input_pairing_code), inputPendingIntent)
            .addRemoteInput(pairingCodeInput)
            .build()

        val launchDevOptionsIntent = Intent(this, javaClass).setAction(ACTION_LAUNCH_DEVELOPER_OPTIONS)
        val launchDevOptionsPendingIntent = PendingIntentCompat.getForegroundService(this, 4, launchDevOptionsIntent, 0, false)
        val launchDevOptionsAction = NotificationCompat.Action.Builder(null, getString(R.string.adb_pairing_launch_developer_options), launchDevOptionsPendingIntent).build()

        mNotificationBuilder.setContentText(getString(R.string.adb_pairing_found_pairing_service_with_port, port))
            .clearActions()
            .addAction(inputAction)
            .addAction(launchDevOptionsAction)
        if (SelfPermissions.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            NotificationManagerCompat.from(this).notify(1, mNotificationBuilder.build())
        }
    }

    @MainThread
    private fun startPairing(port: Int, code: String) {
        mNotificationBuilder.setContentText(getString(R.string.adb_pairing_pairing_in_progress))
            .clearActions()
        ServiceCompat.startForeground(
            this, 1, mNotificationBuilder.build(),
            ForegroundService.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ForegroundService.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        ThreadUtils.postOnBackgroundThread {
            val isSuccess: Boolean
            try {
                AdbConnectionManager.getInstance().pairLiveData(ServerConfig.getAdbHost(this), port, code)
                isSuccess = true
            } catch (e: Exception) {
                Log.w(TAG, "Pairing failed.", e)
                isSuccess = false
            }
            ThreadUtils.postOnMainThread { stopSearching() }
            if (isSuccess) {
                mNotificationBuilder.setContentText(getString(R.string.paired_successfully)).clearActions()
                stopSelf()
            } else {
                val deleteIntent = getStopIntent()
                val retryIntent = Intent(this, javaClass).setAction(ACTION_START_SEARCHING)
                val retryPendingIntent = PendingIntentCompat.getForegroundService(this, 3, retryIntent, 0, false)
                val retryAction = NotificationCompat.Action.Builder(null, getString(R.string.adb_pairing_retry_pairing), retryPendingIntent).build()
                mNotificationBuilder.setContentText(getString(R.string.failed))
                    .clearActions()
                    .setDeleteIntent(deleteIntent)
                    .addAction(retryAction)
            }
            if (SelfPermissions.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                NotificationManagerCompat.from(this).notify(1, mNotificationBuilder.build())
            }
        }
    }

    @MainThread
    private fun stopSearching() {
        if (!mStartedSearching) {
            return
        }
        mStartedSearching = false
        mAdbMdnsPairing?.stop()
        mAdbPairingPort.removeObserver(mAdbPairingPortObserver)
    }

    private fun getStopIntent(): PendingIntent {
        val stopIntent = Intent(this, javaClass).setAction(ACTION_STOP_SEARCHING)
        return PendingIntentCompat.getForegroundService(this, 1, stopIntent, 0, false)
    }

    companion object {
        @JvmField
        val TAG: String = AdbPairingService::class.java.simpleName
        const val CHANNEL_ID = "${BuildConfig.APPLICATION_ID}.channel.ADB_PAIRING"\nconst val ACTION_START_SEARCHING = "${BuildConfig.APPLICATION_ID}.action.START_SEARCHING"\nconst val ACTION_STOP_SEARCHING = "${BuildConfig.APPLICATION_ID}.action.STOP_SEARCHING"\nconst val ACTION_START_PAIRING = "${BuildConfig.APPLICATION_ID}.action.ENTER_CODE"\nconst val ACTION_LAUNCH_DEVELOPER_OPTIONS = "${BuildConfig.APPLICATION_ID}.action.LAUNCH_DEVELOPER_OPTIONS"\nconst val EXTRA_PORT = "port"\nconst val INPUT_CODE = "code"
    }
}
