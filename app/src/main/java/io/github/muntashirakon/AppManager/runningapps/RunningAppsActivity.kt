// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runningapps

import android.app.AppOpsManager
import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.RemoteException
import android.os.UserHandleHidden
import android.text.TextUtils
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import androidx.core.util.Pair
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView
import io.github.muntashirakon.AppManager.runner.Runner
import io.github.muntashirakon.AppManager.scanner.vt.VirusTotal
import io.github.muntashirakon.AppManager.scanner.vt.VtFileReport
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.types.UserPackagePair
import io.github.muntashirakon.AppManager.utils.AppExecutor
import io.github.muntashirakon.AppManager.utils.DigestUtils
import io.github.muntashirakon.io.Paths
import io.github.muntashirakon.proc.ProcFs
import io.github.muntashirakon.proc.ProcMemoryInfo
import java.io.IOException
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RunningAppsViewModel(application: Application) : AndroidViewModel(application) {
    var sortOrder: Int = Prefs.RunningApps.getSortOrder()
        set(value) { field = value; Prefs.RunningApps.setSortOrder(value); mExecutor.submit { filterAndSort() } }
    var filter: Int = Prefs.RunningApps.getFilters()
        private set
    private val mExecutor = AppExecutor.getExecutor()
    private val mVt = VirusTotal.getInstance()

    val isVirusTotalAvailable: Boolean get() = mVt != null
    val vtFileUpload = MutableLiveData<Pair<ProcessItem, String?>>()
    val vtFileReport = MutableLiveData<Pair<ProcessItem, VtFileReport?>>()

    @AnyThread
    fun scanWithVt(processItem: ProcessItem) {
        val file = if (processItem is AppProcessItem) processItem.packageInfo.applicationInfo.publicSourceDir else processItem.commandlineArgs[0]
        if (mVt == null || file == null) { vtFileReport.postValue(Pair(processItem, null)); return }
        mExecutor.submit {
            val proxyFile = Paths.get(file)
            if (!proxyFile.canRead()) { vtFileReport.postValue(Pair(processItem, null)); return@submit }
            val sha256 = DigestUtils.getHexDigest(DigestUtils.SHA_256, proxyFile)
            try {
                mVt.fetchFileReportOrScan(proxyFile, sha256, object : VirusTotal.FullScanResponseInterface {
                    override fun uploadFile(): Boolean {
                        mUploadingEnabled = false
                        mUploadingEnabledWatcher = CountDownLatch(1)
                        vtFileUpload.postValue(Pair(processItem, null))
                        try { mUploadingEnabledWatcher!!.await(2, TimeUnit.MINUTES) } catch (ignore: InterruptedException) {}
                        return mUploadingEnabled
                    }
                    override fun onUploadInitiated() {}
                    override fun onUploadCompleted(permalink: String) { vtFileUpload.postValue(Pair(processItem, permalink)) }
                    override fun onReportReceived(report: VtFileReport) { vtFileReport.postValue(Pair(processItem, report)) }
                })
            } catch (e: IOException) { e.printStackTrace(); vtFileReport.postValue(Pair(processItem, null)) }
        }
    }

    val processLiveData = MutableLiveData<List<ProcessItem>>()
    val deviceMemoryInfo = MutableLiveData<ProcMemoryInfo>()
    private val mProcessItemLiveData = MutableLiveData<ProcessItem>()
    fun observeProcessDetails(): LiveData<ProcessItem> = mProcessItemLiveData
    @AnyThread fun requestDisplayProcessDetails(processItem: ProcessItem) { mProcessItemLiveData.postValue(processItem) }

    private val mProcessList = mutableListOf<ProcessItem>()
    @AnyThread fun loadProcesses() { mExecutor.submit { synchronized(mProcessList) { try { mProcessList.clear(); mProcessList.addAll(ProcessParser().parse()); filterAndSort() } catch (th: Throwable) { Log.e("RunningApps", th) } } } }
    @AnyThread fun loadMemoryInfo() { mExecutor.submit { deviceMemoryInfo.postValue(ProcFs.getInstance().memoryInfo) } }

    private val mKillProcessResult = MutableLiveData<Pair<ProcessItem, Boolean>>()
    private val mKillSelectedProcessesResult = MutableLiveData<List<ProcessItem>>()
    fun killProcess(processItem: ProcessItem) { mExecutor.submit { mKillProcessResult.postValue(Pair(processItem, Runner.runCommand(arrayOf("kill", "-9", processItem.pid.toString())).isSuccessful)) } }
    fun observeKillProcess(): LiveData<Pair<ProcessItem, Boolean>> = mKillProcessResult
    fun killSelectedProcesses() { mExecutor.submit { val failed = mutableListOf<ProcessItem>(); mSelectedItems.forEach { if (!Runner.runCommand(arrayOf("kill", "-9", it.pid.toString())).isSuccessful) failed.add(it) }; mKillSelectedProcessesResult.postValue(failed) } }
    fun observeKillSelectedProcess(): LiveData<List<ProcessItem>> = mKillSelectedProcessesResult

    private val mForceStopAppResult = MutableLiveData<Pair<ApplicationInfo, Boolean>>()
    fun forceStop(info: ApplicationInfo) { mExecutor.submit { try { PackageManagerCompat.forceStopPackage(info.packageName, UserHandleHidden.getUserId(info.uid)); mForceStopAppResult.postValue(Pair(info, true)) } catch (e: SecurityException) { e.printStackTrace(); mForceStopAppResult.postValue(Pair(info, false)) } } }
    fun observeForceStop(): LiveData<Pair<ApplicationInfo, Boolean>> = mForceStopAppResult

    private val mPreventBackgroundRunResult = MutableLiveData<Pair<ApplicationInfo, Boolean>>()
    fun canRunInBackground(info: ApplicationInfo): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return true
        return try {
            val am = AppOpsManagerCompat()
            var can = am.checkOperation(AppOpsManagerCompat.OP_RUN_IN_BACKGROUND, info.uid, info.packageName).let { it != AppOpsManager.MODE_IGNORED && it != AppOpsManager.MODE_ERRORED }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) can = can or am.checkOperation(AppOpsManagerCompat.OP_RUN_ANY_IN_BACKGROUND, info.uid, info.packageName).let { it != AppOpsManager.MODE_IGNORED && it != AppOpsManager.MODE_ERRORED }
            can
        } catch (e: Exception) { true }
    }
    fun preventBackgroundRun(info: ApplicationInfo) {
        mExecutor.submit {
            try {
                val am = AppOpsManagerCompat()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) am.setMode(AppOpsManagerCompat.OP_RUN_IN_BACKGROUND, info.uid, info.packageName, AppOpsManager.MODE_IGNORED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) am.setMode(AppOpsManagerCompat.OP_RUN_ANY_IN_BACKGROUND, info.uid, info.packageName, AppOpsManager.MODE_IGNORED)
                mPreventBackgroundRunResult.postValue(Pair(info, true))
            } catch (e: RemoteException) { e.printStackTrace(); mPreventBackgroundRunResult.postValue(Pair(info, false)) }
        }
    }
    fun observePreventBackgroundRun(): LiveData<Pair<ApplicationInfo, Boolean>> = mPreventBackgroundRunResult

    val totalCount: Int get() = mProcessList.size
    var query: String? = null
        private set
    private var mQueryType: Int = 0
    fun setQuery(query: String?, searchType: Int) {
        this.query = if (query == null) null else if (searchType == AdvancedSearchView.SEARCH_TYPE_PREFIX) query else query.lowercase(Locale.ROOT)
        mQueryType = searchType
        mExecutor.submit { filterAndSort() }
    }

    fun addFilter(f: Int) { filter = filter or f; Prefs.RunningApps.setFilters(filter); mExecutor.submit { filterAndSort() } }
    fun removeFilter(f: Int) { filter = filter and f.inv(); Prefs.RunningApps.setFilters(filter); mExecutor.submit { filterAndSort() } }

    @WorkerThread fun filterAndSort() {
        var filtered = mutableListOf<ProcessItem>()
        val fUser = filter and RunningAppsActivity.FILTER_USER_APPS != 0
        val fApps = !fUser && filter and RunningAppsActivity.FILTER_APPS != 0
        for (item in mProcessList) {
            if (fApps && item !is AppProcessItem) continue
            if (fUser) { if (item is AppProcessItem) { if ((item.packageInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue } else continue }
            filtered.add(item)
        }
        if (!query.isNullOrEmpty()) {
            filtered = AdvancedSearchView.matches(query!!, filtered, object : AdvancedSearchView.ChoicesGenerator<ProcessItem> {
                override fun getChoices(obj: ProcessItem): List<String> {
                    val l = mutableListOf(obj.name!!.lowercase(Locale.getDefault()))
                    if (obj is AppProcessItem) l.add(obj.packageInfo.packageName.lowercase(Locale.getDefault()))
                    return l
                }
            }, mQueryType)!!.toMutableList()
        }
        filtered.sortWith { o1, o2 -> o1.pid.compareTo(o2.pid) }
        if (sortOrder != RunningAppsActivity.SORT_BY_PID) {
            filtered.sortWith { p1, p2 ->
                when (sortOrder) {
                    RunningAppsActivity.SORT_BY_APPS_FIRST -> -(p1 is AppProcessItem).compareTo(p2 is AppProcessItem)
                    RunningAppsActivity.SORT_BY_MEMORY_USAGE -> -p1.rss.compareTo(p2.rss)
                    RunningAppsActivity.SORT_BY_PROCESS_NAME -> p1.name!!.compareTo(p2.name!!, ignoreCase = true)
                    RunningAppsActivity.SORT_BY_CPU_TIME -> -p1.cpuTimeInMillis.compareTo(p2.cpuTimeInMillis)
                    else -> p1.pid.compareTo(p2.pid)
                }
            }
        }
        processLiveData.postValue(filtered)
    }

    private val mSelectedItems = LinkedHashSet<ProcessItem>()
    val lastSelectedItem: ProcessItem? get() = mSelectedItems.lastOrNull()
    val selectionCount: Int get() = mSelectedItems.size
    fun isSelected(item: ProcessItem): Boolean = mSelectedItems.contains(item)
    fun select(item: ProcessItem?) { item?.let { mSelectedItems.add(it) } }
    fun deselect(item: ProcessItem?) { item?.let { mSelectedItems.remove(it) } }
    val selections: ArrayList<ProcessItem> get() = ArrayList(mSelectedItems)
    fun getSelectedPackagesWithUsers(): ArrayList<UserPackagePair> {
        val pairs = ArrayList<UserPackagePair>()
        mSelectedItems.forEach { if (it is AppProcessItem) { val ai = it.packageInfo.applicationInfo; pairs.add(UserPackagePair(ai.packageName, UserHandleHidden.getUserId(ai.uid))) } }
        return pairs
    }
    fun clearSelections() { mSelectedItems.clear() }

    private var mUploadingEnabled = false
    private var mUploadingEnabledWatcher: CountDownLatch? = null
    fun enableUploading() { mUploadingEnabled = true; mUploadingEnabledWatcher?.countDown() }
    fun disableUploading() { mUploadingEnabled = false; mUploadingEnabledWatcher?.countDown() }
}
