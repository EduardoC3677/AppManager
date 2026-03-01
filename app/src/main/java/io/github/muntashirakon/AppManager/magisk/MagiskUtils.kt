// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.magisk

import android.content.pm.ApplicationInfo
import android.content.pm.ComponentInfo
import android.content.pm.PackageInfo
import android.content.pm.ServiceInfo
import android.os.Build
import io.github.muntashirakon.AppManager.runner.Runner
import io.github.muntashirakon.AppManager.utils.AlphanumComparator
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import java.util.*

object MagiskUtils {
    const val NVBASE = "/data/adb"
    private var sBootMode = false

    const val ISOLATED_MAGIC = "isolated"

    private val SCAN_PATHS = arrayOf(
        "/system/app", "/system/priv-app", "/system/preload",
        "/system/product/app", "/system/product/priv-app", "/system/product/overlay",
        "/system/vendor/app", "/system/vendor/overlay",
        "/system/system_ext/app", "/system/system_ext/priv-app",
        "/system_ext/app", "/system_ext/priv-app",
        "/vendor/app", "/vendor/overlay",
        "/product/app", "/product/priv-app", "/product/overlay"
    )

    @JvmStatic
    fun getModDir(): Path {
        return Paths.get(NVBASE + "/modules" + (if (sBootMode) "_update" else ""))
    }

    @JvmStatic
    fun setBootMode(bootMode: Boolean) {
        sBootMode = bootMode
    }

    private var sSystemlessPaths: List<String>? = null

    private fun getSystemlessPaths(): List<String> {
        if (sSystemlessPaths == null) {
            val paths = mutableListOf<String>()
            val modDir = getModDir()
            if (!modDir.canRead()) return emptyList()
            val modulePaths = modDir.listFiles { it.isDirectory }
            for (file in modulePaths) {
                for (sysPath in SCAN_PATHS) {
                    val subPaths = Paths.build(file, sysPath)?.listFiles { it.isDirectory } ?: continue
                    for (path in subPaths) {
                        if (hasApkFile(path)) {
                            paths.add("$sysPath/${path.name}")
                        }
                    }
                }
            }
            sSystemlessPaths = paths
        }
        return sSystemlessPaths!!
    }

    @JvmStatic
    fun isSystemlessPath(path: String): Boolean {
        return getSystemlessPaths().contains(path)
    }

    private fun hasApkFile(file: Path): Boolean {
        if (file.isDirectory) {
            val files = file.listFiles { _, name -> name.endsWith(".apk") }
            return files.isNotEmpty()
        }
        return false
    }

    @JvmStatic
    fun getProcesses(packageInfo: PackageInfo, enabledProcesses: Collection<String>): List<MagiskProcess> {
        val packageName = packageInfo.packageName
        val applicationInfo = packageInfo.applicationInfo!!
        val processNameProcessMap = mutableMapOf<String, MagiskProcess>()
        run {
            val mp = MagiskProcess(packageName)
            mp.isEnabled = enabledProcesses.contains(packageName)
            processNameProcessMap[packageName] = mp
        }
        packageInfo.services?.forEach { info ->
            if ((info.flags and ServiceInfo.FLAG_ISOLATED_PROCESS) != 0) {
                if ((info.flags and ServiceInfo.FLAG_USE_APP_ZYGOTE) != 0) {
                    val procName = (applicationInfo.processName ?: applicationInfo.packageName) + "_zygote"
                    if (processNameProcessMap[procName] == null) {
                        val mp = MagiskProcess(packageName, procName)
                        mp.isEnabled = enabledProcesses.contains(procName)
                        mp.isIsolatedProcess = true
                        mp.isAppZygote = true
                        processNameProcessMap[procName] = mp
                    }
                } else {
                    val procName = getProcessName(applicationInfo, info) + (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ":$packageName" else "")
                    if (processNameProcessMap[procName] == null) {
                        val mp = MagiskProcess(packageName, procName)
                        mp.isEnabled = enabledProcesses.contains(procName)
                        mp.isIsolatedProcess = true
                        processNameProcessMap[procName] = mp
                    }
                }
            } else {
                val procName = getProcessName(applicationInfo, info)
                if (processNameProcessMap[procName] == null) {
                    val mp = MagiskProcess(packageName, procName)
                    mp.isEnabled = enabledProcesses.contains(procName)
                    processNameProcessMap[procName] = mp
                }
            }
        }
        val comps = mutableListOf<Array<out ComponentInfo>?>(packageInfo.activities, packageInfo.providers, packageInfo.receivers)
        comps.forEach { array ->
            array?.forEach { info ->
                val procName = getProcessName(applicationInfo, info)
                if (processNameProcessMap[procName] == null) {
                    val mp = MagiskProcess(packageName, procName)
                    mp.isEnabled = enabledProcesses.contains(procName)
                    processNameProcessMap[procName] = mp
                }
            }
        }
        val magiskProcesses = ArrayList(processNameProcessMap.values)
        magiskProcesses.sortWith { o1, o2 -> AlphanumComparator.compareStringIgnoreCase(o1.name, o2.name) }
        return magiskProcesses
    }

    @JvmStatic
    fun parseProcesses(packageName: String, result: Runner.Result): Collection<String> {
        if (!result.isSuccessful) return emptyList()
        val processes = mutableSetOf<String>()
        for (line in result.outputAsList) {
            val splits = line.split("|").toTypedArray()
            if (splits.size == 1) {
                if (splits[0] == packageName) processes.add(packageName)
            } else if (splits.size >= 2) {
                if (splits[0] == packageName || splits[0] == ISOLATED_MAGIC) processes.add(splits[1])
            }
        }
        return processes
    }

    private fun getProcessName(applicationInfo: ApplicationInfo, info: ComponentInfo): String {
        return info.processName ?: (applicationInfo.processName ?: applicationInfo.packageName)
    }
}
