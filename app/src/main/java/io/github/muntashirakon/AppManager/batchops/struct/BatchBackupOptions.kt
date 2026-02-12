// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct

import android.annotation.UserIdInt
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.json.JSONException
import org.json.JSONObject
import io.github.muntashirakon.AppManager.backup.BackupFlags
import io.github.muntashirakon.AppManager.backup.BackupFlags.BackupFlag
import io.github.muntashirakon.AppManager.backup.BackupUtils
import io.github.muntashirakon.AppManager.backup.struct.BackupOpOptions
import io.github.muntashirakon.AppManager.backup.struct.DeleteOpOptions
import io.github.muntashirakon.AppManager.backup.struct.RestoreOpOptions
import io.github.muntashirakon.AppManager.history.JsonDeserializer
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.AppManager.utils.DateUtils
import io.github.muntashirakon.AppManager.utils.JSONUtils

@Parcelize
data class BatchBackupOptions(
    @BackupFlag private val flags: Int,
    private val backupNames: Array<String>?,
    private val relativeDirs: Array<String>?
) : IBatchOpOptions, Parcelable {

    fun getBackupOpOptions(packageName: String, @UserIdInt userId: Int): BackupOpOptions {
        val customBackup = (flags and BackupFlags.BACKUP_MULTIPLE) != 0
        val backupName = when {
            backupNames != null && backupNames.isNotEmpty() -> backupNames[0]
            customBackup -> DateUtils.formatMediumDateTime(ContextUtils.getContext(), System.currentTimeMillis())
            else -> null
        }
        return BackupOpOptions(packageName, userId, flags, backupName, !customBackup)
    }

    fun getRestoreOpOptions(packageName: String, @UserIdInt userId: Int): RestoreOpOptions {
        // For restore operation, backup names (v4) and relative dirs are only set for single
        // package backups. In all other cases, it only uses base backups.
        val relativeDir = when {
            relativeDirs != null && relativeDirs.isNotEmpty() -> relativeDirs[0]
            backupNames == null || backupNames.isEmpty() -> null // Base backup
            else -> {
                // Generate relative directories
                val backup = BackupUtils.retrieveLatestBackupFromDb(userId, backupNames[0], packageName)
                    ?: throw IllegalArgumentException("Backup with name ${backupNames[0]} doesn't exist.")
                backup.relativeDir
            }
        }
        return RestoreOpOptions(packageName, userId, relativeDir, flags)
    }

    fun getDeleteOpOptions(packageName: String, @UserIdInt userId: Int): DeleteOpOptions {
        // For delete operation, backup names (v4) and relative dirs are only set for single
        // package backups. In all other cases, it only uses base backups.
        val relativeDirs = when {
            this.relativeDirs != null -> this.relativeDirs
            backupNames == null || backupNames.isEmpty() -> null // Base backup
            else -> {
                // Generate relative directories
                backupNames.map { backupName ->
                    val backup = BackupUtils.retrieveLatestBackupFromDb(userId, backupName, packageName)
                        ?: throw IllegalArgumentException("Backup with name $backupName doesn't exist.")
                    backup.relativeDir
                }.filterNotNull().toTypedArray()
            }
        }
        return DeleteOpOptions(packageName, userId, relativeDirs)
    }

    @Throws(JSONException::class)
    constructor(jsonObject: JSONObject) : this(
        flags = jsonObject.getInt("flags"),
        backupNames = JSONUtils.getArrayOrNull(String::class.java, jsonObject.optJSONArray("backup_names")),
        relativeDirs = JSONUtils.getArrayOrNull(String::class.java, jsonObject.optJSONArray("relative_dirs"))
    ) {
        require(jsonObject.getString("tag") == TAG) { "Invalid tag" }
    }

    @Throws(JSONException::class)
    override fun serializeToJson(): JSONObject {
        return JSONObject().apply {
            put("tag", TAG)
            put("flags", flags)
            put("backup_names", JSONUtils.getJSONArray(backupNames))
            put("relative_dirs", JSONUtils.getJSONArray(relativeDirs))
        }
    }

    // Override equals and hashCode because of Array properties
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BatchBackupOptions

        if (flags != other.flags) return false
        if (backupNames != null) {
            if (other.backupNames == null) return false
            if (!backupNames.contentEquals(other.backupNames)) return false
        } else if (other.backupNames != null) return false
        if (relativeDirs != null) {
            if (other.relativeDirs == null) return false
            if (!relativeDirs.contentEquals(other.relativeDirs)) return false
        } else if (other.relativeDirs != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = flags
        result = 31 * result + (backupNames?.contentHashCode() ?: 0)
        result = 31 * result + (relativeDirs?.contentHashCode() ?: 0)
        return result
    }

    companion object {
        const val TAG = "BatchBackupOptions"

        @JvmField
        val DESERIALIZER = JsonDeserializer.Creator { jsonObject: JSONObject ->
            BatchBackupOptions(jsonObject)
        }
    }
}
