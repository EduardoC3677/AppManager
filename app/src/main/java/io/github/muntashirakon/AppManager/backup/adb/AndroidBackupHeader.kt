// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.adb

import android.os.Build
import aosp.libcore.util.HexEncoding
import java.io.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import javax.crypto.*
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal class AndroidBackupHeader {
    companion object {
        private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"

        @JvmStatic
        @Throws(IOException::class)
        private fun readHeaderLine(`in`: InputStream): String {
            var c: Int
            val buffer = StringBuilder(80)
            while (`in`.read().also { c = it } >= 0) {
                if (c == '
'.toInt()) {
                    break // consume and discard the newlines
                }
                buffer.append(c.toChar())
            }
            return buffer.toString()
        }

        @JvmStatic
        @Throws(IOException::class)
        private fun readFullyOrThrow(`in`: InputStream, buffer: ByteArray) {
            var offset = 0
            while (offset < buffer.size) {
                val bytesRead = `in`.read(buffer, offset, buffer.size - offset)
                if (bytesRead <= 0) {
                    throw IOException("Couldn't fully read data")
                }
                offset += bytesRead
            }
        }

        @JvmStatic
        @Throws(Exception::class)
        fun makeKeyChecksum(algorithm: String, pwBytes: ByteArray, salt: ByteArray, rounds: Int): ByteArray {
            val mkAsChar = CharArray(pwBytes.size)
            for (i in pwBytes.indices) {
                mkAsChar[i] = pwBytes[i].toInt().toChar()
            }

            val checksum = buildCharArrayKey(algorithm, mkAsChar, salt, rounds)
            return checksum.encoded
        }

        @JvmStatic
        @Throws(Exception::class)
        private fun buildCharArrayKey(algorithm: String, pwArray: CharArray, salt: ByteArray, rounds: Int): SecretKey {
            val keyFactory = SecretKeyFactory.getInstance(algorithm)
            val ks = PBEKeySpec(pwArray, salt, rounds, Constants.PBKDF2_KEY_SIZE)
            return keyFactory.generateSecret(ks)
        }

        @JvmStatic
        fun byteArrayToHex(data: ByteArray): String {
            return HexEncoding.encodeToString(data, true)
        }

        @JvmStatic
        fun hexToByteArray(digits: String): ByteArray {
            val bytes = digits.length / 2
            if (2 * bytes != digits.length) {
                throw IllegalArgumentException("Hex string must have an even number of digits")
            }

            val result = ByteArray(bytes)
            for (i in 0 until digits.length step 2) {
                result[i / 2] = digits.substring(i, i + 2).toInt(16).toByte()
            }
            return result
        }

        @JvmStatic
        @Throws(Exception::class)
        private fun decodeAesHeaderAndInitialize(
            decryptPassword: CharArray,
            encryptionName: String,
            pbkdf2Fallback: Boolean,
            rawInStream: InputStream
        ): InputStream {
            if (encryptionName != Constants.ENCRYPTION_ALGORITHM_NAME) {
                throw IOException("Unsupported encryption method: $encryptionName")
            }

            val userSaltHex = readHeaderLine(rawInStream) // 5
            val userSalt = hexToByteArray(userSaltHex)

            val ckSaltHex = readHeaderLine(rawInStream) // 6
            val ckSalt = hexToByteArray(ckSaltHex)

            val rounds = Integer.parseInt(readHeaderLine(rawInStream)) // 7
            val userIvHex = readHeaderLine(rawInStream) // 8

            val encryptionKeyBlobHex = readHeaderLine(rawInStream) // 9

            // decrypt the encryption key blob
            return try {
                attemptEncryptionKeyDecryption(
                    decryptPassword, Constants.PBKDF_CURRENT, userSalt,
                    ckSalt, rounds, userIvHex, encryptionKeyBlobHex, rawInStream
                )
            } catch (e: Exception) {
                if (pbkdf2Fallback) {
                    attemptEncryptionKeyDecryption(
                        decryptPassword, Constants.PBKDF_FALLBACK, userSalt,
                        ckSalt, rounds, userIvHex, encryptionKeyBlobHex, rawInStream
                    )
                } else {
                    throw e
                }
            }
        }

        @JvmStatic
        @Throws(Exception::class)
        private fun attemptEncryptionKeyDecryption(
            decryptPassword: CharArray,
            algorithm: String,
            userSalt: ByteArray,
            ckSalt: ByteArray,
            rounds: Int,
            userIvHex: String,
            encryptionKeyBlobHex: String,
            rawInStream: InputStream
        ): InputStream {
            val c = Cipher.getInstance(TRANSFORMATION)
            val userKey = buildCharArrayKey(algorithm, decryptPassword, userSalt, rounds)
            val iv = hexToByteArray(userIvHex)
            var ivSpec = IvParameterSpec(iv)
            c.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(userKey.encoded, "AES"),
                ivSpec
            )
            val mkCipher = hexToByteArray(encryptionKeyBlobHex)
            val mkBlob = c.doFinal(mkCipher)

            // first, the encryption key IV
            var offset = 0
            var len = mkBlob[offset++].toInt()
            val encryptionIv = mkBlob.copyOfRange(offset, offset + len)
            offset += len
            // then the encryption key itself
            len = mkBlob[offset++].toInt()
            val encryptionKey = mkBlob.copyOfRange(offset, offset + len)
            offset += len
            // and finally the encryption key checksum hash
            len = mkBlob[offset++].toInt()
            val mkChecksum = mkBlob.copyOfRange(offset, offset + len)

            // now validate the decrypted encryption key against the checksum
            val calculatedCk = makeKeyChecksum(algorithm, encryptionKey, ckSalt, rounds)
            return if (MessageDigest.isEqual(calculatedCk, mkChecksum)) {
                ivSpec = IvParameterSpec(encryptionIv)
                c.init(
                    Cipher.DECRYPT_MODE,
                    SecretKeySpec(encryptionKey, "AES"),
                    ivSpec
                )
                // Only if all of the above worked properly will 'result' be assigned
                CipherInputStream(rawInStream, c)
            } else {
                throw IOException("Incorrect password")
            }
        }
    }

    private val mRng = SecureRandom()
    private var mBackupFileVersion: Int = 0
    private var mCompress: Boolean = false
    private val mPassword: CharArray?

    constructor(backupFileVersion: Int, compress: Boolean, password: CharArray?) {
        mBackupFileVersion = backupFileVersion
        mCompress = compress
        mPassword = password
    }

    constructor(password: CharArray?) {
        mBackupFileVersion = Constants.getBackupFileVersionFromApi(Build.VERSION.SDK_INT)
        mCompress = true
        mPassword = password
    }

    @Throws(Exception::class)
    fun read(backupStream: InputStream): InputStream {
        // First, parse out the unencrypted/uncompressed header
        var preCompressStream = backupStream

        val headerLen = Constants.BACKUP_FILE_HEADER_MAGIC.length
        val streamHeader = ByteArray(headerLen)
        readFullyOrThrow(backupStream, streamHeader)
        val magicBytes = Constants.BACKUP_FILE_HEADER_MAGIC.toByteArray(StandardCharsets.UTF_8)
        if (Arrays.equals(magicBytes, streamHeader)) {
            // okay, header looks good.  now parse out the rest of the fields.
            var s = readHeaderLine(backupStream)
            mBackupFileVersion = s.toInt()
            if (mBackupFileVersion <= Constants.BACKUP_FILE_VERSION) {
                // okay, it's a version we recognize.  if it's version 1, we may need
                // to try two different PBKDF2 regimes to compare checksums.
                val pbkdf2Fallback = mBackupFileVersion == 1

                s = readHeaderLine(backupStream)
                mCompress = s.toInt() != 0
                s = readHeaderLine(backupStream)
                if (s == "none") {
                    // no more header to parse; we're good to go
                } else if (mPassword != null && mPassword.isNotEmpty()) { // AES-256
                    preCompressStream = decodeAesHeaderAndInitialize(mPassword, s, pbkdf2Fallback, backupStream)
                } else {
                    throw IOException("Archive is encrypted but no password given")
                }
            } else {
                throw IOException("Wrong header version: $s")
            }
        } else {
            throw IOException("Didn't read the right header magic")
        }

        // okay, use the right stream layer based on compression
        return if (mCompress) InflaterInputStream(preCompressStream) else preCompressStream
    }

    @Throws(Exception::class)
    fun write(backupStream: OutputStream): OutputStream {
        val headerbuf = StringBuilder(1024)

        headerbuf.append(Constants.BACKUP_FILE_HEADER_MAGIC)
        headerbuf.append(mBackupFileVersion) // integer, no trailing 

        headerbuf.append(if (mCompress) "
1
" else "
0
")

        var finalOutput = backupStream
        // Set up the encryption stage if appropriate, and emit the correct header
        if (mPassword != null) {
            finalOutput = emitAesBackupHeader(headerbuf, backupStream)
        } else {
            headerbuf.append("none
")
        }

        val header = headerbuf.toString().toByteArray(StandardCharsets.UTF_8)
        backupStream.write(header)

        // Set up the compression stage feeding into the encryption stage (if any)
        if (mCompress) {
            val deflater = Deflater(Deflater.BEST_COMPRESSION)
            finalOutput = DeflaterOutputStream(finalOutput, deflater, true)
        }

        return finalOutput
    }

    @Throws(Exception::class)
    private fun emitAesBackupHeader(headerbuf: StringBuilder, ofstream: OutputStream): OutputStream {
        // User key will be used to encrypt the encryption key.
        val newUserSalt = randomBytes(Constants.PBKDF2_SALT_SIZE)
        val userKey = buildCharArrayKey(Constants.PBKDF_CURRENT, mPassword!!, newUserSalt, Constants.PBKDF2_HASH_ROUNDS)

        // the encryption key is random for each backup
        val encryptionKey = ByteArray(256 / 8)
        mRng.nextBytes(encryptionKey)
        val checksumSalt = randomBytes(Constants.PBKDF2_SALT_SIZE)

        // primary encryption of the datastream with the encryption key
        val c = Cipher.getInstance(TRANSFORMATION)
        val encryptionKeySpec = SecretKeySpec(encryptionKey, "AES")
        c.init(Cipher.ENCRYPT_MODE, encryptionKeySpec)
        val finalOutput = CipherOutputStream(ofstream, c)

        // line 4: name of encryption algorithm
        headerbuf.append(Constants.ENCRYPTION_ALGORITHM_NAME)
        headerbuf.append('
')
        // line 5: user password salt [hex]
        headerbuf.append(byteArrayToHex(newUserSalt))
        headerbuf.append('
')
        // line 6: encryption key checksum salt [hex]
        headerbuf.append(byteArrayToHex(checksumSalt))
        headerbuf.append('
')
        // line 7: number of PBKDF2 rounds used [decimal]
        headerbuf.append(Constants.PBKDF2_HASH_ROUNDS)
        headerbuf.append('
')

        // line 8: IV of the user key [hex]
        val mkC = Cipher.getInstance(TRANSFORMATION)
        mkC.init(Cipher.ENCRYPT_MODE, userKey)

        val ivForUserKey = mkC.iv
        headerbuf.append(byteArrayToHex(ivForUserKey))
        headerbuf.append('
')

        // line 9: encryption IV + key blob, encrypted by the user key [hex].  Blob format:
        //    [byte] IV length = Niv
        //    [array of Niv bytes] IV itself
        //    [byte] encryption key length = Nek
        //    [array of Nek bytes] encryption key itself
        //    [byte] encryption key checksum hash length = Nck
        //    [array of Nck bytes] encryption key checksum hash
        val encryptionIv = c.iv
        val mk = encryptionKeySpec.encoded
        val checksum = makeKeyChecksum(
            Constants.PBKDF_CURRENT,
            encryptionKeySpec.encoded,
            checksumSalt, Constants.PBKDF2_HASH_ROUNDS
        )

        val blob = ByteArrayOutputStream(encryptionIv.size + mk.size + checksum.size + 3)
        val mkOut = DataOutputStream(blob)
        mkOut.writeByte(encryptionIv.size)
        mkOut.write(encryptionIv)
        mkOut.writeByte(mk.size)
        mkOut.write(mk)
        mkOut.writeByte(checksum.size)
        mkOut.write(checksum)
        mkOut.flush()
        val encryptedMk = mkC.doFinal(blob.toByteArray())
        headerbuf.append(byteArrayToHex(encryptedMk))
        headerbuf.append('
')

        return finalOutput
    }

    /**
     * Used for generating random salts or passwords.
     */
    fun randomBytes(bits: Int): ByteArray {
        val array = ByteArray(bits / 8)
        mRng.nextBytes(array)
        return array
    }
}
