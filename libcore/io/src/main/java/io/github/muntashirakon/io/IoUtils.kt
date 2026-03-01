// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io

import android.os.Build
import android.os.FileUtils
import android.util.Log
import androidx.annotation.AnyThread
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.Executor
import kotlin.math.min

object IoUtils {
    @JvmField
    val TAG: String = IoUtils::class.java.simpleName

    const val DEFAULT_BUFFER_SIZE: Int = 1024 * 50

    /**
     * Get byte array from an InputStream most efficiently.
     * Taken from sun.misc.IOUtils
     *
     * @param is      InputStream
     * @param length  Length of the buffer, -1 to read the whole stream
     * @param readAll Whether to read the whole stream
     * @return Desired byte array
     * @throws IOException If maximum capacity exceeded.
     */
    @JvmStatic
    @AnyThread
    @Throws(IOException::class)
    fun readFully(`is`: InputStream, length: Int, readAll: Boolean): ByteArray {
        var len = length
        var output = byteArrayOf()
        if (len == -1) len = Int.MAX_VALUE
        var pos = 0
        while (pos < len) {
            val bytesToRead: Int
            if (pos >= output.size) {
                bytesToRead = min(len - pos, output.size + 1024)
                if (output.size < pos + bytesToRead) {
                    output = output.copyOf(pos + bytesToRead)
                }
            } else {
                bytesToRead = output.size - pos
            }
            val cc = `is`.read(output, pos, bytesToRead)
            if (cc < 0) {
                if (readAll && len != Int.MAX_VALUE) {
                    throw EOFException("Detect premature EOF")
                } else {
                    if (output.size != pos) {
                        output = output.copyOf(pos)
                    }
                    break
                }
            }
            pos += cc
        }
        return output
    }

    @JvmStatic
    @AnyThread
    @Throws(IOException::class)
    fun getInputStreamContent(inputStream: InputStream): String {
        return String(readFully(inputStream, -1, true), Charset.defaultCharset())
    }

    @JvmStatic
    @AnyThread
    @Throws(IOException::class)
    fun copy(from: Path, to: Path): Long {
        from.openInputStream().use { `in` ->
            to.openOutputStream().use { out ->
                return copy(`in`, out)
            }
        }
    }

    /**
     * Copy the contents of one stream to another.
     */
    @JvmStatic
    @AnyThread
    @Throws(IOException::class)
    fun copy(`in`: InputStream, out: OutputStream): Long {
        return copy(`in`, out, null, null)
    }

    /**
     * Copy the contents of one stream to another.
     *
     * @param executor         that listener events should be delivered via.
     * @param progressListener to be periodically notified as the copy progresses.
     */
    @JvmStatic
    @AnyThread
    @Throws(IOException::class)
    fun copy(
        `in`: InputStream, out: OutputStream, executor: Executor?,
        progressListener: ProgressListener?
    ): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            FileUtils.copy(`in`, out, null, executor) { progress ->
                progressListener?.onProgress(progress)
            }
        } else {
            copyLarge(`in`, out, executor, progressListener)
        }
    }

    @JvmStatic
    @AnyThread
    fun closeQuietly(closeable: AutoCloseable?) {
        if (closeable == null) return
        try {
            closeable.close()
        } catch (e: Exception) {
            Log.w(TAG, String.format("Unable to close %s", closeable.javaClass.canonicalName), e)
        }
    }

    @AnyThread
    @Throws(IOException::class)
    private fun copyLarge(
        `in`: InputStream, out: OutputStream, executor: Executor?,
        progressListener: ProgressListener?
    ): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var count: Long = 0
        var checkpoint: Long = 0
        var n: Int
        while (`in`.read(buffer).also { n = it } > 0) {
            out.write(buffer, 0, n)
            count += n.toLong()
            checkpoint += n.toLong()
            if (checkpoint >= (1 shl 19)) { // 512 kB
                if (executor != null && progressListener != null) {
                    val countSnapshot = count
                    executor.execute { progressListener.onProgress(countSnapshot) }
                }
                checkpoint = 0
            }
        }
        return count
    }

    /**
     * Listener that is called periodically as progress is made.
     */
    fun interface ProgressListener {
        fun onProgress(progress: Long)
    }
}
