// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io

import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.WorkerThread
import java.io.IOException
import java.io.OutputStream
import java.util.*

class SplitOutputStream @JvmOverloads constructor(
    private val mBasePath: Path,
    private val mBaseName: String,
    private val mMaxBytesPerFile: Long = (1024 * 1024 * 1024).toLong()
) : OutputStream() {
    private val mOutputStreams: MutableList<OutputStream> = ArrayList(1)
    val files: MutableList<Path> = ArrayList(1)
    private var mCurrentIndex = -1
    private var mBytesWritten: Long
    init {
        mBytesWritten = mMaxBytesPerFile
    }

    @WorkerThread
    @Throws(IOException::class)
    override fun write(b: Int) {
        checkCurrentStream(1)
        mOutputStreams[mCurrentIndex].write(b)
        ++mBytesWritten
    }

    @WorkerThread
    @Throws(IOException::class)
    override fun write(b: ByteArray) {
        checkCurrentStream(b.size)
        mOutputStreams[mCurrentIndex].write(b)
        mBytesWritten += b.size.toLong()
    }

    @WorkerThread
    @Throws(IOException::class)
    override fun write(b: ByteArray, off: Int, len: Int) {
        checkCurrentStream(len)
        mOutputStreams[mCurrentIndex].write(b, off, len)
        mBytesWritten += len.toLong()
    }

    @WorkerThread
    @Throws(IOException::class)
    override fun flush() {
        for (stream in mOutputStreams) {
            stream.flush()
        }
    }

    @WorkerThread
    @Throws(IOException::class)
    override fun close() {
        for (stream in mOutputStreams) {
            stream.close()
        }
    }

    @WorkerThread
    @Throws(IOException::class)
    private fun checkCurrentStream(nextBytesSize: Int) {
        if (mBytesWritten + nextBytesSize > mMaxBytesPerFile) {
            // Need to create a new stream
            val newFile = nextFile
            files.add(newFile)
            mOutputStreams.add(newFile.openOutputStream())
            ++mCurrentIndex
            mBytesWritten = 0
        }
    }

    @get:Throws(IOException::class)
    private val nextFile: Path
        get() = mBasePath.createNewFile(mBaseName + "." + (mCurrentIndex + 1), null)

    companion object {
        private const val MAX_BYTES_WRITTEN = 1024 * 1024 * 1024.toLong() // 1GB
    }
}
