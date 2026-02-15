// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops

import android.content.res.Resources
import android.os.Parcelable
import android.os.UserHandleHidden
import androidx.annotation.StringRes
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.json.JSONException
import org.json.JSONObject
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager.OpType
import io.github.muntashirakon.AppManager.batchops.struct.IBatchOpOptions
import io.github.muntashirakon.AppManager.history.IJsonSerializer
import io.github.muntashirakon.AppManager.history.JsonDeserializer
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.AppManager.utils.JSONUtils

@Parcelize
data class BatchQueueItem(
    @StringRes val titleRes: Int,
    @OpType val op: Int,
    var packages: ArrayList<String>,
    private var _users: ArrayList<Int>?,
    val options: IBatchOpOptions?
) : Parcelable, IJsonSerializer {

    @IgnoredOnParcel
    var users: ArrayList<Int>
        get() = _users ?: run {
            val size = packages.size
            val userId = UserHandleHidden.myUserId()
            ArrayList<Int>(size).apply {
                repeat(size) { add(userId) }
                _users = this
            }
        }
        set(value) {
            _users = value
        }

    val title: String?
        get() {
            val context = ContextUtils.getContext()
            return try {
                context.getString(titleRes)
            } catch (e: Resources.NotFoundException) {
                // This resource may not always be found
                null
            }
        }

    @Throws(JSONException::class)
    constructor(jsonObject: JSONObject) : this(
        titleRes = jsonObject.getInt("title_res"),
        op = jsonObject.getInt("op"),
        packages = JSONUtils.getArray<String>(jsonObject.optJSONArray("packages")),
        _users = if (jsonObject.has("users")) JSONUtils.getArray<Int>(jsonObject.optJSONArray("users")) else null,
        options = jsonObject.optJSONObject("options")?.let {
            IBatchOpOptions.DESERIALIZER.deserialize(it)
        }
    )

    @Throws(JSONException::class)
    override fun serializeToJson(): JSONObject {
        return JSONObject().apply {
            put("title_res", titleRes)
            put("op", op)
            put("packages", JSONUtils.getJSONArray(packages))
            put("users", JSONUtils.getJSONArray(_users))
            put("options", options?.serializeToJson())
        }
    }

    companion object {
        @JvmStatic
        fun getBatchOpQueue(
            @OpType op: Int,
            packages: ArrayList<String>?,
            users: ArrayList<Int>?,
            options: IBatchOpOptions?
        ): BatchQueueItem {
            return BatchQueueItem(
                R.string.batch_ops,
                op,
                packages ?: ArrayList(0),
                users,
                options
            )
        }

        @JvmStatic
        fun getOneClickQueue(
            @OpType op: Int,
            packages: ArrayList<String>?,
            users: ArrayList<Int>?,
            args: IBatchOpOptions?
        ): BatchQueueItem {
            return BatchQueueItem(
                R.string.one_click_ops,
                op,
                packages ?: ArrayList(0),
                users,
                args
            )
        }

        @get:JvmStatic
        val DESERIALIZER = JsonDeserializer.Creator { jsonObject: JSONObject ->
            BatchQueueItem(jsonObject)
        }
    }
}
