// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.util

import android.content.Context

interface LocalizedString {
    fun toLocalizedString(context: Context): CharSequence
}
