// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.AppManager.utils

import android.os.ParcelFileDescriptor
import android.util.Log
import io.github.muntashirakon.io.IoUtils
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

// Copyright 2013 Florian Schmaus
object ParcelFileDescriptorUtil {
    @JvmStatic
    @Throws(IOException::class)
    fun pipeFrom(inputStream: InputStream): ParcelFileDescriptor {
        val pipe = ParcelFileDescriptor.createPipe()
        val readSide = pipe[0]
        val writeSide = pipe[1]

        TransferThread(inputStream, ParcelFileDescriptor.AutoCloseOutputStream(writeSide))
            .start()

        return readSide
    }

    @JvmStatic
    fun pipeTo(outputStream: OutputStream, output: ParcelFileDescriptor): TransferThread {
        val t = TransferThread(ParcelFileDescriptor.AutoCloseInputStream(output), outputStream)
        t.start()
        return t
    }

    @JvmStatic
    @Throws(IOException::class)
    fun pipeTo(outputStream: OutputStream): ParcelFileDescriptor {
        val pipe = ParcelFileDescriptor.createPipe()
        val readSide = pipe[0]
        val writeSide = pipe[1]

        TransferThread(ParcelFileDescriptor.AutoCloseInputStream(readSide), outputStream)
            .start()

        return writeSide
    }

    class TransferThread(private val mIn: InputStream, private val mOut: OutputStream) :
        Thread("IPC Transfer Thread") {
        init {
            isDaemon = true
        }

        override fun run() {
            val buf = ByteArray(IoUtils.DEFAULT_BUFFER_SIZE)
            var len: Int

            try {
                while (mIn.read(buf).also { len = it } > 0) {
                    mOut.write(buf, 0, len)
                }
            } catch (e: IOException) {
                Log.e("FD", "IOException when writing to out", e)
            } finally {
                try {
                    mIn.close()
                } catch (ignored: IOException) {
                }
                try {
                    mOut.close()
                } catch (ignored: IOException) {
                }
            }
        }
    }
}
