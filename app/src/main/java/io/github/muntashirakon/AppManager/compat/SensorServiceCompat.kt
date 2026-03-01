// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat

import android.annotation.UserIdInt
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import io.github.muntashirakon.AppManager.ipc.ProxyBinder
import io.github.muntashirakon.AppManager.utils.BinderShellExecutor
import java.io.IOException

@RequiresApi(Build.VERSION_CODES.P)
object SensorServiceCompat {
    @JvmStatic
    @RequiresPermission(ManifestCompat.permission.MANAGE_SENSORS)
    fun isSensorEnabled(packageName: String, @UserIdInt userId: Int): Boolean {
        val command: Array<String> = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            arrayOf("get-uid-state", packageName, "--user", userId.toString())
        } else {
            arrayOf("get-uid-state", packageName)
        }
        try {
            val result = BinderShellExecutor.execute(sensorService, command)
            return "active" == result.stdout.trim { it <= ' ' }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return true
    }

    @JvmStatic
    @RequiresPermission(ManifestCompat.permission.MANAGE_SENSORS)
    @Throws(IOException::class)
    fun enableSensor(packageName: String, @UserIdInt userId: Int, enable: Boolean) {
        val state = if (enable) "active" else "idle"
        val command: Array<String> = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            arrayOf("set-uid-state", packageName, state, "--user", userId.toString())
        } else {
            arrayOf("set-uid-state", packageName, state)
        }
        val result = BinderShellExecutor.execute(sensorService, command)
        if (result.resultCode != 0) {
            throw IOException("Could not " + (if (enable) "enable" else "disable") + " sensor.")
        }
    }

    @JvmStatic
    @RequiresPermission(ManifestCompat.permission.MANAGE_SENSORS)
    @Throws(IOException::class)
    fun resetSensor(packageName: String, @UserIdInt userId: Int) {
        val command: Array<String> = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            arrayOf("reset-uid-state", packageName, "--user", userId.toString())
        } else {
            arrayOf("reset-uid-state", packageName)
        }
        val result = BinderShellExecutor.execute(sensorService, command)
        if (result.resultCode != 0) {
            throw IOException("Could not reset sensor.")
        }
    }

    private val sensorService: IBinder
        get() = ProxyBinder.getService("sensorservice")
}
