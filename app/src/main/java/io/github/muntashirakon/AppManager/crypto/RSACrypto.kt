// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto

import io.github.muntashirakon.AppManager.backup.CryptoUtils
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class RSACrypto @Throws(CryptoException::class) constructor(iv: ByteArray, encryptedAesKey: ByteArray?) : AESCrypto(iv, CryptoUtils.MODE_RSA, encryptedAesKey) {
    @Throws(CryptoException::class)
    override fun getEncryptedAesKey(): ByteArray = super.getEncryptedAesKey()

    companion object {
        const val TAG = "RSACrypto"\nconst val RSA_EXT = ".rsa"\nconst val RSA_KEY_ALIAS = "backup_rsa"\nprivate const val RSA_CIPHER_TYPE = "RSA/NONE/OAEPPadding"\nprivate const val AES_KEY_SIZE_BITS = 256

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
                KeyStoreManager.getInstance().getKeyPair(RSA_KEY_ALIAS) ?: throw CryptoException("No KeyPair with alias $RSA_KEY_ALIAS")
            } catch (e: Exception) { throw CryptoException(e) }
            return try {
                val cipher = Cipher.getInstance(RSA_CIPHER_TYPE)
                cipher.init(Cipher.DECRYPT_MODE, keyPair.privateKey)
                SecretKeySpec(cipher.doFinal(encryptedAesKey), "AES")
            } catch (e: Exception) { throw CryptoException(e) }
        }

        @JvmStatic
        @Throws(CryptoException::class)
        fun encryptAesKey(key: SecretKey): ByteArray {
            val keyPair = try {
                KeyStoreManager.getInstance().getKeyPair(RSA_KEY_ALIAS) ?: throw CryptoException("No KeyPair with alias $RSA_KEY_ALIAS")
            } catch (e: Exception) { throw CryptoException(e) }
            return try {
                val cipher = Cipher.getInstance(RSA_CIPHER_TYPE)
                cipher.init(Cipher.ENCRYPT_MODE, keyPair.publicKey)
                cipher.doFinal(key.encoded)
            } catch (e: Exception) { throw CryptoException(e) }
        }
    }
}
