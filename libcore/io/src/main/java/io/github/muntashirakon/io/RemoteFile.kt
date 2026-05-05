// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io

import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.system.ErrnoException
import android.system.OsConstants
import aosp.android.content.pm.StringParceledListSlice
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

// Copyright 2022 John "topjohnwu" Wu
// Copyright 2022 Muntashir Al-Islam
internal class RemoteFile : FileImpl<RemoteFile> {

    private val fs: IFileSystemService

    constructor(f: IFileSystemService, path: String) : super(path) {
        fs = f
    }

    constructor(f: IFileSystemService, parent: String?, child: String) : super(parent, child) {
        fs = f
    }

    override fun create(path: String): RemoteFile {
        return RemoteFile(fs, path)
    }

    override fun getChildFile(name: String): RemoteFile {
        return RemoteFile(fs, path, name)
    }

    @Suppress("UNCHECKED_CAST")
    override fun createArray(n: Int): Array<RemoteFile> {
        return arrayOfNulls<RemoteFile>(n) as Array<RemoteFile>
    }

    @Throws(IOException::class)
    override fun getCanonicalPath(): String {
        return try {
            fs.getCanonicalPath(path).tryAndGet()
        } catch (e: RemoteException) {
            throw IOException(e)
        }
    }

    private fun checkAccess(access: Int): Boolean {
        return try {
            fs.checkAccess(path, access)
        } catch (e: RemoteException) {
            false
        }
    }

    override fun canRead(): Boolean {
        return checkAccess(OsConstants.R_OK)
    }

    override fun canWrite(): Boolean {
        return checkAccess(OsConstants.W_OK)
    }

    override fun canExecute(): Boolean {
        return checkAccess(OsConstants.X_OK)
    }

    override fun exists(): Boolean {
        return checkAccess(OsConstants.F_OK)
    }

    override fun isDirectory(): Boolean {
        return try {
            fs.isDirectory(path)
        } catch (e: RemoteException) {
            false
        }
    }

    override fun isFile(): Boolean {
        return try {
            fs.isFile(path)
        } catch (e: RemoteException) {
            false
        }
    }

    @Throws(ErrnoException::class)
    override fun getMode(): Int {
        return try {
            fs.getMode(path).tryAndGetErrnoException()
        } catch (e: RemoteException) {
            0
        }
    }

    @Throws(ErrnoException::class)
    override fun setMode(mode: Int): Boolean {
        return try {
            fs.setMode(path, mode).checkErrnoException()
            true
        } catch (e: RemoteException) {
            false
        }
    }

    @Throws(ErrnoException::class)
    override fun getUidGid(): UidGidPair {
        return try {
            fs.getUidGid(path).tryAndGetErrnoException()
        } catch (e: RemoteException) {
            UidGidPair(0, 0)
        }
    }

    @Throws(ErrnoException::class)
    override fun setUidGid(uid: Int, gid: Int): Boolean {
        return try {
            fs.setUidGid(path, uid, gid).checkErrnoException()
            true
        } catch (e: RemoteException) {
            false
        }
    }

    override fun getSelinuxContext(): String? {
        return try {
            fs.getSelinuxContext(path)
        } catch (e: RemoteException) {
            null
        }
    }

    override fun restoreSelinuxContext(): Boolean {
        return try {
            fs.restoreSelinuxContext(path)
        } catch (e: RemoteException) {
            false
        }
    }

    override fun setSelinuxContext(context: String): Boolean {
        return try {
            fs.setSelinuxContext(path, context)
        } catch (e: RemoteException) {
            false
        }
    }

    override fun isBlock(): Boolean {
        return try {
            OsConstants.S_ISBLK(mode)
        } catch (e: ErrnoException) {
            false
        }
    }

    override fun isCharacter(): Boolean {
        return try {
            OsConstants.S_ISCHR(mode)
        } catch (e: ErrnoException) {
            false
        }
    }

    override fun isSymlink(): Boolean {
        return try {
            OsConstants.S_ISLNK(mode)
        } catch (e: ErrnoException) {
            false
        }
    }

    override fun isNamedPipe(): Boolean {
        return try {
            OsConstants.S_ISFIFO(mode)
        } catch (e: ErrnoException) {
            false
        }
    }

    override fun isSocket(): Boolean {
        return try {
            OsConstants.S_ISSOCK(mode)
        } catch (e: ErrnoException) {
            false
        }
    }

    override fun isHidden(): Boolean {
        return try {
            fs.isHidden(path)
        } catch (e: RemoteException) {
            false
        }
    }

    override fun lastModified(): Long {
        return try {
            fs.lastModified(path)
        } catch (e: RemoteException) {
            Long.MIN_VALUE
        }
    }

