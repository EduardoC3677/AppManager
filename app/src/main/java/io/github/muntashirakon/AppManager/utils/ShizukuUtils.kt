// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.RemoteException
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object ShizukuUtils {
    @JvmStatic
    fun isShizukuInstalled(): Boolean {
        return try {
            Shizuku.pingBinder()
            !Shizuku.isPreV11()
        } catch (e: Exception) {
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
            false
        }
    }

    @JvmStatic
    fun needsPermission(): Boolean {
        return isShizukuInstalled() && !isShizukuAvailable()
    }

    data class CommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    )

    @JvmStatic
    fun runCommand(context: Context, command: String): CommandResult? {
        if (!isShizukuAvailable()) {
            return null
        }

        val result = arrayOfNulls<CommandResult>(1)
        val latch = CountDownLatch(1)

        val args = UserServiceArgs(
            ComponentName(context.packageName, RemoteCommandService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("command")
            .tag("RemoteCommandService")

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                val remoteCommandService = IRemoteCommandService.Stub.asInterface(service)
                try {
                    val bundle = remoteCommandService.runCommand(command)
                    val exitCode = bundle.getInt("exitCode", -1)
                    val stdout = bundle.getString("stdout", "")
                    val stderr = bundle.getString("stderr", "")
                    result[0] = CommandResult(exitCode, stdout, stderr)
                } catch (e: RemoteException) {
                    e.printStackTrace()
                    result[0] = CommandResult(-1, "", "RemoteException: ${e.message}")
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
            e.printStackTrace()
        }

        return result[0]
    }

    @JvmStatic
    fun requestPermission(onGranted: () -> Unit, onDenied: () -> Unit) {
        if (isShizukuAvailable()) {
            onGranted()
            return
        }

        try {
            if (Shizuku.isPreV11()) {
                onDenied()
                return
            }

            val requestCode = 100
            Shizuku.addRequestPermissionResultListener { reqCode, grantResult ->
                if (reqCode == requestCode) {
                    if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        onGranted()
                    } else {
                        onDenied()
                    }
                }
            }

            Shizuku.requestPermission(requestCode)
        } catch (e: Exception) {
            e.printStackTrace()
            onDenied()
        }
    }
}
