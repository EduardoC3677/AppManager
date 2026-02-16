// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.utils

import android.annotation.UserIdInt
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.RemoteException
import android.os.UserHandleHidden
import android.text.TextUtils
import android.util.ArrayMap
import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.backup.BackupUtils
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat
import io.github.muntashirakon.AppManager.db.AppsDb
import io.github.muntashirakon.AppManager.db.entity.App
import io.github.muntashirakon.AppManager.db.entity.Backup
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.settings.FeatureController
import io.github.muntashirakon.AppManager.ssaid.SsaidSettings
import io.github.muntashirakon.AppManager.uri.UriManager
import io.github.muntashirakon.AppManager.usage.AppUsageStatsManager
import io.github.muntashirakon.AppManager.usage.PackageUsageInfo
import io.github.muntashirakon.AppManager.usage.UsageUtils
import io.github.muntashirakon.AppManager.users.Users
import io.github.muntashirakon.AppManager.utils.BroadcastUtils
import io.github.muntashirakon.AppManager.utils.ExUtils
import io.github.muntashirakon.AppManager.utils.KeyStoreUtils
import io.github.muntashirakon.AppManager.utils.PackageUtils
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.ArrayList
import java.util.HashSet

class AppDb {
    private val mAppDao = AppsDb.getInstance().appDao()
    private val mBackupDao = AppsDb.getInstance().backupDao()

    val allApplications: List<App>
        get() = synchronized(sLock) {
            runBlocking { mAppDao.getAll() }
        }

    val allInstalledApplications: List<App>
        get() = synchronized(sLock) {
            runBlocking { mAppDao.getAllInstalled() }
        }

    val allBackups: List<Backup>
        get() = synchronized(sLock) {
            runBlocking { mBackupDao.getAll() }
        }

    fun getAllApplications(packageName: String): List<App> {
        return synchronized(sLock) {
            runBlocking { mAppDao.getAll(packageName) }
        }
    }

    fun getAllApplications(packageName: String, @UserIdInt userId: Int): List<App> {
        return synchronized(sLock) {
            runBlocking { mAppDao.getAll(packageName, userId) }
        }
    }

    fun getAllBackups(packageName: String): List<Backup> {
        return synchronized(sLock) {
            runBlocking { mBackupDao.get(packageName) }
        }
    }

    /**
     * Fetch backups without a lock file. Necessary checks must be done to ensure that the backups actually exist.
     */
    fun getAllBackupsNoLock(packageName: String): List<Backup> {
        return runBlocking { mBackupDao.get(packageName) }
    }

    fun insert(app: App) {
        synchronized(sLock) {
            runBlocking { mAppDao.insert(app) }
        }
    }

    fun insert(backup: Backup) {
        synchronized(sLock) {
            runBlocking { mBackupDao.insert(backup) }
        }
    }

    fun insertBackups(backups: List<Backup>) {
        synchronized(sLock) {
            runBlocking { mBackupDao.insert(backups) }
        }
    }

    fun deleteApplication(packageName: String, userId: Int) {
        synchronized(sLock) {
            runBlocking { mAppDao.delete(packageName, userId) }
        }
    }

    fun deleteAllApplications() {
        synchronized(sLock) {
            runBlocking { mAppDao.deleteAll() }
        }
    }

    fun deleteAllBackups() {
        synchronized(sLock) {
            runBlocking { mBackupDao.deleteAll() }
        }
    }

    fun deleteBackup(backup: Backup) {
        synchronized(sLock) {
            runBlocking { mBackupDao.delete(backup) }
        }
    }

    @WorkerThread
    fun loadInstalledOrBackedUpApplications(context: Context) {
        getBackups(true)
        updateApplications(context)
    }

    @WorkerThread
    fun updateApplications(context: Context, packageNames: Array<String>): List<App> {
        return synchronized(sLock) {
            val appList = ArrayList<App>()
            for (packageName in packageNames) {
                appList.addAll(updateApplicationInternal(context, packageName))
            }
            // Update usage and others
            updateVariableData(context, appList)
            runBlocking { mAppDao.insert(appList) }
            appList
        }
    }

