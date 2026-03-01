// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission

import android.util.AndroidException

class PermissionException : AndroidException {
    constructor(name: String?) : super(name)
    constructor(name: String?, cause: Throwable?) : super(name, cause)
    constructor(cause: Exception?) : super(cause)
}
