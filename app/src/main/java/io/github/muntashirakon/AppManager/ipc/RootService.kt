// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc

import android.content.*
import android.os.IBinder
import android.os.Messenger
import androidx.annotation.CallSuper
import androidx.annotation.MainThread
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.internal.UiThreadHandler
import io.github.muntashirakon.AppManager.BuildConfig
import java.util.concurrent.Executor

/**
 * A remote root service using native Android Binder IPC.
 *
 * Copyright 2020 John "topjohnwu" Wu
 */
abstract class RootService : ContextWrapper(null) {
    companion object {
        /**
         * Launch the service in "Daemon Mode".
         */
        const val CATEGORY_DAEMON_MODE = BuildConfig.APPLICATION_ID + ".DAEMON_MODE"

        @JvmField
        val TAG: String = RootService::class.java.simpleName

        /**
         * Bind to a root service, launching a new root process if needed.
         */
        @JvmStatic
        @MainThread
        fun bind(
            intent: Intent,
            executor: Executor,
            conn: ServiceConnection
        ) {
            val task = bindOrTask(intent, executor, conn)
            if (task != null) {
                Shell.EXECUTOR.execute(asRunnable(task))
            }
        }

        /**
         * Bind to a root service, launching a new root process if needed.
         */
        @JvmStatic
        @MainThread
        fun bind(intent: Intent, conn: ServiceConnection) {
            bind(intent, UiThreadHandler.executor, conn)
        }

        /**
         * Bind to a root service, creating a task to launch a new root process if needed.
         */
        @JvmStatic
        @MainThread
        fun bindOrTask(
            intent: Intent,
            executor: Executor,
            conn: ServiceConnection
        ): Shell.Task? {
            return RootServiceManager.getInstance().createBindTask(intent, executor, conn)
        }

        /**
         * Unbind from a root service.
         */
        @JvmStatic
        @MainThread
        fun unbind(conn: ServiceConnection) {
            RootServiceManager.getInstance().unbind(conn)
        }

        /**
         * Force stop a root service, launching a new root process if needed.
         */
        @JvmStatic
        @MainThread
        fun stop(intent: Intent) {
            val task = stopOrTask(intent)
            if (task != null) {
                Shell.EXECUTOR.execute(asRunnable(task))
            }
        }

        /**
         * Force stop a root service, creating a task to launch a new root process if needed.
         */
        @JvmStatic
        @MainThread
        fun stopOrTask(intent: Intent): Shell.Task? {
            return RootServiceManager.getInstance().createStopTask(intent)
        }

        private fun asRunnable(task: Shell.Task): Runnable {
            return Runnable {
                try {
                    task.run(null, null, null)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    @CallSuper
    open fun onCreate() {
        RootServiceServer.getInstance(this).register(this)
    }

    abstract fun onBind(intent: Intent): IBinder?

    open fun onRebind(intent: Intent) {}

    open fun onUnbind(intent: Intent): Boolean {
        return false
    }

    @CallSuper
    open fun onDestroy() {}

    fun stopSelf() {
        RootServiceServer.getInstance(this).selfStop(componentName)
    }

    val componentName: ComponentName
        get() = ComponentName(this, javaClass)

    override fun getPackageName(): String {
        return BuildConfig.APPLICATION_ID
    }

    override fun getApplicationContext(): Context {
        return this
    }
}