    @WorkerThread
    fun updateApplication(context: Context, packageName: String): List<App> {
        return synchronized(sLock) {
            val appList = updateApplicationInternal(context, packageName)
            // Update usage and others
            updateVariableData(context, appList)
            runBlocking { mAppDao.insert(appList) }
            appList
        }
    }

    @WorkerThread
    private fun updateApplicationInternal(context: Context, packageName: String): List<App> {
        val userIds = Users.getUsersIds()
        val oldApps = ArrayList(runBlocking { mAppDao.getAll(packageName) })
        val appList = ArrayList<App>(userIds.size)
        val backups = ArrayList(runBlocking { mBackupDao.get(packageName) })
        for (userId in userIds) {
            val oldAppIndex = findIndexOfApp(oldApps, packageName, userId)
            var packageInfo: android.content.pm.PackageInfo? = null
            var backup: Backup? = null
            val backupListIterator = backups.listIterator()
            while (backupListIterator.hasNext()) {
                val b = backupListIterator.next()
                if (b.userId == userId) {
                    backup = b
                    backupListIterator.remove()
                    break
                }
            }
            try {
                packageInfo = PackageManagerCompat.getPackageInfo(packageName,
                    PackageManager.GET_META_DATA or PackageManagerCompat.GET_SIGNING_CERTIFICATES or PackageManager.GET_ACTIVITIES
                            or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
                            or PackageManager.GET_SERVICES or PackageManagerCompat.MATCH_DISABLED_COMPONENTS or PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES
                            or PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId)
            } catch (e: RemoteException) {
                // Package does not exist
            } catch (e: PackageManager.NameNotFoundException) {
            } catch (e: SecurityException) {
            }
            if (backup == null && packageInfo == null) {
                // Neither backup nor package exist
                if (oldAppIndex >= 0) {
                    // Delete existing backup
                    runBlocking { mAppDao.delete(oldApps[oldAppIndex]) }
                }
                continue
            }
            if (oldAppIndex >= 0) {
                // There's already existing app
                val oldApp = oldApps[oldAppIndex]
                runBlocking { mAppDao.delete(oldApp) }
                if ((packageInfo != null && isUpToDate(oldApp, packageInfo))
                    || (backup != null && isUpToDate(oldApp, backup))) {
                    // Up-to-date app
                    appList.add(oldApp)
                    oldApp.lastActionTime = System.currentTimeMillis()
                    continue
                }
            }
            // New app
            val app = if (packageInfo != null) App.fromPackageInfo(context, packageInfo) else App.fromBackup(backup!!)
            appList.add(app)
        }

        // Add the rest of the backups if any
        for (backup in backups) {
            appList.add(App.fromBackup(backup))
        }

        // Return the list instead of triggering broadcast
        return appList
    }

