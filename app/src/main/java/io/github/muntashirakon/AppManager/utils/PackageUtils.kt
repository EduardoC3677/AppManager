// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import android.annotation.SuppressLint
import android.annotation.UserIdInt
import android.app.usage.IStorageStatsManager
import android.app.usage.StorageStats
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ComponentInfo
import android.content.pm.IPackageManager
import android.content.pm.IPackageStatsObserver
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.content.pm.SigningInfo
import android.os.Build
import android.os.PowerManager
import android.os.RemoteException
import android.os.UserHandleHidden
import android.os.storage.StorageManagerHidden
import android.system.ErrnoException
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.util.Pair
import androidx.annotation.RequiresPermission
import androidx.annotation.WorkerThread
import androidx.core.content.pm.PackageInfoCompat
import aosp.libcore.util.EmptyArray
import aosp.libcore.util.HexEncoding
import com.android.apksig.ApkVerifier
import com.android.apksig.apk.ApkFormatException
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.apk.signing.Signer
import io.github.muntashirakon.AppManager.apk.signing.SignerInfo
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_DISABLED_COMPONENTS
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES
import io.github.muntashirakon.AppManager.db.entity.App
import io.github.muntashirakon.AppManager.db.entity.Backup
import io.github.muntashirakon.AppManager.db.utils.AppDb
import io.github.muntashirakon.AppManager.ipc.ProxyBinder
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.main.ApplicationItem
import io.github.muntashirakon.AppManager.misc.OidMap
import io.github.muntashirakon.AppManager.rules.RuleType
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils
import io.github.muntashirakon.AppManager.runner.Runner
import io.github.muntashirakon.AppManager.runner.RunnerUtils
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.types.PackageSizeInfo
import io.github.muntashirakon.AppManager.types.UserPackagePair
import io.github.muntashirakon.AppManager.users.Users
import io.github.muntashirakon.AppManager.utils.UIUtils.getBoldString
import io.github.muntashirakon.AppManager.utils.UIUtils.getColoredText
import io.github.muntashirakon.AppManager.utils.UIUtils.getMonospacedText
import io.github.muntashirakon.AppManager.utils.UIUtils.getPrimaryText
import io.github.muntashirakon.AppManager.utils.UIUtils.getStyledKeyValue
import io.github.muntashirakon.AppManager.utils.UIUtils.getTitleText
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes
import io.github.muntashirakon.io.ExtendedFile
import io.github.muntashirakon.io.Paths
import org.jetbrains.annotations.Contract
import java.io.File
import java.io.IOException
import java.security.NoSuchAlgorithmException
import java.security.PublicKey
import java.security.cert.CertificateEncodingException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object PackageUtils {
    @JvmField
    val TAG: String = PackageUtils::class.java.simpleName

    @JvmField
    val PACKAGE_STAGING_DIRECTORY = File("/data/local/tmp")

    @JvmStatic
    fun getUserPackagePairs(applicationItems: List<ApplicationItem>): ArrayList<UserPackagePair> {
        val userPackagePairList = ArrayList<UserPackagePair>()
        val currentUser = UserHandleHidden.myUserId()
        for (item in applicationItems) {
            if (item.userIds.isNotEmpty()) {
                for (userId in item.userIds) {
                    userPackagePairList.add(UserPackagePair(item.packageName, userId))
                }
            } else {
                userPackagePairList.add(UserPackagePair(item.packageName, currentUser))
            }
        }
        return userPackagePairList
    }

    /**
     * List all applications stored in App Manager database as well as from the system.
     *
     * @param loadInBackground Retrieve applications from the system using the given thread instead of the current thread.
     * @param loadBackups      Load/List backup metadata
     * @return List of applications, which could be the cached version if the executor parameter is `null`.
     */
    @WorkerThread
    @JvmStatic
    fun getInstalledOrBackedUpApplicationsFromDb(
        context: Context,
        loadInBackground: Boolean,
        loadBackups: Boolean
    ): List<ApplicationItem> {
        val applicationItems = HashMap<String, ApplicationItem>()
        val appDb = AppDb()
        var apps = appDb.allApplications
        var loadInBackgroundVar = loadInBackground
        if (loadInBackgroundVar && apps.isEmpty()) {
            // Force-load in foreground
            loadInBackgroundVar = false
        }
        if (!loadInBackgroundVar) {
            val wakeLock = CpuUtils.getPartialWakeLock("appDbUpdater")
            try {
                wakeLock.acquire()
                // Load app list for the first time
                Log.d(TAG, "Loading apps for the first time.")
                appDb.loadInstalledOrBackedUpApplications(context)
                apps = appDb.allApplications
            } finally {
                CpuUtils.releaseWakeLock(wakeLock)
            }
        }
        val backups = appDb.getBackups(false)
        val thisUser = UserHandleHidden.myUserId()
        // Get application items from apps
        for (app in apps) {
            val item: ApplicationItem
            val oldItem = applicationItems[app.packageName]
            if (app.isInstalled != 0) {
                val newItem = oldItem == null || !oldItem.isInstalled
                if (oldItem != null) {
                    // Item already exists
                    item = oldItem
                } else {
                    // Item doesn't exist
                    item = ApplicationItem()
                    applicationItems[app.packageName] = item
                    item.packageName = app.packageName
                    // OPTIMIZATION: Pre-compute lowercase for search performance
                    item.packageNameLowerCase = item.packageName.lowercase(Locale.ROOT)
                }
                item.userIds = ArrayUtils.appendInt(item.userIds, app.userId)
                item.isInstalled = true
                item.isOnlyDataInstalled = false
                item.openCount += app.openCount
                item.screenTime += app.screenTime
                if (item.lastUsageTime == 0L || item.lastUsageTime < app.lastUsageTime) {
                    item.lastUsageTime = app.lastUsageTime
                }
                item.hasKeystore = item.hasKeystore || (app.hasKeystore != 0)
                item.usesSaf = item.usesSaf || (app.usesSaf != 0)
                if (app.ssaid != null) {
                    item.ssaid = app.ssaid
                }
                item.totalSize += app.codeSize + app.dataSize
                item.dataUsage += app.wifiDataUsage + app.mobileDataUsage
                if (!newItem && app.userId != thisUser) {
                    // This user has the highest priority
                    continue
                }
            } else {
                // App not installed but may be installed in other profiles
                if (oldItem != null) {
                    // Item exists, use the previous status
                    continue
                } else {
                    // Item doesn't exist, don't add user handle
                    item = ApplicationItem()
                    item.packageName = app.packageName
                    // OPTIMIZATION: Pre-compute lowercase for search performance
                    item.packageNameLowerCase = item.packageName.lowercase(Locale.ROOT)
                    applicationItems[app.packageName] = item
                    item.isInstalled = false
                    item.isOnlyDataInstalled = app.isOnlyDataInstalled != 0
                    item.hasKeystore = item.hasKeystore || (app.hasKeystore != 0)
                }
            }
            item.backup = backups.remove(item.packageName)
            item.flags = app.flags
            item.uid = app.uid
            item.debuggable = app.isDebuggable()
            item.isUser = !app.isSystemApp()
            item.isDisabled = app.isEnabled == 0
            item.label = app.packageLabel
            // OPTIMIZATION: Pre-compute lowercase for search performance
            item.labelLowerCase = item.label?.lowercase(Locale.ROOT) ?: ""
            item.targetSdk = app.sdk
            item.versionName = app.versionName
            item.versionCode = app.versionCode
            item.sharedUserId = app.sharedUserId
            item.sha = Pair(app.certName, app.certAlgo)
            item.firstInstallTime = app.firstInstallTime
            item.lastUpdateTime = app.lastUpdateTime
            item.hasActivities = app.hasActivities != 0
            item.hasSplits = app.hasSplits != 0
            item.blockedCount = app.rulesCount
            item.trackerCount = app.trackerCount
            item.lastActionTime = app.lastActionTime
            item.generateOtherInfo()
        }
        // Add rest of the backups
        for (packageName in backups.keys) {
            val backup = backups[packageName] ?: continue
            val item = ApplicationItem()
            item.packageName = backup.packageName
            // OPTIMIZATION: Pre-compute lowercase for search performance
            item.packageNameLowerCase = item.packageName.lowercase(Locale.ROOT)
            applicationItems[backup.packageName] = item
            item.backup = backup
            item.versionName = backup.versionName
            item.versionCode = backup.versionCode
            item.label = backup.label
            // OPTIMIZATION: Pre-compute lowercase for search performance
            item.labelLowerCase = item.label?.lowercase(Locale.ROOT) ?: ""
            item.firstInstallTime = backup.backupTime
            item.lastUpdateTime = backup.backupTime
            item.isUser = backup.isSystem == 0
            item.isDisabled = false
            item.isInstalled = false
            item.isOnlyDataInstalled = false
            item.hasSplits = backup.hasSplits != 0
            item.hasKeystore = backup.hasKeyStore != 0
            item.generateOtherInfo()
        }
        if (loadInBackgroundVar) {
            // Update list of apps safely in the background.
            // We need to do this here to avoid locks in AppDb
            ThreadUtils.postOnBackgroundThread {
                val wakeLock = CpuUtils.getPartialWakeLock("appDbUpdater")
                try {
                    wakeLock.acquire()
                    if (loadBackups) {
                        appDb.loadInstalledOrBackedUpApplications(context)
                    } else {
                        appDb.updateApplications(context)
                    }
                } finally {
                    CpuUtils.releaseWakeLock(wakeLock)
                }
            }
        }
        return ArrayList(applicationItems.values)
    }

    @JvmStatic
    fun getAllPackages(flags: Int): List<PackageInfo> {
        return getAllPackages(flags, false)
    }

    @JvmStatic
    fun getAllPackages(flags: Int, currentUserOnly: Boolean): List<PackageInfo> {
        if (currentUserOnly) {
            return PackageManagerCompat.getInstalledPackages(flags, UserHandleHidden.myUserId())
        }
        val packageInfoList = ArrayList<PackageInfo>()
        for (userId in Users.getUsersIds()) {
            if (!SelfPermissions.checkCrossUserPermission(userId, false)) {
                // No support for cross user
                continue
            }
            packageInfoList.addAll(PackageManagerCompat.getInstalledPackages(flags, userId))
            if (ThreadUtils.isInterrupted()) {
                break
            }
        }
        return packageInfoList
    }

    @JvmStatic
    fun getAllApplications(flags: Int): List<ApplicationInfo> {
        return getAllApplications(flags, false)
    }

    @JvmStatic
    fun getAllApplications(flags: Int, currentUserOnly: Boolean): List<ApplicationInfo> {
        if (currentUserOnly) {
            return ExUtils.requireNonNullElse(
                {
                    PackageManagerCompat.getInstalledApplications(
                        flags,
                        UserHandleHidden.myUserId()
                    )
                },
                emptyList()
            )
        }
        val applicationInfoList = ArrayList<ApplicationInfo>()
        for (userId in Users.getUsersIds()) {
            try {
                applicationInfoList.addAll(PackageManagerCompat.getInstalledApplications(flags, userId))
                if (ThreadUtils.isInterrupted()) {
                    break
                }
            } catch (ignore: RemoteException) {
            }
        }
        return applicationInfoList
    }

    @WorkerThread
    @RequiresPermission("android.permission.PACKAGE_USAGE_STATS")
    @JvmStatic
    fun getPackageSizeInfo(
        context: Context,
        packageName: String,
        @UserIdInt userHandle: Int,
        storageUuid: UUID?
    ): PackageSizeInfo? {
        val packageSizeInfo = AtomicReference<PackageSizeInfo?>()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            val waitForStats = CountDownLatch(1)
            try {
                val pm: IPackageManager = if (UserHandleHidden.myUserId() == userHandle) {
                    // Since GET_PACKAGE_SIZE is a normal permission, there's no need to use a privileged service
                    PackageManagerCompat.getUnprivilegedPackageManager()
                } else {
                    // May return SecurityException in the ADB mode
                    PackageManagerCompat.getPackageManager()
                }
                pm.getPackageSizeInfo(
                    packageName, userHandle,
                    object : IPackageStatsObserver.Stub() {
                        override fun onGetStatsCompleted(
                            pStats: android.content.pm.PackageStats,
                            succeeded: Boolean
                        ) {
                            try {
                                if (succeeded) {
                                    packageSizeInfo.set(PackageSizeInfo(pStats))
                                }
                            } finally {
                                waitForStats.countDown()
                            }
                        }
                    })
                waitForStats.await(5, TimeUnit.SECONDS)
            } catch (e: RemoteException) {
                Log.e(TAG, e)
            } catch (e: InterruptedException) {
                Log.e(TAG, e)
            } catch (e: SecurityException) {
                Log.e(TAG, e)
            }
        } else {
            try {
                val storageStatsManager = IStorageStatsManager.Stub.asInterface(
                    ProxyBinder.getService("storagestats")
                )
                val uuidString = storageUuid?.let { StorageManagerHidden.convert(it) }
                val storageStats = storageStatsManager.queryStatsForPackage(
                    uuidString, packageName,
                    userHandle, context.packageName
                )
                packageSizeInfo.set(PackageSizeInfo(packageName, storageStats, userHandle))
            } catch (e: Throwable) {
                Log.w(TAG, e)
            }
        }
        return packageSizeInfo.get()
    }

    @JvmStatic
    fun collectComponentClassNames(packageName: String, @UserIdInt userHandle: Int): HashMap<String, RuleType> {
        try {
            val packageInfo = PackageManagerCompat.getPackageInfo(
                packageName,
                PackageManager.GET_ACTIVITIES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
                        or MATCH_DISABLED_COMPONENTS or MATCH_UNINSTALLED_PACKAGES or PackageManager.GET_SERVICES
                        or PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES,
                userHandle
            )
            return collectComponentClassNames(packageInfo)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return HashMap()
    }

    @JvmStatic
    fun collectComponentClassNames(packageInfo: PackageInfo?): HashMap<String, RuleType> {
        val componentClasses = HashMap<String, RuleType>()
        if (packageInfo == null) return componentClasses
        // Add activities
        packageInfo.activities?.let { activities ->
            for (activityInfo in activities) {
                componentClasses[activityInfo.name] = RuleType.ACTIVITY
            }
        }
        // Add others
        packageInfo.services?.let { services ->
            for (componentInfo in services) {
                componentClasses[componentInfo.name] = RuleType.SERVICE
            }
        }
        packageInfo.receivers?.let { receivers ->
            for (componentInfo in receivers) {
                componentClasses[componentInfo.name] = RuleType.RECEIVER
            }
        }
        packageInfo.providers?.let { providers ->
            for (componentInfo in providers) {
                componentClasses[componentInfo.name] = RuleType.PROVIDER
            }
        }
        return componentClasses
    }

    @JvmStatic
    fun getFilteredComponents(
        packageName: String,
        @UserIdInt userHandle: Int,
        signatures: Array<String>
    ): HashMap<String, RuleType> {
        val filteredComponents = HashMap<String, RuleType>()
        val components = collectComponentClassNames(packageName, userHandle)
        for (componentName in components.keys) {
            for (signature in signatures) {
                if (componentName.startsWith(signature) || componentName.contains(signature)) {
                    components[componentName]?.let { filteredComponents[componentName] = it }
                }
            }
        }
        return filteredComponents
    }

    @JvmStatic
    fun getFilteredAppOps(
        packageName: String,
        @UserIdInt userHandle: Int,
        appOps: IntArray,
        mode: Int
    ): Collection<Int> {
        val filteredAppOps = ArrayList<Int>()
        val appOpsManager = AppOpsManagerCompat()
        val uid = getAppUid(UserPackagePair(packageName, userHandle))
        for (appOp in appOps) {
            try {
                if (appOpsManager.checkOperation(appOp, uid, packageName) != mode) {
                    filteredAppOps.add(appOp)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return filteredAppOps
    }

    @JvmStatic
    fun getUserDisabledComponentsForPackage(
        packageName: String,
        @UserIdInt userId: Int
    ): HashMap<String, RuleType> {
        val componentClasses = collectComponentClassNames(packageName, userId)
        val disabledComponents = HashMap<String, RuleType>()
        for (componentName in componentClasses.keys) {
            try {
                if (isComponentDisabledByUser(packageName, componentName, userId)) {
                    componentClasses[componentName]?.let { disabledComponents[componentName] = it }
                }
            } catch (ignore: NameNotFoundException) {
                // Component unavailable
            }
        }
        disabledComponents.putAll(ComponentUtils.getIFWRulesForPackage(packageName))
        return disabledComponents
    }

    @SuppressLint("SwitchIntDef")
    @JvmStatic
    @Throws(SecurityException::class, NameNotFoundException::class)
    fun isComponentDisabledByUser(
        packageName: String,
        componentClassName: String,
        @UserIdInt userId: Int
    ): Boolean {
        try {
            val componentName = ComponentName(packageName, componentClassName)
            return when (PackageManagerCompat.getComponentEnabledSetting(componentName, userId)) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER -> true
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> false
                else -> false
            }
        } catch (e: IllegalArgumentException) {
            throw NameNotFoundException(e.message).initCause(e) as NameNotFoundException
        }
    }

    @JvmStatic
    @Throws(NameNotFoundException::class, RemoteException::class)
    fun getPermissionsForPackage(packageName: String, @UserIdInt userId: Int): Array<String>? {
        val info = PackageManagerCompat.getPackageInfo(
            packageName, PackageManager.GET_PERMISSIONS
                    or PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId
        )
        return info.requestedPermissions
    }

    @JvmStatic
    fun getPackageLabel(pm: PackageManager, packageName: String): String {
        try {
            @SuppressLint("WrongConstant")
            val applicationInfo = pm.getApplicationInfo(packageName, MATCH_UNINSTALLED_PACKAGES)
            return pm.getApplicationLabel(applicationInfo).toString()
        } catch (ignore: NameNotFoundException) {
        }
        return packageName
    }

    @JvmStatic
    fun getPackageLabel(pm: PackageManager, packageName: String, userHandle: Int): CharSequence {
        try {
            val applicationInfo = PackageManagerCompat.getApplicationInfo(
                packageName,
                PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userHandle
            )
            return applicationInfo.loadLabel(pm)
        } catch (ignore: Exception) {
        }
        return packageName
    }

    @JvmStatic
    fun packagesToAppLabels(
        pm: PackageManager,
        packages: List<String>?,
        userHandles: List<Int>
    ): ArrayList<CharSequence>? {
        if (packages == null) return null
        val appLabels = ArrayList<CharSequence>()
        var i = 0
        for (packageName in packages) {
            appLabels.add(getPackageLabel(pm, packageName, userHandles[i]).toString())
            ++i
        }
        return appLabels
    }

    @JvmStatic
    fun getAppUid(pair: UserPackagePair): Int {
        return ExUtils.requireNonNullElse(
            {
                PackageManagerCompat.getApplicationInfo(
                    pair.packageName,
                    PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, pair.userId
                ).uid
            },
            -1
        )
    }

    @JvmStatic
    fun getSourceDir(applicationInfo: ApplicationInfo): String {
        val sourceDir = File(applicationInfo.publicSourceDir).parent // or applicationInfo.sourceDir
            ?: throw RuntimeException("Application source directory cannot be empty")
        return sourceDir
    }

    @JvmStatic
    @Contract("_,!null -> !null")
    fun getHiddenCodePathOrDefault(packageName: String, defaultPath: String?): String? {
        val result = Runner.runCommand(RunnerUtils.CMD_PM + " dump " + packageName + " | grep codePath")
        if (result.isSuccessful) {
            val paths = result.outputAsList
            if (paths.isNotEmpty()) {
                // Get only the last path
                val codePath = paths[paths.size - 1]
                val start = codePath.indexOf('=')
                if (start != -1) return codePath.substring(start + 1)
            }
        }
        return defaultPath?.let { File(it).parent }
    }

    @JvmStatic
    fun getAppOpModeNames(appOpModes: List<Int>): Array<CharSequence> {
        val appOpModeNames = Array<CharSequence>(appOpModes.size) { "" }
        for (i in appOpModes.indices) {
            appOpModeNames[i] = AppOpsManagerCompat.modeToName(appOpModes[i])
        }
        return appOpModeNames
    }

    @JvmStatic
    fun getAppOpNames(appOps: List<Int>): Array<CharSequence> {
        val appOpNames = Array<CharSequence>(appOps.size) { "" }
        for (i in appOps.indices) {
            appOpNames[i] = AppOpsManagerCompat.opToName(appOps[i])
        }
        return appOpNames
    }

    /**
     * Whether the app may be using Play App Signing i.e. letting Google manage the app's signing keys.
     *
     * @param applicationInfo [PackageManager.GET_META_DATA] must be used while fetching application info.
     * @see [Use Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756.zippy.3D.2Capp-signing-process)
     */
    @JvmStatic
    fun usesPlayAppSigning(applicationInfo: ApplicationInfo): Boolean {
        return applicationInfo.metaData != null
                && "STAMP_TYPE_DISTRIBUTION_APK" == applicationInfo.metaData
            .getString("com.android.stamp.type")
                && "https://play.google.com/store" == applicationInfo.metaData
            .getString("com.android.stamp.source")
    }

    @JvmStatic
    fun getSignerInfo(packageInfo: PackageInfo, isExternal: Boolean): SignerInfo? {
        if (!isExternal || Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = packageInfo.signingInfo
                if (signingInfo == null) {
                    if (!isExternal) {
                        return null
                    } // else Could be a false-negative
                } else {
                    return SignerInfo(signingInfo)
                }
            }
        }
        // Is an external app
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P || packageInfo.signatures == null) {
            // Could be a false-negative, try with apksig library
            val apkPath = packageInfo.applicationInfo?.publicSourceDir
            if (apkPath != null) {
                Log.w(TAG, "getSignerInfo: Using fallback method")
                return getSignerInfo(File(apkPath))
            }
        }
        return SignerInfo(packageInfo.signatures)
    }

    private fun getSignerInfo(apkFile: File): SignerInfo? {
        val apkVerifier = ApkVerifier.Builder(apkFile)
            .setMaxCheckedPlatformVersion(Build.VERSION.SDK_INT)
            .build()
        return try {
            SignerInfo(apkVerifier.verify())
        } catch (e: IOException) {
            e.printStackTrace()
            null
        } catch (e: ApkFormatException) {
            e.printStackTrace()
            null
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
            null
        }
    }

    @JvmStatic
    fun getSigningCertSha256Checksum(packageInfo: PackageInfo): Array<String> {
        return getSigningCertSha256Checksum(packageInfo, false)
    }

    @JvmStatic
    fun isSignatureDifferent(newPkgInfo: PackageInfo, oldPkgInfo: PackageInfo): Boolean {
        val newSignerInfo = getSignerInfo(newPkgInfo, true)
        val oldSignerInfo = getSignerInfo(oldPkgInfo, false)
        if (newSignerInfo == null && oldSignerInfo == null) {
            // No signers
            return false
        }
        if (newSignerInfo == null || oldSignerInfo == null) {
            // One of them is signed, other doesn't
            return true
        }
        val newChecksums: Array<String>
        val oldChecksums: MutableList<String>
        newChecksums = getSigningCertChecksums(DigestUtils.SHA_256, newSignerInfo)
        oldChecksums = getSigningCertChecksums(DigestUtils.SHA_256, oldSignerInfo).toMutableList()
        if (newSignerInfo.hasMultipleSigners()) {
            // For multiple signers, all signatures must match.
            if (newChecksums.size != oldChecksums.size) {
                // Signature is different if the number of signatures don't match
                return true
            }
            for (newChecksum in newChecksums) {
                oldChecksums.remove(newChecksum)
            }
            // Old checksums should contain no values if the checksums are the same
            return oldChecksums.isNotEmpty()
        }
        // For single signer, there could be one or more extra certificates for rotation.
        if (newChecksums.isEmpty() && oldChecksums.isEmpty()) {
            // No signers
            return false
        }
        if (newChecksums.isEmpty() || oldChecksums.isEmpty()) {
            // One of them is signed, other doesn't
            return true
        }
        // Check if the user is downgrading or reinstalling
        val oldVersionCode = PackageInfoCompat.getLongVersionCode(oldPkgInfo)
        val newVersionCode = PackageInfoCompat.getLongVersionCode(newPkgInfo)
        if (oldVersionCode >= newVersionCode) {
            // Downgrading to an older version or reinstalling. Match only the first signature
            return newChecksums[0] != oldChecksums[0]
        }
        // Updating or reinstalling. Match only one signature
        for (newChecksum in newChecksums) {
            if (oldChecksums.contains(newChecksum)) {
                return false
            }
        }
        return true
    }

    @JvmStatic
    fun getSigningCertSha256Checksum(packageInfo: PackageInfo, isExternal: Boolean): Array<String> {
        return getSigningCertChecksums(DigestUtils.SHA_256, packageInfo, isExternal)
    }

    @JvmStatic
    fun getSigningCertChecksums(
        @DigestUtils.Algorithm algo: String,
        packageInfo: PackageInfo,
        isExternal: Boolean
    ): Array<String> {
        val signerInfo = getSignerInfo(packageInfo, isExternal)
        return getSigningCertChecksums(algo, signerInfo)
    }

    @JvmStatic
    fun getSigningCertChecksums(
        @DigestUtils.Algorithm algo: String,
        signerInfo: SignerInfo?
    ): Array<String> {
        val signatureArray = signerInfo?.allSignerCerts
        if (signatureArray != null) {
            val checksums = ArrayList<String>()
            for (signature in signatureArray) {
                try {
                    checksums.add(DigestUtils.getHexDigest(algo, signature.encoded))
                } catch (e: CertificateEncodingException) {
                    e.printStackTrace()
                }
            }
            return checksums.toTypedArray()
        }
        return EmptyArray.STRING
    }

    @JvmStatic
    @Throws(CertificateEncodingException::class)
    fun getSigningCertificateInfo(ctx: Context, certificate: X509Certificate?): Spannable {
        val builder = SpannableStringBuilder()
        if (certificate == null) return builder
        val separator = LangUtils.getSeparatorString()
        val certBytes = certificate.encoded
        builder.append(getStyledKeyValue(ctx, R.string.subject, certificate.subjectX500Principal.name, separator))
            .append("\n")
            .append(getStyledKeyValue(ctx, R.string.issuer, certificate.issuerX500Principal.name, separator))
            .append("\n")
            .append(getStyledKeyValue(ctx, R.string.issued_date, certificate.notBefore.toString(), separator))
            .append("\n")
            .append(getStyledKeyValue(ctx, R.string.expiry_date, certificate.notAfter.toString(), separator))
            .append("\n")
            .append(getStyledKeyValue(ctx, R.string.type, certificate.type, separator))
            .append(", ")
            .append(getStyledKeyValue(ctx, R.string.version, certificate.version.toString(), separator))
            .append(", ")
        val validity: Int = try {
            certificate.checkValidity()
            R.string.valid
        } catch (e: CertificateExpiredException) {
            R.string.expired
        } catch (e: CertificateNotYetValidException) {
            R.string.not_yet_valid
        }
        builder.append(getStyledKeyValue(ctx, R.string.validity, ctx.getText(validity), separator))
            .append("\n")
            .append(getPrimaryText(ctx, ctx.getString(R.string.serial_no) + separator))
            .append(getMonospacedText(HexEncoding.encodeToString(certificate.serialNumber.toByteArray(), false)))
            .append("\n")
        // Checksums
        builder.append(getTitleText(ctx, ctx.getString(R.string.checksums))).append("\n")
        val digests = DigestUtils.getDigests(certBytes)
        for (digest in digests) {
            builder.append(getPrimaryText(ctx, digest.first + separator))
                .append(getMonospacedText(digest.second))
                .append("\n")
        }
        // Signature
        builder.append(getTitleText(ctx, ctx.getString(R.string.app_signing_signature)))
            .append("\n")
            .append(getStyledKeyValue(ctx, R.string.algorithm, certificate.sigAlgName, separator))
            .append("\n")
            .append(getStyledKeyValue(ctx, "OID", certificate.sigAlgOID, separator))
            .append("\n")
            .append(getPrimaryText(ctx, ctx.getString(R.string.app_signing_signature) + separator))
            .append(getMonospacedText(HexEncoding.encodeToString(certificate.signature, false))).append("\n")
        // Public key used by Google: https://github.com/google/conscrypt
        // 1. X509PublicKey (PublicKey)
        // 2. OpenSSLRSAPublicKey (RSAPublicKey)
        // 3. OpenSSLECPublicKey (ECPublicKey)
        val publicKey = certificate.publicKey
        builder.append(getTitleText(ctx, ctx.getString(R.string.public_key)))
            .append("\n")
            .append(getStyledKeyValue(ctx, R.string.algorithm, publicKey.algorithm, separator))
            .append("\n")
            .append(getStyledKeyValue(ctx, R.string.format, publicKey.format, separator))
        when (publicKey) {
            is RSAPublicKey -> {
                builder.append("\n")
                    .append(
                        getStyledKeyValue(
                            ctx,
                            R.string.rsa_exponent,
                            publicKey.publicExponent.toString(),
                            separator
                        )
                    )
                    .append("\n")
                    .append(getPrimaryText(ctx, ctx.getString(R.string.rsa_modulus) + separator))
                    .append(getMonospacedText(HexEncoding.encodeToString(publicKey.modulus.toByteArray(), false)))
            }
            is ECPublicKey -> {
                builder.append("\n")
                    .append(getStyledKeyValue(ctx, R.string.dsa_affine_x, publicKey.w.affineX.toString(), separator))
                    .append("\n")
                    .append(getStyledKeyValue(ctx, R.string.dsa_affine_y, publicKey.w.affineY.toString(), separator))
            }
        }
        // TODO(5/10/20): Add description for each extensions
        val critSet = certificate.criticalExtensionOIDs
        if (!critSet.isNullOrEmpty()) {
            builder.append("\n").append(getTitleText(ctx, ctx.getString(R.string.critical_exts)))
            for (oid in critSet) {
                val oidName = OidMap.getName(oid)
                builder.append("\n- ")
                    .append(getPrimaryText(ctx, (oidName ?: oid) + separator))
                    .append(getMonospacedText(HexEncoding.encodeToString(certificate.getExtensionValue(oid), false)))
            }
        }
        val nonCritSet = certificate.nonCriticalExtensionOIDs
        if (!nonCritSet.isNullOrEmpty()) {
            builder.append("\n").append(getTitleText(ctx, ctx.getString(R.string.non_critical_exts)))
            for (oid in nonCritSet) {
                val oidName = OidMap.getName(oid)
                builder.append("\n- ")
                    .append(getPrimaryText(ctx, (oidName ?: oid) + separator))
                    .append(getMonospacedText(HexEncoding.encodeToString(certificate.getExtensionValue(oid), false)))
            }
        }
        return builder
    }

    @JvmStatic
    fun getApkVerifierInfo(result: ApkVerifier.Result?, ctx: Context): Spannable {
        val builder = SpannableStringBuilder()
        if (result == null) return builder
        val colorFailure = ColorCodes.getFailureColor(ctx)
        val colorSuccess = ColorCodes.getSuccessColor(ctx)
        var warnCount = 0
        val errors = ArrayList<CharSequence>()
        for (err in result.errors) {
            errors.add(getColoredText(err.toString(), colorFailure))
        }
        warnCount += result.warnings.size
        for (signer in result.v1SchemeIgnoredSigners) {
            val name = signer.name
            for (err in signer.errors) {
                errors.add(
                    getColoredText(
                        SpannableStringBuilder(getBoldString(name + LangUtils.getSeparatorString())).append(
                            err.toString()
                        ), colorFailure
                    )
                )
            }
            warnCount += signer.warnings.size
        }
        if (result.isVerified) {
            if (warnCount == 0) {
                builder.append(
                    getColoredText(
                        getTitleText(ctx, "✔ " + ctx.getString(R.string.verified)),
                        colorSuccess
                    )
                )
            } else {
                builder.append(
                    getColoredText(
                        getTitleText(
                            ctx, "✔ " + ctx.resources
                                .getQuantityString(R.plurals.verified_with_warning, warnCount, warnCount)
                        ), colorSuccess
                    )
                )
            }
            if (result.isSourceStampVerified) {
                val source = Signer.getSourceStampSource(result.sourceStampInfo)
                if (source != null) {
                    builder.append("\n✔ ")
                        .append(ctx.getString(R.string.source_stamp_verified_and_identified_to_be_from_source, source))
                } else {
                    builder.append("\n✔ ").append(ctx.getString(R.string.source_stamp_verified))
                }
            }
            val sigSchemes = mutableListOf<CharSequence>()
            if (result.isVerifiedUsingV1Scheme) sigSchemes.add("v1")
            if (result.isVerifiedUsingV2Scheme) sigSchemes.add("v2")
            if (result.isVerifiedUsingV3Scheme) sigSchemes.add("v3")
            if (result.isVerifiedUsingV31Scheme) sigSchemes.add("v3.1")
            if (result.isVerifiedUsingV4Scheme) sigSchemes.add("v4")
            builder.append("\n").append(
                getPrimaryText(
                    ctx, ctx.resources
                        .getQuantityString(
                            R.plurals.app_signing_signature_schemes_pl,
                            sigSchemes.size
                        ) + LangUtils.getSeparatorString()
                )
            )
            builder.append(TextUtilsCompat.joinSpannable(", ", sigSchemes))
        } else {
            builder.append(
                getColoredText(
                    getTitleText(ctx, "✘ " + ctx.getString(R.string.not_verified)),
                    colorFailure
                )
            )
        }
        builder.append("\n")
        // If there are errors, no certificate info will be loaded
        builder.append(TextUtilsCompat.joinSpannable("\n", errors)).append("\n")
        return builder
    }

    @JvmStatic
    @Throws(ErrnoException::class)
    fun ensurePackageStagingDirectoryPrivileged() {
        if (!Paths.get("/data/local").canWrite()) {
            return
        }
        val psd = Paths.get(PACKAGE_STAGING_DIRECTORY)
        if (!psd.isDirectory) {
            // Recreate directory
            val parent = psd.parent ?: throw IllegalStateException("Parent should be /data/local")
            if (psd.exists()) psd.delete()
            psd.mkdir()
        }
        // Change permission
        val f = psd.file ?: throw IllegalStateException("File cannot be null")
        if ((f.mode and 511) != 457) { // 0777 octal = 511, 0711 octal = 457
            f.mode = 457 // 0711 octal
        }
        // Change UID, GID
        val uidGidPair = f.uidGid
        if (uidGidPair.uid != 2000 || uidGidPair.gid != 2000) {
            f.setUidGid(2000, 2000)
        }
    }

    @JvmStatic
    fun ensurePackageStagingDirectoryCommand(): String {
        val psd = PACKAGE_STAGING_DIRECTORY.absolutePath
        return String.format(
            "( [ -d  %s ] || ( rm %s; mkdir %s && chmod 771 %s && chown 2000:2000 %s ) )",
            psd, psd, psd, psd, psd
        )
    }

    /**
     * Check if the given package name is valid.
     *
     * @param packageName The name to check.
     * @return Success if it's valid.
     * @see [FrameworkParsingPackageUtils.java](https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/android/content/pm/parsing/FrameworkParsingPackageUtils.java;l=72;drc=1bc76ef01ec070d5155d99be0c495fd4ee60d074)
     */
    @JvmStatic
    fun validateName(packageName: String): Boolean {
        if (packageName == "android") {
            // platform package
            return true
        }
        val N = packageName.length
        var hasSep = false
        var front = true
        for (i in 0 until N) {
            val c = packageName[i]
            if ((c in 'a'..'z') || (c in 'A'..'Z')) {
                front = false
                continue
            }
            if (!front) {
                if ((c in '0'..'9') || c == '_') {
                    continue
                }
            }
            if (c == '.') {
                hasSep = true
                front = true
                continue
            }
            return false
        }
        return hasSep
    }
}
