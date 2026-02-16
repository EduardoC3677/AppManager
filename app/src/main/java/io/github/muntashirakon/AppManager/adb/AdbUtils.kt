// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.os.SystemProperties
import android.provider.Settings
import android.provider.SettingsHidden
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import androidx.core.util.Pair
import io.github.muntashirakon.AppManager.runner.Runner
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.servermanager.ServerConfig
import io.github.muntashirakon.adb.android.AdbMdns
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

object AdbUtils {
    @JvmStatic
    @WorkerThread
    @Throws(InterruptedException::class, IOException::class)
    fun getLatestAdbDaemon(
        context: Context,
        timeout: Long,
        unit: TimeUnit
    ): Pair<String, Int> {
        if (!isAdbdRunning()) {
            throw IOException("ADB daemon not running.")
        }
        val atomicPort = AtomicInteger(-1)
        val atomicHostAddress = AtomicReference<String?>(null)
        val resolveHostAndPort = CountDownLatch(1)

        val adbMdnsTcp = AdbMdns(context, AdbMdns.SERVICE_TYPE_ADB) { hostAddress, port ->
            if (hostAddress != null) {
                atomicHostAddress.set(hostAddress.hostAddress)
                atomicPort.set(port)
            }
            resolveHostAndPort.countDown()
        }
        adbMdnsTcp.start()

        var adbMdnsTls: AdbMdns? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            adbMdnsTls = AdbMdns(context, AdbMdns.SERVICE_TYPE_TLS_CONNECT) { hostAddress, port ->
                if (hostAddress != null) {
                    atomicHostAddress.set(hostAddress.hostAddress)
                    atomicPort.set(port)
                }
                resolveHostAndPort.countDown()
            }
            adbMdnsTls.start()
        }

        try {
            if (!resolveHostAndPort.await(timeout, unit)) {
                throw InterruptedException("Timed out while trying to find a valid host address and port")
            }
        } finally {
            adbMdnsTcp.stop()
            adbMdnsTls?.stop()
        }

        val host = atomicHostAddress.get()
        val port = atomicPort.get()
        if (host == null || port == -1) {
            throw IOException("Could not find any valid host address or port")
        }
        return Pair(host, port)
    }

    @JvmStatic
    @RequiresApi(Build.VERSION_CODES.R)
    fun enableWirelessDebugging(context: Context): Boolean {
        val resolver = context.contentResolver
        val wirelessDebuggingEnabled =
            Settings.Global.getInt(resolver, SettingsHidden.Global.ADB_WIFI_ENABLED, 0) != 0
        if (wirelessDebuggingEnabled && isAdbdRunning()) {
            return true
        }
        if (!SelfPermissions.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)) {
            // No permission
            return false
        }
        try {
            if (Settings.Global.getInt(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 0) {
                val contentValues = ContentValues(2)
                contentValues.put("name", Settings.Global.DEVELOPMENT_SETTINGS_ENABLED)
                contentValues.put("value", 1)
                resolver.insert(Uri.parse("content://settings/global"), contentValues)
            }
            if (!wirelessDebuggingEnabled) {
                val contentValues = ContentValues(2)
                contentValues.put("name", SettingsHidden.Global.ADB_WIFI_ENABLED)
                contentValues.put("value", 1)
                resolver.insert(Uri.parse("content://settings/global"), contentValues)
            }
            // Try at most 3 times to figure out if something has altered
            for (i in 0..4) {
                if (isAdbdRunning()) {
                    return true
                }
                SystemClock.sleep(500)
            }
        } catch (th: Throwable) {
            th.printStackTrace()
        }
        return false
    }

    @JvmStatic
    fun isAdbdRunning(): Boolean {
        // Default is set to “running” to avoid other issues
        return "running" == SystemProperties.get("init.svc.adbd", "running")
    }

    @JvmStatic
    fun getAdbPortOrDefault(): Int {
        return SystemProperties.getInt("service.adb.tcp.port", ServerConfig.DEFAULT_ADB_PORT)
    }

    @JvmStatic
    fun startAdb(port: Int): Boolean {
        return Runner.runCommand(arrayOf("setprop", "service.adb.tcp.port", port.toString())).isSuccessful
                && Runner.runCommand(arrayOf("setprop", "ctl.restart", "adbd")).isSuccessful
    }

    @JvmStatic
    fun stopAdb(): Boolean {
        return Runner.runCommand(arrayOf("setprop", "service.adb.tcp.port", "-1")).isSuccessful
                && Runner.runCommand(arrayOf("setprop", "ctl.restart", "adbd")).isSuccessful
    }
}
