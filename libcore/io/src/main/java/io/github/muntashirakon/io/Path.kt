// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import androidx.annotation.CheckResult
import androidx.documentfile.provider.DocumentFile
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.FileChannel
import java.nio.charset.Charset
import java.util.*

/**
 * Provide an interface to [java.io.File] and [DocumentFile] with basic functionalities.
 */
abstract class Path protected constructor(
    protected val context: Context,
    protected val documentFile: DocumentFile
) : Comparable<Path> {

    /**
     * Return the last segment of this path.
     */
    abstract val name: String

    val extension: String?
        get() {
            val name = name
            val lastIndexOfDot = name.lastIndexOf('.')
            return if (lastIndexOfDot == -1 || lastIndexOfDot + 1 == name.length) {
                null
            } else name.substring(lastIndexOfDot + 1).lowercase(Locale.ROOT)
        }

    /**
     * Return a URI for the underlying document represented by this file. This
     * can be used with other platform APIs to manipulate or share the
     * underlying content. [DocumentFile.isDocumentUri] can
     * be used to test if the returned Uri is backed by an
     * [android.provider.DocumentsProvider].
     */
    val uri: Uri
        get() = documentFile.uri

    /**
     * Return the underlying [ExtendedFile] if the path is backed by a real file,
     * `null` otherwise.
     */
    abstract val file: ExtendedFile?

    /**
     * Same as [.getFile] except it return a raw string.
     */
    abstract val filePath: String?

    /**
     * Same as [.getFile] except it returns the real path if the
     * current path is a symbolic link.
     */
    @get:Throws(IOException::class)
    abstract val realFilePath: String?

    /**
     * Same as [.getFile] except it returns the real path if the
     * current path is a symbolic link.
     */
    @get:Throws(IOException::class)
    abstract val realPath: Path?

    /**
     * Return the MIME type of the path
     */
    abstract val type: String?

    /**
     * Return the content info of the path.
     *
     * This is an expensive operation and should be done in a non-UI thread.
     */
    abstract val pathContentInfo: PathContentInfo

    /**
     * Returns the length of this path in bytes. Returns 0 if the path does not
     * exist, or if the length is unknown. The result for a directory is not
     * defined.
     */
    @CheckResult
    abstract fun length(): Long

    /**
     * Recreate this path if required.
     *
     * This only recreates files and not directories in order to avoid potential mass destructive operation.
     *
     * @return `true` iff the path has been recreated.
     */
    @CheckResult
    abstract fun recreate(): Boolean

    /**
     * Create a new file as a direct child of this directory. If the file
     * already exists, and it is not a directory, it will try to delete it
     * and create a new one.
     *
     * @param displayName Display name for the file with or without extension.
     * The name must not contain any file separator.
     * @param mimeType    Mime type for the new file. Underlying provider may
     * choose to add extension based on the mime type. If
     * displayName contains an extension, set it to null.
     * @return The newly created file.
     * @throws IOException              If the target is a mount point, a directory, or the current file is not a
     * directory, or failed for any other reason.
     * @throws IllegalArgumentException If the display name contains file separator.
     */
    @Throws(IOException::class)
    abstract fun createNewFile(displayName: String, mimeType: String?): Path

    /**
     * Create a new directory as a direct child of this directory.
     *
     * @param displayName Display name for the directory. The name must not
     * contain any file separator.
     * @return The newly created directory.
     * @throws IOException              If the target is a mount point or the current file is not a directory,
     * or failed for any other reason.
     * @throws IllegalArgumentException If the display name contains file separator.
     */
    @Throws(IOException::class)
    abstract fun createNewDirectory(displayName: String): Path

    /**
     * Create a new file at some arbitrary level under this directory,
     * non-existing paths are created if necessary. If the file already exists,
     * and it isn't a directory, it will try to delete it and create a new one.
     * If mount points encountered while iterating through the paths, it will
     * try to create a new file under the last mount point.
     *
     * @param displayName Display name for the file with or without extension
     * and/or file separator.
     * @param mimeType    Mime type for the new file. Underlying provider may
     * choose to add extension based on the mime type. If
     * displayName contains an extension, set it to null.
     * @return The newly created file.
     * @throws IOException              If the target is a mount point, a directory or failed for any other reason.
     * @throws IllegalArgumentException If the display name is malformed.
     */
    @Throws(IOException::class)
    abstract fun createNewArbitraryFile(displayName: String, mimeType: String?): Path


    /**
     * Create all the non-existing directories under this directory. If mount
     * points encountered while iterating through the paths, it will try to
     * create a new directory under the last mount point.
     *
     * @param displayName Relative path to the target directory.
     * @return The newly created directory.
     * @throws IOException If the target is a mount point, or failed for any other reason.
     */
    @Throws(IOException::class)
    abstract fun createDirectoriesIfRequired(displayName: String): Path

    /**
     * Create all the non-existing directories under this directory. If mount
     * points encountered while iterating through the paths, it will try to
     * create a new directory under the last mount point.
     *
     * @param displayName Relative path to the target directory.
     * @return The newly created directory.
     * @throws IOException If the target exists, or it is a mount point, or failed for any other reason.
     */
    @Throws(IOException::class)
    abstract fun createDirectories(displayName: String): Path

    /**
     * Delete this file. If this is a directory, it is deleted recursively.
     *
     * @return `true` if the file was deleted, `false` if the file
     * is a mount point or any other error occurred.
     */
    open fun delete(): Boolean {
        return if (isMountPoint) {
            false
        } else documentFile.delete()
    }

    /**
     * Return the parent file of this document. If this is a mount point,
     * the parent is the parent of the mount point. For tree-documents,
     * the consistency of the parent file isn't guaranteed as the underlying
     * directory tree might be altered by another application.
     */
    abstract val parent: Path?

    /**
     * Return the parent file of this document. If this is a mount point,
     * the parent is the parent of the mount point. For tree-documents,
     * the consistency of the parent file isn't guaranteed as the underlying
     * directory tree might be altered by another application.
     */
    fun requireParent(): Path {
        return Objects.requireNonNull(parent)!!
    }

    /**
     * Whether this file has a file denoted by this abstract name. The file
     * isn't necessarily have to be a direct child of this file.
     *
     * @param displayName Display name for the file with extension and/or
     * file separator if applicable.
     * @return `true` if the file denoted by this abstract name exists.
     */
    abstract fun hasFile(displayName: String): Boolean

    /**
     * Return the file denoted by this abstract name in this file. File name
     * can be either case-sensitive or case-insensitive depending on the file
     * provider.
     *
     * @param displayName Display name for the file with extension and/or
     * file separator if applicable.
     * @return The first file that matches the name.
     * @throws FileNotFoundException If the file was not found.
     */
    @Throws(FileNotFoundException::class)
    abstract fun findFile(displayName: String): Path

    /**
     * Return the file denoted by this abstract name in this file. File name
     * can be either case-sensitive or case-insensitive depending on the file
     * provider.
     *
     * @param displayName Display name for the file with extension and/or
     * file separator if applicable.
     * @return The first file that matches the name, `null` otherwise.
     */
    fun findFileOrNull(displayName: String): Path? {
        return try {
            findFile(displayName)
        } catch (ignore: FileNotFoundException) {
            null
        }
    }

    /**
     * Return a file that is a direct child of this directory, creating if necessary.
     *
     * @param displayName Display name for the file with or without extension.
     * The name must not contain any file separator.
     * @param mimeType    Mime type for the new file. Underlying provider may
     * choose to add extension based on the mime type. If
     * displayName contains an extension, set it to null.
     * @return The existing or newly created file.
     * @throws IOException              If the target is a mount point, a directory, or the current file is not a
     * directory, or failed for any other reason.
     * @throws IllegalArgumentException If the display name contains file separator.
     */
    @Throws(IOException::class)
    abstract fun findOrCreateFile(displayName: String, mimeType: String?): Path

    /**
     * Return a directory that is a direct child of this directory, creating
     * if necessary.
     *
     * @param displayName Display name for the directory. The name must not
     * contain any file separator.
     * @return The existing or newly created directory or mount point.
     * @throws IOException              If the target directory could not be created, or the existing or the
     * current file is not a directory.
     * @throws IllegalArgumentException If the display name contains file separator.
     */
    @Throws(IOException::class)
    abstract fun findOrCreateDirectory(displayName: String): Path

    @get:Throws(IOException::class)
    abstract val attributes: PathAttributes

    /**
     * Whether this file can be found. This is useful only for the paths
     * accessed using Java File API. In other cases, the file has to exist
     * before it can be accessed. However, in SAF, the file can be deleted
     * by another application in which case the URI becomes non-existent.
     *
     * @return `true` if the file exists.
     */
    @CheckResult
    abstract fun exists(): Boolean

    /**
     * Whether this file is a directory. A mount point is also considered as a
     * directory.
     *
     * Note that the return value `false` does not necessarily mean that
     * the path is a file.
     *
     * @return `true` if the file is a directory or a mount point.
     */
    @CheckResult
    abstract fun isDirectory(): Boolean

    /**
     * Whether this file is a file.
     *
     * Note that the return value `false` does not necessarily mean that
     * the path is a directory.
     *
     * @return `true` if the file is a file.
     */
    @CheckResult
    abstract fun isFile(): Boolean

    /**
     * Whether the file is a virtual file i.e. it has no physical existence.
     *
     * @return `true` if this is a virtual file.
     */
    @CheckResult
    abstract fun isVirtual(): Boolean

    /**
     * Whether the file is a symbolic link, only applicable for Java File API.
     *
     * @return `true` iff the file is accessed using Java File API and
     * is a symbolic link.
     */
    @CheckResult
    abstract fun isSymbolicLink(): Boolean

    /**
     * Creates a new symbolic link named by this abstract pathname to a target file if and only if the pathname is a
     * physical file and is not yet exist.
     *
     * @param target the target of the symbolic link.
     * @return `true` if target did not exist and the link was successfully created, and `false` otherwise.
     */
    abstract fun createNewSymbolicLink(target: String): Boolean

    /**
     * Whether the file can be read.
     *
     * @return `true` if it can be read.
     */
    abstract fun canRead(): Boolean

    /**
     * Whether the file can be written.
     *
     * @return `true` if it can be written.
     */
    abstract fun canWrite(): Boolean

    /**
     * Whether the file can be executed.
     *
     * @return `true` if it can be executed.
     */
    abstract fun canExecute(): Boolean

    abstract val mode: Int

    abstract fun setMode(mode: Int): Boolean

    abstract val uidGid: UidGidPair?

    abstract fun setUidGid(uidGidPair: UidGidPair): Boolean

    abstract val selinuxContext: String?

    abstract fun setSelinuxContext(context: String?): Boolean

    /**
     * Whether the file is a mount point, thereby, is being overridden by another file system.
     *
     * @return `true` if this is a mount point.
     */
    abstract val isMountPoint: Boolean

    abstract fun mkdir(): Boolean

    abstract fun mkdirs(): Boolean

    /**
     * Renames this file to `displayName`, both containing in the same directory.
     *
     * Note that this method does *not* throw `IOException` on
     * failure. Callers must check the return value.
     *
     * Some providers may need to create a new document to reflect the rename,
     * potentially with a different MIME type, so [.getUri] and
     * [.getType] may change to reflect the rename.
     *
     * When renaming a directory, children previously enumerated through
     * [.listFiles] may no longer be valid.
     *
     * @param displayName the new display name.
     * @return `true` on success. It returns `false` if the displayName is invalid or if it already exists.
     * @throws UnsupportedOperationException when working with a single document
     */
    abstract fun renameTo(displayName: String): Boolean

    /**
     * Same as [.moveTo] with override enabled
     */
    fun moveTo(dest: Path): Boolean {
        return moveTo(dest, true)
    }

    /**
     * Move the given path based on the following criteria:
     *
     *  1. If both paths are physical (i.e. uses File API), use normal move behaviour
     *  1. If one of the paths is virtual or the above fails, use special copy and delete operation
     *
     *
     * Move behavior is as follows:
     *
     *  * If both are directories, move `this` inside `path`
     *  * If both are files, move `this` to `path` overriding it
     *  * If `this` is a file and `path` is a directory, move the file inside the directory
     *  * If `path` does not exist, it is created based on `this`.
     *
     *
     * @param path     Target file/directory which may or may not exist
     * @param override Whether to override the files in the destination
     * @return `true` on success and `false` on failure
     */
    abstract fun moveTo(path: Path, override: Boolean): Boolean


    fun copyTo(path: Path): Path? {
        return copyTo(path, true)
    }

    abstract fun copyTo(path: Path, override: Boolean): Path?

    abstract fun lastModified(): Long

    abstract fun setLastModified(time: Long): Boolean

    abstract fun lastAccess(): Long

    abstract fun setLastAccess(millis: Long): Boolean

    abstract fun creationTime(): Long

    abstract fun listFiles(): Array<Path>

    fun listFiles(filter: FileFilter?): Array<Path> {
        val ss = listFiles()
        val files = ArrayList<Path>()
        for (s in ss) {
            if (filter == null || filter.accept(s)) {
                files.add(s)
            }
        }
        return files.toTypedArray()
    }

    fun listFiles(filter: FilenameFilter?): Array<Path> {
        val ss = listFiles()
        val files = ArrayList<Path>()
        for (s in ss) {
            if (filter == null || filter.accept(this, s.name)) {
                files.add(s)
            }
        }
        return files.toTypedArray()
    }

    fun listFileNames(): Array<String> {
        val ss = listFiles()
        val files = ArrayList<String>()
        for (s in ss) {
            files.add(s.name)
        }
        return files.toTypedArray()
    }

    fun listFileNames(filter: FileFilter?): Array<String> {
        val ss = listFiles()
        val files = ArrayList<String>()
        for (s in ss) {
            if (filter == null || filter.accept(s)) {
                files.add(s.name)
            }
        }
        return files.toTypedArray()
    }

    fun listFileNames(filter: FilenameFilter?): Array<String> {
        val ss = listFiles()
        val files = ArrayList<String>()
        for (s in ss) {
            val name = s.name
            if (filter == null || filter.accept(this, name)) {
                files.add(name)
            }
        }
        return files.toTypedArray()
    }

    @Throws(FileNotFoundException::class)
    abstract fun openFileDescriptor(mode: String, callbackHandler: Handler): ParcelFileDescriptor

    @Throws(IOException::class)
    fun openOutputStream(): OutputStream {
        return openOutputStream(false)
    }

    @Throws(IOException::class)
    abstract fun openOutputStream(append: Boolean): OutputStream

    @Throws(IOException::class)
    abstract fun openInputStream(): InputStream

    @Throws(IOException::class)
    abstract fun openFileChannel(mode: Int): FileChannel

    val contentAsBinary: ByteArray
        get() = getContentAsBinary(ByteArray(0))!!

    fun getContentAsBinary(emptyValue: ByteArray): ByteArray? {
        try {
            openInputStream().use { inputStream ->
                return IoUtils.readFully(inputStream, -1, true)
            }
        } catch (e: IOException) {
            if (e.cause !is ErrnoException) {
                // This isn't just another EACCESS exception
                e.printStackTrace()
            }
        }
        return emptyValue
    }

    val contentAsString: String
        get() = getContentAsString("")!!

    fun getContentAsString(emptyValue: String?): String? {
        return getContentAsString(emptyValue, Charset.defaultCharset())
    }


    fun getContentAsString(emptyValue: String?, charset: Charset): String? {
        return try {
            openInputStream().use { inputStream ->
                String(IoUtils.readFully(inputStream, -1, true), charset)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyValue
        }
    }

    override fun toString(): String {
        return uri.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Path) return false
        val path = other
        return documentFile.uri == path.documentFile.uri
    }

    override fun hashCode(): Int {
        return documentFile.uri.hashCode()
    }

    override fun compareTo(other: Path): Int {
        return documentFile.uri.compareTo(other.documentFile.uri)
    }

    fun interface FilenameFilter {
        /**
         * Tests if a specified file should be included in a file list.
         *
         * @param dir  the directory in which the file was found.
         * @param name the name of the file.
         * @return `true` if and only if the name should be
         * included in the file list; `false` otherwise.
         */
        fun accept(dir: Path, name: String): Boolean
    }

    fun interface FileFilter {

        /**
         * Tests whether or not the specified abstract pathname should be
         * included in a pathname list.
         *
         * @param pathname The abstract pathname to be tested
         * @return `true` if and only if `pathname`
         * should be included
         */
        fun accept(pathname: Path): Boolean
    }
}
