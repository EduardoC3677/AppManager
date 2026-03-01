// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto.ks

import androidx.core.util.Pair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.Certificate
import javax.security.auth.DestroyFailedException

class KeyPair(first: PrivateKey, second: Certificate) : Pair<PrivateKey, Certificate>(first, second) {
    val privateKey: PrivateKey
        get() = first!!

    val publicKey: PublicKey
        get() = second!!.publicKey

    val certificate: Certificate
        get() = second!!

    @Throws(DestroyFailedException::class)
    fun destroy() {
        try {
            first!!.destroy()
        } catch (ignore: NoSuchMethodError) {}
    }
}
