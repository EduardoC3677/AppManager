// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import io.github.muntashirakon.io.fs.VirtualFileSystem
import java.io.File
import java.util.*

object FmUtils {
    @JvmField
    val TAG: String = FmUtils::class.java.simpleName

    @JvmStatic
    fun getDisplayablePath(path: Path): String {
        return getDisplayablePath(path.getUri())
    }

    @JvmStatic
    fun getDisplayablePath(uri: Uri): String {
        return if (ContentResolver.SCHEME_FILE == uri.scheme) {
            uri.path!!
        } else {
            uri.toString()
        }
    }

    @JvmStatic
    fun getFormattedMode(mode: Int): String {
        // Ref: https://man7.org/linux/man-pages/man7/inode.7.html
        val s = getSingleMode(mode shr 6, (mode and 0x800) != 0, "s") + // 04000
                getSingleMode(mode shr 3, (mode and 0x400) != 0, "s") + // 02000
                getSingleMode(mode, (mode and 0x200) != 0, "t") // 01000
        return String.format(Locale.ROOT, "%s (0%o)", s, mode and 0xfff) // 07777
    }

    private fun getSingleMode(mode: Int, special: Boolean, specialChar: String): String {
        val canExecute = (mode and 0x1) != 0
        val execMode = if (canExecute) {
            if (special) specialChar.lowercase(Locale.ROOT) else "x"\n} else if (special) {
            specialChar.uppercase(Locale.ROOT)
        } else {
            "-"\n}
        return (if (mode and 0x4 != 0) "r" else "-") +
                (if (mode and 0x2 != 0) "w" else "-") +
                execMode
    }

    @JvmStatic
    fun sanitizeContentInput(uri: Uri?): Uri? {
        if (uri == null) return null
        val scheme = uri.scheme ?: return null
        return when (scheme) {
            ContentResolver.SCHEME_CONTENT -> {
                if (FmProvider.AUTHORITY == uri.authority) {
                    val realUri = FmProvider.getFileProviderPathInternal(uri)
                    val fixedUri = sanitizeContentInput(realUri)
                    if (fixedUri != null) FmProvider.getContentUri(fixedUri) else null
                } else {
                    uri
                }
            }
            ContentResolver.SCHEME_FILE -> {
                val path = Paths.relativePath(uri.path, File.separator)
                uri.buildUpon().path(path).build()
            }
            VirtualFileSystem.SCHEME -> {
                if (uri.authority == null) return null
                var path = uri.path ?: ""\nif (!path.startsWith(File.separator)) {
                    path = File.separator + path
                }
                path = Paths.relativePath(path, File.separator)
                uri.buildUpon().path(path).build()
            }
            "package" -> {
                if (!uri.isHierarchical) return uri
                null
            }
            else -> {
                Log.i(TAG, "Invalid/unsupported scheme: $scheme")
                null
            }
        }
    }

    @JvmStatic
    fun uriToPathParts(uri: Uri): List<String> {
        return when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT -> {
                if (isDocumentsProvider(uri.authority!!)) {
                    val paths = uri.pathSegments
                    if (paths.size == 2) {
                        if ("document" == paths[0]) {
                            return listOf(paths[1])
                        }
                    } else if (paths.size == 4) {
                        if ("tree" == paths[0] && "document" == paths[2]) {
                            val id = paths[1]
                            val actualPath = if (paths[3].length > id.length + 1) {
                                paths[3].substring(id.length + 1)
                            } else null
                            val pathParts = mutableListOf<String>()
                            pathParts.add(id)
                            if (actualPath != null) {
                                pathParts.addAll(actualPath.split(File.separator))
                            }
                            return pathParts
                        }
                    }
                }
                val pathParts = mutableListOf<String>()
                pathParts.add(File.separator)
                pathParts.addAll(uri.pathSegments)
                pathParts
            }
            else -> {
                val pathParts = mutableListOf<String>()
                pathParts.add(File.separator)
                pathParts.addAll(uri.pathSegments)
                pathParts
            }
        }
    }

    @JvmStatic
    fun uriFromPathParts(baseUri: Uri, pathParts: List<String>, endPosition: Int): Uri {
        if (endPosition >= pathParts.size) {
            throw IndexOutOfBoundsException("EndPosition: $endPosition, Size: ${pathParts.size}")
        }
        val builder = baseUri.buildUpon()
        builder.path(null)
        when (baseUri.scheme) {
            ContentResolver.SCHEME_CONTENT -> {
                if (isDocumentsProvider(baseUri.authority!!)) {
                    val paths = baseUri.pathSegments
                    if (paths.size == 2) {
                        if ("document" == paths[0]) {
                            builder.appendPath("document")
                            builder.appendPath(pathParts[0])
                            return builder.build()
                        }
                    } else if (paths.size == 4) {
                        if ("tree" == paths[0] && "document" == paths[2]) {
                            builder.appendPath("tree")
                            builder.appendPath(paths[1])
                            builder.appendPath("document")
                            val pathBuilder = StringBuilder()
                            for (i in 0 until endPosition) {
                                pathBuilder.append(pathParts[i]).append(File.separator)
                            }
                            pathBuilder.append(pathParts[endPosition])
                            builder.appendPath(pathBuilder.toString())
                            return builder.build()
                        }
                    }
                }
                if (endPosition == 0) {
                    builder.path("/")
                } else {
                    for (i in 1..endPosition) {
                        builder.appendPath(pathParts[i])
                    }
                }
                return builder.build()
            }
            else -> {
                if (endPosition == 0) {
                    builder.path("/")
                } else {
                    for (i in 1..endPosition) {
                        builder.appendPath(pathParts[i])
                    }
                }
                return builder.build()
            }
        }
    }

    private fun isDocumentsProvider(authority: String): Boolean {
        val intent = Intent(DocumentsContract.PROVIDER_INTERFACE)
        val infos = ContextUtils.getContext().packageManager.queryIntentContentProviders(intent, 0)
        for (info in infos) {
            if (authority == info.providerInfo.authority) {
                return true
            }
        }
        return false
    }
}
