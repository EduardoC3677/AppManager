// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.UserIdInt
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.*
import android.os.*
import android.util.AndroidException
import androidx.annotation.IntDef
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.annotation.WorkerThread
import dev.rikka.tools.refine.Refine
import io.github.muntashirakon.AppManager.ipc.ProxyBinder
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.types.UserPackagePair
import io.github.muntashirakon.AppManager.users.Users
import io.github.muntashirakon.AppManager.utils.*
import io.github.muntashirakon.io.IoUtils
import io.github.muntashirakon.io.Paths
import java.io.IOException
import java.util.*

object PackageManagerCompat {
    @JvmField
    val TAG: String = PackageManagerCompat::class.java.simpleName

    const val MATCH_STATIC_SHARED_AND_SDK_LIBRARIES: Int = 0x04000000
    @JvmField
    val GET_SIGNING_CERTIFICATES: Int
    @JvmField
    val GET_SIGNING_CERTIFICATES_APK: Int
    @JvmField
    val MATCH_DISABLED_COMPONENTS: Int
    @JvmField
    val MATCH_UNINSTALLED_PACKAGES: Int

    init {
        GET_SIGNING_CERTIFICATES = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        GET_SIGNING_CERTIFICATES_APK = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            MATCH_DISABLED_COMPONENTS = PackageManager.MATCH_DISABLED_COMPONENTS
            MATCH_UNINSTALLED_PACKAGES = PackageManager.MATCH_UNINSTALLED_PACKAGES
        } else {
            @Suppress("DEPRECATION")
            MATCH_DISABLED_COMPONENTS = PackageManager.GET_DISABLED_COMPONENTS
            @Suppress("DEPRECATION")
            MATCH_UNINSTALLED_PACKAGES = PackageManager.GET_UNINSTALLED_PACKAGES
        }
    }

    @IntDef(
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class EnabledState

    @IntDef(
        flag = true, value = [
            PackageManager.DONT_KILL_APP,
            PackageManager.SYNCHRONOUS
        ]
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class EnabledFlags

    private const val NEEDED_FLAGS = 0x04000000 or 0x00002000 // MATCH_STATIC_SHARED_AND_SDK_LIBRARIES | MATCH_UNINSTALLED_PACKAGES (hardcoded if necessary, but using property is better)

    @JvmStatic
    @WorkerThread
    fun getInstalledPackages(flags: Int, @UserIdInt userId: Int): List<PackageInfo> {
        val pm = packageManager
        val neededFlags = MATCH_UNINSTALLED_PACKAGES or MATCH_STATIC_SHARED_AND_SDK_LIBRARIES
        // Here we've compromised performance to fix issues in some devices where Binder transaction limit is too small.
        val refPackages = getInstalledPackagesInternal(pm, flags and neededFlags, userId)
        var packageInfoList = getInstalledPackagesInternal(pm, flags, userId)
        if (packageInfoList.size == refPackages.size) {
            // Everything's loaded correctly
            return packageInfoList
        }
        if (packageInfoList.size > refPackages.size) {
            // Should never happen
            val pkgsFromPkgInfo = HashSet<String>(packageInfoList.size)
            val pkgsFromAppInfo = HashSet<String>(refPackages.size)
            for (info in packageInfoList) pkgsFromPkgInfo.add(info.packageName)
            for (info in refPackages) pkgsFromAppInfo.add(info.packageName)
            pkgsFromPkgInfo.removeAll(pkgsFromAppInfo)
            Log.i(TAG, "Loaded extra packages: $pkgsFromPkgInfo")
            throw IllegalStateException(
                "Retrieved " + packageInfoList.size + " packages out of "\n+ refPackages.size + " applications which is impossible"\n)
        }
        Log.w(TAG, "Could not fetch installed packages for user %d using getInstalledPackages(), using workaround", userId)
        packageInfoList = ArrayList(refPackages.size)
        for (i in refPackages.indices) {
            if (ThreadUtils.isInterrupted()) {
                break
            }
            val packageName = refPackages[i].packageName
            try {
                packageInfoList.add(getPackageInfo(pm, packageName, flags, userId))
            } catch (ex: Exception) {
                Log.e(TAG, "Could not retrieve package info for $packageName and user $userId")
                continue
            }
            if (i % 100 == 0) {
                // Prevent DeadObjectException
                SystemClock.sleep(300)
            }
        }
        return packageInfoList
    }

    @JvmStatic
    private fun getInstalledPackagesInternal(
        pm: IPackageManager,
        flags: Int,
        @UserIdInt userId: Int
    ): List<PackageInfo> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(flags.toLong(), userId).list
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(flags, userId).list
            }
        } catch (e: RemoteException) {
            ExUtils.rethrowFromSystemServer(e)
        } catch (e: BadParcelableException) {
            Log.w(TAG, "Could not retrieve all packages for user $userId", e)
            Collections.emptyList()
        }
    }

    @JvmStatic
    @WorkerThread
    @Throws(RemoteException::class)
    fun getInstalledApplications(flags: Int, @UserIdInt userId: Int): List<ApplicationInfo> {
        return getInstalledApplications(packageManager, flags, userId)
    }

    @JvmStatic
    @SuppressLint("NewApi")
    @WorkerThread
    @Throws(RemoteException::class)
    fun getInstalledApplications(
        pm: IPackageManager, flags: Int,
        @UserIdInt userId: Int
    ): List<ApplicationInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(flags.toLong(), userId).list
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(flags, userId).list
        }
    }

    @JvmStatic
    @Throws(RemoteException::class, PackageManager.NameNotFoundException::class)
    fun getPackageInfo(packageName: String, flags: Int, @UserIdInt userId: Int): PackageInfo {
        return getPackageInfo(packageManager, packageName, flags, userId)
    }

    @JvmStatic
    @Throws(RemoteException::class, PackageManager.NameNotFoundException::class)
    private fun getPackageInfo(
        pm: IPackageManager, packageName: String, flags: Int,
        @UserIdInt userId: Int
    ): PackageInfo {
        var info: PackageInfo? = null
        try {
            info = getPackageInfoInternal(pm, packageName, flags, userId)
        } catch (e: DeadObjectException) {
            Log.w(TAG, "Could not fetch info for package %s and user %d with flags 0x%X, using workaround", e, packageName, userId, flags)
        }
        if (info == null) {
            val strippedFlags = flags and (PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES
                    or PackageManager.GET_PROVIDERS or PackageManager.GET_RECEIVERS or PackageManager.GET_PERMISSIONS).inv()
            info = getPackageInfoInternal(pm, packageName, strippedFlags, userId)
            if (info == null) {
                throw PackageManager.NameNotFoundException(
                    String.format(
                        "Could not retrieve info for package %s with flags 0x%X for user %d",
                        packageName, strippedFlags, userId
                    )
                )
            }
            var activities: Array<ActivityInfo>? = null
            if (flags and PackageManager.GET_ACTIVITIES != 0) {
                val newFlags = flags and (PackageManager.GET_SERVICES or PackageManager.GET_PROVIDERS
                        or PackageManager.GET_RECEIVERS or PackageManager.GET_PERMISSIONS).inv()
                val info1 = getPackageInfoInternal(pm, packageName, newFlags, userId)
                if (info1 != null) activities = info1.activities
            }
            var services: Array<ServiceInfo>? = null
            if (flags and PackageManager.GET_SERVICES != 0) {
                val newFlags = flags and (PackageManager.GET_ACTIVITIES or PackageManager.GET_PROVIDERS
                        or PackageManager.GET_RECEIVERS or PackageManager.GET_PERMISSIONS).inv()
                val info1 = getPackageInfoInternal(pm, packageName, newFlags, userId)
                if (info1 != null) services = info1.services
            }
            var providers: Array<ProviderInfo>? = null
            if (flags and PackageManager.GET_PROVIDERS != 0) {
                val newFlags = flags and (PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES
                        or PackageManager.GET_RECEIVERS or PackageManager.GET_PERMISSIONS).inv()
                val info1 = getPackageInfoInternal(pm, packageName, newFlags, userId)
                if (info1 != null) providers = info1.providers
            }
            var receivers: Array<ActivityInfo>? = null
            if (flags and PackageManager.GET_RECEIVERS != 0) {
                val newFlags = flags and (PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES
                        or PackageManager.GET_PROVIDERS or PackageManager.GET_PERMISSIONS).inv()
                val info1 = getPackageInfoInternal(pm, packageName, newFlags, userId)
                if (info1 != null) receivers = info1.receivers
            }
            var permissions: Array<PermissionInfo>? = null
            if (flags and PackageManager.GET_PERMISSIONS != 0) {
                val newFlags = flags and (PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES
                        or PackageManager.GET_PROVIDERS or PackageManager.GET_RECEIVERS).inv()
                val info1 = getPackageInfoInternal(pm, packageName, newFlags, userId)
                if (info1 != null) permissions = info1.permissions
            }
            info.activities = activities
            info.services = services
            info.providers = providers
            info.receivers = receivers
            info.permissions = permissions
        }
        return info!!
    }

    @JvmStatic
    @Throws(RemoteException::class, PackageManager.NameNotFoundException::class)
    fun getApplicationInfo(packageName: String, flags: Int, @UserIdInt userId: Int): ApplicationInfo {
        val pm = packageManager
        val applicationInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(packageName, flags.toLong(), userId)
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(packageName, flags, userId)
        }
        if (applicationInfo == null) {
            throw PackageManager.NameNotFoundException("Package $packageName not found.")
        }
        return applicationInfo
    }

    @JvmStatic
    fun getInstallerPackageName(packageName: String, @UserIdInt userId: Int): String? {
        return try {
            val installSource = getInstallSourceInfo(packageName, userId)
            installSource.installingPackageName ?: installSource.initiatingPackageName
        } catch (e: RemoteException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }

    @JvmStatic
    @Throws(RemoteException::class)
    fun getInstallSourceInfo(packageName: String, @UserIdInt userId: Int): InstallSourceInfoCompat {
        val pm = packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return InstallSourceInfoCompat(pm.getInstallSourceInfo(packageName, userId))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return InstallSourceInfoCompat(pm.getInstallSourceInfo(packageName))
        }
        var installerPackageName: String? = null
        try {
            @Suppress("DEPRECATION")
            installerPackageName = pm.getInstallerPackageName(packageName)
        } catch (e: IllegalArgumentException) {
            val message = e.message
            if (message != null && message.startsWith("Unknown package:")) {
                throw RemoteException(message)
            }
        }
        return InstallSourceInfoCompat(installerPackageName)
    }

    @JvmStatic
    fun getLaunchIntentForPackage(packageName: String, @UserIdInt userId: Int): Intent? {
        val context = ContextUtils.getContext()
        if (userId == UserHandleHidden.myUserId()) {
            val pm = context.packageManager
            return if (Utils.isTv(context)) {
                pm.getLeanbackLaunchIntentForPackage(packageName)
            } else {
                pm.getLaunchIntentForPackage(packageName)
            }
        }
        val userHandle = Users.getUserHandle(userId) ?: return null
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        try {
            if (!launcherApps.isPackageEnabled(packageName, userHandle)) {
                return null
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not retrieve enable state of $packageName for user $userHandle", e)
            return null
        }
        val activityInfoList = launcherApps.getActivityList(packageName, userHandle)
        if (activityInfoList.isEmpty()) {
            return null
        }
        val info = activityInfoList[0]
        return Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            .setComponent(info.componentName)
    }

    @JvmStatic
    @SuppressLint("NewApi")
    @Throws(RemoteException::class)
    fun queryIntentActivities(
        context: Context, intent: Intent, flags: Int,
        @UserIdInt userId: Int
    ): List<ResolveInfo> {
        val pm = packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val pmN = Refine.unsafeCast<IPackageManagerN>(pm)
            val resolveInfoList: ParceledListSlice<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pmN.queryIntentActivities(
                    intent,
                    intent.resolveTypeIfNeeded(context.contentResolver), flags.toLong(), userId
                )
            } else {
                @Suppress("DEPRECATION")
                pmN.queryIntentActivities(
                    intent,
                    intent.resolveTypeIfNeeded(context.contentResolver), flags, userId
                )
            }
            resolveInfoList.list
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(
                intent, intent.resolveTypeIfNeeded(context.contentResolver), flags,
                userId
            )
        }
    }

    @JvmStatic
    @Throws(SecurityException::class, IllegalArgumentException::class)
    fun getComponentEnabledSetting(componentName: ComponentName, @UserIdInt userId: Int): Int {
        return try {
            packageManager.getComponentEnabledSetting(componentName, userId)
        } catch (e: RemoteException) {
            ExUtils.rethrowFromSystemServer(e)
        }
    }

    @JvmStatic
    @RequiresPermission(value = Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE)
    @Throws(RemoteException::class)
    fun setComponentEnabledSetting(
        componentName: ComponentName,
        @EnabledState newState: Int,
        @EnabledFlags flags: Int,
        @UserIdInt userId: Int
    ) {
        val pm = packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val callingPackage = SelfPermissions.getCallingPackage(Users.getSelfOrRemoteUid())
            pm.setComponentEnabledSetting(componentName, newState, flags, userId, callingPackage)
        } else {
            @Suppress("DEPRECATION")
            pm.setComponentEnabledSetting(componentName, newState, flags, userId)
        }
        if (userId != UserHandleHidden.myUserId()) {
            BroadcastUtils.sendPackageAltered(ContextUtils.getContext(), arrayOf(componentName.packageName))
        }
    }

    @JvmStatic
    @RequiresPermission(value = Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE)
    @Throws(SecurityException::class, IllegalArgumentException::class)
    fun setApplicationEnabledSetting(
        packageName: String, @EnabledState newState: Int,
        @EnabledFlags flags: Int, @UserIdInt userId: Int
    ) {
        try {
            packageManager.setApplicationEnabledSetting(packageName, newState, flags, userId, null)
            if (userId != UserHandleHidden.myUserId()) {
                BroadcastUtils.sendPackageAltered(ContextUtils.getContext(), arrayOf(packageName))
            }
        } catch (e: RemoteException) {
            ExUtils.rethrowFromSystemServer(e)
        }
    }

    @JvmStatic
    @Throws(SecurityException::class, IllegalArgumentException::class)
    fun getApplicationEnabledSetting(packageName: String, @UserIdInt userId: Int): Int {
        return try {
            packageManager.getApplicationEnabledSetting(packageName, userId)
        } catch (e: RemoteException) {
            ExUtils.rethrowFromSystemServer(e)
        }
    }

    @JvmStatic
    @RequiresApi(Build.VERSION_CODES.N)
    @RequiresPermission(allOf = ["android.permission.SUSPEND_APPS", ManifestCompat.permission.MANAGE_USERS])
    @Throws(RemoteException::class)
    fun suspendPackages(packageNames: Array<String>, @UserIdInt userId: Int, suspend: Boolean) {
        val callingPackage = SelfPermissions.getCallingPackage(Users.getSelfOrRemoteUid())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                packageManager.setPackagesSuspendedAsUser(packageNames, suspend, null, null, null, 0, callingPackage, 0, userId)
            } catch (e: NoSuchMethodError) {
                packageManager.setPackagesSuspendedAsUser(packageNames, suspend, null, null, null as SuspendDialogInfo?, callingPackage, userId)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            packageManager.setPackagesSuspendedAsUser(packageNames, suspend, null, null, null as SuspendDialogInfo?, callingPackage, userId)
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
            packageManager.setPackagesSuspendedAsUser(packageNames, suspend, null, null, null as String?, callingPackage, userId)
        } else {
            packageManager.setPackagesSuspendedAsUser(packageNames, suspend, userId)
        }
        if (userId != UserHandleHidden.myUserId()) {
            BroadcastUtils.sendPackageAltered(ContextUtils.getContext(), packageNames)
        }
    }

    @JvmStatic
    @Throws(RemoteException::class)
    fun isPackageSuspended(packageName: String, @UserIdInt userId: Int): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            packageManager.isPackageSuspendedForUser(packageName, userId)
        } else false
    }

    @JvmStatic
    @RequiresPermission(ManifestCompat.permission.MANAGE_USERS)
    @Throws(RemoteException::class)
    fun hidePackage(packageName: String, @UserIdInt userId: Int, hide: Boolean) {
        if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_USERS)) {
            val hidden = packageManager.setApplicationHiddenSettingAsUser(packageName, hide, userId)
            if (userId != UserHandleHidden.myUserId()) {
                if (hidden) {
                    if (hide) {
                        BroadcastUtils.sendPackageRemoved(ContextUtils.getContext(), arrayOf(packageName))
                    } else {
                        BroadcastUtils.sendPackageAdded(ContextUtils.getContext(), arrayOf(packageName))
                    }
                }
            }
        } else {
            throw RemoteException("Missing required permission: android.permission.MANAGE_USERS.")
        }
    }

    @JvmStatic
    @Throws(RemoteException::class)
    fun isPackageHidden(packageName: String, @UserIdInt userId: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                // Find using private flags
                val info = getApplicationInfo(
                    packageName,
                    MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId
                )
                return ApplicationInfoCompat.isHidden(info)
            } catch (ignore: PackageManager.NameNotFoundException) {
            }
        }
        return if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_USERS)) {
            packageManager.getApplicationHiddenSettingAsUser(packageName, userId)
        } else false
    }

    @JvmStatic
    @RequiresPermission(
        anyOf = [
            Manifest.permission.INSTALL_PACKAGES,
            "com.android.permission.INSTALL_EXISTING_PACKAGES"\n]
    )
    @Throws(RemoteException::class)
    fun installExistingPackageAsUser(
        packageName: String, @UserIdInt userId: Int, installFlags: Int,
        installReason: Int, whiteListedPermissions: List<String>?
    ): Int {
        val returnCode: Int = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> packageManager.installExistingPackageAsUser(packageName, userId, installFlags, installReason, whiteListedPermissions)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> packageManager.installExistingPackageAsUser(packageName, userId, installFlags, installReason)
            else -> packageManager.installExistingPackageAsUser(packageName, userId)
        }
        if (userId != UserHandleHidden.myUserId()) {
            BroadcastUtils.sendPackageAdded(ContextUtils.getContext(), arrayOf(packageName))
        }
        return returnCode
    }

    @JvmStatic
    @RequiresPermission(ManifestCompat.permission.CLEAR_APP_USER_DATA)
    @Throws(AndroidException::class)
    fun clearApplicationUserData(pair: UserPackagePair) {
        val pm = packageManager
        val obs = ClearDataObserver()
        pm.clearApplicationUserData(pair.packageName, obs, pair.userId)
        synchronized(obs) {
            while (!obs.isCompleted) {
                try {
                    (obs as Object).wait(500)
                } catch (ignore: InterruptedException) {
                }
            }
        }
        if (!obs.isSuccessful) {
            throw AndroidException("Could not clear data of package $pair")
        }
        BroadcastUtils.sendPackageAltered(ContextUtils.getContext(), arrayOf(pair.packageName))
    }

    @JvmStatic
    @RequiresPermission(ManifestCompat.permission.CLEAR_APP_USER_DATA)
    fun clearApplicationUserData(packageName: String, @UserIdInt userId: Int): Boolean {
        return try {
            clearApplicationUserData(UserPackagePair(packageName, userId))
            true
        } catch (e: AndroidException) {
            e.printStackTrace()
            false
        } catch (e: SecurityException) {
            e.printStackTrace()
            false
        }
    }

    @JvmStatic
    @RequiresPermission(
        allOf = [
            Manifest.permission.DELETE_CACHE_FILES,
            "android.permission.INTERNAL_DELETE_CACHE_FILES"\n]
    )
    @Throws(AndroidException::class)
    fun deleteApplicationCacheFilesAsUser(pair: UserPackagePair) {
        val pm = packageManager
        val obs = ClearDataObserver()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            pm.deleteApplicationCacheFilesAsUser(pair.packageName, pair.userId, obs)
        } else {
            @Suppress("DEPRECATION")
            pm.deleteApplicationCacheFiles(pair.packageName, obs)
        }
        synchronized(obs) {
            while (!obs.isCompleted) {
                try {
                    (obs as Object).wait(500)
                } catch (ignore: InterruptedException) {
                }
            }
        }
        if (!obs.isSuccessful) {
            throw AndroidException("Could not clear cache of package $pair")
        }
        BroadcastUtils.sendPackageAltered(ContextUtils.getContext(), arrayOf(pair.packageName))
    }

    @JvmStatic
    @RequiresPermission(
        allOf = [
            Manifest.permission.DELETE_CACHE_FILES,
            "android.permission.INTERNAL_DELETE_CACHE_FILES"\n]
    )
    fun deleteApplicationCacheFilesAsUser(packageName: String, userId: Int): Boolean {
        return try {
            deleteApplicationCacheFilesAsUser(UserPackagePair(packageName, userId))
            true
        } catch (e: AndroidException) {
            e.printStackTrace()
            false
        } catch (e: SecurityException) {
            e.printStackTrace()
            false
        }
    }

    @JvmStatic
    @RequiresPermission(ManifestCompat.permission.FORCE_STOP_PACKAGES)
    fun forceStopPackage(packageName: String, userId: Int) {
        try {
            ActivityManagerCompat.activityManager.forceStopPackage(packageName, userId)
            BroadcastUtils.sendPackageAltered(ContextUtils.getContext(), arrayOf(packageName))
        } catch (e: RemoteException) {
            ExUtils.rethrowFromSystemServer(e)
        }
    }

    @get:JvmStatic
    val packageInstaller: IPackageInstaller
        @Throws(RemoteException::class)
        get() = IPackageInstaller.Stub.asInterface(ProxyBinder(packageManager.packageInstaller.asBinder()))

    @JvmStatic
    @RequiresPermission(Manifest.permission.CLEAR_APP_CACHE)
    @Throws(RemoteException::class)
    fun freeStorageAndNotify(
        volumeUuid: String?,
        freeStorageSize: Long,
        @StorageManagerCompat.AllocateFlags storageFlags: Int
    ) {
        val pm: IPackageManager
        val obs = ClearDataObserver()
        if (SelfPermissions.checkSelfPermission(Manifest.permission.CLEAR_APP_CACHE)) {
            // Clear cache using unprivileged method: Special case for Android Lollipop
            pm = unprivilegedPackageManager
        } else if (SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.CLEAR_APP_CACHE)) { // Use privileged mode
            pm = packageManager
        } else { // Clear one by one
            // Special case: IPackageManager#freeStorageAndNotify cannot be used before Android Oreo because Shell does
            // not have the permission android.permission.CLEAR_APP_CACHE
            val hasPermission: Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.INTERNAL_DELETE_CACHE_FILES)
            } else {
                SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.DELETE_CACHE_FILES)
            }
            if (!hasPermission) {
                // Does not have enough permission
                return
            }
            if (!SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.INTERACT_ACROSS_USERS_FULL)) {
                val userId = UserHandleHidden.myUserId()
                for (info in getInstalledApplications(MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId)) {
                    deleteApplicationCacheFilesAsUser(info.packageName, userId)
                }
                return
            }
            for (userId in Users.getUsersIds()) {
                for (info in getInstalledApplications(MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId)) {
                    deleteApplicationCacheFilesAsUser(info.packageName, userId)
                }
            }
            return
        }
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> pm.freeStorageAndNotify(volumeUuid, freeStorageSize, storageFlags, obs)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> pm.freeStorageAndNotify(volumeUuid, freeStorageSize, obs)
            else -> pm.freeStorageAndNotify(freeStorageSize, obs)
        }
        synchronized(obs) {
            while (!obs.isCompleted) {
                try {
                    (obs as Object).wait(1000)
                } catch (ignore: InterruptedException) {
                }
            }
        }
    }

    @JvmStatic
    @SuppressLint("NewApi")
    @Throws(RemoteException::class)
    private fun getPackageInfoInternal(pm: IPackageManager, packageName: String, flags: Int, @UserIdInt userId: Int): PackageInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, flags.toLong(), userId)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, flags, userId)
        }
    }

    @get:JvmStatic
    val packageManager: IPackageManager
        get() = IPackageManager.Stub.asInterface(ProxyBinder.getService("package"))

    @get:JvmStatic
    val unprivilegedPackageManager: IPackageManager
        get() = IPackageManager.Stub.asInterface(ProxyBinder.getUnprivilegedService("package"))
}
