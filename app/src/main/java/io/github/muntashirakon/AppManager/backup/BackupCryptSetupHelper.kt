// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup

import io.github.muntashirakon.AppManager.crypto.*
import io.github.muntashirakon.AppManager.crypto.ks.CompatUtil
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.utils.ContextUtils

class BackupCryptSetupHelper @Throws(CryptoException::class) constructor(
    @CryptoUtils.Mode val mode: String,
    val version: Int
) {
    val crypto: Crypto = setup()
    var keyIds: String? = null
        private set
    var aes: ByteArray? = null
        private set
    var iv: ByteArray? = null
        private set

    @Throws(CryptoException::class)
    private fun setup(): Crypto {
        return when (mode) {
            CryptoUtils.MODE_OPEN_PGP -> {
                keyIds = Prefs.Encryption.getOpenPgpKeyIds()
                OpenPGPCrypto(ContextUtils.getContext(), keyIds)
            }
            CryptoUtils.MODE_AES -> {
                iv = generateIv()
                val aesCrypto = AESCrypto(iv!!)
                if (version < 4) {
                    // Old backups use 32 bit MAC
                    aesCrypto.setMacSizeBits(AESCrypto.MAC_SIZE_BITS_OLD)
                }
                aesCrypto
            }
            CryptoUtils.MODE_RSA -> {
                iv = generateIv()
                val rsaCrypto = RSACrypto(iv!!, null)
                if (version < 4) {
                    // Old backups use 32 bit MAC
                    rsaCrypto.setMacSizeBits(AESCrypto.MAC_SIZE_BITS_OLD)
                }
                aes = rsaCrypto.encryptedAesKey
                rsaCrypto
            }
            CryptoUtils.MODE_ECC -> {
                iv = generateIv()
                val eccCrypto = ECCCrypto(iv!!, null)
                if (version < 4) {
                    // Old backups use 32 bit MAC
                    eccCrypto.setMacSizeBits(AESCrypto.MAC_SIZE_BITS_OLD)
                }
                aes = eccCrypto.encryptedAesKey
                eccCrypto
            }
            CryptoUtils.MODE_NO_ENCRYPTION -> DummyCrypto()
            else -> DummyCrypto()
        }
    }

    companion object {
        private fun generateIv(): ByteArray {
            val iv = ByteArray(AESCrypto.GCM_IV_SIZE_BYTES)
            CompatUtil.getPrng().nextBytes(iv)
            return iv
        }
    }
}
