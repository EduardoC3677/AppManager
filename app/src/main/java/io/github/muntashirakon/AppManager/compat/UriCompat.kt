// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat

import android.net.Uri

object UriCompat {
    /**
     * Index of a component which was not found.
     */
    private const val NOT_FOUND = -1

    /**
     * Encodes a value it wasn't already encoded.
     *
     * @param value string to encode
     * @param allow characters to allow
     * @return encoded value
     */
    @JvmStatic
    fun encodeIfNotEncoded(value: String?, allow: String?): String? {
        if (value == null) return null
        if (isEncoded(value, allow)) return value
        return Uri.encode(value, allow)
    }

    /**
     * Returns true if the given string is already encoded to safe characters.
     *
     * @param value string to check
     * @param allow characters to allow
     * @return true if the string is already encoded or false if it should be encoded
     */
    private fun isEncoded(value: String?, allow: String?): Boolean {
        if (value == null) return true
        for (index in 0 until value.length) {
            val c = value[index]

            // Allow % because that's the prefix for an encoded character. This method will fail
            // for decoded strings whose onlyinvalid character is %, but it's assumed that % alone
            // cannot cause malicious behavior in the framework.
            if (!isAllowed(c, allow) && c != '%') {
                return false
            }
        }
        return true
    }

    /**
     * Returns true if the given character is allowed.
     *
     * @param c     character to check
     * @param allow characters to allow
     * @return true if the character is allowed or false if it should be
     * encoded
     */
    private fun isAllowed(c: Char, allow: String?): Boolean {
        return (c in 'A'..'Z')
                || (c in 'a'..'z')
                || (c in '0'..'9')
                || "_-!.~'()*".indexOf(c) != NOT_FOUND
                || (allow != null && allow.indexOf(c) != NOT_FOUND)
    }
}
