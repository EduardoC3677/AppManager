// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto.ks

import android.content.Context
import android.net.Uri
import android.sun.misc.BASE64Decoder
import android.sun.misc.BASE64Encoder
import android.sun.security.provider.JavaKeyStoreProvider
import android.sun.security.provider.X509Factory
import android.sun.security.x509.*
import android.text.TextUtils
import androidx.annotation.IntDef
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.io.IoUtils
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECParameterSpec
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.*
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.*
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.DSAPrivateKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPrivateCrtKeySpec
import java.util.*

object KeyStoreUtils {
    val TAG: String = KeyStoreUtils::class.java.simpleName

    @IntDef(KeyType.JKS, KeyType.PKCS12, KeyType.BKS, KeyType.PK8)
    @Retention(AnnotationRetention.SOURCE)
    annotation class KeyType {
        companion object {
            const val JKS = 0
            const val PKCS12 = 1
            const val BKS = 2
            const val PK8 = 3
        }
    }

    const val KEY_STORE_TYPE_JKS = "JKS"\nconst val KEY_STORE_TYPE_PKCS12 = "PKCS12"\nconst val KEY_STORE_TYPE_BKS = "BKS"\nprivate val TYPES = arrayOf(KEY_STORE_TYPE_JKS, KEY_STORE_TYPE_PKCS12, KEY_STORE_TYPE_BKS)

    @JvmStatic
    @Throws(IOException::class, GeneralSecurityException::class)
    fun listAliases(context: Context, ksUri: Uri, @KeyType ksType: Int, ksPass: CharArray?): ArrayList<String> {
        val keyType = TYPES[ksType]
        val ks = KeyStore.getInstance(keyType, getKeyStoreProvider(keyType))
        context.contentResolver.openInputStream(ksUri).use { isStream ->
            if (isStream == null) throw FileNotFoundException("$ksUri does not exist.")
            ks.load(isStream, ksPass)
        }
        return Collections.list(ks.aliases())
    }

    @JvmStatic
    @Throws(GeneralSecurityException::class, IOException::class)
    fun getKeyPair(context: Context, ksUri: Uri, @KeyType ksType: Int, ksAlias: String?, ksPass: CharArray?, aliasPass: CharArray?): KeyPair {
        val keyType = TYPES[ksType]
        val ks = KeyStore.getInstance(keyType, getKeyStoreProvider(keyType))
        context.contentResolver.openInputStream(ksUri).use { isStream ->
            if (isStream == null) throw FileNotFoundException("$ksUri does not exist.")
            ks.load(isStream, ksPass)
        }
        val alias = if (TextUtils.isEmpty(ksAlias)) ks.aliases().nextElement() else ksAlias!!
        val key = ks.getKey(alias, aliasPass)
        if (key is PrivateKey) return KeyPair(key, ks.getCertificate(alias) as X509Certificate)
        throw KeyStoreException("The provided alias $alias does not exist.")
    }

    @JvmStatic
    @Throws(GeneralSecurityException::class, IOException::class)
    fun getKeyPair(context: Context, keyPath: Uri, certPath: Uri): KeyPair {
        val cr = context.contentResolver
        val spec = PKCS8EncodedKeySpec(cr.openInputStream(keyPath).use { IoUtils.readFully(it, -1, true) })
        val cert = cr.openInputStream(certPath).use { CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate }
        val privateKey = KeyFactory.getInstance(cert.publicKey.algorithm).generatePrivate(spec)
        return KeyPair(privateKey, cert)
    }

    @JvmStatic
    @Throws(GeneralSecurityException::class, IOException::class)
    fun generateRSAKeyPair(formattedSubject: String, keySize: Int, expiryDate: Long): KeyPair {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(keySize, SecureRandom.getInstance("SHA1PRNG"))
        val keyPair = generator.generateKeyPair()
        return KeyPair(keyPair.private, generateRSACert(keyPair.private, keyPair.public, formattedSubject, expiryDate))
    }

    private fun getKeyStoreProvider(keyStoreType: String): Provider {
        return when (keyStoreType) {
            KEY_STORE_TYPE_PKCS12, KEY_STORE_TYPE_BKS -> BouncyCastleProvider()
            else -> JavaKeyStoreProvider()
        }
    }

    @JvmStatic
    @Throws(GeneralSecurityException::class)
    fun generateECCKeyPair(formattedSubject: String, expiryDate: Long): KeyPair {
        val curve25519 = CustomNamedCurves.getByName("curve25519")
        val spec = ECParameterSpec(curve25519.curve, curve25519.g, curve25519.n, curve25519.h)
        val generator = KeyPairGenerator.getInstance("ECDH", BouncyCastleProvider())
        generator.initialize(spec, SecureRandom.getInstance("SHA1PRNG"))
        val keyPair = generator.generateKeyPair()
        return KeyPair(keyPair.private, generateECDSACert(keyPair.private, keyPair.public, formattedSubject, expiryDate))
    }

