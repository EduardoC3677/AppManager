// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup

import android.annotation.SuppressLint
import android.annotation.UserIdInt
import android.content.Context
import android.os.UserHandleHidden
import android.text.TextUtils
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5
import io.github.muntashirakon.AppManager.db.entity.Backup
import io.github.muntashirakon.AppManager.db.utils.AppDb
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.misc.OsEnvironment
import io.github.muntashirakon.AppManager.users.Users
import io.github.muntashirakon.AppManager.utils.ArrayUtils
import io.github.muntashirakon.AppManager.utils.BroadcastUtils
import io.github.muntashirakon.AppManager.utils.TarUtils
import io.github.muntashirakon.AppManager.utils.Utils
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import org.jetbrains.annotations.Contract
import java.io.File
import java.io.IOException
import java.util.*
import java.util.regex.Pattern
import java.util.stream.Collectors

object BackupUtils {
    @JvmField
    val TAG: String = BackupUtils::class.java.simpleName

    @JvmField
    val TAR_TYPES = arrayOf(TarUtils.TAR_GZIP, TarUtils.TAR_BZIP2, TarUtils.TAR_ZSTD)
    @JvmField
    val TAR_TYPES_READABLE = arrayOf("GZip", "BZip2", "Zstandard")

    private val UUID_PATTERN = Pattern.compile("[a-f\d]{8}(-[a-f\d]{4}){3}-[a-f\d]{12}")

    @JvmStatic
    fun isUuid(name: String): Boolean {
        return UUID_PATTERN.matcher(name).matches()
    }

    @JvmStatic
    @Contract("!null -> !null")
    fun getCompatBackupName(backupName: String?): String? {
        if (MetadataManager.getCurrentBackupMetaVersion() >= 5) {
            return backupName
        }
        return getV4SanitizedBackupName(backupName)
    }

    @JvmStatic
    fun getV4BackupName(@UserIdInt userId: Int, backupName: String?): String {
        if (backupName == null) {
            return userId.toString()
        }
        // For v4 and earlier, backup name is used as a filename. So, necessary sanitization may be
        // required.
        return userId.toString() + "_" + getV4SanitizedBackupName(backupName)
    }

    @JvmStatic
    @Contract("!null -> !null")
    fun getV4SanitizedBackupName(backupName: String?): String? {
        if (backupName == null) {
            return null
        }
        // [\/:?"<>|\s]
        return backupName.trim { it <= ' ' }.replace("[\/:?"<>|\s]+".toRegex(), "_")
    }

    @JvmStatic
    fun getV5RelativeDir(backupUuid: String): String {
        // backups/{backupUuid}
        return BackupItems.BACKUP_DIRECTORY + File.separator + backupUuid
    }

    @JvmStatic
    fun getV4RelativeDir(backupNameWithUser: String, packageName: String): String {
        // Relative directory needs to be inferred: {packageName}/{backupNameWithUser}
        // where backupNameWithUser = {userid}[_{backup_name}]
        return packageName + File.separator + backupNameWithUser
    }

    @JvmStatic
    fun getV4RelativeDir(@UserIdInt userId: Int, backupName: String?, packageName: String): String {
        // Relative directory needs to be inferred: {packageName}/{backupName}
        // where backupName = {userid}[_{backup_name}]
        return packageName + File.separator + getV4BackupName(userId, backupName)
    }

    @JvmStatic
    fun getRealBackupName(backupVersion: Int, backupNameWithUserId: String?): String? {
        if (backupVersion >= 5) {
            return backupNameWithUserId
        } else {
            // v4 or earlier backup: {userid}[_{backup_name}]
            if (backupNameWithUserId == null || TextUtils.isDigitsOnly(backupNameWithUserId)) {
                // It's only a user ID
                return null
            } else {
                val firstUnderscore = backupNameWithUserId.indexOf('_')
                if (firstUnderscore != -1) {
                    // Found an underscore
                    val userHandle = backupNameWithUserId.substring(0, firstUnderscore)
                    if (TextUtils.isDigitsOnly(userHandle)) {
                        return backupNameWithUserId.substring(firstUnderscore + 1)
                    }
                }
                throw IllegalArgumentException("Invalid backup name $backupNameWithUserId")
            }
        }
    }

    @JvmStatic
    fun getReadableTarType(@TarUtils.TarType tarType: String): String {
        val i = ArrayUtils.indexOf(TAR_TYPES, tarType)
        return if (i == -1) {
            "GZip"\n} else TAR_TYPES_READABLE[i]
    }

