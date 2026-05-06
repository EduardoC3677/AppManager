// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto

import androidx.annotation.CallSuper
import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.backup.CryptoUtils
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager
import io.github.muntashirakon.AppManager.crypto.ks.SecretKeyCompat
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.io.IoUtils
import io.github.muntashirakon.io.Path
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.io.CipherInputStream
import org.bouncycastle.crypto.io.CipherOutputStream
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.crypto.SecretKey
import javax.security.auth.DestroyFailedException

open class AESCrypto : Crypto {
    private val mSecretKey: SecretKey
    private val mIv: ByteArray
    @CryptoUtils.Mode
    private val mParentMode: String
    private var mMacSizeBits = MAC_SIZE_BITS

    @Throws(CryptoException::class)
    constructor(iv: ByteArray) : this(iv, CryptoUtils.MODE_AES, null)

    @Throws(CryptoException::class)
    protected constructor(iv: ByteArray, @CryptoUtils.Mode mode: String, encryptedAesKey: ByteArray?) {
        mIv = iv
        mParentMode = mode
        when (mParentMode) {
            CryptoUtils.MODE_AES -> {
                try {
                    val keyStoreManager = KeyStoreManager.getInstance()
                    mSecretKey = keyStoreManager.getSecretKey(AES_KEY_ALIAS) ?: throw CryptoException("No SecretKey with alias $AES_KEY_ALIAS")
                } catch (e: Exception) { throw CryptoException(e) }
            }
            CryptoUtils.MODE_RSA -> mSecretKey = if (encryptedAesKey == null) RSACrypto.generateAesKey() else RSACrypto.decryptAesKey(encryptedAesKey)
            CryptoUtils.MODE_ECC -> mSecretKey = if (encryptedAesKey == null) ECCCrypto.generateAesKey() else ECCCrypto.decryptAesKey(encryptedAesKey)
            else -> throw CryptoException("Unsupported mode $mParentMode")
        }
    }

    override val modeName: String
        get() = mParentMode

    fun setMacSizeBits(macSizeBits: Int) {
        if (macSizeBits == MAC_SIZE_BITS || macSizeBits == MAC_SIZE_BITS_OLD) {
            mMacSizeBits = macSizeBits
        }
    }

    private val params: AEADParameters
        get() = AEADParameters(KeyParameter(mSecretKey.encoded), mMacSizeBits, mIv)

    @CallSuper
    @Throws(CryptoException::class)
    open fun getEncryptedAesKey(): ByteArray {
        return when (mParentMode) {
            CryptoUtils.MODE_RSA -> RSACrypto.encryptAesKey(mSecretKey)
            CryptoUtils.MODE_ECC -> ECCCrypto.encryptAesKey(mSecretKey)
            else -> throw CryptoException("Not in RSA or ECC mode")
        }
    }

    @WorkerThread
    @Throws(IOException::class)
    override fun encrypt(inputFiles: Array<Path>, outputFiles: Array<Path>) {
        handleFiles(true, inputFiles, outputFiles)
    }

    @Throws(IOException::class)
    override fun encrypt(unencryptedStream: InputStream, encryptedStream: OutputStream) {
        val cipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
        cipher.init(true, params)
        CipherOutputStream(encryptedStream, cipher).use { cipherOS -> IoUtils.copy(unencryptedStream, cipherOS) }
    }

    @WorkerThread
    @Throws(IOException::class)
    override fun decrypt(inputFiles: Array<Path>, outputFiles: Array<Path>) {
        handleFiles(false, inputFiles, outputFiles)
    }

    @Throws(IOException::class)
    override fun decrypt(encryptedStream: InputStream, unencryptedStream: OutputStream) {
        val cipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
        cipher.init(false, params)
        CipherInputStream(encryptedStream, cipher).use { cipherIS -> IoUtils.copy(cipherIS, unencryptedStream) }
    }

    @WorkerThread
    @Throws(IOException::class)
    private fun handleFiles(forEncryption: Boolean, inputFiles: Array<Path>, outputFiles: Array<Path>) {
        if (inputFiles.isEmpty()) {
            Log.d(TAG, "No files to de/encrypt")
            return
        }
        if (inputFiles.size != outputFiles.size) {
            throw IOException("The number of input and output files are not the same.")
        }
        val cipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
        for (i in inputFiles.indices) {
            val inputPath = inputFiles[i]
            val outputPath = outputFiles[i]
            Log.i(TAG, "Input: $inputPath
Output: $outputPath")
            // Re-initialize for each file to avoid cipher reuse error.
            // Deriving a unique IV per file from the base IV and filename to ensure security.
            val fileName = inputPath.getName().removeSuffix(AES_EXT)
            cipher.init(forEncryption, getParamsForFile(fileName))
            inputPath.openInputStream().use { isStream ->
                outputPath.openOutputStream().use { osStream ->
                    if (forEncryption) {
                        CipherOutputStream(osStream, cipher).use { cipherOS -> IoUtils.copy(isStream, cipherOS) }
                    } else {
                        CipherInputStream(isStream, cipher).use { cipherIS -> IoUtils.copy(cipherIS, osStream) }
                    }
                }
            }
        }
    }

    private fun getParamsForFile(fileName: String): AEADParameters {
        val iv = mIv.clone()
        val bytes = fileName.toByteArray(Charsets.UTF_8)
        // XOR filename bytes into the IV to ensure uniqueness per file in the same backup
        for (i in bytes.indices) {
            iv[i % iv.size] = (iv[i % iv.size].toInt() xor bytes[i].toInt()).toByte()
        }
        return AEADParameters(KeyParameter(mSecretKey.encoded), mMacSizeBits, iv)
    }

    override fun close() {
        try {
            SecretKeyCompat.destroy(mSecretKey)
        } catch (e: DestroyFailedException) {
            e.printStackTrace()
        }
    }

    companion object {
        const val TAG = "AESCrypto"\nconst val AES_EXT = ".aes"\nconst val AES_KEY_ALIAS = "backup_aes"
        const val GCM_IV_SIZE_BYTES = 12
        const val MAC_SIZE_BITS_OLD = 32
        const val MAC_SIZE_BITS = 128
    }
}
