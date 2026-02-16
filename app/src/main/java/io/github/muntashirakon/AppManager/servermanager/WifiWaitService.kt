// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.adb.AdbUtils
import io.github.muntashirakon.AppManager.settings.Ops
import io.github.muntashirakon.AppManager.types.ForegroundService
import io.github.muntashirakon.AppManager.utils.NotificationUtils
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.AppManager.utils.Utils

@RequiresApi(Build.VERSION_CODES.R)
class WifiWaitService : Service() {
    private val mNetworkCallback: NetworkCallback = object : NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Wi-Fi network available")
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "Network lost")
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            // Double-check Wi-Fi availability when capabilities change
            if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !mAutoconnectCompleted
            ) {
                connectAdbWifi()
                unregisterNetworkCallback()
            }
        }
    }
    private var mConnectivityManager: ConnectivityManager? = null
    private var mAutoconnectCompleted = false
    private var mUnregisterDone = true

    override fun onCreate() {
        super.onCreate()
        NotificationUtils.getNewNotificationManager(
            this, CHANNEL_ID, "Wi-Fi Wait Service",
            NotificationManagerCompat.IMPORTANCE_LOW
        )
        mConnectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.waiting_for_wifi))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        ForegroundService.start(
            this, NotificationUtils.nextNotificationId(null),
            notification, ForegroundService.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    or ForegroundService.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )

        if (LocalServer.alive(applicationContext)) {
            // Already connected
            mAutoconnectCompleted = true
        }

        if (!mAutoconnectCompleted) {
            registerNetworkCallback()
        } else {
            stopSelf()
        }

        return START_NOT_STICKY // Don't restart if killed
    }

    private fun registerNetworkCallback() {
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        try {
            mConnectivityManager!!.registerNetworkCallback(networkRequest, mNetworkCallback)
            mUnregisterDone = false
            Log.d(TAG, "Network callback registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
            stopSelf()
        }
    }

    private fun connectAdbWifi() {
        if (mAutoconnectCompleted) {
            return // Prevent multiple executions
        }
        mAutoconnectCompleted = true

        ThreadUtils.postOnBackgroundThread {
            try {
                doConnectAdbWifi()
            } finally {
                stopSelf()
            }
        }
    }

    @WorkerThread
    private fun doConnectAdbWifi() {
        val context = applicationContext
        if (!Utils.isWifiActive(context)) {
            Log.w(TAG, "Autoconnect failed: Wi-Fi not enabled.")
            return
        }

        if (!AdbUtils.enableWirelessDebugging(context)) {
            Log.w(TAG, "Autoconnect failed: Could not enable wireless debugging.")
            return
        }

        val status = Ops.autoConnectWirelessDebugging(context)
        when (status) {
            Ops.STATUS_ADB_PAIRING_REQUIRED -> {
                Log.w(TAG, "Autoconnect failed: pairing required")
            }
            Ops.STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED -> {
                Log.w(TAG, "Autoconnect failed: could not find a valid port")
            }
            Ops.STATUS_FAILURE_ADB_NEED_MORE_PERMS -> {
                Log.w(TAG, "Autoconnect failed: not enough permissions available")
            }
            Ops.STATUS_SUCCESS -> {
                Log.i(TAG, "Autoconnect success!")
            }
            else -> {
                Log.w(TAG, "Autoconnect failed")
            }
        }
    }

    private fun unregisterNetworkCallback() {
        if (mUnregisterDone) {
            return
        }
        mUnregisterDone = true
        try {
            mConnectivityManager!!.unregisterNetworkCallback(mNetworkCallback)
            Log.d(TAG, "Network callback unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering callback", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        unregisterNetworkCallback()
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }

    companion object {
        private val TAG = WifiWaitService::class.java.simpleName
        const val CHANNEL_ID: String = BuildConfig.APPLICATION_ID + ".channel.WIFI_WAIT_SERVICE"
    }
}
