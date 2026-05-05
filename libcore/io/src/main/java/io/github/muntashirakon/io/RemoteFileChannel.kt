// SPDX-License-Identifier: GPL-3.0-or-later

/*
 * Copyright 2022 John "topjohnwu" Wu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.muntashirakon.io

import android.os.ParcelFileDescriptor.*
import android.os.RemoteException
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants.*
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.*
import kotlin.math.min

internal class RemoteFileChannel(private val fs: IFileSystemService, file: File, private val mode: Int) :
    FileChannel() {

    private val fdLock = Any()

    private val read: FileDescriptor?
    private val write: FileDescriptor?
    private val handle: Int

    init {
        var fifo: File? = null
        try {
            // We use a FIFO created on the client side instead of opening a pipe and
            // passing it through binder as this is the most portable and reliable method.
            fifo = FileUtils.createTempFIFO()

            // Open the file on the remote process
            val posixMode = FileUtils.modeToPosix(mode)
            handle = fs.openChannel(file.absolutePath, posixMode, fifo.path).tryAndGet()

            // Since we do not have the machinery to interrupt native pthreads, we
            // have to make sure none of our I/O can block in all operations.
            read = Os.open(fifo.path, O_RDONLY or O_NONBLOCK, 0)
            write = Os.open(fifo.path, O_WRONLY or O_NONBLOCK, 0)
        } catch (e: RemoteException) {
            throw IOException(e)
        } catch (e: ErrnoException) {
            throw IOException(e)
        } finally {
            // Once both sides opened the pipe, it can be unlinked
            fifo?.delete()
        }
    }

    @Throws(IOException::class)
    private fun ensureOpen() {
        if (!isOpen)
            throw ClosedChannelException()
    }

    private fun writable(): Boolean {
        return when (mode and MODE_READ_WRITE) {
            MODE_READ_WRITE, MODE_WRITE_ONLY -> true
            else -> false
        }
    }

    private fun readable(): Boolean {
        return when (mode and MODE_READ_WRITE) {
            MODE_READ_WRITE, MODE_READ_ONLY -> true
            else -> false
        }
    }

    @Throws(IOException::class)
    private fun read0(dst: ByteBuffer, offset: Long): Int {
        begin()
        val limit = dst.limit()
        val initial = dst.position()
        var success = false
        var currentOffset = offset
        try {
            var pos = initial
            while (limit > pos) {
                pos = dst.position()
                val len: Int
                synchronized(fdLock) {
                    if (!isOpen || Thread.interrupted())
                        return -1
                    len = fs.pread(handle, limit - pos, currentOffset).tryAndGet()
                    if (len == 0)
                        break
                    dst.limit(pos + len)
                    // Must read exactly len bytes
                    var sz = 0
                    while (sz < len) {
                        sz += Os.read(read, dst)
                    }
                }
                if (currentOffset >= 0) {
                    currentOffset += len.toLong()
                }
            }
            success = true
            return dst.position() - initial
        } catch (e: ErrnoException) {
            throw IOException(e)
        } catch (e: RemoteException) {
            throw IOException(e)
        } finally {
            dst.limit(limit)
            end(success)
        }
    }

    @Throws(IOException::class)
    override fun read(dst: ByteBuffer): Int {
        ensureOpen()
        if (!readable())
            throw NonReadableChannelException()
        return read0(dst, -1)
    }

    @Throws(IOException::class)
    override fun read(dsts: Array<ByteBuffer>, offset: Int, length: Int): Long {
        if (offset < 0 || length < 0 || offset > dsts.size - length)
            throw IndexOutOfBoundsException()
        ensureOpen()
        if (!readable())
            throw NonReadableChannelException()
        var sz = 0
        // Real scattered I/O is too complicated, let's cheat
        for (i in offset until offset + length) {
            sz += read0(dsts[i], -1)
        }
        return sz.toLong()
    }

    @Throws(IOException::class)
    private fun write0(src: ByteBuffer, offset: Long): Int {
        begin()
        val remaining = src.remaining()
        var success = false
        var currentOffset = offset
        try {
            while (src.hasRemaining()) {
                val len: Int
                synchronized(fdLock) {
                    if (!isOpen || Thread.interrupted())
                        return -1
                    len = Os.write(write, src)
                    fs.pwrite(handle, len, currentOffset).checkException()
                }
                if (currentOffset >= 0) {
                    currentOffset += len.toLong()
                }
            }
            success = true
            return remaining
        } catch (e: ErrnoException) {
            throw IOException(e)
        } catch (e: RemoteException) {
            throw IOException(e)
        } finally {
            end(success)
        }
    }

    @Throws(IOException::class)
    override fun write(src: ByteBuffer): Int {
        ensureOpen()
        if (!writable())
            throw NonWritableChannelException()
        return write0(src, -1)
    }

    @Throws(IOException::class)
    override fun write(srcs: Array<ByteBuffer>, offset: Int, length: Int): Long {
        if (offset < 0 || length < 0 || offset > srcs.size - length)
            throw IndexOutOfBoundsException()
        ensureOpen()
        if (!writable())
            throw NonWritableChannelException()
        var sz = 0
        // Real scattered I/O is too complicated, let's cheat
        for (i in offset until offset + length) {
            sz += write(srcs[i])
        }
        return sz.toLong()
    }

    @Throws(IOException::class)
    override fun position(): Long {
        ensureOpen()
        return try {
            fs.lseek(handle, 0, SEEK_CUR.toLong()).tryAndGet()
        } catch (e: RemoteException) {
            throw IOException(e)
        }
    }

    @Throws(IOException::class)
    override fun position(newPosition: Long): RemoteFileChannel {
        ensureOpen()
        if (newPosition < 0)
            throw IllegalArgumentException()
        try {
            fs.lseek(handle, newPosition, SEEK_SET).checkException()
            return this
        } catch (e: RemoteException) {
            throw IOException(e)
        }
    }

    @Throws(IOException::class)
    override fun size(): Long {
        ensureOpen()
        return try {
            fs.size(handle).tryAndGet()
        } catch (e: RemoteException) {
            throw IOException(e)
        }
    }

    @Throws(IOException::class)
    override fun truncate(size: Long): RemoteFileChannel {
        ensureOpen()
        if (size < 0)
            throw IllegalArgumentException("Negative size")
        if (!writable())
            throw NonWritableChannelException()
        try {
            fs.ftruncate(handle, size).checkException()
            return this
        } catch (e: RemoteException) {
            throw IOException(e)
        }
    }

    @Throws(IOException::class)
    override fun force(metaData: Boolean) {
        ensureOpen()
        try {
            fs.sync(handle, metaData).checkException()
        } catch (e: RemoteException) {
            throw IOException(e)
        }
    }

    @Throws(IOException::class)
    override fun transferTo(position: Long, count: Long, target: WritableByteChannel): Long {
        ensureOpen()
        if (!target.isOpen)
            throw ClosedChannelException()
        if (!readable())
            throw NonReadableChannelException()
        if (position < 0 || count < 0)
            throw IllegalArgumentException()

        val b = ByteBuffer.allocateDirect(PIPE_CAPACITY)
        var bytes: Long = 0
        var currentPosition = position
        while (count > bytes) {
            val limit = min(b.capacity().toLong(), count - bytes).toInt()
            b.limit(limit)
            if (read0(b, currentPosition) <= 0)
                break
            b.flip()
            val len = target.write(b)
            currentPosition += len.toLong()
            bytes += len.toLong()
            b.clear()
        }
        return bytes
    }

    @Throws(IOException::class)
    override fun transferFrom(src: ReadableByteChannel, position: Long, count: Long): Long {
        ensureOpen()
        if (!src.isOpen)
            throw ClosedChannelException()
        if (!writable())
            throw NonWritableChannelException()
        if (position < 0 || count < 0)
            throw IllegalArgumentException()

        val b = ByteBuffer.allocateDirect(PIPE_CAPACITY)
        var bytes: Long = 0
        var currentPosition = position
        while (count > bytes) {
            val limit = min(b.capacity().toLong(), count - bytes).toInt()
            b.limit(limit)
            if (src.read(b) <= 0)
                break
            b.flip()
            val len = write0(b, currentPosition)
            currentPosition += len.toLong()
            bytes += len.toLong()
            b.clear()
        }
        return bytes
    }

    @Throws(IOException::class)
    override fun read(dst: ByteBuffer, position: Long): Int {
        if (position < 0)
            throw IllegalArgumentException("Negative position")
        ensureOpen()
        return read0(dst, position)
    }

    @Throws(IOException::class)
    override fun write(src: ByteBuffer, position: Long): Int {
        if (position < 0)
            throw IllegalArgumentException("Negative position")
        ensureOpen()
        return write0(src, position)
    }

    override fun implCloseChannel() {
        try {
            fs.close(handle)
        } catch (ignored: RemoteException) {
        }
        synchronized(fdLock) {
            try {
                Os.close(read)
            } catch (ignored: ErrnoException) {
            }
            try {
                Os.close(write)
            } catch (ignored: ErrnoException) {
            }
        }
    }

    // Unsupported operations

    override fun map(mode: MapMode, position: Long, size: Long): MappedByteBuffer {
        throw UnsupportedOperationException("Memory mapping a remote file is not supported!")
    }

    override fun lock(position: Long, size: Long, shared: Boolean): FileLock {
        throw UnsupportedOperationException("Locking a remote file is not supported!")
    }

    override fun tryLock(position: Long, size: Long, shared: Boolean): FileLock {
        throw UnsupportedOperationException("Locking a remote file is not supported!")
    }

    companion object {
        private const val PIPE_CAPACITY = 16 * 4096
    }
}
