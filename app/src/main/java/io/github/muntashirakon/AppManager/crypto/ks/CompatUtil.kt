// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto.ks

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.muntashirakon.AppManager.logs.Log
import java.io.IOException
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.*
import java.security.cert.CertificateException
import java.security.spec.RSAKeyGenParameterSpec
import java.util.*
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.security.auth.x500.X500Principal

object CompatUtil {
    private val TAG = CompatUtil::class.java.simpleName
    private const val ANDROID_KEY_STORE_PROVIDER = "AndroidKeyStore"\nprivate const val AES_GCM_CIPHER_TYPE = "AES/GCM/NoPadding"\nprivate const val AES_GCM_KEY_SIZE_IN_BITS = 128
    private const val AES_GCM_IV_LENGTH = 12
    private const val AES_LOCAL_PROTECTION_KEY_ALIAS = "aes_local_protection"\nprivate const val RSA_WRAP_LOCAL_PROTECTION_KEY_ALIAS = "rsa_wrap_local_protection"\nprivate const val RSA_WRAP_CIPHER_TYPE = "RSA/NONE/PKCS1Padding"\nprivate const val AES_WRAPPED_PROTECTION_KEY_SHARED_PREFERENCE = "aes_wrapped_local_protection"\nprivate const val SHARED_KEY_ANDROID_VERSION_WHEN_KEY_HAS_BEEN_GENERATED = "android_version_when_key_has_been_generated"\nprivate var sPrng: SecureRandom? = null

