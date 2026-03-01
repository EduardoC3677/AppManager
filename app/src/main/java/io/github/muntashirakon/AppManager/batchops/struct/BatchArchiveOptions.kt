// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct

import android.os.Parcel
import android.os.Parcelable
import io.github.muntashirakon.AppManager.history.JsonDeserializer
import kotlinx.parcelize.Parcelize
import org.json.JSONException
import org.json.JSONObject

@Parcelize
data class BatchArchiveOptions(
    val mode: Int,
    val archiveOptions: Int = 0,  // Bitmask of ARCHIVE_* flags
    val includeCacheClean: Boolean = false,
    val includeDataClean: Boolean = false
) : IBatchOpOptions, Parcelable {

    @Throws(JSONException::class)
    constructor(jsonObject: JSONObject) : this(
        mode = jsonObject.getInt("mode"),
        archiveOptions = jsonObject.optInt("archiveOptions", 0),
        includeCacheClean = jsonObject.optBoolean("includeCacheClean", false),
        includeDataClean = jsonObject.optBoolean("includeDataClean", false)
    ) {
        require(jsonObject.getString("tag") == TAG) { "Invalid tag" }
    }

    @Throws(JSONException::class)
    override fun serializeToJson(): JSONObject {
        return JSONObject().apply {
            put("tag", TAG)
            put("mode", mode)
            put("archiveOptions", archiveOptions)
            put("includeCacheClean", includeCacheClean)
            put("includeDataClean", includeDataClean)
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
