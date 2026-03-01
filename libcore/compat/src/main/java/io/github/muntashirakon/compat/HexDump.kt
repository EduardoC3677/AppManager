// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.compat

object HexDump {
    private val HEX_DIGITS = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')
    private val HEX_LOWER_CASE_DIGITS = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

    @JvmStatic
    fun toHexString(array: ByteArray): String {
        return toHexString(array, 0, array.size, true)
    }

    @JvmStatic
    fun toHexString(array: ByteArray, offset: Int, length: Int): String {
        return toHexString(array, offset, length, true)
    }

    @JvmStatic
    fun toHexString(array: ByteArray, offset: Int, length: Int, upperCase: Boolean): String {
        val digits = if (upperCase) HEX_DIGITS else HEX_LOWER_CASE_DIGITS
        val buf = CharArray(length * 2)

        var bufIndex = 0
        for (i in offset until offset + length) {
            val b = array[i].toInt()
            buf[bufIndex++] = digits[(b ushr 4) and 0x0F]
            buf[bufIndex++] = digits[b and 0x0F]
        }

        return String(buf)
    }

    private fun toByte(c: Char): Int {
        if (c in '0'..'9') return c - '0'
        if (c in 'A'..'F') return c - 'A' + 10
        if (c in 'a'..'f') return c - 'a' + 10

        throw RuntimeException("Invalid hex char '$c'")
    }

    @JvmStatic
    fun hexStringToByteArray(hexString: String): ByteArray {
        val length = hexString.length
        val buffer = ByteArray(length / 2)

        var i = 0
        while (i < length) {
            buffer[i / 2] = ((toByte(hexString[i]) shl 4) or toByte(hexString[i + 1])).toByte()
            i += 2
        }

        return buffer
    }
}