    override fun creationTime(): Long {
        return try {
            fs.creationTime(path).tryAndGetErrnoException()
        } catch (e: RemoteException) {
            0
        } catch (e: ErrnoException) {
            0
        }
    }

    override fun lastAccess(): Long {
        return try {
            fs.lastAccess(path).tryAndGetErrnoException()
        } catch (e: RemoteException) {
            0
        } catch (e: ErrnoException) {
            0
        }
    }

    override fun setLastAccess(millis: Long): Boolean {
        return try {
            fs.setLastAccess(path, millis).checkErrnoException()
            true
        } catch (e: RemoteException) {
            false
        } catch (e: ErrnoException) {
            false
        }
    }

    override fun length(): Long {
        return try {
            fs.length(path)
        } catch (e: RemoteException) {
            0L
        }
    }

    @Throws(IOException::class)
    override fun createNewFile(): Boolean {
        return try {
            fs.createNewFile(path).tryAndGet()
        } catch (e: RemoteException) {
            throw IOException(e)
        }
    }

    @Throws(IOException::class)
    override fun createNewLink(existing: String): Boolean {
        return try {
            fs.createLink(path, existing, false).tryAndGet()
        } catch (e: RemoteException) {
            throw IOException(e)
        }
    }

    @Throws(IOException::class)
    override fun createNewSymlink(target: String): Boolean {
        return try {
            fs.createLink(path, target, true).tryAndGet()
        } catch (e: RemoteException) {
            throw IOException(e)
        }
    }

    override fun delete(): Boolean {
        return try {
            fs.delete(path)
        } catch (e: RemoteException) {
            false
        }
    }

    override fun deleteOnExit() {
        throw UnsupportedOperationException("deleteOnExit() is not supported in RemoteFile")
    }

    override fun list(): Array<String>? {
        return try {
            val list = fs.list(path)
            list?.list?.toTypedArray()
        } catch (e: RemoteException) {
            null
        }
    }

    override fun mkdir(): Boolean {
        return try {
            fs.mkdir(path)
        } catch (e: RemoteException) {
            false
        }
    }

    override fun mkdirs(): Boolean {
        return try {
            fs.mkdirs(path)
        } catch (e: RemoteException) {
            false
        }
    }

    override fun renameTo(dest: File): Boolean {
        return try {
            fs.renameTo(path, dest.absolutePath)
        } catch (e: RemoteException) {
            false
        }
    }

    override fun setLastModified(time: Long): Boolean {
        return try {
            fs.setLastModified(path, time)
        } catch (e: RemoteException) {
            false
        }
    }

    override fun setReadOnly(): Boolean {
        return try {
            fs.setReadOnly(path)
        } catch (e: RemoteException) {
            false
        }
    }

    override fun setWritable(writable: Boolean, ownerOnly: Boolean): Boolean {
        return try {
            fs.setWritable(path, writable, ownerOnly)
        } catch (e: RemoteException) {
            false
        }
    }

    override fun setReadable(readable: Boolean, ownerOnly: Boolean): Boolean {
        return try {
            fs.setReadable(path, readable, ownerOnly)
        } catch (e: RemoteException) {
            false
        }
    }

    override fun setExecutable(executable: Boolean, ownerOnly: Boolean): Boolean {
        return try {
            fs.setExecutable(path, executable, ownerOnly)
        } catch (e: RemoteException) {
            false
        }
    }

    override fun getTotalSpace(): Long {
        return try {
            fs.getTotalSpace(path)
        } catch (e: RemoteException) {
            0L
        }
    }

    override fun getFreeSpace(): Long {
        return try {
            fs.getFreeSpace(path)
        } catch (e: RemoteException) {
            0L
        }
    }

    override fun getUsableSpace(): Long {
        return try {
            fs.getUsableSpace(path)
        } catch (e: RemoteException) {
            0L
        }
    }

    @Throws(IOException::class)
    override fun newInputStream(): FileInputStream {
        val pipe = ParcelFileDescriptor.createPipe()
        try {
            fs.openReadStream(path, pipe[1]).checkException()
        } catch (e: RemoteException) {
            pipe[0].close()
            throw IOException(e)
        } finally {
            pipe[1].close()
        }
        return ParcelFileDescriptor.AutoCloseInputStream(pipe[0])
    }

    @Throws(IOException::class)
    override fun newOutputStream(append: Boolean): FileOutputStream {
        val pipe = ParcelFileDescriptor.createPipe()
        try {
            fs.openWriteStream(path, pipe[0], append).checkException()
        } catch (e: RemoteException) {
            pipe[1].close()
            throw IOException(e)
        } finally {
            pipe[0].close()
        }
        return ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
    }
}
