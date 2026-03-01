// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.AppManager.crypto.ks

import androidx.core.util.Pair
import javax.crypto.SecretKey

class SecretKeyAndVersion(secretKey: SecretKey, androidVersionWhenTheKeyHasBeenGenerated: Int) : Pair<SecretKey, Int>(secretKey, androidVersionWhenTheKeyHasBeenGenerated) {
    val secretKey: SecretKey
        get() = first!!

    val androidVersionWhenTheKeyHasBeenGenerated: Int
        get() = second!!
}
