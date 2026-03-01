// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io

import androidx.annotation.WorkerThread
import java.io.IOException
import java.io.InputStream
import java.util.*
import kotlin.math.min

class SplitInputStream : InputStream {
    private val mInputStreams: MutableList<InputStream>
    private var mCurrentIndex = -1
    private val mFiles: List<Path>

    private val mBuf: ByteArray

    // Number of valid bytes in buf
    private var mCount = 0

    // Current read pos, > 0 in buf, < 0 in markBuf (interpret in bitwise negate)
    private var mPos = 0

    // -1 when no active mark, 0 when markBuf is active, pos when mark is called
    private var mMarkPos = -1

    // Number of valid bytes in markBuf
    private var mMarkBufCount = 0

    // markBuf.length == markBufSize
    private var mMarkBufSize = 0
    private var mMarkBuf: ByteArray? = null

    // Some value ranges:
    // 0 <= count <= buf.length
    // 0 <= pos <= count (if pos > 0)
    // 0 <= markPos <= pos (markPos = -1 means no mark)
    // 0 <= ~pos <= markBufCount (if pos < 0)
    // 0 <= markBufCount <= markLimit

    constructor(files: List<Path>) {
        mFiles = files
        mInputStreams = ArrayList(files.size)
        mBuf = ByteArray(1024 * 4)
    }

    constructor(files: Array<Path>) : this(listOf(*files))

    @Throws(IOException::class)
    override fun read(): Int {
        val bytes = ByteArray(1)
        val readBytes = read(bytes)
        return if (readBytes != 1) -1 else bytes[0].toInt() and 0xFF
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray): Int {
        return read(b, 0, b.size)
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (off < 0 || len < 0 || off + len > b.size)
            throw IndexOutOfBoundsException()
        return read0(b, off, len)
    }

    @Throws(IOException::class)
    override fun skip(n: Long): Long {
        return if (n <= 0) 0 else read0(null, 0, n.toInt()).toLong().coerceAtLeast(0)
    }

    @Throws(IOException::class)
    override fun close() {
        for (stream in mInputStreams) {
            stream.close()
        }
    }

    @Synchronized
    override fun mark(readlimit: Int) {
        // Reset mark
        mMarkPos = mPos
        mMarkBufCount = 0
        mMarkBuf = null

        val remain = mCount - mPos
        if (readlimit <= remain) {
            // Don't need a separate buffer
            mMarkBufSize = 0
        } else {
            // Extra buffer required is remain + n * buf.length
            mMarkBufSize = remain + (readlimit - remain) / mBuf.size * mBuf.size
        }
    }

    @Synchronized
    @Throws(IOException::class)
    override fun reset() {
        if (mMarkPos < 0)
            throw IOException("Resetting to invalid mark")
        // Switch to markPos or use markBuf
        mPos = if (mMarkBuf == null) mMarkPos else -1 // ~0
    }

    @WorkerThread
    @Synchronized
    @Throws(IOException::class)
    override fun available(): Int {
        if (mCount < 0) return 0
        if (mPos >= mCount) {
            // Try to read the next chunk into memory
            read0(null, 0, 1)
            if (mCount < 0) return 0
            // Revert the 1 byte read
            --mPos
        }
        // Return the size available in memory
        return if (mPos < 0) {
            mMarkBufCount - mPos.inv() + mCount
        } else {
            mCount - mPos
        }
    }

    override fun markSupported(): Boolean {
        return true
    }

    @WorkerThread
    @Synchronized
    @Throws(IOException::class)
    private fun read0(b: ByteArray?, off: Int, len: Int): Int {
        var n = 0
        while (n < len) {
            if (mPos < 0) {
                // Read from markBuf
                val pos = mPos.inv()
                val size = min(mMarkBufCount - pos, len - n)
                if (b != null) {
                    System.arraycopy(mMarkBuf!!, pos, b, off + n, size)
                }
                n += size
                val newPos = pos + size
                if (newPos == mMarkBufCount) {
                    // markBuf done, switch to buf
                    mPos = 0
                } else {
                    // continue reading markBuf
                    mPos = newPos.inv()
                }
                continue
            }
            // Read from buf
            if (mPos >= mCount) {
                // We ran out of buffer, need to either refill or abort
                if (mMarkPos >= 0) {
                    // We need to preserve some buffer for mark
                    val size = (mCount - mMarkPos).toLong()
                    if (mMarkBufSize - mMarkBufCount < size) {
                        // Out of mark limit, discard markBuf
                        mMarkBuf = null
                        mMarkBufCount = 0
                        mMarkPos = -1
                    } else if (mMarkBuf == null) {
                        mMarkBuf = ByteArray(mMarkBufSize)
                        mMarkBufCount = 0
                    }
                    if (mMarkBuf != null) {
                        // Accumulate data in markBuf
                        System.arraycopy(mBuf, mMarkPos, mMarkBuf!!, mMarkBufCount, size.toInt())
                        mMarkBufCount += size.toInt()
                        // Set markPos to 0 as buffer will refill
                        mMarkPos = 0
                    }
                }
                // refill buffer
                mPos = 0
                mCount = readStream(mBuf)
                if (mCount < 0) {
                    return if (n == 0) -1 else n
                }
            }
            val size = min(mCount - mPos, len - n)
            if (b != null) {
                System.arraycopy(mBuf, mPos, b, off + n, size)
            }
            n += size
            mPos += size
        }
        return n
    }

    @WorkerThread
    @Synchronized
    @Throws(IOException::class)
    private fun readStream(b: ByteArray): Int {
        var off = 0
        var len = b.size
        if (len <= 0) return len
        try {
            if (mFiles.isEmpty()) {
                // No files supplied, nothing to read
                return -1
            } else if (mCurrentIndex == -1) {
                // Initialize a new stream
                mInputStreams.add(mFiles[0].openInputStream())
                ++mCurrentIndex
            }
            do {
                val readCount = mInputStreams[mCurrentIndex].read(b, off, len)
                if (readCount <= 0) {
                    // This stream has been read completely, initialize new stream if available
                    if (mCurrentIndex + 1 != mFiles.size) {
                        mInputStreams.add(mFiles[mCurrentIndex + 1].openInputStream())
                        ++mCurrentIndex
                    } else {
                        // Last stream reached
                        return if (len == b.size) {
                            // Read nothing
                            -1
                        } else {
                            // Read something
                            b.size - len
                        }
                    }
                } else {
                    off += readCount
                    len -= readCount
                }
            } while (len > 0)
            return b.size - len
        } catch (e: IOException) {
            throw e
        } catch (th: Throwable) {
            throw IOException(th)
        }
    }
}