    @JvmStatic
    @Throws(KeyStoreException::class, CertificateException::class, NoSuchAlgorithmException::class, IOException::class, NoSuchProviderException::class, InvalidAlgorithmParameterException::class, NoSuchPaddingException::class, InvalidKeyException::class, IllegalBlockSizeException::class, UnrecoverableKeyException::class)
    private fun getAesGcmLocalProtectionKey(context: Context): SecretKeyAndVersion {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE_PROVIDER)
        keyStore.load(null)
        Log.i(TAG, "Loading local protection key")
        val sharedPreferences = context.getSharedPreferences("keystore", Context.MODE_PRIVATE)
        val androidVersionWhenTheKeyHasBeenGenerated = sharedPreferences.getInt(SHARED_KEY_ANDROID_VERSION_WHEN_KEY_HAS_BEEN_GENERATED, Build.VERSION.SDK_INT)
        if (keyStore.containsAlias(AES_LOCAL_PROTECTION_KEY_ALIAS)) {
            Log.i(TAG, "AES local protection key found in keystore")
            val secretKey = keyStore.getKey(AES_LOCAL_PROTECTION_KEY_ALIAS, null) as? SecretKey ?: throw KeyStoreException("Could not load AES local protection key from keystore")
            return SecretKeyAndVersion(secretKey, androidVersionWhenTheKeyHasBeenGenerated)
        }
        val secretKey = readKeyApiL(sharedPreferences, keyStore)
        if (secretKey != null) return SecretKeyAndVersion(secretKey, androidVersionWhenTheKeyHasBeenGenerated)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Log.i(TAG, "Generating AES key with keystore")
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE_PROVIDER)
            generator.init(KeyGenParameterSpec.Builder(AES_LOCAL_PROTECTION_KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setKeySize(AES_GCM_KEY_SIZE_IN_BITS)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
            val key = generator.generateKey()
            sharedPreferences.edit().putInt(SHARED_KEY_ANDROID_VERSION_WHEN_KEY_HAS_BEEN_GENERATED, Build.VERSION.SDK_INT).apply()
            return SecretKeyAndVersion(key, androidVersionWhenTheKeyHasBeenGenerated)
        }
        Log.i(TAG, "Generating RSA key pair with keystore")
        val generator = KeyPairGenerator.getInstance("RSA", ANDROID_KEY_STORE_PROVIDER)
        val start = Calendar.getInstance()
        val end = Calendar.getInstance().apply { add(Calendar.YEAR, 10) }
        generator.initialize(android.security.KeyPairGeneratorSpec.Builder(context)
            .setAlgorithmParameterSpec(RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4))
            .setAlias(RSA_WRAP_LOCAL_PROTECTION_KEY_ALIAS)
            .setSubject(X500Principal("CN=App Manager"))
            .setStartDate(start.time)
            .setEndDate(end.time)
            .setSerialNumber(BigInteger.ONE)
            .build())
        val keyPair = generator.generateKeyPair()
        Log.i(TAG, "Generating wrapped AES key")
        val aesKeyRaw = ByteArray(AES_GCM_KEY_SIZE_IN_BITS / Byte.SIZE_BITS)
        getPrng().nextBytes(aesKeyRaw)
        val key = SecretKeySpec(aesKeyRaw, "AES")
        val cipher = Cipher.getInstance(RSA_WRAP_CIPHER_TYPE)
        cipher.init(Cipher.WRAP_MODE, keyPair.public)
        val wrappedAesKey = cipher.wrap(key)
        sharedPreferences.edit()
            .putString(AES_WRAPPED_PROTECTION_KEY_SHARED_PREFERENCE, Base64.encodeToString(wrappedAesKey, 0))
            .putInt(SHARED_KEY_ANDROID_VERSION_WHEN_KEY_HAS_BEEN_GENERATED, Build.VERSION.SDK_INT)
            .apply()
        return SecretKeyAndVersion(key, androidVersionWhenTheKeyHasBeenGenerated)
    }

    @JvmStatic
    @Throws(KeyStoreException::class, NoSuchPaddingException::class, NoSuchAlgorithmException::class, InvalidKeyException::class, UnrecoverableKeyException::class)
    private fun readKeyApiL(sharedPreferences: android.content.SharedPreferences, keyStore: KeyStore): SecretKey? {
        val wrappedAesKeyString = sharedPreferences.getString(AES_WRAPPED_PROTECTION_KEY_SHARED_PREFERENCE, null)
        if (wrappedAesKeyString != null && keyStore.containsAlias(RSA_WRAP_LOCAL_PROTECTION_KEY_ALIAS)) {
            Log.i(TAG, "RSA + wrapped AES local protection keys found in keystore")
            val privateKey = keyStore.getKey(RSA_WRAP_LOCAL_PROTECTION_KEY_ALIAS, null) as PrivateKey
            val wrappedAesKey = Base64.decode(wrappedAesKeyString, 0)
            val cipher = Cipher.getInstance(RSA_WRAP_CIPHER_TYPE)
            cipher.init(Cipher.UNWRAP_MODE, privateKey)
            return cipher.unwrap(wrappedAesKey, "AES", Cipher.SECRET_KEY) as SecretKey
        }
        return null
    }

    @JvmStatic
    fun getPrng(): SecureRandom {
        if (sPrng == null) sPrng = SecureRandom()
        return sPrng!!
    }

    @JvmStatic
    @Throws(GeneralSecurityException::class, IOException::class)
    fun getEncryptedData(unencryptedData: ByteArray, context: Context): AesEncryptedData {
        val keyAndVersion = getAesGcmLocalProtectionKey(context)
        val cipher = Cipher.getInstance(AES_GCM_CIPHER_TYPE)
        val iv: ByteArray
        if (keyAndVersion.androidVersionWhenTheKeyHasBeenGenerated >= Build.VERSION_CODES.M) {
            cipher.init(Cipher.ENCRYPT_MODE, keyAndVersion.secretKey, getPrng())
            iv = cipher.iv
        } else {
            iv = ByteArray(AES_GCM_IV_LENGTH)
            getPrng().nextBytes(iv)
            cipher.init(Cipher.ENCRYPT_MODE, keyAndVersion.secretKey, IvParameterSpec(iv))
        }
        if (iv.size != AES_GCM_IV_LENGTH) throw InvalidAlgorithmParameterException("Invalid IV length ${iv.size}")
        val encryptedData = cipher.doFinal(unencryptedData)
        SecretKeyCompat.destroy(keyAndVersion.secretKey)
        return AesEncryptedData(iv, encryptedData)
    }

    @JvmStatic
    @Throws(GeneralSecurityException::class, IOException::class)
    fun decryptData(context: Context, encryptedData: ByteArray): ByteArray {
        val encryptedBuffer = ByteBuffer.wrap(encryptedData)
        val ivLen = encryptedBuffer.get().toInt()
        if (ivLen != AES_GCM_IV_LENGTH) throw InvalidAlgorithmParameterException("Invalid IV length $ivLen")
        val iv = ByteArray(AES_GCM_IV_LENGTH)
        encryptedBuffer.get(iv)
        val cipher = Cipher.getInstance(AES_GCM_CIPHER_TYPE)
        val keyAndVersion = getAesGcmLocalProtectionKey(context)
        val spec = if (keyAndVersion.androidVersionWhenTheKeyHasBeenGenerated >= Build.VERSION_CODES.M) GCMParameterSpec(AES_GCM_KEY_SIZE_IN_BITS, iv) else IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, keyAndVersion.secretKey, spec)
        val decryptedBuffer = ByteBuffer.allocate(cipher.getOutputSize(encryptedBuffer.remaining()))
        cipher.doFinal(encryptedBuffer, decryptedBuffer)
        return decryptedBuffer.array()
    }
}