    @JvmStatic
    @WorkerThread
    fun storeAllAndGetLatestBackupMetadata(): HashMap<String, Backup> {
        val appDb = AppDb()
        val backupMetadata = HashMap<String, Backup>()
        val allBackupMetadata = getAllMetadata()
        val backups: MutableList<Backup> = ArrayList()
        for (metadataList in allBackupMetadata.values) {
            if (metadataList.isEmpty()) continue
            var latestBackup: Backup? = null
            var backup: Backup
            for (metadataV5 in metadataList) {
                backup = Backup.fromBackupMetadataV5(metadataV5)
                backups.add(backup)
                if (latestBackup == null || backup.backupTime > latestBackup.backupTime) {
                    latestBackup = backup
                }
            }
            if (latestBackup != null) {
                backupMetadata[latestBackup.packageName] = latestBackup
            }
        }
        appDb.deleteAllBackups()
        appDb.insertBackups(backups)
        return backupMetadata
    }

    @JvmStatic
    @WorkerThread
    fun getAllLatestBackupMetadataFromDb(): HashMap<String, Backup> {
        val backupMetadata = HashMap<String, Backup>()
        for (backup in AppDb().allBackups) {
            val latestBackup = backupMetadata[backup.packageName]
            if (latestBackup == null || backup.backupTime > latestBackup.backupTime) {
                backupMetadata[backup.packageName] = backup
            }
        }
        return backupMetadata
    }

    @JvmStatic
    fun putBackupToDbAndBroadcast(context: Context, metadata: BackupMetadataV5) {
        if (Utils.isRoboUnitTest()) {
            return
        }
        val appDb = AppDb()
        appDb.insert(Backup.fromBackupMetadataV5(metadata))
        appDb.updateApplication(context, metadata.metadata.packageName)
        BroadcastUtils.sendDbPackageAltered(context, arrayOf(metadata.metadata.packageName))
    }

    @JvmStatic
    fun deleteBackupToDbAndBroadcast(context: Context, metadata: BackupMetadataV5) {
        val appDb = AppDb()
        appDb.deleteBackup(Backup.fromBackupMetadataV5(metadata))
        appDb.updateApplication(context, metadata.metadata.packageName)
        BroadcastUtils.sendDbPackageAltered(context, arrayOf(metadata.metadata.packageName))
    }

