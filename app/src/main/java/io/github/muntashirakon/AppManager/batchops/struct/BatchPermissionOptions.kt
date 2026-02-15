// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.json.JSONException
import org.json.JSONObject
import io.github.muntashirakon.AppManager.history.JsonDeserializer
import io.github.muntashirakon.AppManager.utils.JSONUtils

@Parcelize
data class BatchPermissionOptions(
    val permissions: Array<String>
) : IBatchOpOptions, Parcelable {

    @Throws(JSONException::class)
    constructor(jsonObject: JSONObject) : this(
        permissions = JSONUtils.getArray(String::class.java, jsonObject.optJSONArray("permissions"))
    ) {
        require(jsonObject.getString("tag") == TAG) { "Invalid tag" }
    }

    @Throws(JSONException::class)
    override fun serializeToJson(): JSONObject {
        return JSONObject().apply {
            put("tag", TAG)
            put("permissions", JSONUtils.getJSONArray(permissions))
        }
    }

    // Override equals and hashCode because of Array property
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BatchPermissionOptions

        return permissions.contentEquals(other.permissions)
    }

    override fun hashCode(): Int {
        return permissions.contentHashCode()
    }

    companion object {
        const val TAG = "BatchPermissionOptions"

        @get:JvmStatic
        val DESERIALIZER = JsonDeserializer.Creator { jsonObject: JSONObject ->
            BatchPermissionOptions(jsonObject)
        }
    }
}
