// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto.ks

import android.annotation.SuppressLint
import io.github.muntashirakon.AppManager.utils.Utils
import java.lang.reflect.Field
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.security.auth.DestroyFailedException

@SuppressLint("SoonBlockedPrivateApi")
object SecretKeyCompat {
    private val KEY: Field? = try {
        SecretKeySpec::class.java.getDeclaredField("key").apply { isAccessible = true }
    } catch (ignore: Exception) { null }

    @JvmStatic
    @Throws(DestroyFailedException::class)
    fun destroy(secretKey: SecretKey) {
        if (KEY != null && secretKey is SecretKeySpec) {
            try {
                (KEY.get(secretKey) as? ByteArray)?.let { Utils.clearBytes(it) }
                KEY.set(secretKey, null)
            } catch (e: Exception) {
                throw DestroyFailedException(e.toString()).apply { stackTrace = e.stackTrace }
            }
        }
    }
}
