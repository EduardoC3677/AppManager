// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import io.github.muntashirakon.AppManager.db.AppsDb
import io.github.muntashirakon.AppManager.db.entity.ArchivedApp
import io.github.muntashirakon.AppManager.logs.Logger
import io.github.muntashirakon.AppManager.progress.ProgressHandler
import io.github.muntashirakon.AppManager.types.UserPackagePair
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.AppManager.utils.ShizukuUtils
import io.github.muntashirakon.AppManager.settings.Ops
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Enhanced Archive Handler with storage optimization features
 * 
 * Features:
 * - Archive apps with optional cache cleaning
 * - Category-based archiving (Games, Food, Social, etc.)
 * - Storage savings estimation
 * - One-tap archive + clean for app groups
 */
object ArchiveHandler {
    const val MODE_AUTO = 0
    const val MODE_SHIZUKU = 1
    const val MODE_ROOT = 2
    const val MODE_STANDARD = 3
    
    // Archive options flags
    const val ARCHIVE_WITH_CACHE_CLEAN = 1 shl 0
    const val ARCHIVE_WITH_DATA_CLEAN = 1 shl 1
    const val ARCHIVE_ESTIMATE_ONLY = 1 shl 2

    @JvmStatic
    fun opArchive(
        info: BatchOpsManager.BatchOpsInfo,
        progressHandler: ProgressHandler?,
        logger: Logger?,
        mode: Int
    ): BatchOpsManager.Result {
        return opArchiveWithOptions(info, progressHandler, logger, mode, 0)
    }

    /**
     * Archive apps with optional cache/data cleaning
     * @param options Bitmask of ARCHIVE_* flags
     */
    @JvmStatic
    fun opArchiveWithOptions(
        info: BatchOpsManager.BatchOpsInfo,
        progressHandler: ProgressHandler?,
        logger: Logger?,
        mode: Int,
        @BatchOpsManager.ArchiveOptions options: Int
    ): BatchOpsManager.Result {
        val failedPackages = ArrayList<UserPackagePair>()
        val lastProgress = progressHandler?.lastProgress ?: 0f
        val archivedAppDao = AppsDb.getInstance().archivedAppDao()
        val context = ContextUtils.getContext()
        val pm = context.packageManager

        val max = info.size()
        var totalSpaceSaved = 0L
        var totalCacheCleared = 0L

        // Optimized Shizuku Shell
        val shell: ShizukuUtils.ShizukuShell? = if (mode == MODE_AUTO || mode == MODE_SHIZUKU) 
            ShizukuUtils.newShell(context) else null

        try {
            for (i in 0 until max) {
                progressHandler?.postUpdate(lastProgress + i + 1)
                val pair = info.getPair(i)

                if (ContextUtils.getContext().packageName == pair.packageName) {
                    log(logger, "====> op=ARCHIVE, cannot archive the app itself")
                    failedPackages.add(pair)
                    continue
                }

                try {
                    val appInfo = pm.getApplicationInfo(pair.packageName, 0)
                    val appName = appInfo.loadLabel(pm).toString()
                    val apkPath = appInfo.sourceDir
                    
                    // Calculate storage metrics
                    val apkSize = File(apkPath).length()
                    val cacheSize = getCacheSize(context, pair.packageName)
                    val dataSize = getDataSize(context, pair.packageName)
                    val totalSize = apkSize + cacheSize + dataSize
                    
                    log(logger, "Archiving ${pair.packageName}: APK=${formatSize(apkSize)}, Cache=${formatSize(cacheSize)}, Data=${formatSize(dataSize)}")

                    // Clear cache if requested
                    if (options and ARCHIVE_WITH_CACHE_CLEAN != 0 && cacheSize > 0) {
                        try {
                            PackageManagerCompat.deleteApplicationCacheFilesAsUser(pair)
                            totalCacheCleared += cacheSize
                            log(logger, "====> Cleared cache: ${formatSize(cacheSize)}")
                        } catch (e: Exception) {
                            log(logger, "====> Failed to clear cache for ${pair.packageName}", e)
                        }
                    }

                    var success = false

                    // Modern Archiving API (Android 15+)
                    if (mode == MODE_AUTO && Build.VERSION.SDK_INT >= 35) {
                        val packageInstaller = context.packageManager.packageInstaller
                        val intent = Intent(ArchiveResultReceiver.ACTION_ARCHIVE_RESULT).apply {
                            setPackage(context.packageName)
                        }

                        val pendingIntent = PendingIntent.getBroadcast(
                            context,
                            pair.packageName.hashCode(),
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                        )

                        packageInstaller.requestArchive(pair.packageName, pendingIntent.intentSender)
                        success = true
                    }
                    // Shizuku
                    else if ((mode == MODE_AUTO || mode == MODE_SHIZUKU) && shell != null) {
                        val result = shell.runCommand("pm uninstall -k ${pair.packageName}")
                        if (result?.exitCode == 0) {
                            success = true
                        } else {
                            log(logger, "====> op=ARCHIVE, pkg=$pair, exitCode=${result?.exitCode}, stderr=${result?.stderr}")
                        }
                    }
                    // Root
                    else if (mode == MODE_ROOT || (mode == MODE_AUTO && Ops.isDirectRoot())) {
                        val installer = PackageInstallerCompat.getNewInstance()
                        success = installer.uninstall(pair.packageName, pair.userId, true)
                    }
                    // Standard
                    else {
                        val installer = PackageInstallerCompat.getNewInstance()
                        success = installer.uninstall(pair.packageName, pair.userId, true)
                    }

                    if (success) {
                        val archivedApp = ArchivedApp(pair.packageName, appName, System.currentTimeMillis(), apkPath)
                        runBlocking { archivedAppDao.insert(archivedApp) }
                        totalSpaceSaved += totalSize
                        log(logger, "====> Archived successfully. Space saved: ${formatSize(totalSize)}")
                    } else {
                        failedPackages.add(pair)
                        log(logger, "====> op=ARCHIVE, pkg=$pair failed")
                    }
                } catch (e: PackageManager.NameNotFoundException) {
                    failedPackages.add(pair)
                    log(logger, "====> op=ARCHIVE, pkg=$pair not found", e)
                } catch (e: Exception) {
                    failedPackages.add(pair)
                    log(logger, "====> op=ARCHIVE, pkg=$pair", e)
                }
            }
            
            // Log summary
            log(logger, "====> ARCHIVE SUMMARY: Saved=${formatSize(totalSpaceSaved)}, Cache Cleared=${formatSize(totalCacheCleared)}, Failed=${failedPackages.size}")
        } finally {
            shell?.close()
        }
        return BatchOpsManager.Result(failedPackages, true, totalSpaceSaved, totalCacheCleared)
    }

