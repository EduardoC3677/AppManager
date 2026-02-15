// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.json.JSONException
import org.json.JSONObject
import io.github.muntashirakon.AppManager.history.JsonDeserializer
import io.github.muntashirakon.AppManager.utils.JSONUtils

@Parcelize
data class BatchAppOpsOptions(
    val appOps: IntArray,
    val mode: Int
) : IBatchOpOptions, Parcelable {

    @Throws(JSONException::class)
    constructor(jsonObject: JSONObject) : this(
        appOps = JSONUtils.getIntArray(jsonObject.optJSONArray("app_ops")),
        mode = jsonObject.getInt("mode")
    ) {
        require(jsonObject.getString("tag") == TAG) { "Invalid tag" }
    }

    @Throws(JSONException::class)
    override fun serializeToJson(): JSONObject {
        return JSONObject().apply {
            put("tag", TAG)
            put("app_ops", JSONUtils.getJSONArray(appOps))
            put("mode", mode)
        }
    }

    // Override equals and hashCode because of IntArray property
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BatchAppOpsOptions

        if (!appOps.contentEquals(other.appOps)) return false
        if (mode != other.mode) return false

        return true
    }

    override fun hashCode(): Int {
        var result = appOps.contentHashCode()
        result = 31 * result + mode
        return result
    }

    companion object {
        const val TAG = "BatchAppOpsOptions"

        @get:JvmStatic
        val DESERIALIZER = JsonDeserializer.Creator { jsonObject: JSONObject ->
            BatchAppOpsOptions(jsonObject)
        }
    }
}
