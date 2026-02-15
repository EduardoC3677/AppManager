// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles.struct

class ProfileApplierResult {
    var requiresRestart: Boolean = false

    @JvmName("requiresRestart")
    fun requiresRestart(): Boolean = requiresRestart

    @JvmName("setRequiresRestart")
    fun setRequiresRestart(requiresRestart: Boolean) {
        this.requiresRestart = requiresRestart
    }

    companion object {
        @JvmField
        val EMPTY_RESULT: ProfileApplierResult = ProfileApplierResult()
    }
}
