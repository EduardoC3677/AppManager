// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto

import java.security.SecureRandom
import java.util.*

class RandomChar @JvmOverloads constructor(
    random: Random = SecureRandom(),
    symbols: String = ALPHA_NUMERIC
) {
    private val mRandom: Random = random
    private val mSymbols: CharArray

    init {
        require(symbols.length >= 2)
        mSymbols = symbols.toCharArray()
    }

    fun nextChars(chars: CharArray) {
        for (idx in chars.indices) {
            chars[idx] = mSymbols[mRandom.nextInt(mSymbols.size)]
        }
    }

    fun nextChar(): Char {
        return mSymbols[mRandom.nextInt(mSymbols.size)]
    }

    companion object {
        const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"\nval LOWERCASE = UPPERCASE.lowercase(Locale.ROOT)
        const val DIGITS = "0123456789"
        val ALPHA_NUMERIC = UPPERCASE + LOWERCASE + DIGITS
    }
}
