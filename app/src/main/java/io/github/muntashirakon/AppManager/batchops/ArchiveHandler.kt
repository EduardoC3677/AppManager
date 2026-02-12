// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.apk.installer.ArchiveResultReceiver
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat
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

object ArchiveHandler {
    const val MODE_AUTO = 0
    const val MODE_SHIZUKU = 1
    const val MODE_ROOT = 2
    const val MODE_STANDARD = 3

    @JvmStatic
    fun opArchive(
        info: BatchOpsManager.BatchOpsInfo,
        progressHandler: ProgressHandler?,
        logger: Logger?,
        mode: Int
    ): BatchOpsManager.Result {
        val failedPackages = ArrayList<UserPackagePair>()
        val lastProgress = progressHandler?.lastProgress ?: 0f
        val archivedAppDao = AppsDb.getInstance().archivedAppDao()
        val context = ContextUtils.getContext()
        val pm = context.packageManager

        val max = info.size()
        
        // Optimized Shizuku Shell
        val shell: ShizukuUtils.ShizukuShell? = if (mode == MODE_AUTO || mode == MODE_SHIZUKU) ShizukuUtils.newShell(context) else null

        try {
            for (i in 0 until max) {
                progressHandler?.postUpdate(lastProgress + i + 1)
                val pair = info.getPair(i)

                if (BuildConfig.APPLICATION_ID == pair.packageName) {
                    log(logger, "====> op=ARCHIVE, cannot archive the app itself")
                    failedPackages.add(pair)
                    continue
                }

                try {
                    val appInfo = pm.getApplicationInfo(pair.packageName, 0)
                    val appName = appInfo.loadLabel(pm).toString()
                    val apkPath = appInfo.sourceDir
                    val size = File(apkPath).length()
                    log(logger, "Archiving ${pair.packageName} size=$size bytes")

                    var success = false

                    // Modern Archiving API (Android 14+)
                    if (mode == MODE_AUTO && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
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
                        success = true // Assume success for API call
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
        } finally {
            shell?.close()
        }
        return BatchOpsManager.Result(failedPackages)
    }

    private fun log(logger: Logger?, msg: String) {
        logger?.println(msg)
    }

    private fun log(logger: Logger?, msg: String, tr: Throwable) {
        logger?.println(msg, tr)
    }
}
