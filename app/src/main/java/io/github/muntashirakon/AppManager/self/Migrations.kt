// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self

import android.annotation.SuppressLint
import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.db.utils.AppDb
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.servermanager.ServerConfig
import io.github.muntashirakon.AppManager.settings.FeatureController
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.AppManager.utils.FileUtils
import io.github.muntashirakon.io.Paths
import java.io.File

@SuppressLint("StaticFieldLeak")
object Migrations {
    val TAG: String = Migrations::class.java.simpleName

    private val MIGRATE_FROM_ALL_VERSION_TO_3_0_0 = object : MigrationTask(409, 410) {
        override fun run() {
            Log.d(TAG, "Running MIGRATE_FROM_ALL_VERSION_TO_3_0_0 from %d to %d", fromVersion, toVersion)
            // Delete am database, am.jar
            val internalFilesDir = ContextUtils.getDeContext(context).filesDir.parentFile
            val paths = arrayOf(
                ServerConfig.getDestJarFile(),
                File(internalFilesDir, "main.jar"),
                File(internalFilesDir, "run_server.sh"),
                context.getDatabasePath("am"),
                context.getDatabasePath("am-shm"),
                context.getDatabasePath("am-wal")
            )
            for (path in paths) {
                FileUtils.deleteSilently(path)
            }
            // Delete old cache dir (removed in v2.6.4 (394))
            val oldCacheDir = context.getExternalFilesDir("cache")
            Paths.get(oldCacheDir).delete()
            // Disable Internet feature by default
            FeatureController.getInstance().modifyState(FeatureController.FEAT_INTERNET, false)
        }
    }

    private val MIGRATE_FROM_3_0_0_RC01_RC04_TO_3_0_0 = object : MigrationTask(406, 410) {
        override fun run() {
            Log.d(TAG, "Running MIGRATE_FROM_3_0_0_RC01_RC04_TO_3_0_0 from %d to %d", fromVersion, toVersion)
            // Clear DB
            AppDb().deleteAllBackups()
        }
    }

    private val MIGRATE_FROM_4_0_5_TO_4_0_6 = object : MigrationTask(445, 446) {
        override fun run() {
            Log.d(TAG, "Running MIGRATE_FROM_4_0_5_TO_4_0_6 from %d to %d", fromVersion, toVersion)
            //  DB
            val newAppsDb = context.getDatabasePath("apps.db")
            if (!newAppsDb.exists()) {
                val oldAppsDb = File(FileUtils.getCachePath(), "apps.db")
                if (oldAppsDb.exists()) {
                    oldAppsDb.renameTo(newAppsDb)
                }
            }
        }
    }

    private val migration: Migration = Migration().apply {
        addTask(MIGRATE_FROM_ALL_VERSION_TO_3_0_0)
        addTask(MIGRATE_FROM_3_0_0_RC01_RC04_TO_3_0_0)
        addTask(MIGRATE_FROM_4_0_5_TO_4_0_6)
    }

    @JvmStatic
    @WorkerThread
    fun startMigration(fromVersion: Long) {
        migration.migrate(fromVersion)
    }
}
