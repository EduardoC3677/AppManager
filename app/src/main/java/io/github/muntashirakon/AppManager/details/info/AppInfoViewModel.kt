// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.UserIdInt
import android.app.ActivityManager
import android.app.Application
import android.content.Intent
import android.content.pm.*
import android.content.pm.verify.domain.DomainVerificationUserState
import android.os.Build
import android.text.TextUtils
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import androidx.core.util.Pair
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.github.muntashirakon.AppManager.StaticDataset
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerService
import io.github.muntashirakon.AppManager.backup.BackupUtils
import io.github.muntashirakon.AppManager.compat.*
import io.github.muntashirakon.AppManager.db.entity.Backup
import io.github.muntashirakon.AppManager.debloat.DebloatObject
import io.github.muntashirakon.AppManager.details.AppDetailsViewModel
import io.github.muntashirakon.AppManager.magisk.*
import io.github.muntashirakon.AppManager.misc.OsEnvironment
import io.github.muntashirakon.AppManager.misc.XposedModuleInfo
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils
import io.github.muntashirakon.AppManager.rules.struct.ComponentRule
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.settings.FeatureController
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.ssaid.SsaidSettings
import io.github.muntashirakon.AppManager.types.PackageSizeInfo
import io.github.muntashirakon.AppManager.uri.UriManager
import io.github.muntashirakon.AppManager.usage.AppUsageStatsManager
import io.github.muntashirakon.AppManager.usage.UsageUtils
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.muntashirakon.AppManager.StaticDataset
...
import io.github.muntashirakon.io.Paths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.File
...
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.zip.ZipFile
import javax.inject.Inject

@HiltViewModel
class AppInfoViewModel @Inject constructor(application: Application) : AndroidViewModel(application) {
    private val mAppLabel = MutableLiveData<CharSequence>()
    private val mTagCloud = MutableLiveData<TagCloud>()
    private val mAppInfo = MutableLiveData<AppInfo>()
    private val mInstallExistingResult = MutableLiveData<Pair<Int, CharSequence>>()
    private var mTagCloudJob: Job? = null
    private var mAppInfoJob: Job? = null
    private var mMainModel: AppDetailsViewModel? = null

    override fun onCleared() {
        mTagCloudJob?.cancel()
        mAppInfoJob?.cancel()
        super.onCleared()
    }
    fun setMainModel(mainModel: AppDetailsViewModel) {
        mMainModel = mainModel
    }

    fun getAppLabel(): LiveData<CharSequence> = mAppLabel
    fun getTagCloud(): LiveData<TagCloud> = mTagCloud
    fun getAppInfo(): LiveData<AppInfo> = mAppInfo
    fun getInstallExistingResult(): LiveData<Pair<Int, CharSequence>> = mInstallExistingResult

