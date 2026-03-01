// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct

import android.os.Parcelable
import io.github.muntashirakon.AppManager.history.IJsonSerializer
import io.github.muntashirakon.AppManager.history.JsonDeserializer
import io.github.muntashirakon.AppManager.utils.JSONUtils
import org.json.JSONException

interface IBatchOpOptions : Parcelable, IJsonSerializer {
    companion object {
        @JvmField
        val DESERIALIZER = JsonDeserializer.Creator { jsonObject ->
            val tag = JSONUtils.getString(jsonObject, "tag")
            when (tag) {
                BatchAppOpsOptions.TAG -> BatchAppOpsOptions.DESERIALIZER.deserialize(jsonObject)
                BatchArchiveOptions.TAG -> BatchArchiveOptions.DESERIALIZER.deserialize(jsonObject)
                BatchBackupImportOptions.TAG -> BatchBackupImportOptions.DESERIALIZER.deserialize(jsonObject)
                BatchBackupOptions.TAG -> BatchBackupOptions.DESERIALIZER.deserialize(jsonObject)
                BatchComponentOptions.TAG -> BatchComponentOptions.DESERIALIZER.deserialize(jsonObject)
                BatchDexOptOptions.TAG -> BatchDexOptOptions.DESERIALIZER.deserialize(jsonObject)
                BatchFreezeOptions.TAG -> BatchFreezeOptions.DESERIALIZER.deserialize(jsonObject)
                BatchNetPolicyOptions.TAG -> BatchNetPolicyOptions.DESERIALIZER.deserialize(jsonObject)
                BatchPermissionOptions.TAG -> BatchPermissionOptions.DESERIALIZER.deserialize(jsonObject)
                else -> throw JSONException("Invalid tag: $tag")
            }
        }
    }
}
