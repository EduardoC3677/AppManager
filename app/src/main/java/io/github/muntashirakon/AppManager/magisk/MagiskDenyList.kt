// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.magisk

import android.content.pm.PackageInfo
import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.runner.Runner
import io.github.muntashirakon.AppManager.settings.Ops

@WorkerThread
object MagiskDenyList {
    @JvmStatic
    fun available(): Boolean {
        return Ops.isWorkingUidRoot() && Runner.runCommand(arrayOf("magisk", "--denylist", "ls")).isSuccessful
    }

    @JvmStatic
    fun enableIfNotAlready(forceEnable: Boolean): Boolean {
        if (!Runner.runCommand(arrayOf("magisk", "--denylist", "status")).isSuccessful) {
            return if (forceEnable) Runner.runCommand(arrayOf("magisk", "--denylist", "enable")).isSuccessful else false
        }
        return true
    }

    @JvmStatic
    fun apply(magiskProcess: MagiskProcess, forceEnable: Boolean): Boolean {
        val packageName = if (magiskProcess.isIsolatedProcess && !magiskProcess.isAppZygote) MagiskUtils.ISOLATED_MAGIC else magiskProcess.packageName
        return if (magiskProcess.isEnabled) add(packageName, magiskProcess.name, forceEnable) else remove(packageName, magiskProcess.name)
    }

    private fun add(packageName: String, processName: String, forceEnable: Boolean): Boolean {
        if (!enableIfNotAlready(forceEnable)) return false
        return Runner.runCommand(arrayOf("magisk", "--denylist", "add", packageName, processName)).isSuccessful
    }

    private fun remove(packageName: String, processName: String): Boolean {
        return Runner.runCommand(arrayOf("magisk", "--denylist", "rm", packageName, processName)).isSuccessful
    }

    @JvmStatic
    fun getProcesses(packageInfo: PackageInfo): List<MagiskProcess> {
        return MagiskUtils.getProcesses(packageInfo, getProcesses(packageInfo.packageName))
    }

    @JvmStatic
    fun getProcesses(packageName: String): Collection<String> {
        val result = Runner.runCommand(arrayOf("magisk", "--denylist", "ls"))
        return MagiskUtils.parseProcesses(packageName, result)
    }
}
