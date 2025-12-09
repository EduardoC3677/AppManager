// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles.struct

class ProfileApplierResult {
    var requiresRestart: Boolean = false

    companion object {
        @JvmField
        val EMPTY_RESULT = ProfileApplierResult()
    }
}
