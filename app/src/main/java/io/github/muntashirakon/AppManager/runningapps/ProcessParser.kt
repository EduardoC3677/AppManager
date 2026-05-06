// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runningapps

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.compat.ActivityManagerCompat
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat
import io.github.muntashirakon.AppManager.ipc.LocalServices
import io.github.muntashirakon.AppManager.ipc.ps.ProcessEntry
import io.github.muntashirakon.AppManager.ipc.ps.Ps
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.AppManager.utils.PackageUtils
import io.github.muntashirakon.AppManager.utils.Utils
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths

@WorkerThread
class ProcessParser {
    private val mContext: Context?
    private val mPm: PackageManager?
    private var mInstalledPackages: HashMap<String, PackageInfo>? = null
    private var mInstalledUidList: HashMap<Int, PackageInfo>? = null
    private val mRunningAppProcesses = HashMap<Int, ActivityManager.RunningAppProcessInfo>(50)

    init {
        if (Utils.isRoboUnitTest()) {
            mInstalledPackages = HashMap()
            mInstalledUidList = HashMap()
            mPm = null
            mContext = null
        } else {
            mContext = ContextUtils.getContext()
            mPm = mContext.packageManager
            loadInstalledPackages()
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun parse(): List<ProcessItem> {
        val processItems = mutableListOf<ProcessItem>()
        try {
            val processEntries: List<ProcessEntry> = if (Paths.get("/proc/1").canRead() && LocalServices.alive()) {
                LocalServices.getAmService().runningProcesses.list as List<ProcessEntry>
            } else {
                Ps().apply { loadProcesses() }.processes
            }
            for (processEntry in processEntries) {
                if (processEntry.seLinuxPolicy?.contains(":kernel:") == true) continue
                try {
                    processItems.addAll(parseProcess(processEntry))
                } catch (ignore: Exception) {}
            }
        } catch (th: Throwable) {
            Log.e("ProcessParser", th)
        }
        return processItems
    }

    @VisibleForTesting
    fun parse(procDir: Path): HashMap<Int, ProcessItem> {
        val processItems = HashMap<Int, ProcessItem>()
        val ps = Ps(procDir).apply { loadProcesses() }
        for (processEntry in ps.processes) {
            try {
                val processItem = parseProcess(processEntry)[0]
                processItems[processItem.pid] = processItem
            } catch (ignore: Exception) {}
        }
        return processItems
    }

    private fun parseProcess(processEntry: ProcessEntry): List<ProcessItem> {
        val packageName = getSupposedPackageName(processEntry.name)
        val processItems = mutableListOf<ProcessItem>()
        val runningInfo = mRunningAppProcesses[processEntry.pid]
        if (runningInfo != null) {
            val pkgList = runningInfo.pkgList
            if (!pkgList.isNullOrEmpty()) {
                for (pkgName in pkgList) {
                    val packageInfo = mInstalledPackages!![pkgName]!!
                    val processItem = AppProcessItem(processEntry, packageInfo)
                    processItem.name = mPm!!.getApplicationLabel(packageInfo.applicationInfo).toString() +
                            getProcessNameFilteringPackageName(processEntry.name, packageInfo.packageName)
                    processItems.add(processItem)
                }
            } else {
                val processItem = ProcessItem(processEntry)
                processItem.name = getProcessName(processEntry.name)
                processItems.add(processItem)
            }
        } else if (mInstalledPackages!!.containsKey(packageName)) {
            val packageInfo = mInstalledPackages!![packageName]!!
            val processItem = AppProcessItem(processEntry, packageInfo)
            processItem.name = mPm!!.getApplicationLabel(packageInfo.applicationInfo).toString() +
                    getProcessNameFilteringPackageName(processEntry.name, packageInfo.packageName)
            processItems.add(processItem)
        } else if (mInstalledUidList!!.containsKey(processEntry.users.fsUid)) {
            val packageInfo = mInstalledUidList!![processEntry.users.fsUid]!!
            val processItem = AppProcessItem(processEntry, packageInfo)
            processItem.name = mPm!!.getApplicationLabel(packageInfo.applicationInfo).toString() +
                    getProcessNameFilteringPackageName(processEntry.name, packageInfo.packageName)
            processItems.add(processItem)
        } else {
            val processItem = ProcessItem(processEntry)
            processItem.name = getProcessName(processEntry.name)
            processItems.add(processItem)
        }
        for (processItem in processItems) {
            if (mContext == null) {
                processItem.state = processEntry.processState
                processItem.state_extra = processEntry.processStatePlus
            } else {
                processItem.state = mContext.getString(Utils.getProcessStateName(processEntry.processState))
                processItem.state_extra = mContext.getString(Utils.getProcessStateExtraName(processEntry.processStatePlus))
            }
        }
        return processItems
    }

    private fun loadInstalledPackages() {
        val packageInfoList = PackageUtils.getAllPackages(PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES)
        mInstalledPackages = HashMap(packageInfoList.size)
        for (info in packageInfoList) mInstalledPackages!![info.packageName] = info
        mInstalledUidList = HashMap(packageInfoList.size)
        val duplicateUids = mutableListOf<Int>()
        for (info in packageInfoList) {
            val uid = info.applicationInfo.uid
            if (mInstalledUidList!!.containsKey(uid)) duplicateUids.add(uid)
            else mInstalledUidList!![uid] = info
        }
        for (uid in duplicateUids) mInstalledUidList!!.remove(uid)
        ActivityManagerCompat.getRunningAppProcesses().forEach { mRunningAppProcesses[it.pid] = it }
    }

    companion object {
        @JvmStatic
        fun getSupposedPackageName(processName: String): String {
            return if (!processName.contains(":")) processName
            else processName.substring(0, processName.indexOf(':'))
        }

        @JvmStatic
        fun getProcessName(processName: String): String {
            val name = processName.split("\u0000").toTypedArray()[0]
            if (!name.startsWith("/")) return name
            return name.substring(name.lastIndexOf('/') + 1)
        }

        @JvmStatic
        private fun getProcessNameFilteringPackageName(processName: String, packageName: String): String {
            if (processName == packageName) return ""\nval name = getProcessName(processName)
            val colonIdx = name.indexOf(':')
            return if (colonIdx < 0) ":$name" else name.substring(colonIdx)
        }
    }
}
