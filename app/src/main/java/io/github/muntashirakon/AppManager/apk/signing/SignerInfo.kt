// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.signing

import android.content.pm.Signature
import android.content.pm.SigningInfo
import android.os.Build
import androidx.annotation.RequiresApi
import com.android.apksig.ApkVerifier
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class SignerInfo {
    val currentSignerCerts: Array<X509Certificate>?
    val signerCertsInLineage: Array<X509Certificate>?
    val allSignerCerts: Array<X509Certificate>?
    val sourceStampCert: X509Certificate?

    constructor(apkVerifierResult: ApkVerifier.Result) {
        val certificates = apkVerifierResult.signerCertificates
        if (certificates.isNullOrEmpty()) {
            currentSignerCerts = null
        } else {
            currentSignerCerts = certificates.toTypedArray()
        }
        val sourceStampInfo = apkVerifierResult.sourceStampInfo
        sourceStampCert = sourceStampInfo?.certificate
        if (currentSignerCerts == null || currentSignerCerts.size > 1) {
            allSignerCerts = currentSignerCerts
            signerCertsInLineage = null
            return
        }
        val lineage = apkVerifierResult.signingCertificateLineage
        if (lineage == null) {
            allSignerCerts = currentSignerCerts
            signerCertsInLineage = null
            return
        }
        val certificatesInLineage = lineage.certificatesInLineage
        if (certificatesInLineage.isNullOrEmpty()) {
            allSignerCerts = currentSignerCerts
            signerCertsInLineage = null
            return
        }
        signerCertsInLineage = certificatesInLineage.toTypedArray()
        allSignerCerts = arrayOf(*currentSignerCerts, *signerCertsInLineage)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    constructor(signingInfo: SigningInfo?) {
        sourceStampCert = null
        if (signingInfo == null) {
            currentSignerCerts = null
            signerCertsInLineage = null
            allSignerCerts = null
            return
        }
        val currentSignatures = signingInfo.apkContentsSigners
        val lineageSignatures = signingInfo.signingCertificateHistory
        val isLineage = !signingInfo.hasMultipleSigners() && signingInfo.hasPastSigningCertificates()
        if (currentSignatures.isNullOrEmpty()) {
            currentSignerCerts = null
            signerCertsInLineage = null
            allSignerCerts = null
            return
        }
        if (isLineage && lineageSignatures.isNullOrEmpty()) {
            currentSignerCerts = null
            signerCertsInLineage = null
            allSignerCerts = null
            return
        }
        val totalSigner = currentSignatures.size + (if (isLineage) lineageSignatures.size else 0)
        currentSignerCerts = Array(currentSignatures.size) { generateCertificateOrFail(currentSignatures[it]) }
        val allCertsList = currentSignerCerts.toMutableList()
        if (isLineage) {
            signerCertsInLineage = Array(lineageSignatures.size) { generateCertificateOrFail(lineageSignatures[it]) }
            allCertsList.addAll(signerCertsInLineage)
        } else {
            signerCertsInLineage = null
        }
        allSignerCerts = allCertsList.toTypedArray()
    }

    constructor(signatures: Array<Signature>?) {
        sourceStampCert = null
        signerCertsInLineage = null
        if (!signatures.isNullOrEmpty()) {
            currentSignerCerts = Array(signatures.size) { generateCertificateOrFail(signatures[it]) }
            allSignerCerts = currentSignerCerts
        } else {
            currentSignerCerts = null
            allSignerCerts = null
        }
    }

    fun hasMultipleSigners(): Boolean = currentSignerCerts != null && currentSignerCerts.size > 1

    fun hasProofOfRotation(): Boolean = !hasMultipleSigners() && signerCertsInLineage != null

    companion object {
        private fun generateCertificateOrFail(signature: Signature): X509Certificate {
            return try {
                ByteArrayInputStream(signature.toByteArray()).use { `is` ->
                    CertificateFactory.getInstance("X.509").generateCertificate(`is`) as X509Certificate
                }
            } catch (e: Exception) {
                throw RuntimeException("Invalid signature", e)
            }
        }
    }
}
