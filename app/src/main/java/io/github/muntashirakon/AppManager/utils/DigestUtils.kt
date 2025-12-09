// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import android.annotation.TargetApi
import android.text.TextUtils
import android.util.Pair
import androidx.annotation.AnyThread
import androidx.annotation.StringDef
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import aosp.libcore.util.HexEncoding
import io.github.muntashirakon.io.IoUtils
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream

object DigestUtils {
    @StringDef(CRC32, MD2, MD5, SHA_1, SHA_224, SHA_256, SHA_384, SHA_512)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Algorithm

    const val CRC32 = "CRC32"
    const val MD2 = "MD2"
    const val MD5 = "MD5"
    const val SHA_1 = "SHA-1"

    @TargetApi(22)
    const val SHA_224 = "SHA-224"
    const val SHA_256 = "SHA-256"
    const val SHA_384 = "SHA-384"
    const val SHA_512 = "SHA-512"

    @AnyThread
    @JvmStatic
    fun getHexDigest(@Algorithm algo: String, bytes: ByteArray): String {
        return HexEncoding.encodeToString(getDigest(algo, bytes), false /* lowercase */)
    }

    @VisibleForTesting
    @WorkerThread
    @JvmStatic
    fun getHexDigest(@Algorithm algo: String, path: File): String {
        return getHexDigest(algo, Paths.get(path))
    }

    @WorkerThread
    @JvmStatic
    fun getHexDigest(@Algorithm algo: String, path: Path): String {
        val allFiles = Paths.getAll(path)
        val hashes = ArrayList<String>(allFiles.size)
        for (file in allFiles) {
            if (file.isDirectory) continue
            try {
                file.openInputStream().use { fileInputStream ->
                    hashes.add(getHexDigest(algo, fileInputStream))
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        if (hashes.isEmpty()) return HexEncoding.encodeToString(ByteArray(0), false /* lowercase */)
        if (hashes.size == 1) return hashes[0]
        val fullString = TextUtils.join("", hashes)
        return getHexDigest(algo, fullString.toByteArray())
    }

    @WorkerThread
    @JvmStatic
    fun getHexDigest(@Algorithm algo: String, stream: InputStream): String {
        return HexEncoding.encodeToString(getDigest(algo, stream), false /* lowercase */)
    }

    @AnyThread
    @JvmStatic
    fun getDigest(@Algorithm algo: String, bytes: ByteArray): ByteArray {
        if (CRC32 == algo) {
            return longToBytes(calculateCrc32(bytes))
        }
        return try {
            MessageDigest.getInstance(algo).digest(bytes)
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
            ByteArray(0)
        }
    }

    @WorkerThread
    @JvmStatic
    fun getDigest(@Algorithm algo: String, stream: InputStream): ByteArray {
        if (CRC32 == algo) {
            return try {
                longToBytes(calculateCrc32(stream))
            } catch (e: IOException) {
                e.printStackTrace()
                ByteArray(0)
            }
        }
        return try {
            val messageDigest = MessageDigest.getInstance(algo)
            DigestInputStream(stream, messageDigest).use { digestInputStream ->
                val buffer = ByteArray(IoUtils.DEFAULT_BUFFER_SIZE)
                while (digestInputStream.read(buffer) != -1) {
                    // Read until end of stream
                }
                digestInputStream.close()
                messageDigest.digest()
            }
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
            ByteArray(0)
        } catch (e: IOException) {
            e.printStackTrace()
            ByteArray(0)
        }
    }

    @WorkerThread
    @JvmStatic
    @Throws(IOException::class)
    fun calculateCrc32(file: Path): Long {
        file.openInputStream().use { inputStream ->
            return calculateCrc32(inputStream)
        }
    }

    @AnyThread
    @JvmStatic
    fun calculateCrc32(bytes: ByteArray): Long {
        val crc32 = CRC32()
        crc32.update(bytes)
        return crc32.value
    }

    @AnyThread
    @JvmStatic
    @Throws(IOException::class)
    fun calculateCrc32(stream: InputStream): Long {
        val crc32 = CRC32()
        val buffer = ByteArray(IoUtils.DEFAULT_BUFFER_SIZE)
        CheckedInputStream(stream, crc32).use { cis ->
            while (cis.read(buffer) >= 0) {
                // Read until end of stream
            }
        }
        return crc32.value
    }

    @WorkerThread
    @JvmStatic
    @Throws(IOException::class)
    fun getDigests(file: Path): Array<Pair<String, String>> {
        if (!file.isFile) {
            throw IOException("$file is not a file.")
        }
        @Algorithm val algorithms = arrayOf(MD5, SHA_1, SHA_256, SHA_384, SHA_512)
        val messageDigests = Array(algorithms.size) { i ->
            try {
                MessageDigest.getInstance(algorithms[i])
            } catch (e: NoSuchAlgorithmException) {
                throw ExUtils.rethrowAsIOException<MessageDigest>(e)
            }
        }
        file.openInputStream().use { inputStream ->
            val buffer = ByteArray(IoUtils.DEFAULT_BUFFER_SIZE)
            var length: Int
            while (inputStream.read(buffer).also { length = it } != -1) {
                for (i in algorithms.indices) {
                    messageDigests[i].update(buffer, 0, length)
                }
            }
        }
        return Array(algorithms.size) { i ->
            Pair(algorithms[i], HexEncoding.encodeToString(messageDigests[i].digest(), false))
        }
    }

    @AnyThread
    @JvmStatic
    fun getDigests(bytes: ByteArray): Array<Pair<String, String>> {
        @Algorithm val algorithms = arrayOf(MD5, SHA_1, SHA_256, SHA_384, SHA_512)
        return Array(algorithms.size) { i ->
            Pair(algorithms[i], getHexDigest(algorithms[i], bytes))
        }
    }

    @JvmStatic
    fun longToBytes(l: Long): ByteArray {
        val bytes = ByteArray(4)
        bytes[0] = ((l shr 24) and 0xFF).toByte()
        bytes[1] = ((l shr 16) and 0xFF).toByte()
        bytes[2] = ((l shr 8) and 0xFF).toByte()
        bytes[3] = (l and 0xFF).toByte()
        return bytes
    }
}
