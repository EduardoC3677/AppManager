// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.struct

import android.annotation.UserIdInt
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.json.JSONException
import org.json.JSONObject
import io.github.muntashirakon.AppManager.history.IJsonSerializer
import io.github.muntashirakon.AppManager.utils.JSONUtils

@Parcelize
data class DeleteOpOptions(
    val packageName: String,
    @UserIdInt val userId: Int,
    val uuids: Array<String>?
) : Parcelable, IJsonSerializer {

    @Throws(JSONException::class)
    constructor(jsonObject: JSONObject) : this(
        packageName = jsonObject.getString("package_name"),
        userId = jsonObject.getInt("user_id"),
        uuids = JSONUtils.getArray(String::class.java, jsonObject.optJSONArray("relative_dirs"))
    )

    @Throws(JSONException::class)
    override fun serializeToJson(): JSONObject {
        return JSONObject().apply {
            put("package_name", packageName)
            put("user_id", userId)
            put("relative_dirs", JSONUtils.getJSONArray(uuids))
        }
    }

    // Override equals and hashCode because of Array property
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DeleteOpOptions

        if (packageName != other.packageName) return false
        if (userId != other.userId) return false
        if (uuids != null) {
            if (other.uuids == null) return false
            if (!uuids.contentEquals(other.uuids)) return false
        } else if (other.uuids != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = packageName.hashCode()
        result = 31 * result + userId
        result = 31 * result + (uuids?.contentHashCode() ?: 0)
        return result
    }
}