    @JvmStatic
    @Throws(IOException::class, GeneralSecurityException::class)
    fun generatePrivateKey(inputStream: InputStream): PrivateKey {
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            var line: String?
            var readingKey = false
            var pkcs8 = false; var rsa = false; var dsa = false
            val base64 = StringBuilder()
            while (reader.readLine().also { line = it } != null) {
                line = line!!.trim()
                if (readingKey) {
                    when (line) {
                        RSA_END_HEADER, DSA_END_HEADER, PKCS8_END_HEADER -> readingKey = false
                        else -> base64.append(line)
                    }
                } else when (line) {
                    RSA_BEGIN_HEADER -> { readingKey = true; rsa = true }
                    DSA_BEGIN_HEADER -> { readingKey = true; dsa = true }
                    PKCS8_BEGIN_HEADER -> { readingKey = true; pkcs8 = true }
                }
            }
            if (base64.isEmpty()) throw IOException("Stream does not contain an unencrypted private key.")
            val bytes = BASE64Decoder().decodeBuffer(base64.toString())
            val kf: KeyFactory; val spec: java.security.spec.KeySpec
            when {
                pkcs8 -> { kf = KeyFactory.getInstance("RSA"); spec = PKCS8EncodedKeySpec(bytes) }
                rsa -> {
                    kf = KeyFactory.getInstance("RSA")
                    val ints = mutableListOf<BigInteger>()
                    ASN1Parse(bytes, ints)
                    if (ints.size < 8) throw InvalidKeyException("Stream does not appear to be a properly formatted RSA key.")
                    spec = RSAPrivateCrtKeySpec(ints[1], ints[2], ints[3], ints[4], ints[5], ints[6], ints[7], ints[8])
                }
                dsa -> {
                    kf = KeyFactory.getInstance("DSA")
                    val ints = mutableListOf<BigInteger>()
                    ASN1Parse(bytes, ints)
                    if (ints.size < 5) throw InvalidKeyException("Stream does not appear to be a properly formatted DSA key")
                    spec = DSAPrivateKeySpec(ints[1], ints[3], ints[4], ints[5])
                }
                else -> throw NoSuchAlgorithmException("Couldn't find any suitable algorithm")
            }
            return kf.generatePrivate(spec)
        }
    }

    @JvmStatic
    @Throws(java.security.cert.CertificateEncodingException::class, IOException::class)
    fun getPemCertificate(certificate: Certificate): ByteArray {
        val encoder = BASE64Encoder()
        ByteArrayOutputStream().use { bos ->
            bos.write(X509Factory.BEGIN_CERT.toByteArray(StandardCharsets.UTF_8))
            bos.write('
'.toInt())
            encoder.encode(certificate.encoded, bos)
            bos.write('
'.toInt())
            bos.write(X509Factory.END_CERT.toByteArray(StandardCharsets.UTF_8))
            return bos.toByteArray()
        }
    }

    private const val RSA_BEGIN_HEADER = "-----BEGIN RSA PRIVATE KEY-----"\nprivate const val RSA_END_HEADER = "-----END RSA PRIVATE KEY-----"\nprivate const val PKCS8_BEGIN_HEADER = "-----BEGIN PRIVATE KEY-----"\nprivate const val PKCS8_END_HEADER = "-----END PRIVATE KEY-----"\nprivate const val DSA_BEGIN_HEADER = "-----BEGIN DSA PRIVATE KEY-----"\nprivate const val DSA_END_HEADER = "-----END DSA PRIVATE KEY-----"\nprivate fun ASN1Parse(b: ByteArray, integers: MutableList<BigInteger>) {
        var pos = 0
        while (pos < b.size) {
            val tag = b[pos++].toInt()
            var len = b[pos++].toInt() and 0xFF
            if ((len and 0x80) != 0) {
                var extLen = 0
                for (i in 0 until (len and 0x7F)) extLen = (extLen shl 8) or (b[pos++].toInt() and 0xFF)
                len = extLen
            }
            val contents = b.copyOfRange(pos, pos + len)
            pos += len
            if (tag == 0x30) ASN1Parse(contents, integers)
            else if (tag == 0x02) integers.add(BigInteger(contents))
            else throw KeyException("Unsupported ASN.1 tag $tag")
        }
    }

    private fun generateECDSACert(privateKey: PrivateKey, publicKey: PublicKey, formattedSubject: String, expiryDate: Long): X509Certificate {
        val x500 = org.bouncycastle.asn1.x500.X500Name(formattedSubject)
        val builder = JcaContentSignerBuilder("SHA512withECDSA").setProvider(BouncyCastleProvider())
        val v3 = X509v3CertificateBuilder(x500, BigInteger.valueOf(SecureRandom().nextInt().toLong() and 0x7FFFFFFF), Date(), Date(expiryDate), x500, SubjectPublicKeyInfo.getInstance(publicKey.encoded))
        return JcaX509CertificateConverter().getCertificate(v3.build(builder.build(privateKey)))
    }

    private fun generateRSACert(privateKey: PrivateKey, publicKey: PublicKey, formattedSubject: String, expiryDate: Long): X509Certificate {
        val alg = "SHA512withRSA"
        val info = X509CertInfo()
        val ext = CertificateExtensions()
        ext.set(SubjectKeyIdentifierExtension.NAME, SubjectKeyIdentifierExtension(KeyIdentifier(publicKey).identifier))
        val name = X500Name(formattedSubject)
        val start = Date(); val end = Date(expiryDate)
        ext.set(PrivateKeyUsageExtension.NAME, PrivateKeyUsageExtension(start, end))
        info.set(X509CertInfo.VERSION, CertificateVersion(CertificateVersion.V3))
        info.set(X509CertInfo.SERIAL_NUMBER, CertificateSerialNumber(SecureRandom().nextInt() and 0x7FFFFFFF))
        info.set(X509CertInfo.ALGORITHM_ID, CertificateAlgorithmId(AlgorithmId.get(alg)))
        info.set(X509CertInfo.SUBJECT, CertificateSubjectName(name))
        info.set(X509CertInfo.KEY, CertificateX509Key(publicKey))
        info.set(X509CertInfo.VALIDITY, CertificateValidity(start, end))
        info.set(X509CertInfo.ISSUER, CertificateIssuerName(name))
        info.set(X509CertInfo.EXTENSIONS, ext)
        return X509CertImpl(info).apply { sign(privateKey, alg) }
    }
}
