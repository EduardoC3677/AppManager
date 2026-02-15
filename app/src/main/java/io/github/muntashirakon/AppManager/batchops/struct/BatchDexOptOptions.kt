// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.json.JSONException
import org.json.JSONObject
import io.github.muntashirakon.AppManager.apk.dexopt.DexOptOptions
import io.github.muntashirakon.AppManager.history.JsonDeserializer

@Parcelize
data class BatchDexOptOptions(
    val dexOptOptions: DexOptOptions
) : IBatchOpOptions, Parcelable {

    @Throws(JSONException::class)
    constructor(jsonObject: JSONObject) : this(
        dexOptOptions = DexOptOptions.DESERIALIZER.deserialize(
            jsonObject.getJSONObject("dex_opt_options")
        )
    ) {
        require(jsonObject.getString("tag") == TAG) { "Invalid tag" }
    }

    @Throws(JSONException::class)
    override fun serializeToJson(): JSONObject {
        return JSONObject().apply {
            put("tag", TAG)
            put("dex_opt_options", dexOptOptions.serializeToJson())
        }
    }

    companion object {
        const val TAG = "BatchDexOptOptions"

        @JvmField
        val DESERIALIZER = JsonDeserializer.Creator { jsonObject: JSONObject ->
            BatchDexOptOptions(jsonObject)
        }
    }
}
