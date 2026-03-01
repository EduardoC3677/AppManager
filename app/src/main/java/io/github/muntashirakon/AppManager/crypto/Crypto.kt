// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto

import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.backup.CryptoUtils
import io.github.muntashirakon.io.Path
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

interface Crypto : Closeable {
    @get:CryptoUtils.Mode
    val modeName: String

    @WorkerThread
    @Throws(IOException::class)
    fun encrypt(inputFiles: Array<Path>, outputFiles: Array<Path>)

    @WorkerThread
    @Throws(IOException::class)
    fun encrypt(unencryptedStream: InputStream, encryptedStream: OutputStream)

    @WorkerThread
    @Throws(IOException::class)
    fun decrypt(inputFiles: Array<Path>, outputFiles: Array<Path>)

    @WorkerThread
    @Throws(IOException::class)
    fun decrypt(encryptedStream: InputStream, unencryptedStream: OutputStream)

    override fun close()
}
