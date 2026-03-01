// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io

import android.annotation.SuppressLint
import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.os.SELinux
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants.*
import android.util.LruCache
import aosp.android.content.pm.StringParceledListSlice
import io.github.muntashirakon.compat.system.OsCompat
import io.github.muntashirakon.compat.system.StructTimespec
import io.github.muntashirakon.io.FileUtils.createFileDescriptor
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

// Copyright 2022 John "topjohnwu" Wu
// Copyright 2022 Muntashir Al-Islam
internal class FileSystemService : IFileSystemService.Stub() {

    private val mCache = object : LruCache<String, File>(100) {
        override fun create(key: String): File {
            return File(key)
        }
    }

    override fun getCanonicalPath(path: String): IOResult {
        return try {
            IOResult(mCache[path].canonicalPath)
        } catch (e: IOException) {
            IOResult(e)
        }
    }

    override fun isDirectory(path: String): Boolean {
        return mCache[path].isDirectory
    }

    override fun isFile(path: String): Boolean {
        return mCache[path].isFile
    }

    override fun isHidden(path: String): Boolean {
        return mCache[path].isHidden
    }

    override fun lastModified(path: String): Long {
        return mCache[path].lastModified()
    }

    override fun lastAccess(path: String): IOResult {
        return try {
            IOResult(Os.lstat(path).st_atime * 1000)
        } catch (e: ErrnoException) {
            IOResult(e)
        }
    }

    override fun creationTime(path: String): IOResult {
        return try {
            IOResult(Os.lstat(path).st_ctime * 1000)
        } catch (e: ErrnoException) {
            IOResult(e)
        }
    }

    override fun length(path: String): Long {
        return mCache[path].length()
    }

    override fun createNewFile(path: String): IOResult {
        return try {
            IOResult(mCache[path].createNewFile())
        } catch (e: IOException) {
            IOResult(e)
        }
    }

    override fun delete(path: String): Boolean {
        return mCache[path].delete()
    }

    override fun list(path: String): StringParceledListSlice? {
        val list = mCache[path].list()
        return if (list != null) StringParceledListSlice(listOf(*list)) else null
    }

    override fun mkdir(path: String): Boolean {
        return mCache[path].mkdir()
    }

    override fun mkdirs(path: String): Boolean {
        return mCache[path].mkdirs()
    }

    override fun renameTo(path: String, dest: String): Boolean {
        return mCache[path].renameTo(mCache[dest])
    }

    override fun setLastModified(path: String, time: Long): Boolean {
        return mCache[path].setLastModified(time)
    }

    override fun setLastAccess(path: String, time: Long): IOResult {
        val seconds_part = time / 1_000
        val nanoseconds_part = time % 1_000 * 1_000_000
        val atime = StructTimespec(seconds_part, nanoseconds_part)
        val mtime = StructTimespec(0, OsCompat.UTIME_OMIT)
        return try {
            OsCompat.utimensat(OsCompat.AT_FDCWD, path, atime, mtime, OsCompat.AT_SYMLINK_NOFOLLOW)
            IOResult(true)
        } catch (e: ErrnoException) {
            IOResult(e)
        }
    }

    override fun setReadOnly(path: String): Boolean {
        return mCache[path].setReadOnly()
    }

    override fun setWritable(path: String, writable: Boolean, ownerOnly: Boolean): Boolean {
        return mCache[path].setWritable(writable, ownerOnly)
    }

    override fun setReadable(path: String, readable: Boolean, ownerOnly: Boolean): Boolean {
        return mCache[path].setReadable(readable, ownerOnly)
    }

    override fun setExecutable(path: String, executable: Boolean, ownerOnly: Boolean): Boolean {
        return mCache[path].setExecutable(executable, ownerOnly)
    }

    override fun checkAccess(path: String, access: Int): Boolean {
        return try {
            Os.access(path, access)
        } catch (e: ErrnoException) {
            false
        }
    }

    override fun getTotalSpace(path: String): Long {
        return mCache[path].totalSpace
    }

    override fun getFreeSpace(path: String): Long {
        return mCache[path].freeSpace
    }

    @SuppressLint("UsableSpace")
    override fun getUsableSpace(path: String): Long {
        return mCache[path].usableSpace
    }

    override fun getMode(path: String): IOResult {
        return try {
            IOResult(Os.lstat(path).st_mode)
        } catch (e: ErrnoException) {
            IOResult(e)
        }
    }

    override fun setMode(path: String, mode: Int): IOResult {
        return try {
            Os.chmod(path, mode)
            IOResult(true)
        } catch (e: ErrnoException) {
            IOResult(e)
        }
    }

    override fun getUidGid(path: String): IOResult {
        return try {
            val s = Os.lstat(path)
            IOResult(UidGidPair(s.st_uid, s.st_gid))
        } catch (e: ErrnoException) {
            IOResult(e)
        }
    }

    override fun setUidGid(path: String, uid: Int, gid: Int): IOResult {
        return try {
            Os.chown(path, uid, gid)
            IOResult(true)
        } catch (e: ErrnoException) {
            IOResult(e)
        }
    }

