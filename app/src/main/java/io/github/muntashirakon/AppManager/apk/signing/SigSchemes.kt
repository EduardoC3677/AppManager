// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.signing

import androidx.annotation.IntDef

class SigSchemes(@SignatureScheme var flags: Int) {
    @IntDef(flag = true, value = [SIG_SCHEME_V1, SIG_SCHEME_V2, SIG_SCHEME_V3, SIG_SCHEME_V4])
    @Retention(AnnotationRetention.SOURCE)
    annotation class SignatureScheme

    fun isEmpty(): Boolean = flags == 0

    fun getAllItems(): List<Int> {
        val allItems = mutableListOf<Int>()
        for (i in 0 until TOTAL_SIG_SCHEME) {
            allItems.add(1 shl i)
        }
        return allItems
    }

    fun v1SchemeEnabled(): Boolean = (flags and SIG_SCHEME_V1) != 0
    fun v2SchemeEnabled(): Boolean = (flags and SIG_SCHEME_V2) != 0
    fun v3SchemeEnabled(): Boolean = (flags and SIG_SCHEME_V3) != 0
    fun v4SchemeEnabled(): Boolean = (flags and SIG_SCHEME_V4) != 0

    companion object {
        const val SIG_SCHEME_V1 = 1 shl 0
        const val SIG_SCHEME_V2 = 1 shl 1
        const val SIG_SCHEME_V3 = 1 shl 2
        const val SIG_SCHEME_V4 = 1 shl 3
        const val TOTAL_SIG_SCHEME = 4
        const val DEFAULT_SCHEMES = SIG_SCHEME_V1 or SIG_SCHEME_V2
    }
}
