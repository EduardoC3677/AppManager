// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops;

import android.content.Context;
import android.content.Intent;
import android.app.PendingIntent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.apk.installer.ArchiveResultReceiver;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat;
import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.db.dao.ArchivedAppDao;
import io.github.muntashirakon.AppManager.db.entity.ArchivedApp;
import io.github.muntashirakon.AppManager.logs.Logger;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.ShizukuUtils;

public class ArchiveHandler {

    public static final int MODE_AUTO = 0;
    public static final int MODE_SHIZUKU = 1;
    public static final int MODE_ROOT = 2;
    public static final int MODE_STANDARD = 3;

    @NonNull
    public static BatchOpsManager.Result opArchive(@NonNull BatchOpsManager.BatchOpsInfo info,
                                                   @Nullable ProgressHandler progressHandler,
                                                   @Nullable Logger logger,
                                                   int mode) {
        List<UserPackagePair> failedPackages = new ArrayList<>();
        float lastProgress = progressHandler != null ? progressHandler.getLastProgress() : 0;
        ArchivedAppDao archivedAppDao = AppsDb.getInstance().archivedAppDao();
        Context context = ContextUtils.getContext();
        PackageManager pm = context.getPackageManager();

        int max = info.size();
        UserPackagePair pair;

        // Optimized Shizuku Shell
        ShizukuUtils.ShizukuShell shell = null;
        if (mode == MODE_AUTO || mode == MODE_SHIZUKU) {
            shell = ShizukuUtils.newShell(context);
        }

        try {
            for (int i = 0; i < max; ++i) {
                if (progressHandler != null) {
                    progressHandler.postUpdate(lastProgress + i + 1);
                }
                pair = info.getPair(i);

                if (BuildConfig.APPLICATION_ID.equals(pair.getPackageName())) {
                    log(logger, "====> op=ARCHIVE, cannot archive the app itself");
                    failedPackages.add(pair);
                    continue;
                }

                try {
                    ApplicationInfo appInfo = pm.getApplicationInfo(pair.getPackageName(), 0);
                    String appName = appInfo.loadLabel(pm).toString();
                    String apkPath = appInfo.sourceDir;
                    long size = new java.io.File(apkPath).length();
                    log(logger, "Archiving " + pair.getPackageName() + " size=" + size + " bytes");

                    boolean success = false;

                    // Modern Archiving API (Android 14+)
                    if (mode == MODE_AUTO && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();
                        Intent intent = new Intent(ArchiveResultReceiver.ACTION_ARCHIVE_RESULT);
                        intent.setPackage(context.getPackageName());

                        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                                context,
                                pair.getPackageName().hashCode(),
                                intent,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
                        );

                        packageInstaller.requestArchive(pair.getPackageName(), pendingIntent.getIntentSender());
                        success = true; // Assume success
                    } 
                    // Shizuku
                    else if ((mode == MODE_AUTO || mode == MODE_SHIZUKU) && shell != null) {
                        // Use persistent shell
                        ShizukuUtils.CommandResult result = shell.runCommand("pm uninstall -k " + pair.getPackageName());
                        if (result != null && result.exitCode == 0) {
                            success = true;
                        } else {
                            log(logger, "====> op=ARCHIVE, pkg=" + pair + ", exitCode=" + (result != null ? result.exitCode : "null") +
                                    ", stderr=" + (result != null ? result.stderr : "null"));
                        }
                    } 
                    // Root
                    else if (mode == MODE_ROOT || (mode == MODE_AUTO && io.github.muntashirakon.AppManager.settings.Ops.isDirectRoot())) {
                        // PackageInstallerCompat.uninstall uses shell if available
                        PackageInstallerCompat installer = PackageInstallerCompat.getNewInstance();
                        success = installer.uninstall(pair.getPackageName(), pair.getUserId(), true);
                    }
                    // Standard (Non-root fallback)
                    else {
                        // This will trigger a system confirmation dialog to keep data
                        PackageInstallerCompat installer = PackageInstallerCompat.getNewInstance();
                        success = installer.uninstall(pair.getPackageName(), pair.getUserId(), true);
                    }

                    if (success) {
                        ArchivedApp archivedApp = new ArchivedApp(pair.getPackageName(), appName, System.currentTimeMillis(), apkPath);
                        archivedAppDao.insert(archivedApp);
                    } else {
                        failedPackages.add(pair);
                        log(logger, "====> op=ARCHIVE, pkg=" + pair + " failed");
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    failedPackages.add(pair);
                    log(logger, "====> op=ARCHIVE, pkg=" + pair + " not found", e);
                } catch (Exception e) {
                    failedPackages.add(pair);
                    log(logger, "====> op=ARCHIVE, pkg=" + pair, e);
                }
            }
        } finally {
            if (shell != null) {
                shell.close();
            }
        }
        return new BatchOpsManager.Result(failedPackages);
    }

    private static void log(@Nullable Logger logger, String msg) {
        if (logger != null) logger.println(msg);
    }

    private static void log(@Nullable Logger logger, String msg, Throwable tr) {
        if (logger != null) logger.println(msg, tr);
    }
}
