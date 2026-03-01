// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.compat

import java.util.Objects

object ObjectsCompat {
    @JvmStatic
    fun <T> requireNonNullElse(obj: T?, defaultObj: T): T {
        return obj ?: Objects.requireNonNull(defaultObj, "defaultObj")
    }
}
