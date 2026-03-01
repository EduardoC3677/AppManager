// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io

import android.system.ErrnoException
import java.io.File
import java.io.FileFilter
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilenameFilter
import java.io.IOException
import java.net.URI

/**
 * [File] API with extended features.
 *
 * The goal of this class is to extend missing features in the [File] API that are available
 * in the NIO package but not possible to be re-implemented without low-level file system access.
 * For instance, detecting file types other than regular files and directories, handling and
 * creating hard links and symbolic links.
 *
 * Another goal of this class is to provide a generalized API interface for custom file system
 * backends. The library includes backends for accessing files locally, accessing files remotely
 * via IPC, and accessing files through shell commands (by using `SuFile`, included in the
 * `io` module). The developer can get instances of this class with
 * [FileSystemManager.getFile].
 *
 * Implementations of this class is required to return the same type of [ExtendedFile] in
 * all of its APIs returning [File]s. This means that, for example, if the developer is
 * getting a list of files in a directory using a remote file system with [.listFiles],
 * all files returned in the array will also be using the same remote file system backend.
 */
// Copyright 2022 John "topjohnwu" Wu
// Copyright 2022 Muntashir Al-Islam
abstract class ExtendedFile : File {

    /**
     * @see File.File
     */
    protected constructor(pathname: String) : super(pathname)

    /**
     * @see File.File
     */
    protected constructor(parent: String?, child: String) : super(parent, child)

    /**
     * @see File.File
     */
    protected constructor(parent: File?, child: String) : super(parent, child)

    /**
     * @see File.File
     */
    protected constructor(uri: URI) : super(uri)

    /**
     * @return Get mode (permission) of the abstract pathname.
     */
    @Throws(ErrnoException::class)
    abstract fun getMode(): Int

    /**
     * Set mode (permission) of the abstract pathname.
     *
     * @return `true` on success.
     * @see android.system.Os.chmod
     */
    @Throws(ErrnoException::class)
    abstract fun setMode(mode: Int): Boolean

    /**
     * @return Get UID and GID of the abstract pathname.
     */
    @Throws(ErrnoException::class)
    abstract fun getUidGid(): UidGidPair

    /**
     * Set UID and GID of the abstract pathname.
     *
     * @return `true` on success.
     * @see android.system.Os.chown
     */
    @Throws(ErrnoException::class)
    abstract fun setUidGid(uid: Int, gid: Int): Boolean

    abstract fun getSelinuxContext(): String?

    abstract fun restoreSelinuxContext(): Boolean

    abstract fun setSelinuxContext(context: String): Boolean

    /**
     * @return true if the abstract pathname denotes a block device.
     */
    abstract fun isBlock(): Boolean

    /**
     * @return true if the abstract pathname denotes a character device.
     */
    abstract fun isCharacter(): Boolean

    /**
     * @return true if the abstract pathname denotes a symbolic link.
     */
    abstract fun isSymlink(): Boolean

    /**
     * @return true if the abstract pathname denotes a named pipe (FIFO).
     */
    abstract fun isNamedPipe(): Boolean

    /**
     * @return true if the abstract pathname denotes a socket file.
     */
    abstract fun isSocket(): Boolean

    /**
     * Returns the time that the file denoted by this abstract pathname was
     *
     * @return A `long` value representing the time the file was
     * created, measured in milliseconds since the epoch
     * (00:00:00 GMT, January 1, 1970), or `0L` if the
     * file does not exist or if an I/O error occurs
     */
    abstract fun creationTime(): Long

    /**
     * Returns the time that the file denoted by this abstract pathname was last accessed.
     *
     * @return A `long` value representing the time the file was
     * last accessed, measured in milliseconds since the epoch
     * (00:00:00 GMT, January 1, 1970), or `0L` if the
     * file does not exist or if an I/O error occurs
     */
    abstract fun lastAccess(): Long

    /**
     * Set the time that the file denoted by this abstract pathname was last accessed.
     *
     * @param millis A `long` value representing the time the file was
     * last accessed, measured in milliseconds since the epoch
     * (00:00:00 GMT, January 1, 1970)
     * @return `true` if and only if the operation succeeded; `false` otherwise.
     */
    abstract fun setLastAccess(millis: Long): Boolean

    /**
     * Creates a new hard link named by this abstract pathname of an existing file
     * if and only if a file with this name does not yet exist.
     *
     * @param existing a path to an existing file.
     * @return `true` if the named file does not exist and was successfully
     * created; `false` if the named file already exists.
     * @throws IOException if an I/O error occurred.
     */
    @Throws(IOException::class)
    abstract fun createNewLink(existing: String): Boolean

    /**
     * Creates a new symbolic link named by this abstract pathname to a target file
     * if and only if a file with this name does not yet exist.
     *
     * @param target the target of the symbolic link.
     * @return `true` if the named file does not exist and was successfully
     * created; `false` if the named file already exists.
     * @throws IOException if an I/O error occurred.
     */
    @Throws(IOException::class)
    abstract fun createNewSymlink(target: String): Boolean

    /**
     * Opens an InputStream with the matching file system backend of the file.
     *
     * @see FileInputStream.FileInputStream
     */
    @Throws(IOException::class)
    abstract fun newInputStream(): FileInputStream

    /**
     * Opens an OutputStream with the matching file system backend of the file.
     *
     * @see FileOutputStream.FileOutputStream
     */
    @Throws(IOException::class)
    fun newOutputStream(): FileOutputStream {
        return newOutputStream(false)
    }

    /**
     * Opens an OutputStream with the matching file system backend of the file.
     *
     * @see FileOutputStream.FileOutputStream
     */
    @Throws(IOException::class)
    abstract fun newOutputStream(append: Boolean): FileOutputStream

    /**
     * Create a child relative to the abstract pathname using the same file system backend.
     *
     * @see File.File
     */
    abstract fun getChildFile(child: String): ExtendedFile

    /**
     * {@inheritDoc}
     */
    override abstract fun getAbsoluteFile(): ExtendedFile

    /**
     * {@inheritDoc}
     */
    @Throws(IOException::class)
    override abstract fun getCanonicalFile(): ExtendedFile

    /**
     * {@inheritDoc}
     */
    override abstract fun getParentFile(): ExtendedFile?

    /**
     * {@inheritDoc}
     */
    override abstract fun listFiles(): Array<ExtendedFile>?

    /**
     * {@inheritDoc}
     */
    override abstract fun listFiles(filter: FilenameFilter?): Array<ExtendedFile>?

    /**
     * {@inheritDoc}
     */
    override abstract fun listFiles(filter: FileFilter?): Array<ExtendedFile>?
}
