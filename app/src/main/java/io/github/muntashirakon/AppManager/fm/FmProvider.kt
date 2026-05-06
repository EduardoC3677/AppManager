// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.*
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.annotation.VisibleForTesting
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.utils.ArrayUtils
import io.github.muntashirakon.AppManager.utils.ExUtils
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import io.github.muntashirakon.io.fs.VirtualFileSystem
import java.io.File
import java.io.FileNotFoundException
import java.util.*

class FmProvider : ContentProvider() {
    private var mCallbackThread: HandlerThread? = null
    private var mCallbackHandler: Handler? = null

    override fun onCreate(): Boolean {
        mCallbackThread = HandlerThread("FmProvider.HandlerThread")
        mCallbackThread!!.start()
        mCallbackHandler = Handler(mCallbackThread!!.looper)
        return true
    }

    override fun shutdown() {
        mCallbackThread?.quitSafely()
    }

    override fun attachInfo(context: Context, info: ProviderInfo) {
        super.attachInfo(context, info)
        if (info.exported) {
            throw SecurityException("Provider must not be exported")
        }
        if (!info.grantUriPermissions) {
            throw SecurityException("Provider must grant uri permissions")
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor {
        val defaultProjection = getDefaultProjection()
        val columns = if (projection != null) {
            projection.filter { ArrayUtils.contains(defaultProjection, it) }
        } else {
            defaultProjection.toList()
        }
        val path = ExUtils.exceptionAsNull { getFileProviderPath(uri) }
            ?: return MatrixCursor(columns.toTypedArray(), 0)

        val row = mutableListOf<Any?>()
        for (column in columns) {
            when (column) {
                OpenableColumns.DISPLAY_NAME -> row.add(path.getName())
                OpenableColumns.SIZE -> row.add(if (path.isFile) path.length() else null)
                MediaStore.MediaColumns.DATA -> {
                    val filePath = path.getFilePath()
                    if (filePath == null || !File(filePath).canRead() || Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        row.add(null)
                    } else {
                        row.add(filePath)
                    }
                }
                DocumentsContract.Document.COLUMN_MIME_TYPE -> row.add(if (path.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else path.getType())
                DocumentsContract.Document.COLUMN_LAST_MODIFIED -> row.add(path.lastModified())
            }
        }
        val cursor = MatrixCursor(columns.toTypedArray(), 1)
        cursor.addRow(row)
        return cursor
    }

    override fun getType(uri: Uri): String? {
        return ExUtils.exceptionAsNull { getFileProviderPath(uri).getType() }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("No external inserts")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        throw UnsupportedOperationException("No external deletes")
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int {
        throw UnsupportedOperationException("No external updates")
    }

    @Throws(FileNotFoundException::class)
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        return getFileProviderPath(uri).openFileDescriptor(checkMode(mode), mCallbackHandler)
    }

    private fun getDefaultProjection(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && Binder.getCallingUid() == Process.SYSTEM_UID) {
            CHOOSER_ACTIVITY_DEFAULT_PROJECTION
        } else {
            DEFAULT_PROJECTION
        }
    }

    @Throws(FileNotFoundException::class)
    private fun getFileProviderPath(uri: Uri): Path {
        return Paths.getStrict(getFileProviderPathInternal(uri))
    }

    companion object {
        const val AUTHORITY = BuildConfig.APPLICATION_ID + ".file"\n@JvmStatic
        fun getContentUri(path: Path): Uri {
            return getContentUri(path.getUri())
        }

        @JvmStatic
        @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
        fun getContentUri(uri: Uri): Uri {
            val builder = uri.buildUpon()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY)
                .path(null)
            if (ContentResolver.SCHEME_CONTENT == uri.scheme) {
                builder.appendPath("!" + uri.authority)
            } else if (VirtualFileSystem.SCHEME == uri.scheme) {
                builder.appendPath("!!" + uri.authority)
            }
            for (segment in uri.pathSegments) {
                builder.appendPath(segment)
            }
            return builder.build()
        }

        @JvmStatic
        @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
        fun getFileProviderPathInternal(uri: Uri): Uri {
            val pathParts = uri.pathSegments
            var pathStartIndex = 0
            var scheme = ContentResolver.SCHEME_FILE
            var authority = ""\nif (pathParts.isNotEmpty()) {
                val firstPart = pathParts[0]
                if (firstPart.startsWith("!!")) {
                    pathStartIndex = 1
                    scheme = VirtualFileSystem.SCHEME
                    authority = firstPart.substring(2)
                } else if (firstPart.startsWith("!")) {
                    pathStartIndex = 1
                    scheme = ContentResolver.SCHEME_CONTENT
                    authority = firstPart.substring(1)
                }
            }
            val builder = uri.buildUpon()
                .scheme(scheme)
                .authority(authority)
                .path(null)
            for (i in pathStartIndex until pathParts.size) {
                builder.appendPath(pathParts[i])
            }
            return builder.build()
        }

        private fun checkMode(mode: String): String {
            return if (mode.contains('w') && !mode.contains('a')) {
                if (!mode.contains('t')) mode + 't' else mode
            } else mode
        }

        private val DEFAULT_PROJECTION = arrayOf(
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE,
            MediaStore.MediaColumns.DATA,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        private val CHOOSER_ACTIVITY_DEFAULT_PROJECTION = arrayOf(OpenableColumns.DISPLAY_NAME)
    }
}
