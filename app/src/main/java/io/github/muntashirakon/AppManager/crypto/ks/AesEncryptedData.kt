// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto.ks

import androidx.core.util.Pair

class AesEncryptedData(iv: ByteArray, encryptedData: ByteArray) : Pair<ByteArray, ByteArray>(iv, encryptedData) {
    val iv: ByteArray
        get() = first!!

    val encryptedData: ByteArray
        get() = second!!
}
