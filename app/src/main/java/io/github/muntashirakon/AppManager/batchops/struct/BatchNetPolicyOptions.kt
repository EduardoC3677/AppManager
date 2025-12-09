// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.json.JSONException
import org.json.JSONObject
import io.github.muntashirakon.AppManager.compat.NetworkPolicyManagerCompat.NetPolicy
import io.github.muntashirakon.AppManager.history.JsonDeserializer

@Parcelize
data class BatchNetPolicyOptions(
    @NetPolicy val policies: Int
) : IBatchOpOptions, Parcelable {

    @Throws(JSONException::class)
    constructor(jsonObject: JSONObject) : this(
        policies = jsonObject.getInt("policies")
    ) {
        require(jsonObject.getString("tag") == TAG) { "Invalid tag" }
    }

    @Throws(JSONException::class)
    override fun serializeToJson(): JSONObject {
        return JSONObject().apply {
            put("tag", TAG)
            put("policies", policies)
        }
    }

    companion object {
        const val TAG = "BatchNetPolicyOptions"

        @JvmField
        val DESERIALIZER = JsonDeserializer.Creator { jsonObject: JSONObject ->
            BatchNetPolicyOptions(jsonObject)
        }
    }
}