    @AnyThread
    fun loadAppLabel(applicationInfo: ApplicationInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            val appLabel = applicationInfo.loadLabel(getApplication<Application>().packageManager)
            mAppLabel.postValue(appLabel)
        }
    }

    @AnyThread
    fun loadTagCloud(packageInfo: PackageInfo, isExternalApk: Boolean) {
        mTagCloudJob?.cancel()
        mTagCloudJob = viewModelScope.launch(Dispatchers.IO) { loadTagCloudInternal(packageInfo, isExternalApk) }
    }

    @WorkerThread
    private fun loadTagCloudInternal(packageInfo: PackageInfo, isExternalApk: Boolean) {
        val mainModel = mMainModel ?: return
        val packageName = packageInfo.packageName
        val userId = mainModel.getUserId()
        val applicationInfo = packageInfo.applicationInfo!!
        val tagCloud = TagCloud()
        try {
            val trackerComponentsMap = ComponentUtils.getTrackerComponentsForPackage(packageInfo)
            tagCloud.trackerComponents = ArrayList(trackerComponentsMap.size)
            for ((component, ruleType) in trackerComponentsMap) {
                val componentRule = mainModel.getComponentRule(component) ?: ComponentRule(packageName, component, ruleType, Prefs.Blocking.getDefaultBlockingMethod())
                tagCloud.trackerComponents!!.add(componentRule)
                tagCloud.areAllTrackersBlocked = tagCloud.areAllTrackersBlocked and componentRule.isBlocked
            }
            ensureActive()
            tagCloud.isSystemApp = ApplicationInfoCompat.isSystemApp(applicationInfo)
            tagCloud.isUpdatedSystemApp = (applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val codePath = PackageUtils.getHiddenCodePathOrDefault(packageName, applicationInfo.publicSourceDir)
            tagCloud.isSystemlessPath = !isExternalApk && MagiskUtils.isSystemlessPath(codePath)
            if (!isExternalApk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                DomainVerificationManagerCompat.getDomainVerificationUserState(packageName, userId)?.let { userState ->
                    tagCloud.canOpenLinks = userState.isLinkHandlingAllowed
                    if (userState.hostToStateMap.isNotEmpty()) {
                        tagCloud.hostsToOpen = userState.hostToStateMap
                    }
                }
            }
            tagCloud.splitCount = mainModel.getSplitCount()
            tagCloud.isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            tagCloud.isTestOnly = ApplicationInfoCompat.isTestOnly(applicationInfo)
            tagCloud.hasCode = (applicationInfo.flags and ApplicationInfo.FLAG_HAS_CODE) != 0
            tagCloud.isOverlay = PackageInfoCompat2.getOverlayTarget(packageInfo) != null
            tagCloud.hasRequestedLargeHeap = (applicationInfo.flags and ApplicationInfo.FLAG_LARGE_HEAP) != 0
            ensureActive()
            tagCloud.isRunning = ActivityManagerCompat.getRunningAppProcesses().any { ArrayUtils.contains(it.pkgList, packageName) }
            tagCloud.runningServices = ActivityManagerCompat.getRunningServices(packageName, userId)
            tagCloud.isForceStopped = ApplicationInfoCompat.isStopped(applicationInfo)
            tagCloud.isAppEnabled = applicationInfo.enabled
            tagCloud.isAppSuspended = ApplicationInfoCompat.isSuspended(applicationInfo)
            tagCloud.isAppHidden = ApplicationInfoCompat.isHidden(applicationInfo)
            ensureActive()
            tagCloud.magiskHiddenProcesses = MagiskHide.getProcesses(packageInfo)
            var magiskHideEnabled = false
            for (proc in tagCloud.magiskHiddenProcesses!!) {
                magiskHideEnabled = magiskHideEnabled or proc.isEnabled
                if (tagCloud.runningServices!!.any { it.process.startsWith(proc.name) }) proc.isRunning = true
            }
            tagCloud.isMagiskHideEnabled = !isExternalApk && magiskHideEnabled
            tagCloud.magiskDeniedProcesses = MagiskDenyList.getProcesses(packageInfo)
            var magiskDenyListEnabled = false
            for (proc in tagCloud.magiskDeniedProcesses!!) {
                magiskDenyListEnabled = magiskDenyListEnabled or proc.isEnabled
                if (tagCloud.runningServices!!.any { it.process.startsWith(proc.name) }) proc.isRunning = true
            }
            tagCloud.isMagiskDenyListEnabled = !isExternalApk && magiskDenyListEnabled
            ensureActive()
            StaticDataset.getDebloatObjects().find { packageName == it.packageName }?.let { tagCloud.bloatwareRemovalType = it.removal }
            ensureActive()
            tagCloud.sensorsEnabled = if (!isExternalApk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_SENSORS)) SensorServiceCompat.isSensorEnabled(packageName, userId) else true
            try {
                ZipFile(applicationInfo.publicSourceDir).use { zipFile ->
                    val isXposed = XposedModuleInfo.isXposedModule(applicationInfo, zipFile)
                    if (isXposed != false) {
                        tagCloud.xposedModuleInfo = XposedModuleInfo(applicationInfo, if (isXposed == null) null else zipFile)
                    }
                }
            } catch (ignore: Throwable) {}
            tagCloud.canWriteAndExecute = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && applicationInfo.targetSdkVersion < Build.VERSION_CODES.Q
            tagCloud.hasKeyStoreItems = KeyStoreUtils.hasKeyStore(applicationInfo.uid)
            tagCloud.hasMasterKeyInKeyStore = KeyStoreUtils.hasMasterKey(applicationInfo.uid)
            tagCloud.usesPlayAppSigning = PackageUtils.usesPlayAppSigning(applicationInfo)
            ensureActive()
            tagCloud.backups = BackupUtils.getBackupMetadataFromDbNoLockValidate(packageName)
            tagCloud.isBatteryOptimized = if (!isExternalApk) DeviceIdleManagerCompat.isBatteryOptimizedApp(packageName) else true
            tagCloud.netPolicies = if (!isExternalApk && SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_NETWORK_POLICY)) NetworkPolicyManagerCompat.getUidPolicy(applicationInfo.uid) else 0
            if (!isExternalApk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    tagCloud.ssaid = SsaidSettings(userId).getSsaid(packageName, applicationInfo.uid).takeIf { it.isNotEmpty() }
                } catch (ignore: IOException) {}
            }
            if (!isExternalApk) {
                ensureActive()
                UriManager().getGrantedUris(packageName)?.let { grants ->
                    tagCloud.uriGrants = grants.filter { it.targetUserId == userId }
                }
            }
            if (ApplicationInfoCompat.isStaticSharedLibrary(applicationInfo)) {
                ensureActive()
                val names = mutableListOf<String>()
                try {
                    PackageManagerCompat.getInstalledApplications(PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId).forEach {
                        if (it.packageName == packageName) names.add(it.processName)
                    }
                } catch (ignore: Throwable) { names.add(applicationInfo.processName) }
                tagCloud.staticSharedLibraryNames = names.toTypedArray()
            }
            ensureActive()
            mTagCloud.postValue(tagCloud)
        } catch (th: Throwable) {
            ThreadUtils.postOnMainThread { throw RuntimeException(th) }
        }
    }

    @AnyThread
    fun loadAppInfo(packageInfo: PackageInfo, isExternalApk: Boolean) {
        mAppInfoJob?.cancel()
        mAppInfoJob = viewModelScope.launch(Dispatchers.IO) { loadAppInfoInternal(packageInfo, isExternalApk) }
    }

    @WorkerThread
    private fun loadAppInfoInternal(packageInfo: PackageInfo, isExternalApk: Boolean) {
        val packageName = packageInfo.packageName
        val applicationInfo = packageInfo.applicationInfo!!
        val userId = UserHandleHidden.getUserId(applicationInfo.uid)
        val pm = getApplication<Application>().packageManager
        val appInfo = AppInfo()
        try {
            if (!isExternalApk) {
                appInfo.sourceDir = File(applicationInfo.publicSourceDir).parent
                appInfo.dataDir = applicationInfo.dataDir
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) appInfo.dataDeDir = applicationInfo.deviceProtectedDataDir
                appInfo.extDataDirs = mutableListOf()
                OsEnvironment.getUserEnvironment(userId).buildExternalStorageAppDataDirs(packageName).forEach { dir ->
                    Paths.getAccessiblePath(dir).takeIf { it.exists() }?.filePath?.let { appInfo.extDataDirs!!.add(it) }
                }
                if (Paths.exists(applicationInfo.nativeLibraryDir)) appInfo.jniDir = applicationInfo.nativeLibraryDir
                if (FeatureController.isUsageAccessEnabled() && SelfPermissions.checkUsageStatsPermission()) {
                    val dataUsage = AppUsageStatsManager.getDataUsageForPackage(applicationInfo.uid, UsageUtils.getLastWeek())
                    appInfo.dataUsage = if (dataUsage.total == 0L && !ArrayUtils.contains(packageInfo.requestedPermissions, Manifest.permission.INTERNET)) null else dataUsage
                    appInfo.sizeInfo = PackageUtils.getPackageSizeInfo(getApplication(), packageName, userId, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) applicationInfo.storageUuid else null)
                }
                PackageManagerCompat.getInstallSourceInfo(packageName, userId)?.let { info ->
                    info.installingPackageName?.let { pkg ->
                        val label = PackageUtils.getPackageLabel(pm, pkg, userId)
                        appInfo.installerApp = label
                        info.installingPackageLabel = label
                    }
                    info.initiatingPackageName?.let { pkg ->
                        val label = PackageUtils.getPackageLabel(pm, pkg, userId)
                        if (appInfo.installerApp == null) appInfo.installerApp = label
                        info.initiatingPackageLabel = label
                    }
                    info.originatingPackageName?.let { pkg -> info.originatingPackageLabel = PackageUtils.getPackageLabel(pm, pkg, userId) }
                    appInfo.installSource = info
                }
                appInfo.mainActivity = PackageManagerCompat.getLaunchIntentForPackage(packageName, userId)
                appInfo.seInfo = ApplicationInfoCompat.getSeInfo(applicationInfo)
                appInfo.primaryCpuAbi = ApplicationInfoCompat.getPrimaryCpuAbi(applicationInfo)
                appInfo.zygotePreloadName = ApplicationInfoCompat.getZygotePreloadName(applicationInfo)
                appInfo.hiddenApiEnforcementPolicy = ApplicationInfoCompat.getHiddenApiEnforcementPolicy(applicationInfo)
            }
            mAppInfo.postValue(appInfo)
        } catch (th: Throwable) {
            ThreadUtils.postOnMainThread { throw RuntimeException(th) }
        }
    }

    fun installExisting(packageName: String, userId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val installer = PackageInstallerCompat.getNewInstance()
            installer.setOnInstallListener(object : PackageInstallerCompat.OnInstallListener {
                override fun onStartInstall(sessionId: Int, packageName: String) {}
                override fun onFinishedInstall(sessionId: Int, packageName: String, result: Int, blockingPackage: String?, statusMessage: String?) {
                    val sb = StringBuilder(PackageInstallerService.getStringFromStatus(getApplication(), result, getAppLabel().value, blockingPackage))
                    statusMessage?.let { sb.append("\n").append(it) }
                    mInstallExistingResult.postValue(Pair(result, sb.toString()))
                }
            })
            installer.installExisting(packageName, userId)
        }
    }

    class TagCloud {
        var trackerComponents: MutableList<ComponentRule>? = null
        var areAllTrackersBlocked = true
        var isSystemApp: Boolean = false
        var isSystemlessPath: Boolean = false
        var isUpdatedSystemApp: Boolean = false
        var canOpenLinks: Boolean = false
        var hostsToOpen: Map<String, Int>? = null
        var splitCount: Int = 0
        var isDebuggable: Boolean = false
        var isTestOnly: Boolean = false
        var hasCode: Boolean = false
        var isOverlay: Boolean = false
        var hasRequestedLargeHeap: Boolean = false
        var isRunning: Boolean = false
        var runningServices: List<ActivityManager.RunningServiceInfo>? = null
        var magiskHiddenProcesses: List<MagiskProcess>? = null
        var magiskDeniedProcesses: List<MagiskProcess>? = null
        var isForceStopped: Boolean = false
        var isAppEnabled: Boolean = false
        var isAppHidden: Boolean = false
        var isAppSuspended: Boolean = false
        var isMagiskHideEnabled: Boolean = false
        var isMagiskDenyListEnabled: Boolean = false
        @DebloatObject.Removal var bloatwareRemovalType: Int = 0
        var sensorsEnabled: Boolean = false
        var xposedModuleInfo: XposedModuleInfo? = null
        var canWriteAndExecute: Boolean = false
        var hasKeyStoreItems: Boolean = false
        var hasMasterKeyInKeyStore: Boolean = false
        var usesPlayAppSigning: Boolean = false
        var backups: List<Backup>? = null
        var isBatteryOptimized: Boolean = false
        var netPolicies: Int = 0
        var ssaid: String? = null
        var uriGrants: List<UriManager.UriGrant>? = null
        var staticSharedLibraryNames: Array<String>? = null
    }

    class AppInfo {
        var sourceDir: String? = null
        var dataDir: String? = null
        var dataDeDir: String? = null
        var extDataDirs: MutableList<String>? = null
        var jniDir: String? = null
        var dataUsage: AppUsageStatsManager.DataUsage? = null
        var sizeInfo: PackageSizeInfo? = null
        var installSource: InstallSourceInfoCompat? = null
        var installerApp: CharSequence? = null
        var mainActivity: Intent? = null
        var seInfo: String? = null
        var primaryCpuAbi: String? = null
        var zygotePreloadName: String? = null
        var hiddenApiEnforcementPolicy: Int = 0
    }
}
