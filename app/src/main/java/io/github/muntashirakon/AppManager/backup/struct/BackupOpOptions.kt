// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.struct

import android.annotation.UserIdInt
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.json.JSONException
import org.json.JSONObject
import io.github.muntashirakon.AppManager.backup.BackupFlags
import io.github.muntashirakon.AppManager.history.IJsonSerializer
import io.github.muntashirakon.AppManager.utils.JSONUtils

@Parcelize
data class BackupOpOptions(
    val packageName: String,
    @UserIdInt val userId: Int,
    val flagsValue: Int,
    val backupName: String?,
    val override: Boolean
) : Parcelable, IJsonSerializer {

    val flags: BackupFlags
        get() = BackupFlags(flagsValue)

    constructor(
        packageName: String,
        userId: Int,
        flags: BackupFlags,
        backupName: String?,
        override: Boolean
    ) : this(packageName, userId, flags.flags, backupName, override)

    @Throws(JSONException::class)
    constructor(jsonObject: JSONObject) : this(
        packageName = jsonObject.getString("package_name"),
        userId = jsonObject.getInt("user_id"),
        flagsValue = jsonObject.getInt("flags"),
        backupName = JSONUtils.optString(jsonObject, "backup_name"),
        override = jsonObject.getBoolean("override")
    )

    @Throws(JSONException::class)
    override fun serializeToJson(): JSONObject {
        return JSONObject().apply {
            put("package_name", packageName)
            put("user_id", userId)
            put("flags", flagsValue)
            put("backup_name", backupName)
            put("override", override)
        }
    }
}
