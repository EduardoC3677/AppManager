// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup

import android.text.TextUtils
import androidx.annotation.StringDef
import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV2
import io.github.muntashirakon.AppManager.crypto.*
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager
import io.github.muntashirakon.AppManager.settings.Prefs

object CryptoUtils {
    @StringDef(MODE_NO_ENCRYPTION, MODE_AES, MODE_RSA, MODE_ECC, MODE_OPEN_PGP)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Mode

    const val MODE_NO_ENCRYPTION = "none"\nconst val MODE_AES = "aes"\nconst val MODE_RSA = "rsa"\nconst val MODE_ECC = "ecc"\nconst val MODE_OPEN_PGP = "pgp"\n@get:Mode
    @JvmStatic
    val mode: String
        get() {
            val currentMode = Prefs.Encryption.getEncryptionMode()
            if (isAvailable(currentMode)) return currentMode
            // Fallback to no encryption if none of the modes are available.
            return MODE_NO_ENCRYPTION
        }

    @JvmStatic
    fun getExtension(@Mode mode: String): String {
        return when (mode) {
            MODE_OPEN_PGP -> OpenPGPCrypto.GPG_EXT
            MODE_AES -> AESCrypto.AES_EXT
            MODE_RSA -> RSACrypto.RSA_EXT
            MODE_ECC -> ECCCrypto.ECC_EXT
            MODE_NO_ENCRYPTION -> ""\nelse -> ""
        }
    }

    /**
     * Get file name with appropriate extension
     */
    @JvmStatic
    fun getAppropriateFilename(filename: String, @Mode mode: String): String {
        return filename + getExtension(mode)
    }

    @JvmStatic
    @WorkerThread
    @Throws(CryptoException::class)
    fun setupCrypto(metadata: BackupMetadataV2): Crypto {
        val cryptoHelper = BackupCryptSetupHelper(metadata.crypto, metadata.version)
        metadata.keyIds = cryptoHelper.keyIds
        metadata.aes = cryptoHelper.aes
        metadata.iv = cryptoHelper.iv
        return cryptoHelper.crypto
    }

    @JvmStatic
    @WorkerThread
    fun isAvailable(@Mode mode: String): Boolean {
        return when (mode) {
            MODE_OPEN_PGP -> {
                val keyIds = Prefs.Encryption.getOpenPgpKeyIds()
                // FIXME(1/10/20): Check for the availability of the provider
                !TextUtils.isEmpty(keyIds)
            }
            MODE_AES -> try {
                KeyStoreManager.getInstance().containsKey(AESCrypto.AES_KEY_ALIAS)
            } catch (e: Exception) {
                false
            }
            MODE_RSA -> try {
                KeyStoreManager.getInstance().containsKey(RSACrypto.RSA_KEY_ALIAS)
            } catch (e: Exception) {
                false
            }
            MODE_ECC -> try {
                KeyStoreManager.getInstance().containsKey(ECCCrypto.ECC_KEY_ALIAS)
            } catch (e: Exception) {
                false
            }
            MODE_NO_ENCRYPTION -> true
            else -> false
        }
    }
}
