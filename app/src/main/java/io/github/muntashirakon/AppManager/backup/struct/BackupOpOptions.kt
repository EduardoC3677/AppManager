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
    val flags: BackupFlags,
    val backupName: String?,
    val override: Boolean
) : Parcelable, IJsonSerializer {

    constructor(
        packageName: String,
        userId: Int,
        flags: Int,
        backupName: String?,
        override: Boolean
    ) : this(packageName, userId, BackupFlags(flags), backupName, override)

    @Throws(JSONException::class)
    constructor(jsonObject: JSONObject) : this(
        packageName = jsonObject.getString("package_name"),
        userId = jsonObject.getInt("user_id"),
        flags = BackupFlags(jsonObject.getInt("flags")),
        backupName = JSONUtils.optString(jsonObject, "backup_name"),
        override = jsonObject.getBoolean("override")
    )

    @Throws(JSONException::class)
    override fun serializeToJson(): JSONObject {
        return JSONObject().apply {
            put("package_name", packageName)
            put("user_id", userId)
            put("flags", flags.flags)
            put("backup_name", backupName)
            put("override", override)
        }
    }
}
