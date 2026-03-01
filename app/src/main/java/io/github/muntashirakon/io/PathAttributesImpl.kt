// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.system.OsConstants
import androidx.core.provider.DocumentsContractCompat
import androidx.documentfile.provider.DocumentFile
import androidx.documentfile.provider.DocumentFileUtils
import androidx.documentfile.provider.ExtendedRawDocumentFile
import androidx.documentfile.provider.VirtualDocumentFile
import io.github.muntashirakon.AppManager.utils.ExUtils
import java.io.IOException

internal class PathAttributesImpl private constructor(
    displayName: String,
    mimeType: String?,
    lastModified: Long,
    lastAccess: Long,
    creationTime: Long,
    isRegularFile: Boolean,
    isDirectory: Boolean,
    isSymbolicLink: Boolean,
    size: Long
) : PathAttributes(
    displayName, mimeType, lastModified, lastAccess, creationTime, isRegularFile, isDirectory, isSymbolicLink,
    size
) {
    companion object {
        @JvmStatic
        fun fromFile(file: ExtendedRawDocumentFile): PathAttributesImpl {
            val f = file.getFile()!!
            val mode = ExUtils.requireNonNullElse({ f.getMode() }, 0)
            return PathAttributesImpl(
                f.name!!, file.type, f.lastModified(), f.lastAccess(), f.creationTime(),
                OsConstants.S_ISREG(mode), OsConstants.S_ISDIR(mode), OsConstants.S_ISLNK(mode), f.length()
            )
        }

        @JvmStatic
        fun fromVirtual(file: VirtualDocumentFile): PathAttributesImpl {
            val mode = file.getMode()
            return PathAttributesImpl(
                file.name!!, file.type, file.lastModified(), file.lastAccess(), file.creationTime(),
                OsConstants.S_ISREG(mode), OsConstants.S_ISDIR(mode), OsConstants.S_ISLNK(mode), file.length()
            )
        }

        @JvmStatic
        @Throws(IOException::class)
        fun fromSaf(context: Context, safDocumentFile: DocumentFile): PathAttributesImpl {
            val documentUri = safDocumentFile.uri
            val resolver = context.contentResolver
            try {
                resolver.query(documentUri, null, null, null, null).use { c ->
                    if (c == null || !c.moveToFirst()) {
                        throw IOException("Could not fetch attributes for tree $documentUri")
                    }
                    val columns = c.columnNames
                    var name: String? = null
                    var type: String? = null
                    var lastModified: Long = 0
                    var size: Long = 0
                    for (i in columns.indices) {
                        when (columns[i]) {
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME -> name = c.getString(i)
                            DocumentsContract.Document.COLUMN_MIME_TYPE -> type = c.getString(i)
                            DocumentsContract.Document.COLUMN_LAST_MODIFIED -> lastModified = c.getLong(i)
                            DocumentsContract.Document.COLUMN_SIZE -> size = c.getLong(i)
                        }
                    }
                    val isDirectory = DocumentsContract.Document.MIME_TYPE_DIR == type
                    if (name == null) {
                        name = DocumentFileUtils.resolveAltNameForSaf(safDocumentFile)
                    }
                    return PathAttributesImpl(
                        name!!, type, lastModified, 0, 0, !isDirectory, isDirectory,
                        false, size
                    )
                }
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                throw IOException(e)
            }
        }

        @JvmStatic
        fun fromSafTreeCursor(treeUri: Uri, c: Cursor): PathAttributesImpl {
            if (!DocumentsContractCompat.isTreeUri(treeUri)) {
                throw IllegalArgumentException("Not a tree document.")
            }
            val columns = c.columnNames
            var name: String? = null
            var type: String? = null
            var lastModified: Long = 0
            var size: Long = 0
            for (i in columns.indices) {
                when (columns[i]) {
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME -> name = c.getString(i)
                    DocumentsContract.Document.COLUMN_MIME_TYPE -> type = c.getString(i)
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED -> lastModified = c.getLong(i)
                    DocumentsContract.Document.COLUMN_SIZE -> size = c.getLong(i)
                }
            }
            val isDirectory = DocumentsContract.Document.MIME_TYPE_DIR == type
            if (name == null) {
                name = DocumentFileUtils.resolveAltNameForTreeUri(treeUri)
            }
            return PathAttributesImpl(
                name!!, type, lastModified, 0, 0, !isDirectory, isDirectory,
                false, size
            )
        }
    }
}
