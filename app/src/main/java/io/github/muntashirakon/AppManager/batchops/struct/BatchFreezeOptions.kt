// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.json.JSONException
import org.json.JSONObject
import io.github.muntashirakon.AppManager.history.JsonDeserializer
import io.github.muntashirakon.AppManager.utils.FreezeUtils.FreezeMethod

@Parcelize
data class BatchFreezeOptions(
    @FreezeMethod val type: Int,
    val isPreferCustom: Boolean
) : IBatchOpOptions, Parcelable {

    @Throws(JSONException::class)
    constructor(jsonObject: JSONObject) : this(
        type = jsonObject.getInt("type"),
        isPreferCustom = jsonObject.getBoolean("prefer_custom")
    ) {
        require(jsonObject.getString("tag") == TAG) { "Invalid tag" }
    }

    @Throws(JSONException::class)
    override fun serializeToJson(): JSONObject {
        return JSONObject().apply {
            put("tag", TAG)
            put("type", type)
            put("prefer_custom", isPreferCustom)
        }
    }

    companion object {
        const val TAG = "BatchFreezeOptions"

        @get:JvmStatic
        val DESERIALIZER = JsonDeserializer.Creator { jsonObject: JSONObject ->
            BatchFreezeOptions(jsonObject)
        }
    }
}
