// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import io.github.muntashirakon.AppManager.IRemoteProcess
import io.github.muntashirakon.io.IoUtils
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Copyright 2020 Rikka
 * Copyright 2023 Muntashir Al-Islam
 */
class RemoteProcessImpl(private val mProcess: Process) : IRemoteProcess.Stub() {
    private var mIn: ParcelFileDescriptor? = null
    private var mOutputTransferThread: OutputTransferThread? = null

    override fun getOutputStream(): ParcelFileDescriptor {
        if (mOutputTransferThread == null) {
            mOutputTransferThread = OutputTransferThread(mProcess)
            mOutputTransferThread!!.start()
        }
        return try {
            mOutputTransferThread!!.writeSide
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }
    }

    override fun closeOutputStream() {
        mOutputTransferThread?.interrupt()
    }

    override fun getInputStream(): ParcelFileDescriptor {
        if (mIn == null) {
            try {
                val thread = InputTransferThread(mProcess, false)
                thread.start()
                mIn = thread.readSide
            } catch (e: Exception) {
                throw IllegalStateException(e)
            }
        }
        return mIn!!
    }

    override fun getErrorStream(): ParcelFileDescriptor {
        return try {
            val thread = InputTransferThread(mProcess, true)
            thread.start()
            thread.readSide
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }
    }

    override fun waitFor(): Int {
        return try {
            mProcess.waitFor()
        } catch (e: InterruptedException) {
            throw IllegalStateException(e)
        }
    }

    override fun exitValue(): Int {
        return mProcess.exitValue()
    }

    override fun destroy() {
        mProcess.destroy()
    }

    override fun alive(): Boolean {
        return try {
            exitValue()
            false
        } catch (e: IllegalThreadStateException) {
            true
        }
    }

    override fun waitForTimeout(timeout: Long, unitName: String): Boolean {
        val unit = TimeUnit.valueOf(unitName)
        val startTime = System.nanoTime()
        var rem = unit.toNanos(timeout)

        do {
            try {
                exitValue()
                return true
            } catch (ex: IllegalThreadStateException) {
                if (rem > 0) {
                    SystemClock.sleep(TimeUnit.NANOSECONDS.toMillis(rem).coerceAtMost(100) + 1)
                }
            }
            rem = unit.toNanos(timeout) - (System.nanoTime() - startTime)
        } while (rem > 0)
        return false
    }

    private class OutputTransferThread(private val mProcess: Process) : Thread() {
        private var mProcessOutputStream: OutputStream? = null
        @Volatile
        private var mWriteSide: ParcelFileDescriptor? = null
        private var mWaitForWriteSide: CountDownLatch = CountDownLatch(1)

        init {
            isDaemon = true
        }

        @get:Throws(IOException::class, InterruptedException::class)
        val writeSide: ParcelFileDescriptor
            get() {
                mWaitForWriteSide.await()
                return mWriteSide ?: throw IOException("Could not get the write side")
            }

        override fun run() {
            if (mProcessOutputStream == null) {
                mProcessOutputStream = mProcess.outputStream
            }
            try {
                do {
                    if (mWaitForWriteSide.count == 0L) {
                        mWaitForWriteSide = CountDownLatch(1)
                    }
                    val pipe = ParcelFileDescriptor.createPipe()
                    val readSide = pipe[0]
                    mWriteSide = pipe[1]
                    mWaitForWriteSide.countDown()
                    ParcelFileDescriptor.AutoCloseInputStream(readSide).use { inStream ->
                        val buf = ByteArray(IoUtils.DEFAULT_BUFFER_SIZE)
                        var len: Int
                        while (inStream.read(buf).also { len = it } > 0) {
                            mProcessOutputStream!!.write(buf, 0, len)
                        }
                    }
                    mProcessOutputStream!!.flush()
                } while (!isInterrupted)
                mProcessOutputStream!!.close()
            } catch (e: IOException) {
                Log.e("FD", "IOException when writing to out", e)
                mWaitForWriteSide.countDown()
            }
        }
    }

    private class InputTransferThread(private val mProcess: Process, private val mErrorStream: Boolean) : Thread() {
        private val mWaitForReadSide: CountDownLatch = CountDownLatch(1)
        @Volatile
        private var mReadSide: ParcelFileDescriptor? = null

        init {
            isDaemon = true
        }

        @get:Throws(IOException::class, InterruptedException::class)
        val readSide: ParcelFileDescriptor
            get() {
                mWaitForReadSide.await()
                return mReadSide ?: throw IOException("Could not get the write side")
            }

        override fun run() {
            try {
                val pipe = ParcelFileDescriptor.createPipe()
                mReadSide = pipe[0]
                val writeSide = pipe[1]
                mWaitForReadSide.countDown()
                ParcelFileDescriptor.AutoCloseOutputStream(writeSide).use { out ->
                    (if (mErrorStream) mProcess.errorStream else mProcess.inputStream).use { inStream ->
                        val buf = ByteArray(IoUtils.DEFAULT_BUFFER_SIZE)
                        var len: Int
                        while (inStream.read(buf).also { len = it } > 0) {
                            out.write(buf, 0, len)
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e("FD", "IOException when writing to out", e)
                mWaitForReadSide.countDown()
            }
        }
    }
}
