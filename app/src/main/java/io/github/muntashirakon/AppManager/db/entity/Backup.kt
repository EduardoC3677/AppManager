//SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.entity

import android.text.TextUtils
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.muntashirakon.AppManager.backup.BackupFlags
import io.github.muntashirakon.AppManager.backup.BackupItems
import io.github.muntashirakon.AppManager.backup.BackupUtils
import io.github.muntashirakon.AppManager.backup.CryptoUtils
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV2
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5
import io.github.muntashirakon.AppManager.utils.TarUtils
import java.io.IOException
import java.util.Objects

@Suppress("UNUSED_PARAMETER", "unused")
@Entity(tableName = "backup", primaryKeys = ["backup_name", "package_name"])
data class Backup @JvmOverloads constructor(
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    public var packageName: String = "",

    @PrimaryKey
    @ColumnInfo(name = "backup_name")
    public var backupName: String = "",

    @JvmField
    @ColumnInfo(name = "label")
    var label: String? = null,

    @JvmField
    @ColumnInfo(name = "version_name")
    var versionName: String? = null,

    @JvmField
    @ColumnInfo(name = "version_code")
    var versionCode: Long = 0,

    @JvmField
    @ColumnInfo(name = "is_system")
    var isSystem: Boolean = false,

    @JvmField
    @ColumnInfo(name = "has_splits")
    var hasSplits: Boolean = false,

    @JvmField
    @ColumnInfo(name = "has_rules")
    var hasRules: Boolean = false,

    @JvmField
    @ColumnInfo(name = "backup_time")
    var backupTime: Long = 0,

    @JvmField
    @ColumnInfo(name = "crypto")
    @CryptoUtils.Mode
    var crypto: String? = null,

    @JvmField
    @ColumnInfo(name = "meta_version")
    var version: Int = 0,

    @JvmField
    @ColumnInfo(name = "flags")
    var flags: Int = 0,

    @PrimaryKey
    @ColumnInfo(name = "user_id")
    public var userId: Int = 0,

    @JvmField
    @ColumnInfo(name = "tar_type")
    @TarUtils.TarType
    var tarType: String? = null,

    @JvmField
    @ColumnInfo(name = "has_key_store")
    var hasKeyStore: Boolean = false,

    @JvmField
    @ColumnInfo(name = "installer_app")
    var installer: String? = null,

    @JvmField
    @ColumnInfo(name = "info_hash")
    var uuid: String? = null
) {
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
            return Backup().apply {
                packageName = metadata.packageName
                backupName = metadata.backupName ?: ""
                label = metadata.label
                versionName = metadata.versionName
                versionCode = metadata.versionCode
                isSystem = metadata.isSystem
                hasSplits = metadata.isSplitApk
                hasRules = metadata.hasRules
                backupTime = metadata.backupTime
                crypto = metadata.crypto
                version = metadata.version
                flags = metadata.flags.flags
                userId = metadata.userId
                tarType = metadata.tarType
                hasKeyStore = metadata.keyStore
                installer = metadata.installer
                uuid = metadata.backupItem.getRelativeDir()
            }
        }

        @JvmStatic
        fun fromBackupMetadataV5(metadata: BackupMetadataV5): Backup {
            return fromBackupInfoAndMeta(metadata.info, metadata.metadata)
        }

        @JvmStatic
        fun fromBackupInfoAndMeta(info: BackupMetadataV5.Info, metadata: BackupMetadataV5.Metadata): Backup {
            return Backup().apply {
                packageName = metadata.packageName
                backupName = metadata.backupName ?: ""
                label = metadata.label
                versionName = metadata.versionName
                versionCode = metadata.versionCode
                isSystem = metadata.isSystem
                hasSplits = metadata.isSplitApk
                hasRules = metadata.hasRules
                backupTime = info.backupTime
                crypto = info.crypto
                version = metadata.version
                flags = info.flags.flags
                userId = info.userId
                tarType = info.tarType
                hasKeyStore = metadata.keyStore
                installer = metadata.installer
                uuid = info.getRelativeDir()
            }
        }
    }
}