    @WorkerThread
    fun updateApplications(context: Context) {
        synchronized(sLock) {
            val backups = getBackups(false)
            val oldApps = ArrayList(runBlocking { mAppDao.getAll() })
            val modifiedApps = ArrayList<App>()
            val newApps = HashSet<String>()
            val updatedApps = HashSet<String>()

            // Interrupt thread on request
            if (ThreadUtils.isInterrupted()) return

            for (userId in Users.getUsersIds()) {
                // Interrupt thread on request
                if (ThreadUtils.isInterrupted()) return

                if (!SelfPermissions.checkCrossUserPermission(userId, false)) {
                    // No support for cross user
                    continue
                }

                val packageInfoList = PackageManagerCompat.getInstalledPackages(
                    PackageManagerCompat.GET_SIGNING_CERTIFICATES or PackageManager.GET_ACTIVITIES
                            or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
                            or PackageManager.GET_SERVICES or PackageManagerCompat.MATCH_DISABLED_COMPONENTS
                            or PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES or PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId)

                for (packageInfo in packageInfoList) {
                    // Interrupt thread on request
                    if (ThreadUtils.isInterrupted()) return

                    val oldAppIndex = findIndexOfApp(oldApps, packageInfo.packageName, UserHandleHidden.getUserId(packageInfo.applicationInfo!!.uid))
                    if (oldAppIndex >= 0) {
                        // There's already existing app
                        val oldApp = oldApps.removeAt(oldAppIndex)
                        if (isUpToDate(oldApp, packageInfo)) {
                            // Up-to-date app
                            updatedApps.add(oldApp.packageName)
                            modifiedApps.add(oldApp)
                            backups.remove(packageInfo.packageName)
                            oldApp.lastActionTime = System.currentTimeMillis()
                            continue
                        }
                    }
                    // New app
                    val app = App.fromPackageInfo(context, packageInfo)
                    backups.remove(packageInfo.packageName)
                    newApps.add(app.packageName)
                    modifiedApps.add(app)
                }
            }

            // Update usage and others
            updateVariableData(context, modifiedApps)

            // Add rest of the backup items, i.e., items that aren't installed
            for (backup in backups.values) {
                // Interrupt thread on request
                if (ThreadUtils.isInterrupted()) return

                val oldAppIndex = findIndexOfApp(oldApps, backup.packageName, backup.userId)
                if (oldAppIndex >= 0) {
                    // There's already existing app
                    val oldApp = oldApps.removeAt(oldAppIndex)
                    if (isUpToDate(oldApp, backup)) {
                        // Up-to-date app
                        updatedApps.add(oldApp.packageName)
                        modifiedApps.add(oldApp)
                        continue
                    }
                }
                // New app
                val app = App.fromBackup(backup)
                newApps.add(app.packageName)
                modifiedApps.add(app)
            }
            // Add new data
            runBlocking {
                mAppDao.delete(oldApps)
                mAppDao.insert(modifiedApps)
            }
            if (!oldApps.isEmpty()) {
                // Delete broadcast
                BroadcastUtils.sendDbPackageRemoved(context, getPackageNamesFromApps(oldApps))
            }
            if (!newApps.isEmpty()) {
                // New apps
                BroadcastUtils.sendDbPackageAdded(context, newApps.toTypedArray())
            }
            if (!updatedApps.isEmpty()) {
                // Altered apps
                BroadcastUtils.sendDbPackageAltered(context, updatedApps.toTypedArray())
            }
        }
    }

    @WorkerThread
    fun getBackups(loadBackups: Boolean): MutableMap<String, Backup> {
        return if (loadBackups) {
            // Very long operation
            BackupUtils.storeAllAndGetLatestBackupMetadata().toMutableMap()
        } else {
            BackupUtils.getAllLatestBackupMetadataFromDb().toMutableMap()
        }
    }

