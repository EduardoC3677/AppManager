// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.signing

import android.os.Build
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.utils.DigestUtils
import io.github.muntashirakon.AppManager.utils.ExUtils
import java.io.File
import java.security.PrivateKey
import java.security.SignatureException
import java.security.cert.CertificateEncodingException
import java.security.cert.X509Certificate
import java.security.interfaces.DSAKey
import java.security.interfaces.ECKey
import java.security.interfaces.RSAKey
import aosp.libcore.util.HexEncoding

class Signer private constructor(
    private val mSigSchemes: SigSchemes,
    private val mPrivateKey: PrivateKey,
    private val mCertificate: X509Certificate
) {
    private var mIdsigFile: File? = null

    val isV4SchemeEnabled: Boolean
        get() = mSigSchemes.v4SchemeEnabled()

    fun setIdsigFile(idsigFile: File?) {
        mIdsigFile = idsigFile
    }

    fun sign(input: File, output: File, minSdk: Int, alignFileSize: Boolean): Boolean {
        val signerConfig = ApkSigner.SignerConfig.Builder("CERT", mPrivateKey, listOf(mCertificate)).build()
        val builder = ApkSigner.Builder(listOf(signerConfig)).apply {
            setInputApk(input)
            setOutputApk(output)
            setCreatedBy("AppManager")
            setAlignFileSize(alignFileSize)
            if (minSdk != -1) setMinSdkVersion(minSdk)
            setV1SigningEnabled(mSigSchemes.v1SchemeEnabled())
            setV2SigningEnabled(mSigSchemes.v2SchemeEnabled())
            setV3SigningEnabled(mSigSchemes.v3SchemeEnabled())
            if (mSigSchemes.v4SchemeEnabled()) {
                if (mIdsigFile == null) throw RuntimeException("idsig file is mandatory for v4 signature scheme.")
                setV4SigningEnabled(true)
                setV4SignatureOutputFile(mIdsigFile)
            } else {
                setV4SigningEnabled(false)
            }
        }
        val signer = builder.build()
        Log.i(TAG, "SignApk: $input")
        return try {
            if (alignFileSize && !ZipAlign.verify(input, ZipAlign.ALIGNMENT_4, true)) {
                ZipAlign.align(input, ZipAlign.ALIGNMENT_4, true)
            }
            signer.sign()
            Log.i(TAG, "The signature is complete and the output file is $output")
            true
        } catch (e: Exception) {
            Log.w(TAG, e)
            false
        }
    }

    companion object {
        const val TAG = "Signer"\nconst val SIGNING_KEY_ALIAS = "signing_key"\n@JvmStatic
        fun canSign(): Boolean {
            return try {
                KeyStoreManager.getInstance().containsKey(SIGNING_KEY_ALIAS)
            } catch (e: Exception) {
                false
            }
        }

        @JvmStatic
        @Throws(SignatureException::class)
        fun getInstance(sigSchemes: SigSchemes): Signer {
            return try {
                val manager = KeyStoreManager.getInstance()
                val signingKey = manager.getKeyPair(SIGNING_KEY_ALIAS) ?: throw java.security.KeyStoreException("Alias $SIGNING_KEY_ALIAS does not exist in KeyStore.")
                Signer(sigSchemes, signingKey.privateKey, signingKey.certificate as X509Certificate)
            } catch (e: Exception) {
                throw SignatureException(e)
            }
        }

        @JvmStatic
        fun verify(sigSchemes: SigSchemes, apk: File, idsig: File?): Boolean {
            val builder = ApkVerifier.Builder(apk).setMaxCheckedPlatformVersion(Build.VERSION.SDK_INT)
            if (sigSchemes.v4SchemeEnabled()) {
                if (idsig == null) throw RuntimeException("idsig file is mandatory for v4 signature scheme.")
                builder.setV4SignatureFile(idsig)
            }
            val verifier = builder.build()
            return try {
                val result = verifier.verify()
                Log.i(TAG, "$apk")
                val isVerify = result.isVerified
                if (isVerify) {
                    if (sigSchemes.v1SchemeEnabled() && result.isVerifiedUsingV1Scheme()) Log.i(TAG, "V1 signature verified.") else Log.w(TAG, "V1 signature verification failed/disabled.")
                    if (sigSchemes.v2SchemeEnabled() && result.isVerifiedUsingV2Scheme()) Log.i(TAG, "V2 signature verified.") else Log.w(TAG, "V2 signature verification failed/disabled.")
                    if (sigSchemes.v3SchemeEnabled()) {
                        if (result.isVerifiedUsingV3Scheme()) Log.i(TAG, "V3 signature verified.") else Log.w(TAG, "V3 signature verification failed.")
                        if (result.isVerifiedUsingV31Scheme()) Log.i(TAG, "V3.1 signature verified.") else Log.w(TAG, "V3.1 signature verification failed.")
                    } else Log.w(TAG, "V3 signature verification disabled.")
                    if (sigSchemes.v4SchemeEnabled() && result.isVerifiedUsingV4Scheme()) Log.i(TAG, "V4 signature verified.") else Log.w(TAG, "V4 signature verification failed/disabled.")
                    if (result.isSourceStampVerified) Log.i(TAG, "SourceStamp verified.") else Log.w(TAG, "SourceStamp not verified/unavailable.")
                    result.signerCertificates.forEachIndexed { i, cert -> logCert(cert, "Signature${i + 1}") }
                }
                result.warnings.forEach { Log.w(TAG, "$it") }
                result.errors.forEach { Log.e(TAG, "$it") }
                if (sigSchemes.v1SchemeEnabled()) {
                    result.v1SchemeIgnoredSigners.forEach { signer ->
                        signer.errors.forEach { Log.e(TAG, "${signer.name}: $it") }
                        signer.warnings.forEach { Log.w(TAG, "${signer.name}: $it") }
                    }
                }
                isVerify
            } catch (e: Exception) {
                Log.w(TAG, "Verification failed.", e)
                false
            }
        }

        @JvmStatic
        fun getSourceStampSource(sourceStampInfo: ApkVerifier.Result.SourceStampInfo): String? {
            val certBytes = ExUtils.exceptionAsNull { sourceStampInfo.certificate.encoded } ?: return null
            val sourceStampHash = DigestUtils.getHexDigest(DigestUtils.SHA_256, certBytes)
            return if (sourceStampHash == "3257d599a49d2c961a471ca9843f59d341a405884583fc087df4237b733bbd6d") "Google Play" else null
        }

        private fun logCert(x509Certificate: X509Certificate, charSequence: CharSequence) {
            val subjectDN = x509Certificate.subjectDN
            Log.i(TAG, "$charSequence - Unique distinguished name: $subjectDN")
            logEncoded(charSequence, x509Certificate.encoded)
            val publicKey = x509Certificate.publicKey
            val bitLength = when (publicKey) {
                is RSAKey -> publicKey.modulus.bitLength()
                is ECKey -> publicKey.params.order.bitLength()
                is DSAKey -> publicKey.params?.p?.bitLength() ?: -1
                else -> -1
            }
            Log.i(TAG, "$charSequence - key size: ${if (bitLength != -1) bitLength.toString() else "Unknown"}")
            Log.i(TAG, "$charSequence - key algorithm: ${publicKey.algorithm}")
            logEncoded(charSequence, publicKey.encoded)
        }

        private fun logEncoded(charSequence: CharSequence, bArr: ByteArray) {
            log("$charSequence - SHA-256: ", DigestUtils.getDigest(DigestUtils.SHA_256, bArr))
            log("$charSequence - SHA-1: ", DigestUtils.getDigest(DigestUtils.SHA_1, bArr))
            log("$charSequence - MD5: ", DigestUtils.getDigest(DigestUtils.MD5, bArr))
        }

        private fun log(str: String, bArr: ByteArray) {
            Log.i(TAG, str)
            Log.w(TAG, HexEncoding.encodeToString(bArr))
        }
    }
}
