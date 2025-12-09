// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder

object TextUtilsCompat {
    /**
     * Returns a string containing the tokens joined by delimiters.
     *
     * @param delimiter a CharSequence that will be inserted between the tokens. If null, the string
     *                  "null" will be used as the delimiter.
     * @param tokens    an array objects to be joined. Strings will be formed from the objects by
     *                  calling object.toString() except CharSequence. If tokens is null, a
     *                  NullPointerException will be thrown. If tokens is empty, an empty string
     *                  will be returned.
     */
    @JvmStatic
    fun joinSpannable(delimiter: CharSequence, tokens: Iterable<*>): Spannable {
        val it = tokens.iterator()
        if (!it.hasNext()) {
            return SpannableString("")
        }
        val sb = SpannableStringBuilder()
        var obj = it.next()
        if (obj is CharSequence) {
            sb.append(obj)
        } else {
            sb.append(obj.toString())
        }
        while (it.hasNext()) {
            sb.append(delimiter)
            obj = it.next()
            if (obj is CharSequence) {
                sb.append(obj)
            } else {
                sb.append(obj.toString())
            }
        }
        return sb
    }

    /**
     * @return interned string if it's not null.
     */
    @JvmStatic
    fun safeIntern(s: String?): String? {
        return s?.intern()
    }
}
