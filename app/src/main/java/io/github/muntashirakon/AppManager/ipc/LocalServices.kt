// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc

import android.os.Process
import android.os.RemoteException
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.IAMService
import io.github.muntashirakon.AppManager.misc.NoOps
import io.github.muntashirakon.AppManager.settings.Ops
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.io.FileSystemManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object LocalServices {
    private val sBindLock = Any()

    private val sFileSystemServiceConnectionWrapper =
        ServiceConnectionWrapper(BuildConfig.APPLICATION_ID, FileSystemService::class.java.name)

    @JvmStatic
    @WorkerThread
    @Throws(RemoteException::class)
    fun bindServicesIfNotAlready() {
        if (!alive()) {
            bindServices()
        }
    }

    @JvmStatic
    @WorkerThread
    @Throws(RemoteException::class)
    fun bindServices() {
        synchronized(sBindLock) {
            unbindServicesIfRunning()
            bindAmService()
            bindFileSystemManager()
            // Verify binding
            if (!amService.asBinder().pingBinder()) {
                throw RemoteException("IAmService not running.")
            }
            fileSystemManager
            // Update UID
            Ops.setWorkingUid(amService.uid)
        }
    }

    @JvmStatic
    fun alive(): Boolean {
        synchronized(sAMServiceConnectionWrapper) {
            return sAMServiceConnectionWrapper.isBinderActive
        }
    }

    @JvmStatic
    @WorkerThread
    @NoOps(used = true)
    @Throws(RemoteException::class)
    private fun bindFileSystemManager() {
        synchronized(sFileSystemServiceConnectionWrapper) {
            try {
                sFileSystemServiceConnectionWrapper.bindService()
            } finally {
                (sFileSystemServiceConnectionWrapper as Object).notifyAll()
            }
        }
    }

    @get:JvmStatic
    @get:AnyThread
    @get:NoOps
    val fileSystemManager: FileSystemManager
        @Throws(RemoteException::class)
        get() {
            synchronized(sFileSystemServiceConnectionWrapper) {
                return try {
                    FileSystemManager.getRemote(sFileSystemServiceConnectionWrapper.service)
                } finally {
                    (sFileSystemServiceConnectionWrapper as Object).notifyAll()
                }
            }
        }

    private val sAMServiceConnectionWrapper =
        ServiceConnectionWrapper(BuildConfig.APPLICATION_ID, AMService::class.java.name)

    @JvmStatic
    @WorkerThread
    @NoOps(used = true)
    @Throws(RemoteException::class)
    private fun bindAmService() {
        synchronized(sAMServiceConnectionWrapper) {
            try {
                sAMServiceConnectionWrapper.bindService()
            } finally {
                (sAMServiceConnectionWrapper as Object).notifyAll()
            }
        }
    }

    @get:JvmStatic
    @get:AnyThread
    @get:NoOps
    val amService: IAMService
        @Throws(RemoteException::class)
        get() {
            synchronized(sAMServiceConnectionWrapper) {
                return try {
                    IAMService.Stub.asInterface(sAMServiceConnectionWrapper.service)
                } finally {
                    (sAMServiceConnectionWrapper as Object).notifyAll()
                }
            }
        }

    @JvmStatic
    @WorkerThread
    @NoOps
    fun stopServices() {
        synchronized(sAMServiceConnectionWrapper) {
            sAMServiceConnectionWrapper.stopDaemon()
        }
        synchronized(sFileSystemServiceConnectionWrapper) {
            sFileSystemServiceConnectionWrapper.stopDaemon()
        }
        Ops.setWorkingUid(Process.myUid())
    }

    @JvmStatic
    @MainThread
    fun unbindServices() {
        synchronized(sAMServiceConnectionWrapper) {
            sAMServiceConnectionWrapper.unbindService()
        }
        synchronized(sFileSystemServiceConnectionWrapper) {
            sFileSystemServiceConnectionWrapper.unbindService()
        }
        Ops.setWorkingUid(Process.myUid())
    }

    @JvmStatic
    @WorkerThread
    private fun unbindServicesIfRunning() {
        // Basically unregister the services so that we can open another connection
        val unbindWatcher = CountDownLatch(1)
        ThreadUtils.postOnMainThread {
            try {
                unbindServices()
            } finally {
                unbindWatcher.countDown()
            }
        }
        try {
            unbindWatcher.await(30, TimeUnit.SECONDS)
        } catch (ignore: InterruptedException) {
        }
    }
}
