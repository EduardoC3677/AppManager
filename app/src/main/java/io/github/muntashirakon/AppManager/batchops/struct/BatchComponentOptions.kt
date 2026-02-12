// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.json.JSONException
import org.json.JSONObject
import io.github.muntashirakon.AppManager.history.JsonDeserializer
import io.github.muntashirakon.AppManager.utils.JSONUtils

@Parcelize
data class BatchComponentOptions(
    val signatures: Array<String>
) : IBatchOpOptions, Parcelable {

    @Throws(JSONException::class)
    constructor(jsonObject: JSONObject) : this(
        signatures = JSONUtils.getArray(String::class.java, jsonObject.getJSONArray("signatures"))
    ) {
        require(jsonObject.getString("tag") == TAG) { "Invalid tag" }
    }

    @Throws(JSONException::class)
    override fun serializeToJson(): JSONObject {
        return JSONObject().apply {
            put("tag", TAG)
            put("signatures", JSONUtils.getJSONArray(signatures))
        }
    }

    // Override equals and hashCode because of Array property
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BatchComponentOptions

        return signatures.contentEquals(other.signatures)
    }

    override fun hashCode(): Int {
        return signatures.contentHashCode()
    }

    companion object {
        const val TAG = "BatchComponentOptions"

        @JvmField
        val DESERIALIZER = JsonDeserializer.Creator { jsonObject: JSONObject ->
            BatchComponentOptions(jsonObject)
        }
    }
}
