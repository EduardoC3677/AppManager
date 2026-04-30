// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.RemoteException
import io.github.muntashirakon.AppManager.logs.Log
import rikka.shizuku.Shizuku
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object ShizukuUtils {
    // Cache Shizuku status to avoid repeated slow binder calls
    private var sIsInstalled: Boolean? = null
    private var sLastInstalledCheck: Long? = null
    private const val CACHE_VALIDITY_MS: Long = 5000 // 5 seconds

    // WeakReference cache for ShizukuShell instances, keyed by Context identity.
    // Avoids creating a new service connection per archive operation while not preventing GC.
    private val sShellCache = HashMap<Int, WeakReference<ShizukuShell>>()

    @JvmStatic
    fun isShizukuInstalled(): Boolean {
        val now = System.currentTimeMillis()
        if (sIsInstalled != null && sLastInstalledCheck != null &&
            now - sLastInstalledCheck!! < CACHE_VALIDITY_MS
        ) {
            return sIsInstalled!!
        }

        return try {
            Shizuku.pingBinder()
            sIsInstalled = !Shizuku.isPreV11()
            sLastInstalledCheck = now
            sIsInstalled!!
        } catch (e: Exception) {
            Log.w("ShizukuUtils", "isShizukuInstalled check failed: %s", e.message, e)
            sIsInstalled = false
            sLastInstalledCheck = now
            false
        }
    }

    @JvmStatic
    fun isShizukuAvailable(): Boolean {
        return try {
            if (Shizuku.isPreV11()) {
                return false
            }
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.w("ShizukuUtils", "isShizukuAvailable check failed: %s", e.message, e)
            false
        }
    }

    // Clear cache when permission changes
    @JvmStatic
    fun clearCache() {
        sIsInstalled = null
        sLastInstalledCheck = null
    }

    @JvmStatic
    fun needsPermission(): Boolean {
        return isShizukuInstalled() && !isShizukuAvailable()
    }

    class CommandResult @JvmOverloads constructor(
        @JvmField val exitCode: Int,
        @JvmField val stdout: String,
        @JvmField val stderr: String
    ) {
        @JvmName("getExitCode")
        fun getExitCode(): Int = exitCode

        @JvmName("getStdout")
        fun getStdout(): String = stdout

        @JvmName("getStderr")
        fun getStderr(): String = stderr
    }

    @JvmStatic
    fun runCommand(context: Context, command: String): CommandResult? {
        if (!isShizukuAvailable()) {
            Log.w("ShizukuUtils", "Shizuku not available for command: $command")
            return null
        }

        val result = arrayOfNulls<CommandResult>(1)
        val latch = CountDownLatch(1)

        val args = Shizuku.UserServiceArgs(
            ComponentName(context.packageName, RemoteCommandService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("command")
            .tag("RemoteCommandService")

        val connection: ServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                val remoteCommandService = IRemoteCommandService.Stub.asInterface(service)
                try {
                    val bundle = remoteCommandService.runCommand(command)
                    val exitCode = bundle.getInt("exitCode", -1)
                    val stdout = bundle.getString("stdout", "") ?: ""
                    val stderr = bundle.getString("stderr", "") ?: ""
                    result[0] = CommandResult(exitCode, stdout, stderr)
                } catch (e: RemoteException) {
                    Log.e("ShizukuUtils", "RemoteException during command execution: $command", e)
                    result[0] = CommandResult(-1, "", "RemoteException: " + e.message)
                } finally {
                    Shizuku.unbindUserService(args, this, true)
                    latch.countDown()
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                latch.countDown()
            }
        }

        Shizuku.bindUserService(args, connection)

        try {
            latch.await(35, TimeUnit.SECONDS) // Increased timeout to 35s (30s command + 5s overhead)
        } catch (e: InterruptedException) {
            Log.e("ShizukuUtils", "InterruptedException while waiting for command: $command", e)
        }

        return result[0]
    }

    @JvmStatic
    fun requestPermission(onGranted: Runnable, onDenied: Runnable) {
        if (isShizukuAvailable()) {
            onGranted.run()
            return
        }

        try {
            if (Shizuku.isPreV11()) {
                onDenied.run()
                return
            }

            val requestCode = 100
            Shizuku.addRequestPermissionResultListener { reqCode, grantResult ->
                if (reqCode == requestCode) {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        onGranted.run()
                    } else {
                        onDenied.run()
                    }
                }
            }

            Shizuku.requestPermission(requestCode)
        } catch (e: Exception) {
            Log.e("ShizukuUtils", "Exception during requestPermission", e)
            onDenied.run()
        }
    }

    class ShizukuShell internal constructor(context: Context) : AutoCloseable {
        private var mService: IRemoteCommandService? = null
        private val mConnection: ServiceConnection
        private val mArgs: Shizuku.UserServiceArgs
        private val mConnectionLatch = CountDownLatch(1)
        private var mIsClosed = false

        init {
            mArgs = Shizuku.UserServiceArgs(
                ComponentName(context.packageName, RemoteCommandService::class.java.name)
            )
                .daemon(false)
                .processNameSuffix("command")
                .tag("RemoteCommandService")

            mConnection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    mService = IRemoteCommandService.Stub.asInterface(service)
                    mConnectionLatch.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    mService = null
                }
            }

            Shizuku.bindUserService(mArgs, mConnection)
            try {
                if (!mConnectionLatch.await(10, TimeUnit.SECONDS)) {
                    Log.e("ShizukuUtils", "Timeout waiting for Shizuku service connection")
                }
            } catch (e: InterruptedException) {
                Log.e("ShizukuUtils", "Interrupted waiting for Shizuku service connection", e)
            }
        }

        fun isClosed(): Boolean = mIsClosed

        fun runCommand(command: String): CommandResult {
            if (mIsClosed) {
                return CommandResult(-1, "", "Shell is closed")
            }
            if (mService == null) {
                return CommandResult(-1, "", "Service not connected")
            }
            return try {
                val bundle = mService!!.runCommand(command)
                val exitCode = bundle.getInt("exitCode", -1)
                val stdout = bundle.getString("stdout", "") ?: ""
                val stderr = bundle.getString("stderr", "") ?: ""
                CommandResult(exitCode, stdout, stderr)
            } catch (e: RemoteException) {
                Log.e("ShizukuUtils", "RemoteException during shell command: $command", e)
                CommandResult(-1, "", "RemoteException: " + e.message)
            }
        }

        override fun close() {
            if (!mIsClosed) {
                try {
                    Shizuku.unbindUserService(mArgs, mConnection, true)
                } catch (e: Exception) {
                    // Ignore
                }
                mIsClosed = true
            }
        }
    }

    /**
     * Returns a cached [ShizukuShell] for the given context if Shizuku is available,
     * or null otherwise. Uses a [WeakReference] cache to avoid creating a new service
     * connection per archive operation while not preventing GC of stale instances.
     */
    @JvmStatic
    fun newShell(context: Context): ShizukuShell? {
        if (!isShizukuAvailable()) return null
        val key = System.identityHashCode(context)
        val cached = sShellCache[key]?.get()
        if (cached != null && !cached.isClosed()) {
            return cached
        }
        val shell = ShizukuShell(context)
        sShellCache[key] = WeakReference(shell)
        return shell
    }
}
