// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.json.JSONException
import org.json.JSONObject
import io.github.muntashirakon.AppManager.backup.convert.ImportType
import io.github.muntashirakon.AppManager.history.JsonDeserializer

@Parcelize
data class BatchBackupImportOptions(
    @ImportType val importType: Int,
    val directory: Uri,
    val isRemoveImportedDirectory: Boolean
) : IBatchOpOptions, Parcelable {

    @Throws(JSONException::class)
    constructor(jsonObject: JSONObject) : this(
        importType = jsonObject.getInt("import_type"),
        directory = Uri.parse(jsonObject.getString("directory")),
        isRemoveImportedDirectory = jsonObject.getBoolean("remove_imported_directory")
    ) {
        require(jsonObject.getString("tag") == TAG) { "Invalid tag" }
    }

    @Throws(JSONException::class)
    override fun serializeToJson(): JSONObject {
        return JSONObject().apply {
            put("tag", TAG)
            put("import_type", importType)
            put("directory", directory.toString())
            put("remove_imported_directory", isRemoveImportedDirectory)
        }
    }

    companion object {
        const val TAG = "BatchBackupImportOptions"

        @get:JvmStatic
        val DESERIALIZER = JsonDeserializer.Creator { jsonObject: JSONObject ->
            BatchBackupImportOptions(jsonObject)
        }
    }
}
