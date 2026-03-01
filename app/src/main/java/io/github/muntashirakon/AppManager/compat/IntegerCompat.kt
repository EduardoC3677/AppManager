// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat

object IntegerCompat {
    /**
     * Return a 0x prefixed signed hex.
     */
    @JvmStatic
    fun toSignedHex(signedInt: Int): String {
        val sb = StringBuilder()
        sb.append(Integer.toString(signedInt, 16))
        sb.insert(if (sb[0] == '-') 1 else 0, "0x")
        return sb.toString()
    }

    /**
     * Return a 0x prefixed unsigned hex.
     */
    @JvmStatic
    fun toUnsignedHex(signedInt: Int): String {
        return "0x" + Integer.toHexString(signedInt)
    }

    /**
     * Same as {@link Integer#decode(String)} except it allows decoding both signed and unsigned values
     */
    @JvmStatic
    @Throws(NumberFormatException::class)
    fun decode(nm: String): Int {
        var radix = 10
        var index = 0

        if (nm.isEmpty()) {
            throw NumberFormatException("Zero length string")
        }
        val firstChar = nm[0]
        // Handle sign, if present
        if (firstChar == '-') {
            // First character is a signed character, use regular decoding
            return Integer.decode(nm)
        } else if (firstChar == '+') {
            index++
        }

        // Handle radix specifier, if present
        if (nm.startsWith("0x", index) || nm.startsWith("0X", index)) {
            index += 2
            radix = 16
        } else if (nm.startsWith("#", index)) {
            index++
            radix = 16
        } else if (nm.startsWith("0", index) && nm.length > 1 + index) {
            index++
            radix = 8
        }

        if (nm.startsWith("-", index) || nm.startsWith("+", index)) {
            throw NumberFormatException("Sign character in wrong position")
        }

        return Integer.parseUnsignedInt(nm.substring(index), radix)
    }
}
