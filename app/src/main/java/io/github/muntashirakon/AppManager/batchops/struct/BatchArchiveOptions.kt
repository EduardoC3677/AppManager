// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct

import android.os.Parcel
import android.os.Parcelable
import io.github.muntashirakon.AppManager.history.JsonDeserializer
import kotlinx.parcelize.Parcelize
import org.json.JSONException
import org.json.JSONObject

/**
 * Archive options - simple and focused on archiving only
 * Cache and data cleaning are separate operations
 */
@Parcelize
data class BatchArchiveOptions(
    val mode: Int
) : IBatchOpOptions, Parcelable {

    @Throws(JSONException::class)
    constructor(jsonObject: JSONObject) : this(
        mode = jsonObject.getInt("mode")
    ) {
        require(jsonObject.getString("tag") == TAG) { "Invalid tag" }
    }

    @Throws(JSONException::class)
    override fun serializeToJson(): JSONObject {
        return JSONObject().apply {
            put("tag", TAG)
            put("mode", mode)
        }
    }

    companion object {
        const val TAG = "BatchArchiveOptions"

        @JvmField
        val DESERIALIZER = JsonDeserializer.Creator { jsonObject: JSONObject ->
            BatchArchiveOptions(jsonObject)
        }
    }
}