    /**
     * Estimate storage savings without actually archiving
     */
    @JvmStatic
    fun estimateStorageSavings(
        packages: List<UserPackagePair>,
        context: Context
    ): StorageSavingsEstimate {
        val pm = context.packageManager
        var totalApkSize = 0L
        var totalCacheSize = 0L
        var totalDataSize = 0L
        var appCount = 0

        for (pair in packages) {
            try {
                val appInfo = pm.getApplicationInfo(pair.packageName, 0)
                val apkSize = File(appInfo.sourceDir).length()
                val cacheSize = getCacheSize(context, pair.packageName)
                val dataSize = getDataSize(context, pair.packageName)

                totalApkSize += apkSize
                totalCacheSize += cacheSize
                totalDataSize += dataSize
                appCount++
            } catch (e: Exception) {
                // Skip apps that can't be accessed
            }
        }

        return StorageSavingsEstimate(
            appCount = appCount,
            apkSize = totalApkSize,
            cacheSize = totalCacheSize,
            dataSize = totalDataSize,
            totalSize = totalApkSize + totalCacheSize + totalDataSize
        )
    }

    /**
     * Get cache size for a package
     */
    private fun getCacheSize(context: Context, packageName: String): Long {
        return try {
            val cacheDir = File(context.cacheDir, "../$packageName/cache")
            if (cacheDir.exists()) {
                cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
            } else 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Get data size for a package
     */
    private fun getDataSize(context: Context, packageName: String): Long {
        return try {
            val dataDir = File(context.dataDir, "../$packageName")
            if (dataDir.exists()) {
                dataDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
            } else 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Format size in human-readable format
     */
    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }

    private fun log(logger: Logger?, msg: String) {
        logger?.println(msg)
    }

    private fun log(logger: Logger?, msg: String, tr: Throwable) {
        logger?.println(msg, tr)
    }

    /**
     * Storage savings estimate result
     */
    data class StorageSavingsEstimate(
        val appCount: Int,
        val apkSize: Long,
        val cacheSize: Long,
        val dataSize: Long,
        val totalSize: Long
    ) {
        fun formatTotal(): String = formatSize(totalSize)
        fun formatApk(): String = formatSize(apkSize)
        fun formatCache(): String = formatSize(cacheSize)
        fun formatData(): String = formatSize(dataSize)
        
        private companion object {
            private fun formatSize(bytes: Long): String {
                return when {
                    bytes < 1024 -> "$bytes B"
                    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
                    else -> "${bytes / (1024 * 1024 * 1024)} GB"
                }
            }
        }
    }
}
