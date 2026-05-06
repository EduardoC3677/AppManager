// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io

import android.os.SELinux
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import io.github.muntashirakon.compat.system.OsCompat
import io.github.muntashirakon.compat.system.StructTimespec
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

// Copyright 2022 John "topjohnwu" Wu
// Copyright 2022 Muntashir Al-Islam
internal class LocalFile : FileImpl<LocalFile> {

    constructor(pathname: String) : super(pathname)

    constructor(parent: String?, child: String) : super(parent, child)

    override fun create(path: String): LocalFile {
        return LocalFile(path)
    }

    override fun getChildFile(name: String): LocalFile {
        return LocalFile(path, name)
    }

    @Suppress("UNCHECKED_CAST")
    override fun createArray(n: Int): Array<LocalFile> {
        return arrayOfNulls<LocalFile>(n) as Array<LocalFile>
    }

    @Throws(ErrnoException::class)
    override fun getMode(): Int {
        return Os.lstat(path).st_mode
    }

    @Throws(ErrnoException::class)
    override fun setMode(mode: Int): Boolean {
        Os.chmod(path, mode)
        return true
    }

    @Throws(ErrnoException::class)
    override fun getUidGid(): UidGidPair {
        val s = Os.lstat(path)
        return UidGidPair(s.st_uid, s.st_gid)
    }

    @Throws(ErrnoException::class)
    override fun setUidGid(uid: Int, gid: Int): Boolean {
        Os.chown(path, uid, gid)
        return true
    }

    override fun getSelinuxContext(): String? {
        return SELinux.getFileContext(path)
    }

    override fun restoreSelinuxContext(): Boolean {
        return SELinux.restorecon(path)
    }

    override fun setSelinuxContext(context: String): Boolean {
        return SELinux.setFileContext(path, context)
    }

    override fun isBlock(): Boolean {
        return try {
            OsConstants.S_ISBLK(getMode())
        } catch (e: ErrnoException) {
            false
        }
    }

    override fun isCharacter(): Boolean {
        return try {
            OsConstants.S_ISCHR(getMode())
        } catch (e: ErrnoException) {
            false
        }
    }

    override fun isSymlink(): Boolean {
        return try {
            OsConstants.S_ISLNK(getMode())
        } catch (e: ErrnoException) {
            false
        }
    }

    override fun isNamedPipe(): Boolean {
        return try {
            OsConstants.S_ISFIFO(getMode())
        } catch (e: ErrnoException) {
            false
        }
    }

    override fun isSocket(): Boolean {
        return try {
            OsConstants.S_ISSOCK(getMode())
        } catch (e: ErrnoException) {
            false
        }
    }

    override fun creationTime(): Long {
        return try {
            Os.lstat(path).st_ctime * 1000
        } catch (e: ErrnoException) {
            0
        }
    }

    override fun lastAccess(): Long {
        return try {
            Os.lstat(path).st_atime * 1000
        } catch (e: ErrnoException) {
            0
        }
    }

    override fun setLastAccess(millis: Long): Boolean {
        val seconds_part = millis / 1_000
        val nanoseconds_part = millis % 1_000 * 1_000_000
        val atime = StructTimespec(seconds_part, nanoseconds_part)
        val mtime = StructTimespec(0, OsCompat.UTIME_OMIT)
        return try {
            OsCompat.utimensat(OsCompat.AT_FDCWD, path, atime, mtime, OsCompat.AT_SYMLINK_NOFOLLOW)
            true
        } catch (e: ErrnoException) {
            false
        }
    }

    @Throws(IOException::class)
    override fun newInputStream(): FileInputStream {
        return FileInputStream(this)
    }

    @Throws(IOException::class)
    override fun newOutputStream(append: Boolean): FileOutputStream {
        return FileOutputStream(this, append)
    }

    @Throws(IOException::class)
    override fun createNewLink(existing: String): Boolean {
        return createLink(existing, false)
    }

    @Throws(IOException::class)
    override fun createNewSymlink(target: String): Boolean {
        return createLink(target, true)
    }

    @Throws(IOException::class)
    private fun createLink(target: String, soft: Boolean): Boolean {
        try {
            if (soft) Os.symlink(target, path) else Os.link(target, path)
            return true
        } catch (e: ErrnoException) {
            if (e.errno != OsConstants.EEXIST) {
                throw IOException(e)
            }
            return false
        }
    }
}
