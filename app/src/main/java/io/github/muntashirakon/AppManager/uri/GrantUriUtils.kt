// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.uri

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.text.SpannableStringBuilder
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText
import io.github.muntashirakon.AppManager.utils.UIUtils.getStyledKeyValue
import java.io.File
import java.util.*

object GrantUriUtils {
    @JvmStatic
    fun toLocalisedString(context: Context, uri: Uri): CharSequence {
        if (ContentResolver.SCHEME_CONTENT != uri.scheme) {
            throw UnsupportedOperationException("Invalid URI: $uri")
        }
        val authority = uri.authority!!
        val paths = uri.pathSegments
        val isTree = paths.size >= 2 && "tree" == paths[0]
        val isDocument = paths.size >= 2 && "document" == paths[0]
        val basePath = if (isTree) paths[1] else null
        val file: String? = when {
            isTree -> if (paths.size == 4) paths[3] else null
            isDocument -> paths[1]
            else -> uri.path
        }
        val sb = SpannableStringBuilder()
        if (basePath != null) {
            val realPath = getRealPath(authority, basePath)
            sb.append(getStyledKeyValue(context, R.string.folder, realPath ?: basePath))
        }
        if (file != null) {
            if (basePath != null) sb.append("
")
            val realFile = getRealPath(authority, file)
            sb.append(getStyledKeyValue(context, R.string.file, realFile ?: file))
        }
        sb.append("
")
            .append(getSmallerText(getStyledKeyValue(context, R.string.authority, authority)))
            .append("
")
            .append(getSmallerText(getStyledKeyValue(context, R.string.type, if (isTree) "Tree" else "Document")))
        return sb
    }

    private fun getRealPath(authority: String, dirtyFile: String): String? {
        return when (authority) {
            "com.android.externalstorage.documents" -> {
                val splitIndex = dirtyFile.indexOf(":", 1)
                val rootId = dirtyFile.substring(0, splitIndex)
                val path = dirtyFile.substring(splitIndex + 1)
                if ("primary" == rootId || "home" == rootId) {
                    File(Environment.getExternalStorageDirectory(), path).absolutePath
                } else {
                    String.format(Locale.ROOT, "/storage/%s/%s", rootId, path)
                }
            }
            "com.android.providers.downloads.documents" -> {
                val splitIndex = dirtyFile.indexOf(":", 1)
                val rootId = dirtyFile.substring(0, splitIndex)
                val path = dirtyFile.substring(splitIndex + 1)
                if ("raw" == rootId) path else null
            }
            "com.termux.documents" -> dirtyFile
            "me.zhanghai.android.files.file_provider" -> {
                val uri = Uri.parse(dirtyFile)
                if (uri == null || ContentResolver.SCHEME_FILE != uri.scheme) dirtyFile else uri.path
            }
            else -> null
        }
    }
}
