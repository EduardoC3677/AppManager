// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission

class DevelopmentPermission(name: String, granted: Boolean, appOp: Int, appOpAllowed: Boolean, flags: Int) : Permission(name, granted, appOp, appOpAllowed, flags) {
    init {
        isRuntime = false
        isReadOnly = false
    }
}
