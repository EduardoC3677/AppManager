// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.io

import android.annotation.SuppressLint
import android.os.Build
import android.system.ErrnoException
import android.system.Int64Ref
import android.system.Os
import android.system.OsConstants.*
import android.util.ArraySet
import android.util.MutableLong
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.lang.reflect.AccessibleObject
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.nio.file.OpenOption
import java.nio.file.StandardOpenOption

// Copyright 2022 John "topjohnwu" Wu
@Suppress("ConstantConditions", "JavaReflectionMemberAccess")
@SuppressLint("DiscouragedPrivateApi")
internal object FileUtils {
    private var os: Any? = null
    private var splice: Method? = null
    private var sendfile: Method? = null
    private var setFd: AccessibleObject? = null

    class Flag {
        var read: Boolean = false
        var write: Boolean = false
        var create: Boolean = false
        var truncate: Boolean = false
        var append: Boolean = false
    }

    fun modeToPosix(mode: Int): Int {
        var res: Int
        if (mode and FileSystemManager.MODE_READ_WRITE == FileSystemManager.MODE_READ_WRITE) {
            res = O_RDWR
        } else if (mode and FileSystemManager.MODE_WRITE_ONLY == FileSystemManager.MODE_WRITE_ONLY) {
            res = O_WRONLY
        } else if (mode and FileSystemManager.MODE_READ_ONLY == FileSystemManager.MODE_READ_ONLY) {
            res = O_RDONLY
        } else {
            throw IllegalArgumentException("Bad mode: $mode")
        }
        if (mode and FileSystemManager.MODE_CREATE == FileSystemManager.MODE_CREATE) {
            res = res or O_CREAT
        }
        if (mode and FileSystemManager.MODE_TRUNCATE == FileSystemManager.MODE_TRUNCATE) {
            res = res or O_TRUNC
        }
        if (mode and FileSystemManager.MODE_APPEND == FileSystemManager.MODE_APPEND) {
            res = res or O_APPEND
        }
        return res
    }

    @RequiresApi(api = 26)
    fun modeToOptions(mode: Int): Set<OpenOption> {
        val set = ArraySet<OpenOption>()
        if (mode and FileSystemManager.MODE_READ_WRITE == FileSystemManager.MODE_READ_WRITE) {
            set.add(StandardOpenOption.READ)
            set.add(StandardOpenOption.WRITE)
        } else if (mode and FileSystemManager.MODE_WRITE_ONLY == FileSystemManager.MODE_WRITE_ONLY) {
            set.add(StandardOpenOption.WRITE)
        } else if (mode and FileSystemManager.MODE_READ_ONLY == FileSystemManager.MODE_READ_ONLY) {
            set.add(StandardOpenOption.READ)
        } else {
            throw IllegalArgumentException("Bad mode: $mode")
        }
        if (mode and FileSystemManager.MODE_CREATE == FileSystemManager.MODE_CREATE) {
            set.add(StandardOpenOption.CREATE)
        }
        if (mode and FileSystemManager.MODE_TRUNCATE == FileSystemManager.MODE_TRUNCATE) {
            set.add(StandardOpenOption.TRUNCATE_EXISTING)
        }
        if (mode and FileSystemManager.MODE_APPEND == FileSystemManager.MODE_APPEND) {
            set.add(StandardOpenOption.APPEND)
        }
        return set
    }

