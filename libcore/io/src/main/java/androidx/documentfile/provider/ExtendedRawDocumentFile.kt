// SPDX-License-Identifier: GPL-3.0-or-later

package androidx.documentfile.provider

import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import io.github.muntashirakon.io.ExtendedFile
import java.io.File
import java.io.IOException
import java.util.*

/**
 * Same as [RawDocumentFile] with additional support for [ExtendedFile].
 */
class ExtendedRawDocumentFile : DocumentFile {
    private var mFile: ExtendedFile

    constructor(file: ExtendedFile) : super(getParentDocumentFile(file)) {
        mFile = file
    }

    constructor(parent: DocumentFile?, file: ExtendedFile) : super(parent) {
        mFile = file
    }

    override fun createFile(mimeType: String, displayName: String): DocumentFile? {
        var name = displayName
        if (name.contains(File.separator)) {
            // displayName cannot contain a separator
            return null
        }
        // Tack on extension when valid MIME type provided
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        if (extension != null) {
            name += ".$extension"
        }
        val target = mFile.getChildFile(name)
        return try {
            target.createNewFile()
            ExtendedRawDocumentFile(this, target)
        } catch (e: IOException) {
            Log.w(TAG, "Failed to create $target", e)
            null
        }
    }

    override fun createDirectory(displayName: String): DocumentFile? {
        if (displayName.contains(File.separator)) {
            // displayName cannot contain a separator
            return null
        }
        val target = mFile.getChildFile(displayName)
        return if (target.isDirectory || target.mkdir()) {
            ExtendedRawDocumentFile(this, target)
        } else {
            null
        }
    }

    override fun getUri(): Uri {
        return Uri.fromFile(mFile)
    }

    fun getFile(): ExtendedFile {
        return mFile
    }

    override fun getName(): String? {
        return mFile.name
    }

    override fun getType(): String? {
        if (mFile.isDirectory) {
            return "resource/folder"
        } else if (mFile.isFile) {
            val name = mFile.name
            val lastDot = name.lastIndexOf('.')
            if (lastDot >= 0) {
                val extension = name.substring(lastDot + 1).lowercase(Locale.ROOT)
                return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            }
        }
        return null
    }

    override fun isDirectory(): Boolean {
        return mFile.isDirectory
    }

    override fun isFile(): Boolean {
        return mFile.isFile
    }

    override fun isVirtual(): Boolean {
        return false
    }

    override fun lastModified(): Long {
        return mFile.lastModified()
    }

    override fun length(): Long {
        return mFile.length()
    }

    override fun canRead(): Boolean {
        return mFile.canRead()
    }

    override fun canWrite(): Boolean {
        return mFile.canWrite()
    }

    override fun delete(): Boolean {
        deleteContents(mFile)
        return mFile.delete()
    }

    override fun exists(): Boolean {
        return mFile.exists()
    }

    override fun findFile(displayName: String): DocumentFile? {
        if (displayName.contains(File.separator)) {
            // displayName cannot contain a separator
            return null
        }
        val file = mFile.getChildFile(displayName)
        return if (file.exists()) ExtendedRawDocumentFile(this, file) else null
    }

    override fun listFiles(): Array<DocumentFile> {
        val results = ArrayList<DocumentFile>()
        val files = mFile.listFiles()
        if (files != null) {
            for (file in files) {
                results.add(ExtendedRawDocumentFile(this, file))
            }
        }
        return results.toTypedArray()
    }

    override fun renameTo(displayName: String): Boolean {
        if (displayName.contains(File.separator)) {
            // displayName cannot contain a separator
            return false
        }
        val parent = mFile.parentFile ?: return false
        val target = mFile.parentFile!!.getChildFile(displayName)
        return if (mFile.renameTo(target)) {
            mFile = target
            true
        } else {
            false
        }
    }

    fun renameTo(targetFile: ExtendedRawDocumentFile): Boolean {
        return if (mFile.renameTo(targetFile.mFile)) {
            mFile = targetFile.mFile
            true
        } else {
            false
        }
    }

    companion object {
        const val TAG = "DF"

        private fun getTypeForName(name: String): String? {
            val lastDot = name.lastIndexOf('.')
            if (lastDot >= 0) {
                val extension = name.substring(lastDot + 1).lowercase(Locale.ROOT)
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                if (mime != null) {
                    return mime
                }
            }
            return "application/octet-stream"
        }

        private fun deleteContents(dir: ExtendedFile): Boolean {
            if (dir.isSymlink()) {
                // Do not follow symbolic links
                return true
            }
            val files = dir.listFiles()
            var success = true
            if (files != null) {
                for (file in files) {
                    if (file.isDirectory) {
                        success = success and deleteContents(file)
                    }
                    if (!file.delete()) {
                        Log.w(TAG, "Failed to delete $file")
                        success = false
                    }
                }
            }
            return success
        }

        private fun getParentDocumentFile(file: ExtendedFile): DocumentFile? {
            val parent = file.parentFile
            return if (parent != null) {
                ExtendedRawDocumentFile(parent)
            } else null
        }
    }
}
