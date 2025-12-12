// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.struct

import android.annotation.UserIdInt
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import org.json.JSONException
import org.json.JSONObject
import io.github.muntashirakon.AppManager.backup.BackupFlags
import io.github.muntashirakon.AppManager.history.IJsonSerializer
import io.github.muntashirakon.AppManager.utils.JSONUtils

@Parcelize
data class RestoreOpOptions(
    val packageName: String,
    @UserIdInt val userId: Int,
    val relativeDir: String?,
    @field:RawValue val flags: BackupFlags
) : Parcelable, IJsonSerializer {

    constructor(
        packageName: String,
        userId: Int,
        relativeDir: String?,
        flags: Int
    ) : this(packageName, userId, relativeDir, BackupFlags(flags))

    @Throws(JSONException::class)
    constructor(jsonObject: JSONObject) : this(
        packageName = jsonObject.getString("package_name"),
        userId = jsonObject.getInt("user_id"),
        relativeDir = JSONUtils.optString(jsonObject, "relative_dir"),
        flags = BackupFlags(jsonObject.getInt("flags"))
    )

    @Throws(JSONException::class)
    override fun serializeToJson(): JSONObject {
        return JSONObject().apply {
            put("package_name", packageName)
            put("user_id", userId)
            put("relative_dir", relativeDir)
            put("flags", flags.flags)
        }
    }
}
