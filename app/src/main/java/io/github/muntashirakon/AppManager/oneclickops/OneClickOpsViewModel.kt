// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops

import android.Manifest
import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.AnyThread
import androidx.core.util.Pair
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat
import io.github.muntashirakon.AppManager.compat.ManifestCompat
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat
import io.github.muntashirakon.AppManager.compat.StorageManagerCompat
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.settings.Ops
import io.github.muntashirakon.AppManager.users.Users
import io.github.muntashirakon.AppManager.utils.PackageUtils
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import java.util.concurrent.Future

class OneClickOpsViewModel(application: Application) : AndroidViewModel(application) {
    private val mPm: PackageManager = application.packageManager
    private val mTrackerCount = MutableLiveData<List<ItemCount>>()
    private val mComponentCount = MutableLiveData<Pair<List<ItemCount>, Array<String>>>()
    private val mAppOpsCount = MutableLiveData<Pair<List<AppOpCount>, Pair<IntArray, Int>>>()
    private val mClearDataCandidates = MutableLiveData<List<String>>()
    private val mTrimCachesResult = MutableLiveData<Boolean>()
    private val mAppsInstalledByAmForDexOpt = MutableLiveData<Array<String>>()

    private var mFutureResult: Future<*>? = null

    override fun onCleared() {
        mFutureResult?.cancel(true)
        super.onCleared()
    }

    fun watchTrackerCount(): LiveData<List<ItemCount>> = mTrackerCount
    fun watchComponentCount(): LiveData<Pair<List<ItemCount>, Array<String>>> = mComponentCount
    fun watchAppOpsCount(): LiveData<Pair<List<AppOpCount>, Pair<IntArray, Int>>> = mAppOpsCount
    fun getClearDataCandidates(): LiveData<List<String>> = mClearDataCandidates
    fun watchTrimCachesResult(): LiveData<Boolean> = mTrimCachesResult
    fun getAppsInstalledByAmForDexOpt(): MutableLiveData<Array<String>> = mAppsInstalledByAmForDexOpt

