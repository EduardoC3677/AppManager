// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat

import android.os.Build
import android.os.IDeviceIdleController
import android.os.RemoteException
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import io.github.muntashirakon.AppManager.ipc.ProxyBinder
import io.github.muntashirakon.AppManager.utils.ExUtils

object DeviceIdleManagerCompat {
    @JvmStatic
    @RequiresPermission(ManifestCompat.permission.DEVICE_POWER)
    fun disableBatteryOptimization(packageName: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                deviceIdleController.addPowerSaveWhitelistApp(packageName)
                return true // returns true when the package isn't installed
            } catch (e: RemoteException) {
                ExUtils.rethrowFromSystemServer(e)
            }
        }
        return false
    }

    @JvmStatic
    @RequiresPermission(ManifestCompat.permission.DEVICE_POWER)
    fun enableBatteryOptimization(packageName: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                deviceIdleController.removePowerSaveWhitelistApp(packageName)
                return true
            } catch (e: RemoteException) {
                ExUtils.rethrowFromSystemServer(e)
            } catch (e: UnsupportedOperationException) {
                // System whitelisted app
                e.printStackTrace()
            }
        }
        return false
    }

    @JvmStatic
    fun isBatteryOptimizedApp(packageName: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val controller = deviceIdleController
                return !controller.isPowerSaveWhitelistExceptIdleApp(packageName) &&
                        !controller.isPowerSaveWhitelistApp(packageName)
            } catch (e: RemoteException) {
                ExUtils.rethrowFromSystemServer(e)
            }
        }
        // Not supported
        return true
    }

    private val deviceIdleController: IDeviceIdleController
        @RequiresApi(Build.VERSION_CODES.M)
        get() = IDeviceIdleController.Stub.asInterface(ProxyBinder.getService("deviceidle"))
}