    companion object {
        @JvmField
        val TAG: String = AppDb::class.java.simpleName

        private val sLock = Any()

        @JvmStatic
        private fun updateVariableData(context: Context, modifiedApps: List<App>) {
            val uriManager = UriManager()
            val userIdSsaidSettingsMap = ArrayMap<Int, SsaidSettings>()
            val packageUsageInfoList = ArrayList<PackageUsageInfo>()
            val hasUsageAccess = FeatureController.isUsageAccessEnabled() && SelfPermissions.checkUsageStatsPermission()
            for (userId in Users.getUsersIds()) {
                // Interrupt thread on request
                if (ThreadUtils.isInterrupted()) return
                if (hasUsageAccess) {
                    val interval = UsageUtils.getLastWeek()
                    val usageInfoList = ExUtils.exceptionAsNull<List<PackageUsageInfo>> {
                        AppUsageStatsManager.getInstance().getUsageStats(interval, userId)
                    }
                    if (usageInfoList != null) {
                        packageUsageInfoList.addAll(usageInfoList)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        userIdSsaidSettingsMap[userId] = SsaidSettings(userId)
                    } catch (e: IOException) {
                        Log.w(TAG, "Error: " + e.message)
                    }
                }
            }
            for (app in modifiedApps) {
                if (app.isInstalled == 0 && !app.isSystemApp()) {
                    continue
                }
                val userId = app.userId
                ComponentsBlocker.getInstance(app.packageName, userId, false).use { cb ->
                    app.rulesCount = cb.entryCount()
                }
                app.dataSize = 0
                app.codeSize = 0
                if (hasUsageAccess) {
                    val sizeInfo = PackageUtils.getPackageSizeInfo(context, app.packageName, userId, null)
                    if (sizeInfo != null) {
                        app.codeSize = sizeInfo.codeSize + sizeInfo.obbSize
                        app.dataSize = sizeInfo.dataSize + sizeInfo.mediaSize + sizeInfo.cacheSize
                    }
                }
                // Interrupt thread on request
                if (ThreadUtils.isInterrupted()) return
                if (!app.isInstalled) {
                    continue
                }
                app.hasKeystore = KeyStoreUtils.hasKeyStore(app.uid)
                app.usesSaf = uriManager.getGrantedUris(app.packageName) != null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val ssaidSettings = userIdSsaidSettingsMap[userId]
                    if (ssaidSettings != null) {
                        val ssaid = ssaidSettings.getSsaid(app.packageName, app.uid)
                        app.ssaid = if (TextUtils.isEmpty(ssaid)) null else ssaid
                    } else {
                        app.ssaid = null
                    }
                }
                val usageInfo = findUsage(packageUsageInfoList, app.packageName, userId)
                if (usageInfo != null) {
                    val mobileData = usageInfo.mobileData
                    app.mobileDataUsage = if (mobileData != null) mobileData.total else 0
                    val wifiData = usageInfo.wifiData
                    app.wifiDataUsage = if (wifiData != null) wifiData.total else 0
                    app.openCount = usageInfo.timesOpened
                    app.screenTime = usageInfo.screenTime
                    app.lastUsageTime = usageInfo.lastUsageTime
                } else {
                    app.lastUsageTime = 0
                    app.screenTime = 0
                    app.wifiDataUsage = 0
                    app.mobileDataUsage = 0
                    app.openCount = 0
                }
            }
        }

        private fun findIndexOfApp(appList: List<App>, packageName: String, userId: Int): Int {
            for (i in appList.indices) {
                val app = appList[i]
                if (app.userId == userId && app.packageName == packageName) {
                    return i
                }
            }
            return -1
        }

        private fun findUsage(usageInfoList: List<PackageUsageInfo>, packageName: String, userId: Int): PackageUsageInfo? {
            for (usageInfo in usageInfoList) {
                if (usageInfo.userId == userId && usageInfo.packageName == packageName) {
                    return usageInfo
                }
            }
            return null
        }

        private fun isUpToDate(currentApp: App, installedPackageInfo: android.content.pm.PackageInfo): Boolean {
            if (currentApp.isInstalled == 0) {
                // The app was not installed earlier
                return false
            }
            // App was installed
            return currentApp.lastUpdateTime == installedPackageInfo.lastUpdateTime && currentApp.flags == installedPackageInfo.applicationInfo!!.flags
        }

        private fun isUpToDate(currentApp: App, backup: Backup): Boolean {
            if (currentApp.isInstalled != 0) {
                // The app was installed earlier
                return false
            }
            // App was not installed
            if (currentApp.sdk != 0) {
                // The app is a system app
                return true
            }
            // The app is a backed up app
            return currentApp.lastUpdateTime == backup.backupTime
        }

        private fun getPackageNamesFromApps(apps: List<App>): Array<String> {
            val packages = HashSet<String>(apps.size)
            for (app in apps) {
                packages.add(app.packageName)
            }
            return packages.toTypedArray()
        }
    }
}
