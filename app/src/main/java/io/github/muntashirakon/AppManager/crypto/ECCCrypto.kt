// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto

import io.github.muntashirakon.AppManager.backup.CryptoUtils
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class ECCCrypto @Throws(CryptoException::class) constructor(iv: ByteArray, encryptedAesKey: ByteArray?) : AESCrypto(iv, CryptoUtils.MODE_ECC, encryptedAesKey) {
    @Throws(CryptoException::class)
    override fun getEncryptedAesKey(): ByteArray = super.getEncryptedAesKey()

    companion object {
        const val TAG = "ECCCrypto"
        const val ECC_EXT = ".ecc"
        const val ECC_KEY_ALIAS = "backup_ecc"
        private const val ECC_CIPHER_TYPE = "ECIES"
        private const val AES_KEY_SIZE_BITS = 256

        @JvmStatic
        fun generateAesKey(): SecretKey {
            val random = SecureRandom()
            val key = ByteArray(AES_KEY_SIZE_BITS / 8)
            random.nextBytes(key)
            return SecretKeySpec(key, "AES")
        }

        @JvmStatic
        @Throws(CryptoException::class)
        fun decryptAesKey(encryptedAesKey: ByteArray): SecretKey {
            val keyPair = try {
                KeyStoreManager.getInstance().getKeyPair(ECC_KEY_ALIAS) ?: throw CryptoException("No KeyPair with alias $ECC_KEY_ALIAS")
            } catch (e: Exception) { throw CryptoException(e) }
            return try {
                val cipher = Cipher.getInstance(ECC_CIPHER_TYPE, BouncyCastleProvider())
                cipher.init(Cipher.DECRYPT_MODE, keyPair.privateKey)
                SecretKeySpec(cipher.doFinal(encryptedAesKey), "AES")
            } catch (e: Exception) { throw CryptoException(e) }
        }

        @JvmStatic
        @Throws(CryptoException::class)
        fun encryptAesKey(key: SecretKey): ByteArray {
            val keyPair = try {
                KeyStoreManager.getInstance().getKeyPair(ECC_KEY_ALIAS) ?: throw CryptoException("No KeyPair with alias $ECC_KEY_ALIAS")
            } catch (e: Exception) { throw CryptoException(e) }
            return try {
                val cipher = Cipher.getInstance(ECC_CIPHER_TYPE, BouncyCastleProvider())
                cipher.init(Cipher.ENCRYPT_MODE, keyPair.publicKey)
                cipher.doFinal(key.encoded)
            } catch (e: Exception) { throw CryptoException(e) }
        }
    }
}
