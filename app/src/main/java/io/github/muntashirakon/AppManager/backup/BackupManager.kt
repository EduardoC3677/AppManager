// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup

import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5
import io.github.muntashirakon.AppManager.backup.struct.BackupOpOptions
import io.github.muntashirakon.AppManager.backup.struct.DeleteOpOptions
import io.github.muntashirakon.AppManager.backup.struct.RestoreOpOptions
import io.github.muntashirakon.AppManager.db.entity.Backup
import io.github.muntashirakon.AppManager.progress.ProgressHandler
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.AppManager.utils.ExUtils
import io.github.muntashirakon.AppManager.utils.TarUtils
import java.io.IOException
import java.util.*

/**
 * Manage backups for individual package belong to individual user.
 */
class BackupManager {
    private var mRequiresRestart = false

    init {
        ExUtils.exceptionAsIgnored { BackupItems.createNoMediaIfNotExists() }
    }

    fun requiresRestart(): Boolean {
        return mRequiresRestart
    }

    @Throws(BackupException::class)
    fun backup(options: BackupOpOptions, progressHandler: ProgressHandler?) {
        if (options.packageName == "android") {
            throw BackupException("Android System (android) cannot be backed up.")
        }
        if (options.flags.flags == 0) {
            throw BackupException("Backup is requested without any flags.")
        }
        val backupItem: BackupItems.BackupItem = try {
            if (options.override) {
                BackupItems.findOrCreateBackupItem(options.userId, options.backupName, options.packageName)
            } else {
                BackupItems.createBackupItemGracefully(options.userId, options.backupName, options.packageName)
            }
        } catch (e: IOException) {
            throw BackupException("Could not create BackupItem.", e)
        }
        if (progressHandler != null) {
            val max = calculateMaxProgress(options.flags)
            progressHandler.setProgressTextInterface(ProgressHandler.PROGRESS_PERCENT)
            progressHandler.postUpdate(max.toFloat(), 0f)
        }
        BackupOp(options.packageName, options.flags, backupItem, options.userId).use { backupOp ->
            backupOp.runBackup(progressHandler)
            BackupUtils.putBackupToDbAndBroadcast(ContextUtils.getContext(), backupOp.metadata)
        }
    }

    /**
     * Restore a single backup for a given package belonging to the given package
     */
    @Throws(BackupException::class)
    fun restore(options: RestoreOpOptions, progressHandler: ProgressHandler?) {
        if (options.packageName == "android") {
            throw BackupException("Android System (android) cannot be restored.")
        }
        if (options.flags.flags == 0) {
            throw BackupException("Restore is requested without any flags.")
        }
        val backupItem: BackupItems.BackupItem = try {
            if (options.uuid != null) {
                BackupItems.findBackupItem(options.uuid!!)
            } else {
                // Use base backup
                val baseBackup = BackupUtils.retrieveBaseBackupFromDb(options.userId, options.packageName)
                if (baseBackup != null) {
                    baseBackup.item
                } else {
                    throw BackupException("No base backup found.")
                }
            }
        } catch (e: IOException) {
            throw BackupException("Could not get backup files.", e)
        }
        if (progressHandler != null) {
            val max = calculateMaxProgress(options.flags)
            progressHandler.setProgressTextInterface(ProgressHandler.PROGRESS_PERCENT)
            progressHandler.postUpdate(max.toFloat(), 0f)
        }
        RestoreOp(options.packageName, options.flags, backupItem, options.userId).use { restoreOp ->
            restoreOp.runRestore(progressHandler)
            mRequiresRestart = mRequiresRestart or restoreOp.requiresRestart()
        }
    }

    @Throws(BackupException::class)
    fun deleteBackup(options: DeleteOpOptions) {
        val backupItemList: MutableList<BackupItems.BackupItem>
        if (options.uuids == null) {
            // Delete base backup
            val baseBackup = BackupUtils.retrieveBaseBackupFromDb(options.userId, options.packageName)
            if (baseBackup != null) {
                try {
                    backupItemList = mutableListOf(baseBackup.item)
                } catch (e: IOException) {
                    throw BackupException("Could not get backup files.", e)
                }
            } else backupItemList = mutableListOf()
        } else {
            backupItemList = ArrayList(options.uuids!!.size)
            for (relativeDir in options.uuids!!) {
                try {
                    backupItemList.add(BackupItems.findBackupItem(relativeDir))
                } catch (e: IOException) {
                    throw BackupException("Could not get backup files.", e)
                }
            }
        }
        for (backupItem in backupItemList) {
            val metadata: BackupMetadataV5 = try {
                backupItem.metadata
            } catch (e: IOException) {
                throw BackupException("Could not retrieve metadata from backup.", e)
            }
            if (!backupItem.isFrozen() && !backupItem.delete()) {
                throw BackupException("Could not delete the selected backups")
            }
            BackupUtils.deleteBackupToDbAndBroadcast(ContextUtils.getContext(), metadata)
        }
    }

    @Throws(BackupException::class)
    fun verify(relativeDir: String) {
        val backupItem: BackupItems.BackupItem = try {
            BackupItems.findBackupItem(relativeDir)
        } catch (e: IOException) {
            throw BackupException("Could not get backup files.", e)
        }
        VerifyOp(backupItem).use { verifyOp ->
            verifyOp.verify()
        }
    }

    private fun calculateMaxProgress(flags: BackupFlags): Int {
        var tasks = 1
        if (flags.backupApkFiles()) ++tasks
        if (flags.backupData()) ++tasks
        if (flags.backupExtras()) ++tasks
        if (flags.backupRules()) ++tasks
        return tasks
    }

    companion object {
        @JvmField
        val TAG: String = BackupManager::class.java.simpleName

        @JvmField
        val CACHE_DIRS = arrayOf("cache/.*", "code_cache/.*", "no_backup/.*")
        @JvmField
        val LIB_DIR = arrayOf("lib/")
        const val SOURCE_PREFIX = "source"
        const val DATA_PREFIX = "data"
        const val KEYSTORE_PREFIX = "keystore"
        const val KEYSTORE_PLACEHOLDER = -1000
        const val DATA_BACKUP_SPECIAL_PREFIX = "special:"
        const val DATA_BACKUP_SPECIAL_ADB = DATA_BACKUP_SPECIAL_PREFIX + "adb"

        const val CERT_PREFIX = "cert_"
        const val MASTER_KEY = ".masterkey"

        @JvmStatic
        fun getExt(@TarUtils.TarType tarType: String): String {
            return when (tarType) {
                TarUtils.TAR_BZIP2 -> ".tar.bz2"
                TarUtils.TAR_ZSTD -> ".tar.zst"
                else -> ".tar.gz"
            }
        }
    }
}