    @AnyThread
    fun blockTrackers(systemApps: Boolean) {
        mFutureResult?.cancel(true)
        mFutureResult = ThreadUtils.postOnBackgroundThread {
            val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or
                    PackageManager.GET_SERVICES or PackageManagerCompat.MATCH_DISABLED_COMPONENTS or PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES or
                    PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES
            val canChange = SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE)
            if (!canChange) {
                try {
                    val packageInfo = mPm.getPackageInfo(BuildConfig.APPLICATION_ID, flags)
                    if (systemApps || !ApplicationInfoCompat.isSystemApp(packageInfo.applicationInfo)) {
                        val trackerCount = getTrackerCountForApp(packageInfo)
                        if (trackerCount.count > 0) {
                            mTrackerCount.postValue(listOf(trackerCount))
                            return@postOnBackgroundThread
                        }
                    }
                } catch (e: PackageManager.NameNotFoundException) {
                    e.printStackTrace()
                }
                mTrackerCount.postValue(emptyList())
                return@postOnBackgroundThread
            }
            val crossUser = SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.INTERACT_ACROSS_USERS) ||
                    SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.INTERACT_ACROSS_USERS_FULL)
            val isShell = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Users.getSelfOrRemoteUid() == Ops.SHELL_UID
            val trackerCounts = mutableListOf<ItemCount>()
            val packageNames = mutableSetOf<String>()
            for (packageInfo in PackageUtils.getAllPackages(flags, !crossUser)) {
                if (packageNames.contains(packageInfo.packageName)) continue
                packageNames.add(packageInfo.packageName)
                if (ThreadUtils.isInterrupted()) return@postOnBackgroundThread
                val ai = packageInfo.applicationInfo
                if (isShell && !ApplicationInfoCompat.isTestOnly(ai)) continue
                if (!systemApps && ApplicationInfoCompat.isSystemApp(ai)) continue
                val trackerCount = getTrackerCountForApp(packageInfo)
                if (trackerCount.count > 0) trackerCounts.add(trackerCount)
            }
            mTrackerCount.postValue(trackerCounts)
        }
    }

    @AnyThread
    fun blockComponents(systemApps: Boolean, signatures: Array<String>) {
        if (signatures.isEmpty()) return
        mFutureResult?.cancel(true)
        mFutureResult = ThreadUtils.postOnBackgroundThread {
            val canChange = SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE)
            if (!canChange) {
                val ai = getApplication<Application>().applicationInfo
                if (systemApps || !ApplicationInfoCompat.isSystemApp(ai)) {
                    val componentCount = ItemCount().apply {
                        packageName = ai.packageName
                        packageLabel = ai.loadLabel(mPm).toString()
                        count = PackageUtils.getFilteredComponents(ai.packageName, Users.getSelfOrRemoteUid(), signatures).size
                    }
                    if (componentCount.count > 0) {
                        mComponentCount.postValue(Pair(listOf(componentCount), signatures))
                        return@postOnBackgroundThread
                    }
                }
                mComponentCount.postValue(Pair(emptyList(), signatures))
                return@postOnBackgroundThread
            }
            val crossUser = SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.INTERACT_ACROSS_USERS) ||
                    SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.INTERACT_ACROSS_USERS_FULL)
            val isShell = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Users.getSelfOrRemoteUid() == Ops.SHELL_UID
            val componentCounts = mutableListOf<ItemCount>()
            val packageNames = mutableSetOf<String>()
            for (ai in PackageUtils.getAllApplications(PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES or PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, !crossUser)) {
                if (packageNames.contains(ai.packageName)) continue
                packageNames.add(ai.packageName)
                if (ThreadUtils.isInterrupted()) return@postOnBackgroundThread
                if (isShell && !ApplicationInfoCompat.isTestOnly(ai)) continue
                if (!systemApps && (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue
                val componentCount = ItemCount().apply {
                    packageName = ai.packageName
                    packageLabel = ai.loadLabel(mPm).toString()
                    count = PackageUtils.getFilteredComponents(ai.packageName, Users.getSelfOrRemoteUid(), signatures).size
                }
                if (componentCount.count > 0) componentCounts.add(componentCount)
            }
            mComponentCount.postValue(Pair(componentCounts, signatures))
        }
    }

    @AnyThread
    fun setAppOps(appOpList: IntArray, mode: Int, systemApps: Boolean) {
        mFutureResult?.cancel(true)
        mFutureResult = ThreadUtils.postOnBackgroundThread {
            val appOpCounts = mutableListOf<AppOpCount>()
            val packageNames = mutableSetOf<String>()
            for (ai in PackageUtils.getAllApplications(PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES or PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES)) {
                if (packageNames.contains(ai.packageName)) continue
                packageNames.add(ai.packageName)
                if (ThreadUtils.isInterrupted()) return@postOnBackgroundThread
                if (!systemApps && (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue
                val appOpCount = AppOpCount().apply {
                    packageName = ai.packageName
                    packageLabel = ai.loadLabel(mPm).toString()
                    appOps = PackageUtils.getFilteredAppOps(ai.packageName, Users.getSelfOrRemoteUid(), appOpList, mode)
                    count = appOps!!.size
                }
                if (appOpCount.count > 0) appOpCounts.add(appOpCount)
            }
            mAppOpsCount.postValue(Pair(appOpCounts, Pair(appOpList, mode)))
        }
    }

    fun clearData() {
        mFutureResult?.cancel(true)
        mFutureResult = ThreadUtils.postOnBackgroundThread {
            val packageNames = mutableSetOf<String>()
            for (ai in PackageUtils.getAllApplications(PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES or PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES)) {
                if (packageNames.contains(ai.packageName)) continue
                if (ApplicationInfoCompat.isOnlyDataInstalled(ai)) packageNames.add(ai.packageName)
                if (ThreadUtils.isInterrupted()) return@postOnBackgroundThread
            }
            mClearDataCandidates.postValue(ArrayList(packageNames))
        }
    }

    @AnyThread
    fun trimCaches() {
        ThreadUtils.postOnBackgroundThread {
            val size = 1024L * 1024 * 1024 * 1024 // 1 TB
            try {
                PackageManagerCompat.freeStorageAndNotify(null, size, StorageManagerCompat.FLAG_ALLOCATE_DEFY_ALL_RESERVED)
                mTrimCachesResult.postValue(true)
            } catch (e: Exception) {
                mTrimCachesResult.postValue(false)
            }
        }
    }

    fun listAppsInstalledByAmForDexOpt() {
        ThreadUtils.postOnBackgroundThread {
            val packageNames = mutableSetOf<String>()
            for (ai in PackageUtils.getAllApplications(0)) {
                if (packageNames.contains(ai.packageName)) continue
                packageNames.add(ai.packageName)
                if (ThreadUtils.isInterrupted()) return@postOnBackgroundThread
            }
            mAppsInstalledByAmForDexOpt.postValue(packageNames.toTypedArray())
        }
    }

    private fun getTrackerCountForApp(packageInfo: PackageInfo): ItemCount {
        return ItemCount().apply {
            packageName = packageInfo.packageName
            packageLabel = packageInfo.applicationInfo.loadLabel(mPm).toString()
            count = ComponentUtils.getTrackerComponentsCountForPackage(packageInfo)
        }
    }

    companion object {
        val TAG: String = OneClickOpsViewModel::class.java.simpleName
    }
}
