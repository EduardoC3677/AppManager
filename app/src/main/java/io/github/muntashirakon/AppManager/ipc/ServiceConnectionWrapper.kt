// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.misc.NoOps
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class ServiceConnectionWrapper {
    companion object {
        @JvmField
        val TAG: String = ServiceConnectionWrapper::class.java.simpleName
    }

    private var mIBinder: IBinder? = null
    private var mServiceBoundWatcher: CountDownLatch? = null

    private inner class ServiceConnectionImpl : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            Log.d(TAG, "service onServiceConnected: %s", name)
            mIBinder = service
            onResponseReceived()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.d(TAG, "service onServiceDisconnected: %s", name)
            mIBinder = null
            onResponseReceived()
        }

        override fun onBindingDied(name: ComponentName) {
            Log.d(TAG, "service onBindingDied: %s", name)
            mIBinder = null
            onResponseReceived()
        }

        override fun onNullBinding(name: ComponentName) {
            Log.d(TAG, "service onNullBinding: %s", name)
            mIBinder = null
            onResponseReceived()
        }

        private fun onResponseReceived() {
            if (mServiceBoundWatcher != null) {
                // Should never be null
                mServiceBoundWatcher!!.countDown()
            } else {
                throw RuntimeException("Service watcher should never be null!")
            }
        }
    }

    private val mComponentName: ComponentName
    private val mServiceConnection: ServiceConnectionImpl

    constructor(pkgName: String, className: String) : this(ComponentName(pkgName, className))

    constructor(cn: ComponentName) {
        mComponentName = cn
        mServiceConnection = ServiceConnectionImpl()
    }

    @get:Throws(RemoteException::class)
    val service: IBinder
        get() {
            if (!isBinderActive) {
                throw RemoteException("Binder not running.")
            }
            return mIBinder!!
        }

    @Throws(RemoteException::class)
    @NoOps(used = true)
    fun bindService(): IBinder {
        synchronized(mServiceConnection) {
            if (!isBinderActive) {
                startDaemon()
            }
            return service
        }
    }

    @MainThread
    fun unbindService() {
        synchronized(mServiceConnection) {
            RootService.unbind(mServiceConnection)
        }
    }

    @WorkerThread
    private fun startDaemon() {
        synchronized(mServiceConnection) {
            if (isBinderActive) {
                Log.d(TAG, "Binder is already active?")
                return
            }
            mServiceBoundWatcher = CountDownLatch(1)
            Log.d(TAG, "Launching service...")
            val intent = Intent()
            intent.component = mComponentName
            ThreadUtils.postOnMainThread {
                if (mIBinder != null) {
                    RootService.stop(intent)
                }
                RootService.bind(intent, mServiceConnection)
            }
            // Wait for service to be bound
            try {
                mServiceBoundWatcher!!.await(45, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                Log.e(TAG, "Service watcher interrupted.")
            }
        }
    }

    @WorkerThread
    fun stopDaemon() {
        val intent = Intent()
        intent.component = mComponentName
        ThreadUtils.postOnMainThread { RootService.stop(intent) }
        mIBinder = null
    }

    val isBinderActive: Boolean
        get() = mIBinder != null && mIBinder!!.pingBinder()
}
