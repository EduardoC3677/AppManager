// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.entity

import android.text.TextUtils
import androidx.room.ColumnInfo
import androidx.room.Entity
import io.github.muntashirakon.AppManager.backup.BackupFlags
import io.github.muntashirakon.AppManager.backup.BackupItems
import io.github.muntashirakon.AppManager.backup.BackupUtils
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV2
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5
import io.github.muntashirakon.AppManager.utils.CryptoUtils
import io.github.muntashirakon.io.TarUtils
import java.io.IOException

@Entity(tableName = "backup", primaryKeys = ["backup_name", "package_name"])
class Backup {
    @JvmField
    @ColumnInfo(name = "package_name")
    var packageName: String = ""

    @JvmField
    @ColumnInfo(name = "backup_name")
    var backupName: String = ""

    @JvmField
    @ColumnInfo(name = "package_label")
    var label: String? = null

    @JvmField
    @ColumnInfo(name = "version_name")
    var versionName: String? = null

    @JvmField
    @ColumnInfo(name = "version_code")
    var versionCode: Long = 0

    @JvmField
    @ColumnInfo(name = "is_system")
    var isSystem: Boolean = false

    @JvmField
    @ColumnInfo(name = "has_splits")
    var hasSplits: Boolean = false

    @JvmField
    @ColumnInfo(name = "has_rules")
    var hasRules: Boolean = false

    @JvmField
    @ColumnInfo(name = "backup_time")
    var backupTime: Long = 0

    @JvmField
    @ColumnInfo(name = "crypto")
    var crypto: String? = null

    @JvmField
    @ColumnInfo(name = "meta_version")
    var version: Int = 0

    @JvmField
    @ColumnInfo(name = "flags")
    var flags: Int = 0

    @JvmField
    @ColumnInfo(name = "user_id")
    var userId: Int = 0

    @JvmField
    @ColumnInfo(name = "tar_type")
    var tarType: String? = null

    @JvmField
    @ColumnInfo(name = "has_key_store")
    var hasKeyStore: Boolean = false

    @JvmField
    @ColumnInfo(name = "installer_app")
    var installer: String? = null

    @JvmField
    @ColumnInfo(name = "info_hash")
    var uuid: String? = null

    fun getRelativeDir(): String? {
        return uuid
    }

    fun getFlags(): BackupFlags {
        return BackupFlags(flags)
    }

    @Throws(IOException::class)
    fun getItem(): BackupItems.BackupItem {
        val relativeDir = when {
            TextUtils.isEmpty(this.uuid) -> {
                if (version >= 5) {
                    // In backup v5 onwards, relativeDir must be set
                    throw IOException("relativeDir not set.")
                }
                // Relative directory needs to be inferred.
                BackupUtils.getV4RelativeDir(userId, backupName, packageName)
            }
            else -> this.uuid!!
        }
        return BackupItems.findBackupItem(relativeDir)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Backup) return false
        return packageName == other.packageName &&
                userId == other.userId &&
                backupName == other.backupName
    }

    override fun hashCode(): Int {
        var result = packageName.hashCode()
        result = 31 * result + userId
        result = 31 * result + backupName.hashCode()
        return result
    }

    companion object {
        @JvmStatic
        fun fromBackupMetadata(metadata: BackupMetadataV2): Backup {
            val backup = Backup()
            backup.packageName = metadata.packageName
            backup.backupName = metadata.backupName ?: ""
            backup.label = metadata.label
            backup.versionName = metadata.versionName
            backup.versionCode = metadata.versionCode
            backup.isSystem = metadata.isSystem
            backup.hasSplits = metadata.isSplitApk
            backup.hasRules = metadata.hasRules
            backup.backupTime = metadata.backupTime
            backup.crypto = metadata.crypto
            backup.version = metadata.version
            backup.flags = metadata.flags.flags
            backup.userId = metadata.userId
            backup.tarType = metadata.tarType
            backup.hasKeyStore = metadata.keyStore
            backup.installer = metadata.installer
            backup.uuid = metadata.backupItem.getRelativeDir()
            return backup
        }

        @JvmStatic
        fun fromBackupMetadataV5(metadata: BackupMetadataV5): Backup {
            return fromBackupInfoAndMeta(metadata.info, metadata.metadata)
        }

        @JvmStatic
        fun fromBackupInfoAndMeta(info: BackupMetadataV5.Info, metadata: BackupMetadataV5.Metadata): Backup {
            val backup = Backup()
            backup.packageName = metadata.packageName
            backup.backupName = metadata.backupName ?: ""
            backup.label = metadata.label
            backup.versionName = metadata.versionName
            backup.versionCode = metadata.versionCode
            backup.isSystem = metadata.isSystem
            backup.hasSplits = metadata.isSplitApk
            backup.hasRules = metadata.hasRules
            backup.backupTime = info.backupTime
            backup.crypto = info.crypto
            backup.version = metadata.version
            backup.flags = info.flags.flags
            backup.userId = info.userId
            backup.tarType = info.tarType
            backup.hasKeyStore = metadata.keyStore
            backup.installer = metadata.installer
            backup.uuid = info.getRelativeDir()
            return backup
        }
    }
}
