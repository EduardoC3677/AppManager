// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io

import android.annotation.SuppressLint
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.RemoteException
import android.system.ErrnoException
import android.system.OsConstants
import androidx.annotation.RestrictTo
import java.io.*
import java.nio.channels.FileChannel
import java.util.*

// Copyright 2022 John "topjohnwu" Wu
@RestrictTo(RestrictTo.Scope.LIBRARY)
object NIOFactory {

    @JvmStatic
    fun createLocal(): FileSystemManager {
        return object : FileSystemManager() {
            override fun getFile(pathname: String): ExtendedFile {
                return LocalFile(pathname)
            }

            override fun getFile(parent: String?, child: String): ExtendedFile {
                return LocalFile(parent, child)
            }

            @SuppressLint("NewApi")
            @Throws(IOException::class)
            override fun openChannel(file: File, mode: Int): FileChannel {
                if (Build.VERSION.SDK_INT >= 26) {
                    return FileChannel.open(file.toPath(), FileUtils.modeToOptions(mode))
                } else {
                    val f = FileUtils.modeToFlag(mode)
                    if (f.write) {
                        if (!f.create) {
                            if (!file.exists()) {
                                val e = ErrnoException("open", OsConstants.ENOENT)
                                throw FileNotFoundException("$file: ${e.message}")
                            }
                        }
                        if (f.append) {
                            return FileOutputStream(file, true).channel
                        }
                        if (!f.read && f.truncate) {
                            return FileOutputStream(file, false).channel
                        }

                        // Unfortunately, there is no way to create a write-only channel
                        // without truncating. Forced to open rw RAF in all cases.
                        val ch = RandomAccessFile(file, "rw").channel
                        if (f.truncate) {
                            ch.truncate(0)
                        }
                        return ch
                    } else {
                        return FileInputStream(file).channel
                    }
                }
            }
        }
    }

    @JvmStatic
    @Throws(RemoteException::class)
    fun createRemote(b: IBinder): FileSystemManager {
        Objects.requireNonNull(b)
        val fs = IFileSystemService.Stub.asInterface(b)
            ?: throw IllegalArgumentException("The IBinder provided is invalid")

        fs.register(Binder())
        return object : FileSystemManager() {
            override fun getFile(pathname: String): ExtendedFile {
                return RemoteFile(fs, pathname)
            }

            override fun getFile(parent: String?, child: String): ExtendedFile {
                return RemoteFile(fs, parent, child)
            }

            @Throws(IOException::class)
            override fun openChannel(file: File, mode: Int): FileChannel {
                return RemoteFileChannel(fs, file, mode)
            }
        }
    }

    @JvmStatic
    internal fun createFsService(): FileSystemService {
        return FileSystemService()
    }
}
