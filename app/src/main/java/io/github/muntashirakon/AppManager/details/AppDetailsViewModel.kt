// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.UserIdInt
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.om.OverlayInfo
import android.content.pm.*
import android.os.Build
import android.os.RemoteException
import android.text.TextUtils
import androidx.annotation.AnyThread
import androidx.annotation.GuardedBy
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.content.pm.PermissionInfoCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.android.apksig.ApkVerifier
import io.github.muntashirakon.AppManager.apk.ApkFile
import io.github.muntashirakon.AppManager.apk.ApkSource
import io.github.muntashirakon.AppManager.apk.CachedApkSource
import io.github.muntashirakon.AppManager.apk.dexopt.DexOptimizer
import io.github.muntashirakon.AppManager.apk.signing.SignerInfo
import io.github.muntashirakon.AppManager.compat.*
import io.github.muntashirakon.AppManager.details.struct.*
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView
import io.github.muntashirakon.AppManager.permission.*
import io.github.muntashirakon.AppManager.rules.RuleType
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker
import io.github.muntashirakon.AppManager.rules.struct.*
import io.github.muntashirakon.AppManager.scanner.NativeLibraries
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.settings.Ops
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.types.PackageChangeReceiver
import io.github.muntashirakon.AppManager.types.UserPackagePair
import io.github.muntashirakon.AppManager.users.UserInfo
import io.github.muntashirakon.AppManager.users.Users
import io.github.muntashirakon.AppManager.utils.*
import io.github.muntashirakon.io.IoUtils
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AppDetailsViewModel(application: Application) : AndroidViewModel(application) {
    private val mPackageManager: PackageManager = application.packageManager
    private val mBlockerLocker = Any()
    private val mExecutor: ExecutorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2)
    private val mPackageInfoWatcher = CountDownLatch(1)
    private val mPackageInfoLiveData = MutableLiveData<PackageInfo>()
    private val mTagsAlteredLiveData = MutableLiveData<Boolean>()
    private val mFreezeTypeLiveData = MutableLiveData<Int>()
    private val mComponentChangedLiveData = MutableLiveData<AppDetailsComponentItem>()

    var mPackageInfo: PackageInfo? = null
    var mInstalledPackageInfo: PackageInfo? = null
    var mPackageName: String? = null
    @GuardedBy("mBlockerLocker")
    var mBlocker: ComponentsBlocker? = null
    private var mReceiver: PackageIntentReceiver? = PackageIntentReceiver(this)
    private var mApkPath: String? = null
    private var mApkSource: ApkSource? = null
    private var mApkFile: ApkFile? = null
    private var mUserId: Int = UserHandleHidden.myUserId()
    private var mSortOrderComponents = Prefs.AppDetailsPage.getComponentsSortOrder()
    private var mSortOrderAppOps = Prefs.AppDetailsPage.getAppOpsSortOrder()
    private var mSortOrderPermissions = Prefs.AppDetailsPage.getPermissionsSortOrder()
    private var mSortOrderOverlays = Prefs.AppDetailsPage.getOverlaysSortOrder()
    private var mSearchQuery: String? = null
    private var mSearchType: Int = AdvancedSearchView.SEARCH_TYPE_CONTAINS
    private var mWaitForBlocker = true
    var isExternalApk = false
        private set

    init {
        mUserId = UserHandleHidden.myUserId()
    }

    override fun onCleared() {
        Log.d(TAG, "On Clear called for $mPackageName")
        super.onCleared()
        mExecutor.submit {
            synchronized(mBlockerLocker) {
                mBlocker?.let {
                    it.setReadOnly()
                    it.close()
                }
            }
        }
        mReceiver?.let { getApplication<Application>().unregisterReceiver(it) }
        mReceiver = null
        IoUtils.closeQuietly(mApkFile)
        if (mApkSource is CachedApkSource) (mApkSource as CachedApkSource).cleanup()
        mExecutor.shutdownNow()
    }

    fun getFreezeTypeLiveData(): LiveData<Int> = mFreezeTypeLiveData

    fun loadFreezeType() {
        mExecutor.submit {
            val freezeType = FreezeUtils.loadFreezeMethod(mPackageName)
            mFreezeTypeLiveData.postValue(freezeType)
        }
    }

    fun getTagsAlteredLiveData(): MutableLiveData<Boolean> = mTagsAlteredLiveData

    @UiThread
    fun setPackage(apkSource: ApkSource): LiveData<PackageInfo> {
        mApkSource = apkSource
        isExternalApk = true
        mExecutor.submit {
            try {
                Log.d(TAG, "Package Uri is being set")
                mApkFile = mApkSource!!.resolve()
                setPackageNameInternal(mApkFile!!.packageName!!)
                val cachedApkFile = mApkFile!!.baseEntry.getFile(false)
                if (!cachedApkFile.canRead()) throw Exception("Cannot read $cachedApkFile")
                mApkPath = cachedApkFile.absolutePath
                setPackageInfoInternal(false)
                mPackageInfoLiveData.postValue(mPackageInfo)
            } catch (th: Throwable) {
                Log.e(TAG, "Could not fetch package info.", th)
                mPackageInfoLiveData.postValue(null)
            } finally {
                mPackageInfoWatcher.countDown()
            }
        }
        return mPackageInfoLiveData
    }

    @UiThread
    fun setPackage(packageName: String): LiveData<PackageInfo> {
        isExternalApk = false
        mExecutor.submit {
            try {
                Log.d(TAG, "Package name is being set")
                setPackageNameInternal(packageName)
                setPackageInfoInternal(false)
                val pi = mPackageInfo ?: throw ApkFile.ApkFileException("Package not installed.")
                mApkSource = ApkSource.getApkSource(pi.applicationInfo)
                mApkFile = mApkSource!!.resolve()
                mPackageInfoLiveData.postValue(pi)
            } catch (th: Throwable) {
                Log.e(TAG, "Could not fetch package info.", th)
                mPackageInfoLiveData.postValue(null)
            } finally {
                mPackageInfoWatcher.countDown()
            }
        }
        return mPackageInfoLiveData
    }

    fun setUserId(@UserIdInt userId: Int) { mUserId = userId }
    fun getUserId(): Int = mUserId

    private fun setPackageNameInternal(packageName: String) {
        if (mPackageName != null) return
        Log.d(TAG, "Package name is being set for $packageName")
        mPackageName = packageName
        if (isExternalApk) return
        mExecutor.submit {
            synchronized(mBlockerLocker) {
                try {
                    mWaitForBlocker = true
                    mBlocker?.let {
                        it.setReadOnly()
                        it.close()
                    }
                    mBlocker = ComponentsBlocker.getInstance(packageName, mUserId)
                } finally {
                    mWaitForBlocker = false
                    (mBlockerLocker as Object).notifyAll()
                }
            }
        }
    }

    fun getPackageNameNonNull(): String = mPackageName ?: ""
    fun getApkFileNonNull(): ApkFile? = mApkFile
    fun getApkSourceNonNull(): ApkSource? = mApkSource

    fun isTestOnlyApp(): Boolean = mPackageInfo?.applicationInfo?.let { ApplicationInfoCompat.isTestOnly(it) } ?: false

    @SuppressLint("SwitchIntDef")
    fun setSortOrder(@AppDetailsFragment.SortOrder sortOrder: Int, @AppDetailsFragment.Property property: Int) {
        when (property) {
            AppDetailsFragment.ACTIVITIES, AppDetailsFragment.SERVICES, AppDetailsFragment.RECEIVERS, AppDetailsFragment.PROVIDERS -> {
                mSortOrderComponents = sortOrder
                Prefs.AppDetailsPage.setComponentsSortOrder(sortOrder)
            }
            AppDetailsFragment.APP_OPS -> {
                mSortOrderAppOps = sortOrder
                Prefs.AppDetailsPage.setAppOpsSortOrder(sortOrder)
            }
            AppDetailsFragment.USES_PERMISSIONS -> {
                mSortOrderPermissions = sortOrder
                Prefs.AppDetailsPage.setPermissionsSortOrder(sortOrder)
            }
            AppDetailsFragment.OVERLAYS -> {
                mSortOrderOverlays = sortOrder
                Prefs.AppDetailsPage.setOverlaysSortOrder(sortOrder)
            }
        }
        mExecutor.submit { filterAndSortItemsInternal(property) }
    }

    @SuppressLint("SwitchIntDef")
    @AppDetailsFragment.SortOrder
    fun getSortOrder(@AppDetailsFragment.Property property: Int): Int {
        return when (property) {
            AppDetailsFragment.ACTIVITIES, AppDetailsFragment.SERVICES, AppDetailsFragment.RECEIVERS, AppDetailsFragment.PROVIDERS -> mSortOrderComponents
            AppDetailsFragment.APP_OPS -> mSortOrderAppOps
            AppDetailsFragment.USES_PERMISSIONS -> mSortOrderPermissions
            AppDetailsFragment.OVERLAYS -> mSortOrderOverlays
            else -> AppDetailsFragment.SORT_BY_NAME
        }
    }

    fun setSearchQuery(searchQuery: String, searchType: Int, @AppDetailsFragment.Property property: Int) {
        mSearchQuery = if (searchType == AdvancedSearchView.SEARCH_TYPE_REGEX) searchQuery else searchQuery.lowercase(Locale.ROOT)
        mSearchType = searchType
        mExecutor.submit { filterAndSortItemsInternal(property) }
    }

    fun getSearchQuery(): String? = mSearchQuery

    fun filterAndSortItems(@AppDetailsFragment.Property property: Int) {
        mExecutor.submit { filterAndSortItemsInternal(property) }
    }

    @SuppressLint("SwitchIntDef")
    private fun filterAndSortItemsInternal(@AppDetailsFragment.Property property: Int) {
        when (property) {
            AppDetailsFragment.ACTIVITIES -> synchronized(mActivityItems) { mActivities.postValue(filterAndSortComponents(mActivityItems)) }
            AppDetailsFragment.PROVIDERS -> synchronized(mProviderItems) { mProviders.postValue(filterAndSortComponents(mProviderItems)) }
            AppDetailsFragment.RECEIVERS -> synchronized(mReceiverItems) { mReceivers.postValue(filterAndSortComponents(mReceiverItems)) }
            AppDetailsFragment.SERVICES -> synchronized(mServiceItems) { mServices.postValue(filterAndSortComponents(mServiceItems)) }
            AppDetailsFragment.APP_OPS -> {
                val items = synchronized(mAppOpItems) {
                    if (!TextUtils.isEmpty(mSearchQuery)) AdvancedSearchView.matches(mSearchQuery!!, mAppOpItems, { lowercaseIfNotRegex(it.name, mSearchType) }, mSearchType)
                    else ArrayList(mAppOpItems)
                }
                items!!.sortWith { o1, o2 ->
                    when (mSortOrderAppOps) {
                        AppDetailsFragment.SORT_BY_NAME -> o1.name.compareTo(o2.name, ignoreCase = true)
                        AppDetailsFragment.SORT_BY_APP_OP_VALUES -> o1.op.compareTo(o2.op)
                        AppDetailsFragment.SORT_BY_DENIED_APP_OPS -> -o1.mode.compareTo(o2.mode)
                        else -> 0
                    }
                }
                mAppOps.postValue(items)
            }
            AppDetailsFragment.USES_PERMISSIONS -> {
                val items = synchronized(mUsesPermissionItems) {
                    if (!TextUtils.isEmpty(mSearchQuery)) AdvancedSearchView.matches(mSearchQuery!!, mUsesPermissionItems, { lowercaseIfNotRegex(it.name, mSearchType) }, mSearchType)
                    else ArrayList(mUsesPermissionItems)
                }
                items!!.sortWith { o1, o2 ->
                    when (mSortOrderPermissions) {
                        AppDetailsFragment.SORT_BY_NAME -> o1.name.compareTo(o2.name, ignoreCase = true)
                        AppDetailsFragment.SORT_BY_DANGEROUS_PERMS -> -o1.isDangerous.compareTo(o2.isDangerous)
                        AppDetailsFragment.SORT_BY_DENIED_PERMS -> o1.permission.isGranted().compareTo(o2.permission.isGranted())
                        else -> 0
                    }
                }
                mUsesPermissions.postValue(ArrayList(items))
            }
            AppDetailsFragment.PERMISSIONS -> synchronized(mPermissionItems) { mPermissions.postValue(filterAndSortPermissions(mPermissionItems)) }
            AppDetailsFragment.OVERLAYS -> {
                val items = synchronized(mOverlays) {
                    val list = mOverlays.value ?: emptyList()
                    if (!TextUtils.isEmpty(mSearchQuery)) AdvancedSearchView.matches(mSearchQuery!!, list, { lowercaseIfNotRegex(it.name, mSearchType) }, mSearchType)
                    else ArrayList(list)
                }
                items!!.sortWith { o1, o2 ->
                    when (mSortOrderOverlays) {
                        AppDetailsFragment.SORT_BY_NAME -> o1.name.compareTo(o2.name, ignoreCase = true)
                        AppDetailsFragment.SORT_BY_PRIORITY -> o1.priority.compareTo(o2.priority)
                        else -> 0
                    }
                }
                mOverlays.postValue(ArrayList(items))
            }
        }
    }

    private fun filterAndSortComponents(items: List<AppDetailsItem<ComponentInfo>>): List<AppDetailsItem<ComponentInfo>> {
        val filtered = if (TextUtils.isEmpty(mSearchQuery)) ArrayList(items)
        else AdvancedSearchView.matches(mSearchQuery!!, items, { lowercaseIfNotRegex(it.name, mSearchType) }, mSearchType)!!
        sortComponents(filtered)
        return filtered
    }

    private fun filterAndSortPermissions(items: List<AppDetailsItem<PermissionInfo>>): List<AppDetailsItem<PermissionInfo>> {
        val filtered = if (TextUtils.isEmpty(mSearchQuery)) ArrayList(items)
        else AdvancedSearchView.matches(mSearchQuery!!, items, { lowercaseIfNotRegex(it.name, mSearchType) }, mSearchType)!!
        filtered.sortWith { o1, o2 -> o1.name.compareTo(o2.name, ignoreCase = true) }
        return filtered
    }

    private fun lowercaseIfNotRegex(s: String, filterType: Int): String = if (filterType == AdvancedSearchView.SEARCH_TYPE_REGEX) s else s.lowercase(Locale.ROOT)

    private val mRuleApplicationStatus = MutableLiveData<Int>()
    fun getRuleApplicationStatus(): LiveData<Int> {
        if (mRuleApplicationStatus.value == null) mExecutor.submit { setRuleApplicationStatusInternal() }
        return mRuleApplicationStatus
    }

    fun setRuleApplicationStatusInternal() {
        if (mPackageName == null || isExternalApk) {
            mRuleApplicationStatus.postValue(RULE_NO_RULE)
            return
        }
        synchronized(mBlockerLocker) {
            waitForBlockerOrExit()
            var status = if (mBlocker!!.isRulesApplied) RULE_APPLIED else RULE_NOT_APPLIED
            if (mBlocker!!.componentCount() == 0) status = RULE_NO_RULE
            mRuleApplicationStatus.postValue(status)
        }
    }

    fun updateRulesForComponent(componentItem: AppDetailsComponentItem, type: RuleType, componentStatus: String) {
        if (isExternalApk) return
        mExecutor.submit {
            mReceiver?.pauseWatcher()
            val componentName = componentItem.name
            synchronized(mBlockerLocker) {
                waitForBlockerOrExit()
                mBlocker!!.setMutable()
                if (mBlocker!!.hasComponentName(componentName)) mBlocker!!.deleteComponent(componentName)
                mBlocker!!.addComponent(componentName, type, componentStatus)
                if (Prefs.Blocking.globalBlockingEnabled() || mRuleApplicationStatus.value == RULE_APPLIED) {
                    mBlocker!!.applyRules(true)
                }
                setRuleApplicationStatusInternal()
                mBlocker!!.commit()
                mBlocker!!.setReadOnly()
                mReceiver?.resumeWatcher()
            }
        }
    }

    fun getComponentRule(componentName: String): ComponentRule? {
        synchronized(mBlockerLocker) { return mBlocker?.getComponent(componentName) }
    }

    fun addRules(entries: List<RuleEntry>, forceApply: Boolean) {
        if (isExternalApk) return
        synchronized(mBlockerLocker) {
            waitForBlockerOrExit()
            mBlocker!!.setMutable()
            for (entry in entries) {
                if (mBlocker!!.hasComponentName(entry.name)) mBlocker!!.removeComponent(entry.name)
                mBlocker!!.addComponent(entry.name, entry.type)
            }
            if (forceApply || Prefs.Blocking.globalBlockingEnabled() || mRuleApplicationStatus.value == RULE_APPLIED) {
                mBlocker!!.applyRules(true)
            }
            setRuleApplicationStatusInternal()
            mBlocker!!.commit()
            mBlocker!!.setReadOnly()
            reloadComponents()
        }
    }

    fun removeRules(entries: List<RuleEntry>, forceApply: Boolean) {
        if (isExternalApk) return
        synchronized(mBlockerLocker) {
            waitForBlockerOrExit()
            mBlocker!!.setMutable()
            for (entry in entries) {
                if (mBlocker!!.hasComponentName(entry.name)) mBlocker!!.removeComponent(entry.name)
            }
            if (forceApply || Prefs.Blocking.globalBlockingEnabled() || mRuleApplicationStatus.value == RULE_APPLIED) {
                mBlocker!!.applyRules(true)
            }
            setRuleApplicationStatusInternal()
            mBlocker!!.commit()
            mBlocker!!.setReadOnly()
            reloadComponents()
        }
    }

    fun togglePermission(permissionItem: AppDetailsPermissionItem): Boolean {
        if (isExternalApk) return false
        val pi = getPackageInfoInternal() ?: return false
        try {
            if (!permissionItem.isGranted) permissionItem.grantPermission(pi, mAppOpsManager)
            else permissionItem.revokePermission(pi, mAppOpsManager)
        } catch (e: Exception) { return false }
        mExecutor.submit {
            synchronized(mBlockerLocker) {
                waitForBlockerOrExit()
                mBlocker!!.setMutable()
                mBlocker!!.setPermission(permissionItem.name, permissionItem.permission.isGranted(), permissionItem.permission.getFlags())
                mBlocker!!.commit()
                mBlocker!!.setReadOnly()
                (mBlockerLocker as Object).notifyAll()
            }
        }
        setUsesPermission(permissionItem)
        return true
    }

    fun revokeDangerousPermissions(): Boolean {
        if (isExternalApk) return false
        val pi = getPackageInfoInternal() ?: return false
        val revoked = mutableListOf<AppDetailsPermissionItem>()
        var success = true
        synchronized(mUsesPermissionItems) {
            for (item in mUsesPermissionItems) {
                if (!item.isDangerous || !item.permission.isGranted()) continue
                try {
                    item.revokePermission(pi, mAppOpsManager)
                    revoked.add(item)
                } catch (e: Exception) { success = false }
            }
        }
        mExecutor.submit {
            synchronized(mBlockerLocker) {
                waitForBlockerOrExit()
                mBlocker!!.setMutable()
                for (item in revoked) mBlocker!!.setPermission(item.name, item.permission.isGranted(), item.permission.getFlags())
                mBlocker!!.commit()
                mBlocker!!.setReadOnly()
                (mBlockerLocker as Object).notifyAll()
            }
        }
        return success
    }

    private val mAppOpsManager = AppOpsManagerCompat()

    fun setAppOp(op: Int, mode: Int): Boolean {
        if (isExternalApk) return false
        val pi = getPackageInfoInternal() ?: return false
        try {
            PermUtils.setAppOpMode(mAppOpsManager, op, mPackageName!!, pi.applicationInfo.uid, mode)
            mExecutor.submit {
                synchronized(mBlockerLocker) {
                    waitForBlockerOrExit()
                    mBlocker!!.setMutable()
                    mBlocker!!.setAppOp(op, mode)
                    mBlocker!!.commit()
                    mBlocker!!.setReadOnly()
                    (mBlockerLocker as Object).notifyAll()
                }
            }
        } catch (e: Exception) { return false }
        return true
    }

    fun setAppOpMode(appOpItem: AppDetailsAppOpItem): Boolean {
        if (isExternalApk) return false
        val pi = getPackageInfoInternal() ?: return false
        try {
            if (appOpItem.isAllowed) appOpItem.disallowAppOp(pi, mAppOpsManager)
            else appOpItem.allowAppOp(pi, mAppOpsManager)
            mExecutor.submit {
                synchronized(mBlockerLocker) {
                    waitForBlockerOrExit()
                    mBlocker!!.setMutable()
                    mBlocker!!.setAppOp(appOpItem.op, appOpItem.mode)
                    mBlocker!!.commit()
                    mBlocker!!.setReadOnly()
                    (mBlockerLocker as Object).notifyAll()
                }
            }
            return true
        } catch (e: Exception) { return false }
    }

    fun setAppOpMode(appOpItem: AppDetailsAppOpItem, mode: Int): Boolean {
        if (isExternalApk) return false
        val pi = getPackageInfoInternal() ?: return false
        try {
            appOpItem.setAppOp(pi, mAppOpsManager, mode)
            mExecutor.submit {
                synchronized(mBlockerLocker) {
                    waitForBlockerOrExit()
                    mBlocker!!.setMutable()
                    mBlocker!!.setAppOp(appOpItem.op, appOpItem.mode)
                    mBlocker!!.commit()
                    mBlocker!!.setReadOnly()
                    (mBlockerLocker as Object).notifyAll()
                }
            }
            return true
        } catch (e: Exception) { return false }
    }

    fun resetAppOps(): Boolean {
        if (isExternalApk || mPackageName == null) return false
        return try {
            mAppOpsManager.resetAllModes(mUserId, mPackageName!!)
            mExecutor.submit { loadAppOps() }
            mExecutor.submit {
                synchronized(mBlockerLocker) {
                    waitForBlockerOrExit()
                    val entries = mBlocker!!.getAll(AppOpRule::class.java)
                    mBlocker!!.setMutable()
                    for (entry in entries) mBlocker!!.removeEntry(entry)
                    mBlocker!!.commit()
                    mBlocker!!.setReadOnly()
                    (mBlockerLocker as Object).notifyAll()
                }
            }
            true
        } catch (e: Exception) { false }
    }

    fun ignoreDangerousAppOps(): Boolean {
        if (isExternalApk) return false
        val pi = getPackageInfoInternal() ?: return false
        val ops = mutableListOf<Int>()
        var success = true
        synchronized(mAppOpItems) {
            for (item in mAppOpItems) {
                try {
                    val perm = AppOpsManagerCompat.opToPermission(item.op)
                    if (perm != null) {
                        val info = mPackageManager.getPermissionInfo(perm, PackageManager.GET_META_DATA)
                        if (PermissionInfoCompat.getProtection(info) == PermissionInfo.PROTECTION_DANGEROUS) {
                            PermUtils.setAppOpMode(mAppOpsManager, item.op, mPackageName!!, pi.applicationInfo.uid, AppOpsManager.MODE_IGNORED)
                            ops.add(item.op)
                            item.invalidate(mAppOpsManager, pi)
                        }
                    }
                } catch (ignore: Exception) { success = false }
            }
        }
        mExecutor.submit {
            synchronized(mBlockerLocker) {
                waitForBlockerOrExit()
                mBlocker!!.setMutable()
                for (op in ops) mBlocker!!.setAppOp(op, AppOpsManager.MODE_IGNORED)
                mBlocker!!.commit()
                mBlocker!!.setReadOnly()
                (mBlockerLocker as Object).notifyAll()
            }
        }
        return success
    }

    fun applyRulesToggle() {
        if (isExternalApk) return
        mExecutor.submit {
            synchronized(mBlockerLocker) {
                waitForBlockerOrExit()
                val old = mBlocker!!.isRulesApplied
                mBlocker!!.setMutable()
                mBlocker!!.applyRules(!old)
                mBlocker!!.commit()
                mBlocker!!.setReadOnly()
                reloadComponents()
                setRuleApplicationStatusInternal()
                (mBlockerLocker as Object).notifyAll()
            }
        }
    }

    fun observeActivities(): LiveData<List<AppDetailsActivityItem>> = mActivities as LiveData<List<AppDetailsActivityItem>>
    fun observeServices(): LiveData<List<AppDetailsServiceItem>> = mServices as LiveData<List<AppDetailsServiceItem>>
    fun observeReceivers(): LiveData<List<AppDetailsComponentItem>> = mReceivers as LiveData<List<AppDetailsComponentItem>>
    fun observeProviders(): LiveData<List<AppDetailsComponentItem>> = mProviders as LiveData<List<AppDetailsComponentItem>>
    fun observeAppOps(): LiveData<List<AppDetailsAppOpItem>> = mAppOps as LiveData<List<AppDetailsAppOpItem>>
    fun observeUsesPermissions(): LiveData<List<AppDetailsPermissionItem>> = mUsesPermissions as LiveData<List<AppDetailsPermissionItem>>
    fun observePermissions(): LiveData<List<AppDetailsDefinedPermissionItem>> = mPermissions as LiveData<List<AppDetailsDefinedPermissionItem>>
    fun observeFeatures(): LiveData<List<AppDetailsFeatureItem>> = mFeatures as LiveData<List<AppDetailsFeatureItem>>
    fun observeConfigurations(): LiveData<List<AppDetailsItem<ConfigurationInfo>>> = mConfigurations as LiveData<List<AppDetailsItem<ConfigurationInfo>>>
    fun observeSignatures(): LiveData<List<AppDetailsItem<X509Certificate>>> = mSignatures as LiveData<List<AppDetailsItem<X509Certificate>>>
    fun observeSharedLibraries(): LiveData<List<AppDetailsLibraryItem<*>>> = mSharedLibraries as LiveData<List<AppDetailsLibraryItem<*>>>
    fun observeOverlays(): LiveData<List<AppDetailsOverlayItem>> = mOverlays as LiveData<List<AppDetailsOverlayItem>>
    fun observeAppInfo(): LiveData<List<AppDetailsItem<PackageInfo>>> = mAppInfo as LiveData<List<AppDetailsItem<PackageInfo>>>

    val isPackageExistLiveData: LiveData<Boolean> get() = mIsPackageExistLiveData
    val isPackageExist: Boolean get() = mIsPackageExist
    fun isPackageChanged(): LiveData<Boolean> = mPackageChanged

    fun triggerPackageChange() { mExecutor.submit { setPackageChangedInternal() } }

    fun setPackageChangedInternal() {
        setPackageInfoInternal(true)
        if (isExternalApk || mExecutor.isShutdown) return
        mExecutor.submit {
            synchronized(mBlockerLocker) {
                waitForBlockerOrExit()
                mBlocker!!.reloadComponents()
                (mBlockerLocker as Object).notifyAll()
            }
        }
    }

    private fun reloadComponents() {
        mExecutor.submit {
            mReceiver?.pauseWatcher()
            loadActivities()
            loadServices()
            loadReceivers()
            loadProviders()
            loadOverlays()
            mReceiver?.resumeWatcher()
        }
    }

    @SuppressLint("WrongConstant")
    private fun setPackageInfoInternal(reload: Boolean) {
        val pkgName = mPackageName ?: return
        synchronized(mBlockerLocker) { waitForBlockerOrExit() }
        if (!reload && mPackageInfo != null) return
        try {
            mInstalledPackageInfo = try {
                val pi = PackageManagerCompat.getPackageInfo(pkgName, PackageManager.GET_META_DATA
                        or PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or MATCH_DISABLED_COMPONENTS
                        or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or MATCH_UNINSTALLED_PACKAGES
                        or PackageManager.GET_SERVICES or PackageManager.GET_CONFIGURATIONS or GET_SIGNING_CERTIFICATES
                        or PackageManager.GET_SHARED_LIBRARY_FILES or PackageManager.GET_URI_PERMISSION_PATTERNS
                        or PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, mUserId)
                if (!ApplicationInfoCompat.isInstalled(pi.applicationInfo)) throw Exception()
                pi
            } catch (e: Exception) { null }
            if (isExternalApk) {
                mPackageInfo = mPackageManager.getPackageArchiveInfo(mApkPath!!, PackageManager.GET_PERMISSIONS
                        or PackageManager.GET_ACTIVITIES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
                        or PackageManager.GET_SERVICES or MATCH_DISABLED_COMPONENTS or PackageManager.GET_CONFIGURATIONS
                        or PackageManager.GET_SHARED_LIBRARY_FILES or PackageManager.GET_URI_PERMISSION_PATTERNS
                        or PackageManager.GET_META_DATA) ?: throw PackageManager.NameNotFoundException()
                mPackageInfo!!.applicationInfo.sourceDir = mApkPath
                mPackageInfo!!.applicationInfo.publicSourceDir = mApkPath
            } else {
                mPackageInfo = mInstalledPackageInfo ?: throw PackageManager.NameNotFoundException()
            }
            mIsPackageExist = true
            mIsPackageExistLiveData.postValue(true)
        } catch (e: Exception) {
            mIsPackageExist = false
            mIsPackageExistLiveData.postValue(false)
        } finally {
            mPackageChanged.postValue(true)
        }
    }

    private fun getPackageInfoInternal(): PackageInfo? {
        try { mPackageInfoWatcher.await() } catch (e: InterruptedException) { return null }
        return mPackageInfo
    }

    private val mAppInfo = MutableLiveData<List<AppDetailsItem<PackageInfo>>>()
    private fun loadAppInfo() {
        val pi = getPackageInfoInternal() ?: run { mAppInfo.postValue(null); return }
        mAppInfo.postValue(listOf(AppDetailsItem(pi, "").apply { name = pi.packageName }))
    }

    private val mActivities = MutableLiveData<List<AppDetailsActivityItem>>()
    private val mActivityItems = mutableListOf<AppDetailsActivityItem>()
    private fun loadActivities() {
        synchronized(mActivityItems) {
            mActivityItems.clear()
            val pi = getPackageInfoInternal() ?: run { mActivities.postValue(emptyList()); return }
            if (pi.activities == null) { mActivities.postValue(emptyList()); return }
            val appLabel = pi.applicationInfo.loadLabel(mPackageManager)
            val canStartAny = SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.START_ANY_ACTIVITY)
            val canStartAssist = UserHandleHidden.myUserId() == mUserId && SelfPermissions.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
            for (info in pi.activities) {
                val item = AppDetailsActivityItem(info).apply {
                    label = getComponentLabel(info, appLabel)
                    if (!isExternalApk) synchronized(mBlockerLocker) { setRule(mBlocker?.getComponent(info.name)) }
                    isTracker = ComponentUtils.isTracker(info.name)
                    isDisabled = isComponentDisabled(info)
                    canLaunch = !isExternalApk && (canStartAny || info.exported) && !isDisabled && !isBlocked
                    canLaunchAssist = !isExternalApk && canStartAssist && !isDisabled && !isBlocked
                }
                mActivityItems.add(item)
            }
            mActivities.postValue(filterAndSortComponents(mActivityItems) as List<AppDetailsActivityItem>)
        }
    }

    private val mServices = MutableLiveData<List<AppDetailsServiceItem>>()
    private val mServiceItems = mutableListOf<AppDetailsServiceItem>()
    private fun loadServices() {
        synchronized(mServiceItems) {
            mServiceItems.clear()
            val pi = getPackageInfoInternal() ?: run { mServices.postValue(emptyList()); return }
            if (pi.services == null) { mServices.postValue(emptyList()); return }
            val running = ActivityManagerCompat.getRunningServices(mPackageName, mUserId)
            val appLabel = pi.applicationInfo.loadLabel(mPackageManager)
            for (info in pi.services) {
                val item = AppDetailsServiceItem(info).apply {
                    label = getComponentLabel(info, appLabel)
                    if (!isExternalApk) synchronized(mBlockerLocker) { setRule(mBlocker?.getComponent(info.name)) }
                    isTracker = ComponentUtils.isTracker(info.name)
                    isDisabled = isComponentDisabled(info)
                    runningServiceInfo = running.find { it.service.className == info.name }
                    canLaunch = !isExternalApk && canLaunchService(info) && !isDisabled && !isBlocked
                }
                mServiceItems.add(item)
            }
            mServices.postValue(filterAndSortComponents(mServiceItems) as List<AppDetailsServiceItem>)
        }
    }

    private val mReceivers = MutableLiveData<List<AppDetailsComponentItem>>()
    private val mReceiverItems = mutableListOf<AppDetailsComponentItem>()
    private fun loadReceivers() {
        synchronized(mReceiverItems) {
            mReceiverItems.clear()
            val pi = getPackageInfoInternal() ?: run { mReceivers.postValue(emptyList()); return }
            if (pi.receivers == null) { mReceivers.postValue(emptyList()); return }
            val appLabel = pi.applicationInfo.loadLabel(mPackageManager)
            for (info in pi.receivers) {
                val item = AppDetailsComponentItem(info).apply {
                    label = getComponentLabel(info, appLabel)
                    if (!isExternalApk) synchronized(mBlockerLocker) { setRule(mBlocker?.getComponent(info.name)) }
                    isTracker = ComponentUtils.isTracker(info.name)
                    isDisabled = isComponentDisabled(info)
                }
                mReceiverItems.add(item)
            }
            mReceivers.postValue(filterAndSortComponents(mReceiverItems) as List<AppDetailsComponentItem>)
        }
    }

    private val mProviders = MutableLiveData<List<AppDetailsComponentItem>>()
    private val mProviderItems = mutableListOf<AppDetailsComponentItem>()
    private fun loadProviders() {
        synchronized(mProviderItems) {
            mProviderItems.clear()
            val pi = getPackageInfoInternal() ?: run { mProviders.postValue(emptyList()); return }
            if (pi.providers == null) { mProviders.postValue(emptyList()); return }
            val appLabel = pi.applicationInfo.loadLabel(mPackageManager)
            for (info in pi.providers) {
                val item = AppDetailsComponentItem(info).apply {
                    label = getComponentLabel(info, appLabel)
                    if (!isExternalApk) synchronized(mBlockerLocker) { setRule(mBlocker?.getComponent(info.name)) }
                    isTracker = ComponentUtils.isTracker(info.name)
                    isDisabled = isComponentDisabled(info)
                }
                mProviderItems.add(item)
            }
            mProviders.postValue(filterAndSortComponents(mProviderItems) as List<AppDetailsComponentItem>)
        }
    }

    private fun getComponentLabel(info: ComponentInfo, appLabel: CharSequence): CharSequence {
        val label = info.loadLabel(mPackageManager)
        if (label == info.name || label == appLabel) return Utils.camelCaseToSpaceSeparatedString(Utils.getLastComponent(info.name))
        return label
    }

    private fun sortComponents(items: MutableList<out AppDetailsItem<ComponentInfo>>) {
        items.sortWith { o1, o2 -> o1.name.compareTo(o2.name, ignoreCase = true) }
        if (mSortOrderComponents == AppDetailsFragment.SORT_BY_NAME) return
        items.sortWith { o1, o2 ->
            when (mSortOrderComponents) {
                AppDetailsFragment.SORT_BY_BLOCKED -> -(o1 as AppDetailsComponentItem).isBlocked.compareTo((o2 as AppDetailsComponentItem).isBlocked)
                AppDetailsFragment.SORT_BY_TRACKERS -> -(o1 as AppDetailsComponentItem).isTracker.compareTo((o2 as AppDetailsComponentItem).isTracker)
                else -> 0
            }
        }
    }

    fun isComponentDisabled(info: ComponentInfo): Boolean {
        if (mInstalledPackageInfo?.applicationInfo?.let { FreezeUtils.isFrozen(it) } == true) return true
        val cn = ComponentName(info.packageName, info.name)
        try {
            val setting = PackageManagerCompat.getComponentEnabledSetting(cn, mUserId)
            if (setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED || setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED || setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER) return true
            if (setting == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return false
        } catch (ignore: Throwable) {}
        return !info.isEnabled
    }

    private fun canLaunchService(info: ServiceInfo): Boolean {
        if (info.exported && info.permission == null) return true
        val uid = Users.getSelfOrRemoteUid()
        if (uid == Ops.ROOT_UID || (uid == Ops.SYSTEM_UID && info.permission == null)) return true
        return info.permission != null && SelfPermissions.checkSelfOrRemotePermission(info.permission, uid)
    }

    private val mAppOps = MutableLiveData<List<AppDetailsAppOpItem>>()
    private val mAppOpItems = mutableListOf<AppDetailsAppOpItem>()
    fun setAppOp(item: AppDetailsAppOpItem) {
        synchronized(mAppOpItems) {
            val i = mAppOpItems.indexOfFirst { it.name == item.name }
            if (i != -1) mAppOpItems[i] = item
        }
    }

    private fun loadAppOps() {
        val pi = getPackageInfoInternal()
        if (pi == null || isExternalApk || !SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.GET_APP_OPS_STATS)) {
            mAppOps.postValue(emptyList()); return
        }
        val canGetFlags = SelfPermissions.checkGetGrantRevokeRuntimePermissions()
        synchronized(mAppOpItems) {
            mAppOpItems.clear()
            try {
                val uid = pi.applicationInfo.uid
                val pkg = pi.packageName
                val map = mutableMapOf<Int, AppOpsManagerCompat.OpEntry>()
                AppOpsManagerCompat.getConfiguredOpsForPackage(mAppOpsManager, pkg, uid).forEach { if (map[it.op] == null) map[it.op] = it }
                val perms = getRawPermissions()
                val other = mutableSetOf<Int>()
                perms.forEach { p ->
                    val op = AppOpsManagerCompat.permissionToOpCode(p)
                    if (op != AppOpsManagerCompat.OP_NONE && op < AppOpsManagerCompat._NUM_OP && map[op] == null) other.add(op)
                }
                if (Prefs.AppDetailsPage.displayDefaultAppOps()) {
                    AppOpsManagerCompat.getOpsWithoutPermissions().forEach { if (it != AppOpsManagerCompat.OP_NONE && it < AppOpsManagerCompat._NUM_OP && map[it] == null) other.add(it) }
                }
                map.values.forEach { entry ->
                    val opPerm = AppOpsManagerCompat.opToPermission(entry.op)
                    mAppOpItems.add(if (opPerm != null) {
                        val granted = PermissionCompat.checkPermission(opPerm, pkg, mUserId) == PackageManager.PERMISSION_GRANTED
                        val flags = if (canGetFlags) PermissionCompat.getPermissionFlags(opPerm, pkg, mUserId) else PermissionCompat.FLAG_PERMISSION_NONE
                        val info = PermissionCompat.getPermissionInfo(opPerm, pkg, 0) ?: PermissionInfo().apply { name = opPerm }
                        AppDetailsAppOpItem(entry, info, granted, flags, perms.contains(opPerm))
                    } else AppDetailsAppOpItem(entry))
                }
                other.forEach { op ->
                    val opPerm = AppOpsManagerCompat.opToPermission(op)
                    mAppOpItems.add(if (opPerm != null) {
                        val granted = PermissionCompat.checkPermission(opPerm, pkg, mUserId) == PackageManager.PERMISSION_GRANTED
                        val flags = if (canGetFlags) PermissionCompat.getPermissionFlags(opPerm, pkg, mUserId) else PermissionCompat.FLAG_PERMISSION_NONE
                        val info = PermissionCompat.getPermissionInfo(opPerm, pkg, 0) ?: PermissionInfo().apply { name = opPerm }
                        AppDetailsAppOpItem(op, info, granted, flags, perms.contains(opPerm))
                    } else AppDetailsAppOpItem(op))
                }
            } catch (ignore: Throwable) {}
        }
        filterAndSortItemsInternal(AppDetailsFragment.APP_OPS)
    }

    private val mUsesPermissions = MutableLiveData<List<AppDetailsPermissionItem>>()
    private val mUsesPermissionItems = mutableListOf<AppDetailsPermissionItem>()
    fun setUsesPermission(item: AppDetailsPermissionItem) {
        synchronized(mUsesPermissionItems) {
            val i = mUsesPermissionItems.indexOfFirst { it.name == item.name }
            if (i != -1) mUsesPermissionItems[i] = item
        }
    }

    private fun loadUsesPermissions() {
        synchronized(mUsesPermissionItems) {
            mUsesPermissionItems.clear()
            val pi = getPackageInfoInternal() ?: run { mUsesPermissions.postValue(emptyList()); return }
            if (pi.requestedPermissions == null) { mUsesPermissions.postValue(emptyList()); return }
            val ops = ExUtils.requireNonNullElse({ AppOpsManagerCompat.getConfiguredOpsForPackage(mAppOpsManager, pi.packageName, pi.applicationInfo.uid) }, emptyList())
            for (i in pi.requestedPermissions.indices) {
                getPermissionItem(pi.requestedPermissions[i], (pi.requestedPermissionsFlags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0, ops)?.let { mUsesPermissionItems.add(it) }
            }
        }
        filterAndSortItemsInternal(AppDetailsFragment.USES_PERMISSIONS)
    }

    fun getRawPermissions(): List<String> = getPackageInfoInternal()?.requestedPermissions?.toList() ?: emptyList()

    private fun getPermissionItem(name: String, granted: Boolean, ops: List<AppOpsManagerCompat.OpEntry>): AppDetailsPermissionItem? {
        val pi = getPackageInfoInternal() ?: return null
        return try {
            val info = PermissionCompat.getPermissionInfo(name, pi.packageName, PackageManager.GET_META_DATA) ?: PermissionInfo().apply { this.name = name }
            val flags = info.flags
            val op = AppOpsManagerCompat.permissionToOpCode(name)
            val permFlags = if (!isExternalApk && SelfPermissions.checkGetGrantRevokeRuntimePermissions()) PermissionCompat.getPermissionFlags(name, pi.packageName, mUserId) else PermissionCompat.FLAG_PERMISSION_NONE
            var opAllowed = false
            if (!isExternalApk && op != AppOpsManagerCompat.OP_NONE) {
                val mode = AppOpsManagerCompat.getModeFromOpEntriesOrDefault(op, ops)
                opAllowed = mode == AppOpsManager.MODE_ALLOWED || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mode == AppOpsManager.MODE_FOREGROUND)
            }
            val prot = PermissionInfoCompat.getProtection(info)
            val protFlags = PermissionInfoCompat.getProtectionFlags(info)
            val perm = when {
                prot == PermissionInfo.PROTECTION_DANGEROUS && PermUtils.systemSupportsRuntimePermissions() -> RuntimePermission(name, granted, op, opAllowed, permFlags)
                (protFlags and PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) != 0 -> DevelopmentPermission(name, granted, op, opAllowed, permFlags)
                else -> ReadOnlyPermission(name, granted, op, opAllowed, permFlags)
            }
            AppDetailsPermissionItem(info, perm, flags).apply { this.name = name }
        } catch (ignore: Throwable) { null }
    }

    private val mPermissions = MutableLiveData<List<AppDetailsDefinedPermissionItem>>()
    private val mPermissionItems = mutableListOf<AppDetailsDefinedPermissionItem>()
    private fun loadPermissions() {
        synchronized(mPermissionItems) {
            mPermissionItems.clear()
            val pi = getPackageInfoInternal() ?: run { mPermissions.postValue(emptyList()); return }
            val visited = mutableSetOf<String>()
            pi.permissions?.forEach { mPermissionItems.add(AppDetailsDefinedPermissionItem(it, false)); visited.add(it.name) }
            fun addFromComp(comps: Array<out ComponentInfo>?) = comps?.forEach { if (it.permission != null && !visited.contains(it.permission)) {
                try {
                    val info = PermissionCompat.getPermissionInfo(it.permission!!, pi.packageName, PackageManager.GET_META_DATA) ?: PermissionInfo().apply { name = it.permission }
                    mPermissionItems.add(AppDetailsDefinedPermissionItem(info, true))
                    visited.add(info.name)
                } catch (ignore: Exception) {}
            } }
            addFromComp(pi.activities)
            addFromComp(pi.services)
            addFromComp(pi.receivers)
            pi.providers?.forEach {
                listOfNotNull(it.readPermission, it.writePermission).forEach { p ->
                    if (!visited.contains(p)) {
                        try {
                            val info = PermissionCompat.getPermissionInfo(p, pi.packageName, PackageManager.GET_META_DATA) ?: PermissionInfo().apply { name = p }
                            mPermissionItems.add(AppDetailsDefinedPermissionItem(info, true))
                            visited.add(info.name)
                        } catch (ignore: Exception) {}
                    }
                }
            }
            mPermissions.postValue(filterAndSortPermissions(mPermissionItems) as List<AppDetailsDefinedPermissionItem>)
        }
    }

    private val mFeatures = MutableLiveData<List<AppDetailsFeatureItem>>()
    private fun loadFeatures() {
        val pi = getPackageInfoInternal() ?: run { mFeatures.postValue(emptyList()); return }
        if (pi.reqFeatures == null) { mFeatures.postValue(emptyList()); return }
        val items = pi.reqFeatures!!.map { fi ->
            val name = fi.name ?: AppDetailsFeatureItem.OPEN_GL_ES
            val avail = if (fi.name == null) {
                val am = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                fi.reqGlEsVersion <= am.deviceConfigurationInfo.reqGlEsVersion
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) mPackageManager.hasSystemFeature(fi.name, fi.version)
            else mPackageManager.hasSystemFeature(fi.name)
            AppDetailsFeatureItem(fi, avail).apply { this.name = name }
        }.sortedBy { it.name.lowercase(Locale.ROOT) }
        mFeatures.postValue(items)
    }

    private val mConfigurations = MutableLiveData<List<AppDetailsItem<ConfigurationInfo>>>()
    private fun loadConfigurations() {
        val pi = getPackageInfoInternal() ?: run { mConfigurations.postValue(emptyList()); return }
        mConfigurations.postValue(pi.configPreferences?.map { AppDetailsItem(it, "") } ?: emptyList())
    }

    private val mSignatures = MutableLiveData<List<AppDetailsItem<X509Certificate>>>()
    private var mApkVerifierResult: ApkVerifier.Result? = null
    fun getApkVerifierResult(): ApkVerifier.Result? = mApkVerifierResult
    private fun loadSignatures() {
        val pi = getPackageInfoInternal() ?: run { mSignatures.postValue(emptyList()); return }
        val af = mApkFile ?: run { mSignatures.postValue(emptyList()); return }
        val items = mutableListOf<AppDetailsItem<X509Certificate>>()
        try {
            val builder = ApkVerifier.Builder(af.baseEntry.getFile(false)).setMaxCheckedPlatformVersion(Build.VERSION.SDK_INT)
            af.idsigFile?.let { builder.setV4SignatureFile(it) }
            mApkVerifierResult = builder.build().verify()
            val si = SignerInfo(mApkVerifierResult!!)
            si.currentSignerCerts?.forEach { items.add(AppDetailsItem(it, "").apply { name = "Signer Certificate" }) }
                ?: items.add(AppDetailsItem(null, ""))
            if (mApkVerifierResult!!.isSourceStampVerified) si.sourceStampCert?.let { items.add(AppDetailsItem(it, "").apply { name = "SourceStamp Certificate" }) }
            si.signerCertsInLineage?.forEach { items.add(AppDetailsItem(it, "").apply { name = "Certificate for Lineage" }) }
        } catch (ignore: Exception) {}
        mSignatures.postValue(items)
    }

    private val mSharedLibraries = MutableLiveData<List<AppDetailsLibraryItem<*>>>()
    private fun loadSharedLibraries() {
        val pi = getPackageInfoInternal() ?: run { mSharedLibraries.postValue(emptyList()); return }
        val af = mApkFile ?: run { mSharedLibraries.postValue(emptyList()); return }
        val items = mutableListOf<AppDetailsLibraryItem<*>>()
        pi.applicationInfo.sharedLibraryFiles?.forEach { path ->
            val file = File(path)
            var item: AppDetailsLibraryItem<*>? = null
            if (file.exists() && file.name.endsWith(".apk")) {
                mPackageManager.getPackageArchiveInfo(path, 0)?.let {
                    item = AppDetailsLibraryItem(it).apply {
                        name = it.applicationInfo.loadLabel(mPackageManager).toString()
                        type = "APK"
                    }
                }
            }
            if (item == null) {
                item = AppDetailsLibraryItem(file).apply {
                    name = file.name
                    type = if (path.endsWith(".so")) "SO" else "JAR"
                }
            }
            item!!.path = file
            item!!.size = file.length()
            items.add(item!!)
        }
        af.entries.forEach { entry ->
            if (entry.type in listOf(ApkFile.APK_BASE, ApkFile.APK_SPLIT_FEATURE, ApkFile.APK_SPLIT_ABI, ApkFile.APK_SPLIT_UNKNOWN)) {
                try {
                    val nl = try { entry.getInputStream(false).use { NativeLibraries(it) } } catch (e: IOException) { NativeLibraries(entry.getFile(false)) }
                    nl.libs.forEach { lib ->
                        val libItem = AppDetailsLibraryItem(lib).apply {
                            name = lib.name
                            type = if (lib is NativeLibraries.ElfLib) {
                                when (lib.type) {
                                    NativeLibraries.ElfLib.TYPE_DYN -> "SHARED"
                                    NativeLibraries.ElfLib.TYPE_EXEC -> "EXEC"
                                    else -> "SO"
                                }
                            } else "⚠️"
                        }
                        items.add(libItem)
                    }
                } catch (ignore: Throwable) {}
            }
        }
        items.sortBy { it.name.lowercase(Locale.ROOT) }
        mSharedLibraries.postValue(items)
    }

    private val mOverlays = MutableLiveData<List<AppDetailsOverlayItem>>()
    private fun loadOverlays() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || mPackageName == null || isExternalApk || !SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.CHANGE_OVERLAY_PACKAGES)) {
            mOverlays.postValue(emptyList()); return
        }
        val overlays = ExUtils.requireNonNullElse({ OverlayManagerCompact.getOverlayManager().getOverlayInfosForTarget(mPackageName!!, mUserId) }, emptyList())
        mOverlays.postValue(overlays.map { AppDetailsOverlayItem(it) })
    }

    class PackageIntentReceiver(private val mModel: AppDetailsViewModel) : PackageChangeReceiver(mModel.getApplication()) {
        private var mPauseWatcher = false
        private var mChangeCount = 0

        fun resumeWatcher() {
            if (mChangeCount > 0) {
                mChangeCount = 0
                mModel.setPackageChangedInternal()
            }
            mPauseWatcher = false
        }

        fun pauseWatcher() {
            mChangeCount = 0
            mPauseWatcher = true
        }

        override fun onPackageChanged(intent: Intent, uid: Int?, packages: Array<out String>?) {
            var changed = false
            if (uid != null) {
                if (mModel.mPackageInfo?.applicationInfo?.uid == uid) changed = true
            } else if (packages != null) {
                if (packages.contains(mModel.mPackageName)) changed = true
            }
            if (changed) {
                if (mPauseWatcher) mChangeCount++
                else mModel.setPackageChangedInternal()
            }
        }
    }

    companion object {
        const val RULE_APPLIED = 0
        const val RULE_NOT_APPLIED = 1
        const val RULE_NO_RULE = 2
    }
}
