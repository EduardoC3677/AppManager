// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.AnyThread
import androidx.annotation.GuardedBy
import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.misc.NoOps
import io.github.muntashirakon.AppManager.server.common.Caller
import io.github.muntashirakon.AppManager.server.common.CallerResult
import io.github.muntashirakon.AppManager.server.common.Shell
import io.github.muntashirakon.AppManager.server.common.ShellCaller
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.adb.AdbPairingRequiredException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException

// Copyright 2016 Zheng Li
class LocalServer @WorkerThread @NoOps(used = true) @Throws(IOException::class, AdbPairingRequiredException::class) private constructor() {
    private val mContext: Context = ContextUtils.getDeContext(ContextUtils.getContext())
    private val mLocalServerManager: LocalServerManager = LocalServerManager.getInstance(mContext)
    private val mConnectLock = Any()
    private var mConnectStarted = false

    init {
        // Initialise necessary files and permissions
        ServerConfig.init(mContext)
        // Start server if not already
        checkConnect()
    }

    @GuardedBy("connectLock")
    @WorkerThread
    @NoOps(used = true)
    @Throws(IOException::class, AdbPairingRequiredException::class)
    fun checkConnect() {
        synchronized(mConnectLock) {
            if (mConnectStarted) {
                try {
                    (mConnectLock as Object).wait()
                } catch (e: InterruptedException) {
                    return
                }
            }
            mConnectStarted = true
            try {
                mLocalServerManager.start()
            } catch (e: IOException) {
                mConnectStarted = false
                (mConnectLock as Object).notify()
                throw e
            } catch (e: AdbPairingRequiredException) {
                mConnectStarted = false
                (mConnectLock as Object).notify()
                throw e
            }
            mConnectStarted = false
            (mConnectLock as Object).notify()
        }
    }

    @Throws(IOException::class)
    fun runCommand(command: String): Shell.Result {
        val shellCaller = ShellCaller(command)
        val callerResult = exec(shellCaller)
        val th = callerResult.throwable
        if (th != null) {
            throw IOException(th)
        }
        return callerResult.replyObj as Shell.Result
    }

    @WorkerThread
    @Throws(IOException::class)
    fun exec(caller: Caller): CallerResult {
        try {
            checkConnect()
            return mLocalServerManager.execNew(caller)
        } catch (e: SocketTimeoutException) {
            e.printStackTrace()
            closeBgServer()
            // Retry
            try {
                checkConnect()
                return mLocalServerManager.execNew(caller)
            } catch (e2: AdbPairingRequiredException) {
                throw IOException(e2)
            }
        } catch (e: AdbPairingRequiredException) {
            throw IOException(e)
        }
    }

    @AnyThread
    fun isRunning(): Boolean {
        return mLocalServerManager.isRunning
    }

    fun destroy() {
        mLocalServerManager.stop()
    }

    @WorkerThread
    @Throws(IOException::class)
    fun closeBgServer() {
        mLocalServerManager.closeBgServer()
        mLocalServerManager.stop()
    }

    companion object {
        private val sLock = Any()

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var sLocalServer: LocalServer? = null

        @JvmStatic
        @WorkerThread
        @NoOps(used = true)
        @Throws(IOException::class, AdbPairingRequiredException::class)
        fun getInstance(): LocalServer {
            // Non-null check must be done outside the synchronised block to prevent deadlock on ADB over TCP mode.
            if (sLocalServer != null) return sLocalServer!!
            synchronized(sLock) {
                if (sLocalServer != null) return sLocalServer!!
                try {
                    Log.d("IPC", "Init: Local server")
                    sLocalServer = LocalServer()
                } finally {
                    (sLock as Object).notifyAll()
                }
            }
            return sLocalServer!!
        }

        @JvmStatic
        fun die() {
            synchronized(sLock) {
                try {
                    sLocalServer?.destroy()
                } finally {
                    sLocalServer = null
                }
            }
        }

        @JvmStatic
        @WorkerThread
        @NoOps
        fun alive(context: Context): Boolean {
            return try {
                ServerSocket().use { socket ->
                    socket.bind(
                        InetSocketAddress(
                            ServerConfig.getLocalServerHost(context),
                            ServerConfig.getLocalServerPort()
                        ), 1
                    )
                    false
                }
            } catch (e: IOException) {
                true
            }
        }

        @JvmStatic
        @WorkerThread
        @NoOps(used = true)
        @Throws(IOException::class, AdbPairingRequiredException::class)
        fun restart() {
            val server = sLocalServer
            if (server != null) {
                val manager = server.mLocalServerManager
                manager.closeBgServer()
                manager.stop()
                manager.start()
            } else {
                getInstance()
            }
        }
    }
}
