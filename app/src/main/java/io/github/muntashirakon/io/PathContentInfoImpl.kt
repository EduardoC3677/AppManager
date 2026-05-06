// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io

import com.j256.simplemagic.ContentInfo
import com.j256.simplemagic.ContentInfoUtil
import io.github.muntashirakon.AppManager.fm.ContentType2
import io.github.muntashirakon.AppManager.logs.Log

internal class PathContentInfoImpl private constructor(
    name: String,
    message: String?,
    mimeType: String?,
    fileExtensions: Array<String>?,
    partial: Boolean
) : PathContentInfo(name, message, mimeType, fileExtensions, partial) {
    companion object {
        @JvmField
        val TAG: String = PathContentInfoImpl::class.java.simpleName

        // Associations not present in ContentInfoUtil, they're derived from simple-name
        private val sSimpleNameMimeAssociations = hashMapOf(
            "SQLite" to "application/vnd.sqlite3"\n)

        private val sPartialOverrides = hashMapOf(
            "application/zip" to true
        )

        private var sContentInfoUtil: ContentInfoUtil? = null

        @JvmStatic
        fun fromExtension(path: Path): PathContentInfoImpl {
            if (path.isDirectory()) {
                return DIRECTORY
            }
            val ext = path.getExtension()
            val extInfo = if (ext != null) ContentInfoUtil.findExtensionMatch(ext) else null
            val extType2 = if (ext != null) ContentType2.fromFileExtension(ext) else null
            if (extInfo != null) {
                return withPartialOverride(fromContentInfo(extInfo), extType2)
            }
            if (extType2 != null) {
                return fromContentType2(extType2)
            }
            return fromContentType2(ContentType2.OTHER)
        }

        @JvmStatic
        fun fromPath(path: Path): PathContentInfoImpl {
            if (path.isDirectory()) {
                return DIRECTORY
            }
            if (sContentInfoUtil == null) {
                sContentInfoUtil = ContentInfoUtil()
            }
            val ext = path.getExtension()
            val extInfo = if (ext != null) ContentInfoUtil.findExtensionMatch(ext) else null
            val extType2 = if (ext != null) ContentType2.fromFileExtension(ext) else null
            try {
                path.openInputStream().use { `is` ->
                    val contentInfo = sContentInfoUtil!!.findMatch(`is`)
                    if (contentInfo != null) {
                        // FIXME: 20/11/22 This will not work for invalid extensions. A better option is to use magic-mime-db
                        //  instead which is currently a WIP.
                        if (extInfo != null) {
                            return withPartialOverride(
                                fromPathContentInfo(
                                    PathContentInfoImpl(
                                        extInfo.name, contentInfo.message, extInfo.mimeType,
                                        extInfo.fileExtensions, contentInfo.isPartial
                                    )
                                ), extType2
                            )
                        }
                        if (extType2 != null) {
                            return fromPathContentInfo(
                                PathContentInfoImpl(
                                    extType2.simpleName, contentInfo.message,
                                    extType2.mimeType, extType2.fileExtensions, contentInfo.isPartial
                                )
                            )
                        }
                        return fromContentInfo(contentInfo)
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Could not load MIME type for path %s", e, path)
            }
            if (extInfo != null) {
                return withPartialOverride(fromContentInfo(extInfo), extType2)
            }
            if (extType2 != null) {
                return fromContentType2(extType2)
            }
            return fromContentType2(ContentType2.OTHER)
        }

        private fun withPartialOverride(contentInfo: PathContentInfoImpl, contentType2: ContentType2?): PathContentInfoImpl {
            if (contentType2 != null) {
                val partial = contentInfo.isPartial() || sPartialOverrides[contentInfo.getMimeType()] == true
                if (partial) {
                    // Override MIME type, name and extension
                    return PathContentInfoImpl(
                        contentType2.simpleName, contentInfo.getMessage(),
                        contentType2.mimeType, contentType2.fileExtensions, false
                    )
                }
            }
            return contentInfo
        }

        private fun fromContentInfo(contentInfo: ContentInfo): PathContentInfoImpl {
            val mime = sSimpleNameMimeAssociations[contentInfo.name]
            if (mime != null) {
                val contentType2 = ContentType2.fromMimeType(mime)
                // Association exists, replace MIME type and merge file extensions
                val extensions = hashSetOf<String>()
                if (contentInfo.fileExtensions != null) {
                    extensions.addAll(contentInfo.fileExtensions)
                }
                if (contentType2.fileExtensions != null) {
                    extensions.addAll(contentType2.fileExtensions)
                }
                return PathContentInfoImpl(
                    contentInfo.name, contentInfo.message, mime,
                    if (extensions.isEmpty()) null else extensions.toTypedArray(), contentInfo.isPartial
                )
            }
            return PathContentInfoImpl(
                contentInfo.name, contentInfo.message, contentInfo.mimeType,
                contentInfo.fileExtensions, contentInfo.isPartial
            )
        }

        private fun fromPathContentInfo(contentInfo: PathContentInfoImpl): PathContentInfoImpl {
            val mime = sSimpleNameMimeAssociations[contentInfo.getName()]
            if (mime != null) {
                val contentType2 = ContentType2.fromMimeType(mime)
                // Association exists, replace MIME type and merge file extensions
                val extensions = hashSetOf<String>()
                if (contentInfo.getFileExtensions() != null) {
                    extensions.addAll(contentInfo.getFileExtensions()!!)
                }
                if (contentType2.fileExtensions != null) {
                    extensions.addAll(contentType2.fileExtensions)
                }
                return PathContentInfoImpl(
                    contentInfo.getName(), contentInfo.getMessage(), mime,
                    if (extensions.isEmpty()) null else extensions.toTypedArray(), contentInfo.isPartial()
                )
            }
            return contentInfo
        }

        private fun fromContentType2(contentType2: ContentType2): PathContentInfoImpl {
            return PathContentInfoImpl(
                contentType2.simpleName, null, contentType2.mimeType,
                contentType2.fileExtensions, false
            )
        }

        private val DIRECTORY = PathContentInfoImpl(
            "Directory", null,
            "resource/folder", null, false
        )
    }
}
