// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

object NonNullUtils {
    @JvmStatic
    fun defeatNullable(longValue: Long?): Long {
        return longValue ?: 0
    }

    @JvmStatic
    fun defeatNullable(integerValue: Int?): Int {
        return integerValue ?: 0
    }
}