    override fun getSelinuxContext(path: String): String? {
        return SELinux.getFileContext(path)
    }

    override fun restoreSelinuxContext(path: String): Boolean {
        return SELinux.restorecon(path)
    }

    override fun setSelinuxContext(path: String, context: String): Boolean {
        return SELinux.setFileContext(path, context)
    }

    override fun createLink(link: String, target: String, soft: Boolean): IOResult {
        try {
            if (soft) {
                Os.symlink(target, link)
            } else {
                Os.link(target, link)
            }
            return IOResult(true)
        } catch (e: ErrnoException) {
            return if (e.errno == EEXIST) {
                IOResult(false)
            } else {
                IOResult(e)
            }
        }
    }

    // I/O APIs

    private val openFiles = FileContainer()
    private val streamPool = Executors.newFixedThreadPool(coreCount * 2)

    private val coreCount: Int
        get() = Runtime.getRuntime().availableProcessors()

    override fun register(client: IBinder) {
        val pid = Binder.getCallingPid()
        try {
            client.linkToDeath({ openFiles.pidDied(pid) }, 0)
        } catch (ignored: RemoteException) {
        }
    }

    @Suppress("OctalInteger")
    override fun openChannel(path: String, mode: Int, fifo: String): IOResult {
        val f = OpenFile()
        try {
            f.fd = Os.open(path, mode or O_NONBLOCK, 438) // 0666
            f.read = Os.open(fifo, O_RDONLY or O_NONBLOCK, 0)
            f.write = Os.open(fifo, O_WRONLY or O_NONBLOCK, 0)
            return IOResult(openFiles.put(f))
        } catch (e: ErrnoException) {
            f.close()
            return IOResult(e)
        }
    }

    override fun openReadStream(path: String, fd: ParcelFileDescriptor): IOResult {
        val f = OpenFile()
        return try {
            f.fd = Os.open(path, O_RDONLY, 0)
            streamPool.execute {
                try {
                    f.use { of ->
                        of.write = createFileDescriptor(fd.detachFd())
                        while (of.pread(PIPE_CAPACITY, -1) > 0);
                    }
                } catch (ignored: ErrnoException) {
                } catch (ignored: IOException) {
                }
            }
            IOResult()
        } catch (e: ErrnoException) {
            f.close()
            IOResult(e)
        }
    }

    @Suppress("OctalInteger")
    override fun openWriteStream(path: String, fd: ParcelFileDescriptor, append: Boolean): IOResult {
        val f = OpenFile()
        return try {
            val mode = O_CREAT or O_WRONLY or if (append) O_APPEND else O_TRUNC
            f.fd = Os.open(path, mode, 438) // 0666
            streamPool.execute {
                try {
                    f.use { of ->
                        of.read = createFileDescriptor(fd.detachFd())
                        while (of.pwrite(PIPE_CAPACITY, -1, false) > 0);
                    }
                } catch (ignored: ErrnoException) {
                } catch (ignored: IOException) {
                }
            }
            IOResult()
        } catch (e: ErrnoException) {
            f.close()
            IOResult(e)
        }
    }

    override fun close(handle: Int) {
        openFiles.remove(handle)
    }

    override fun pread(handle: Int, len: Int, offset: Long): IOResult {
        return try {
            IOResult(openFiles[handle].pread(len, offset))
        } catch (e: IOException) {
            IOResult(e)
        } catch (e: ErrnoException) {
            IOResult(e)
        }
    }

    override fun pwrite(handle: Int, len: Int, offset: Long): IOResult {
        return try {
            openFiles[handle].pwrite(len, offset, true)
            IOResult()
        } catch (e: IOException) {
            IOResult(e)
        } catch (e: ErrnoException) {
            IOResult(e)
        }
    }

    override fun lseek(handle: Int, offset: Long, whence: Int): IOResult {
        return try {
            IOResult(openFiles[handle].lseek(offset, whence))
        } catch (e: IOException) {
            IOResult(e)
        } catch (e: ErrnoException) {
            IOResult(e)
        }
    }

    override fun size(handle: Int): IOResult {
        return try {
            IOResult(openFiles[handle].size())
        } catch (e: IOException) {
            IOResult(e)
        } catch (e: ErrnoException) {
            IOResult(e)
        }
    }

    override fun ftruncate(handle: Int, length: Long): IOResult {
        return try {
            openFiles[handle].ftruncate(length)
            IOResult()
        } catch (e: IOException) {
            IOResult(e)
        } catch (e: ErrnoException) {
            IOResult(e)
        }
    }

    override fun sync(handle: Int, metadata: Boolean): IOResult {
        return try {
            openFiles[handle].sync(metadata)
            IOResult()
        } catch (e: IOException) {
            IOResult(e)
        } catch (e: ErrnoException) {
            IOResult(e)
        }
    }

    companion object {
        const val PIPE_CAPACITY = 16 * 4096
    }
}