    fun modeToFlag(mode: Int): Flag {
        val f = Flag()
        if (mode and FileSystemManager.MODE_READ_WRITE == FileSystemManager.MODE_READ_WRITE) {
            f.read = true
            f.write = true
        } else if (mode and FileSystemManager.MODE_WRITE_ONLY == FileSystemManager.MODE_WRITE_ONLY) {
            f.write = true
        } else if (mode and FileSystemManager.MODE_READ_ONLY == FileSystemManager.MODE_READ_ONLY) {
            f.read = true
        } else {
            throw IllegalArgumentException("Bad mode: $mode")
        }
        if (mode and FileSystemManager.MODE_CREATE == FileSystemManager.MODE_CREATE) {
            f.create = true
        }
        if (mode and FileSystemManager.MODE_TRUNCATE == FileSystemManager.MODE_TRUNCATE) {
            f.truncate = true
        }
        if (mode and FileSystemManager.MODE_APPEND == FileSystemManager.MODE_APPEND) {
            f.append = true
        }

        // Validate flags
        if (f.append && f.read) {
            throw IllegalArgumentException("READ + APPEND not allowed")
        }
        if (f.append && f.truncate) {
            throw IllegalArgumentException("APPEND + TRUNCATE not allowed")
        }

        return f
    }

    @RequiresApi(api = 28)
    @Throws(ErrnoException::class)
    fun splice(
        fdIn: FileDescriptor, offIn: Int64Ref?,
        fdOut: FileDescriptor, offOut: Int64Ref?,
        len: Long, flags: Int
    ): Long {
        try {
            if (splice == null) {
                splice = Os::class.java.getMethod(
                    "splice",
                    FileDescriptor::class.java, Int64Ref::class.java,
                    FileDescriptor::class.java, Int64Ref::class.java,
                    Long::class.javaPrimitiveType, Int::class.javaPrimitiveType
                )
            }
            return splice!!.invoke(null, fdIn, offIn, fdOut, offOut, len, flags) as Long
        } catch (e: InvocationTargetException) {
            throw e.targetException as ErrnoException
        } catch (e: ReflectiveOperationException) {
            throw ErrnoException("splice", ENOSYS)
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("NewApi")
    @Throws(ErrnoException::class)
    fun sendfile(
        outFd: FileDescriptor, inFd: FileDescriptor,
        inOffset: MutableLong?, byteCount: Long
    ): Long {
        if (Build.VERSION.SDK_INT >= 28) {
            val off = if (inOffset == null) null else Int64Ref(inOffset.value)
            val result = Os.sendfile(outFd, inFd, off, byteCount)
            if (off != null)
                inOffset!!.value = off.value
            return result
        } else {
            try {
                if (os == null) {
                    os = Class.forName("libcore.io.Libcore").getField("os").get(null)
                }
                if (sendfile == null) {
                    sendfile = os!!.javaClass.getMethod(
                        "sendfile",
                        FileDescriptor::class.java, FileDescriptor::class.java,
                        MutableLong::class.java, Long::class.javaPrimitiveType
                    )
                }
                return sendfile!!.invoke(os, outFd, inFd, inOffset, byteCount) as Long
            } catch (e: InvocationTargetException) {
                throw e.targetException as ErrnoException
            } catch (e: ReflectiveOperationException) {
                throw ErrnoException("sendfile", ENOSYS)
            }
        }
    }

    @Suppress("OctalInteger")
    @Throws(ErrnoException::class, IOException::class)
    fun createTempFIFO(): File {
        val fifo = File.createTempFile("libsu-fifo-", null)
        fifo.delete()
        Os.mkfifo(fifo.path, 420) // 0644
        return fifo
    }

    @JvmStatic
    fun createFileDescriptor(fd: Int): FileDescriptor? {
        if (setFd == null) {
            try {
                // Available API 24+
                setFd = FileDescriptor::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType)
            } catch (e: NoSuchMethodException) {
                // This is actually how the Android framework sets the fd internally
                try {
                    setFd = FileDescriptor::class.java.getDeclaredMethod("setInt$", Int::class.javaPrimitiveType)
                } catch (ignored: NoSuchMethodException) {
                }
            }
            setFd?.isAccessible = true
        }
        return try {
            if (setFd is Constructor<*>) {
                (setFd as Constructor<*>).newInstance(fd) as FileDescriptor
            } else {
                val f = FileDescriptor()
                (setFd as Method).invoke(f, fd)
                f
            }
        } catch (e: ReflectiveOperationException) {
            null
        }
    }
}
