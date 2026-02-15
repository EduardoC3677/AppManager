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
data class RestoreOpOptions(
    @JvmField val packageName: String,
    @UserIdInt @JvmField val userId: Int,
    @JvmField val relativeDir: String?,
    @JvmField val flagsValue: Int
) : Parcelable, IJsonSerializer {

    @JvmField
    val flags: BackupFlags = BackupFlags(flagsValue)

    constructor(
        packageName: String,
        userId: Int,
        relativeDir: String?,
        flags: BackupFlags
    ) : this(packageName, userId, relativeDir, flags.flags)

    @Throws(JSONException::class)
    constructor(jsonObject: JSONObject) : this(
        packageName = jsonObject.getString("package_name"),
        userId = jsonObject.getInt("user_id"),
        relativeDir = JSONUtils.optString(jsonObject, "relative_dir"),
        flagsValue = jsonObject.getInt("flags")
    )

    @Throws(JSONException::class)
    override fun serializeToJson(): JSONObject {
        return JSONObject().apply {
            put("package_name", packageName)
            put("user_id", userId)
            put("relative_dir", relativeDir)
            put("flags", flagsValue)
        }
    }
}
