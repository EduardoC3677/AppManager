// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.types

import android.annotation.SuppressLint
import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import androidx.annotation.CallSuper
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.app.ServiceCompat
import androidx.core.os.BundleCompat
import io.github.muntashirakon.AppManager.utils.ThreadUtils

abstract class ForegroundService protected constructor(private val mName: String) : Service() {
    companion object {
        @JvmField
        val FOREGROUND_SERVICE_TYPE_DATA_SYNC = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

        @JvmField
        val FOREGROUND_SERVICE_TYPE_SPECIAL_USE = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

        @JvmStatic
        @SuppressLint("ForegroundServiceType")
        fun start(
            service: Service, id: Int, notification: Notification,
            foregroundServiceType: Int
        ) {
            ServiceCompat.startForeground(service, id, notification, foregroundServiceType)
        }
    }

    class Binder internal constructor(private val mService: ForegroundService) : android.os.Binder() {
        @Suppress("UNCHECKED_CAST")
        fun <T : ForegroundService> getService(): T {
            return mService as T
        }
    }

    private val mBinder: IBinder = Binder(this)
    private lateinit var mServiceLooper: Looper
    private lateinit var mServiceHandler: ServiceHandler
    @Volatile
    private var mIsWorking = false

    // Handler that receives messages from the thread
    private inner class ServiceHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            val intent = BundleCompat.getParcelable(msg.data, "intent", Intent::class.java)
            ThreadUtils.postOnMainThread { onStartIntent(intent) }
            onHandleIntent(intent)
            // It works because of Handler uses FIFO
            stopSelfResult(msg.arg1)
        }
    }

    fun isWorking(): Boolean {
        return mIsWorking
    }

    override fun onCreate() {
        val thread = HandlerThread(mName, Process.THREAD_PRIORITY_BACKGROUND)
        thread.start()
        mServiceLooper = thread.looper
        mServiceHandler = ServiceHandler(mServiceLooper)
    }

    @CallSuper
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // TODO: 15/6/21 Make it final, extended classes shouldn't need to use it
        if (mIsWorking) {
            // Service already running
            onQueued(intent)
        }
        mIsWorking = true
        val msg = mServiceHandler.obtainMessage()
        msg.arg1 = startId
        val args = Bundle()
        args.putParcelable("intent", intent)
        msg.data = args
        mServiceHandler.sendMessage(msg)
        return START_NOT_STICKY
    }

    @CallSuper
    override fun onDestroy() {
        super.onDestroy()
        mServiceLooper.quitSafely()
    }

    /**
     * The work to be performed.
     *
     * @param intent The intent sent by [android.content.Context.startService]
     */
    @WorkerThread
    protected abstract fun onHandleIntent(intent: Intent?)

    /**
     * The service is running and a new intent has been queued.
     *
     * @param intent The new intent that has been queued
     */
    @UiThread
    protected open fun onQueued(intent: Intent?) {
    }

    /**
     * An intent is being processed. Called right before [onHandleIntent].
     *
     * @param intent The intent to be processed.
     */
    @UiThread
    protected open fun onStartIntent(intent: Intent?) {
    }

    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }
}