    @JvmStatic
    @WorkerThread
    fun getBackupMetadataFromDbNoLockValidate(packageName: String): List<Backup> {
        val backups = AppDb().getAllBackupsNoLock(packageName)
        val validatedBackups: MutableList<Backup> = ArrayList(backups.size)
        for (backup in backups) {
            try {
                if (backup.item.exists()) {
                    validatedBackups.add(backup)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return validatedBackups
    }

    @JvmStatic
    fun retrieveBackupFromDb(
        @UserIdInt userId: Int,
        backupName: String?,
        packageName: String
    ): List<Backup> {
        val backups = getBackupMetadataFromDbNoLockValidate(packageName)
        val sanitizedBackupName = getV4SanitizedBackupName(backupName ?: "")
        val backupList: MutableList<Backup> = ArrayList()
        for (backup in backups) {
            if (backup.userId != userId) {
                continue
            }
            if (sanitizedBackupName != getV4SanitizedBackupName(backup.backupName)) {
                continue
            }
            backupList.add(backup)
        }
        return backupList
    }

    @JvmStatic
    fun retrieveLatestBackupFromDb(
        @UserIdInt userId: Int,
        backupName: String?,
        packageName: String
    ): Backup? {
        val backups = getBackupMetadataFromDbNoLockValidate(packageName)
        val sanitizedBackupName = getV4SanitizedBackupName(backupName ?: "")
        for (backup in backups) {
            if (backup.userId == userId && sanitizedBackupName == getV4SanitizedBackupName(backup.backupName)) {
                return backup
            }
        }
        return null
    }

    @JvmStatic
    fun retrieveBaseBackupFromDb(
        @UserIdInt userId: Int,
        packageName: String
    ): Backup? {
        val backups = getBackupMetadataFromDbNoLockValidate(packageName)
        for (backup in backups) {
            if (backup.userId == userId && TextUtils.isEmpty(backup.backupName)) {
                return backup
            }
        }
        return null
    }

    @JvmStatic
    @WorkerThread
    fun getLatestBackupMetadataFromDbNoLockValidate(packageName: String): Backup? {
        val backups = getBackupMetadataFromDbNoLockValidate(packageName)
        var latestBackup: Backup? = null
        for (backup in backups) {
            if (latestBackup == null || backup.backupTime > latestBackup.backupTime) {
                latestBackup = backup
            }
        }
        return latestBackup
    }

    /**
     * Retrieves all metadata for all packages
     */
    @WorkerThread
    private fun getAllMetadata(): HashMap<String, List<BackupMetadataV5>> {
        val backupMetadata = HashMap<String, List<BackupMetadataV5>>()
        val backupPaths = BackupItems.findAllBackupItems()
        for (backupItem in backupPaths) {
            try {
                val metadataV5 = backupItem.metadata
                val metadata = metadataV5.metadata
                if (!backupMetadata.containsKey(metadata.packageName)) {
                    backupMetadata[metadata.packageName] = ArrayList()
                }
                (backupMetadata[metadata.packageName] as MutableList<BackupMetadataV5>).add(metadataV5)
            } catch (e: IOException) {
                Log.w(TAG, "Invalid backup: %s", e, backupItem.relativeDir)
            }
        }
        return backupMetadata
    }

    @JvmStatic
    fun getSourceFilePrefix(fullExtension: String?): String {
        return if (fullExtension == null) {
            BackupManager.SOURCE_PREFIX
        } else BackupManager.SOURCE_PREFIX + fullExtension
    }

    @JvmStatic
    fun getDataFilePrefix(index: Int, fullExtension: String?): String {
        return if (fullExtension == null) {
            BackupManager.DATA_PREFIX + index
        } else BackupManager.DATA_PREFIX + index + fullExtension
    }

    @JvmStatic
    fun getExcludeDirs(excludeCache: Boolean, publicSourceDir: String?): Array<String> {
        val excludeDirs: MutableList<String> = ArrayList()
        if (excludeCache) {
            excludeDirs.add("cache/")
            excludeDirs.add("code_cache/")
        }
        if (publicSourceDir != null) {
            excludeDirs.add(publicSourceDir)
        }
        return excludeDirs.toTypedArray()
    }

    @SuppressLint("SdCardPath")
    @JvmStatic
    fun getWritableDataDirectory(dataDir: String, backupUserId: Int, targetUserId: Int): String {
        if (backupUserId == targetUserId) {
            return dataDir
        }
        // data/user/{id}
        var storageCe = String.format(Locale.ROOT, "/data/user/%d/", backupUserId)
        if (dataDir.startsWith(storageCe)) {
            return dataDir.replaceFirst(storageCe.toRegex(), String.format(Locale.ROOT, "/data/user/%d/", targetUserId))
        }
        // data/data/ (User 0 only)
        if (dataDir.startsWith("/data/data/")) {
            return dataDir.replaceFirst("/data/data/".toRegex(), String.format(Locale.ROOT, "/data/user/%d/", targetUserId))
        }
        // data/user_de/{id}
        val storageDe = String.format(Locale.ROOT, "/data/user_de/%d/", backupUserId)
        if (dataDir.startsWith(storageDe)) {
            return dataDir.replaceFirst(storageDe.toRegex(), String.format(Locale.ROOT, "/data/user_de/%d/", targetUserId))
        }
        // storage/emulated/{id}
        val storageEmulatedDir = String.format(Locale.ROOT, "/storage/emulated/%d/", backupUserId)
        if (dataDir.startsWith(storageEmulatedDir)) {
            return dataDir.replaceFirst(
                storageEmulatedDir.toRegex(),
                String.format(Locale.ROOT, "/storage/emulated/%d/", targetUserId)
            )
        }
        // data/media/{id}
        val dataMediaDir = String.format(Locale.ROOT, "/data/media/%d/", backupUserId)
        if (dataDir.startsWith(dataMediaDir)) {
            return dataDir.replaceFirst(dataMediaDir.toRegex(), String.format(Locale.ROOT, "/data/media/%d/", targetUserId))
        }
        // sdcard (User 0 only)
        if (dataDir.startsWith("/sdcard/")) {
            return dataDir.replaceFirst("/sdcard/".toRegex(), String.format(Locale.ROOT, "/storage/emulated/%d/", targetUserId))
        }
        return dataDir
    }

    @VisibleForTesting
    @JvmStatic
    fun getV5RelativeDirFromV4(packageName: String, userIdBackupName: String): String {
        val packageNameDir = Prefs.Storage.getAppManagerDirectory().findOrCreateDirectory(packageName)
        val backupDir = packageNameDir.findOrCreateDirectory(userIdBackupName)
        val backupUuid = UUID.randomUUID().toString()
        val newBackupPath = Prefs.Storage.getAppManagerDirectory()
            .findOrCreateDirectory(BackupItems.BACKUP_DIRECTORY)
            .findOrCreateDirectory(backupUuid)
        backupDir.moveTo(newBackupPath)
        // Delete packageNameDir if empty
        if (packageNameDir.listFiles().isEmpty()) {
            packageNameDir.delete()
        }
        return backupUuid
    }
}
