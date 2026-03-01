// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc

class ServiceNotFoundException : RuntimeException {
    constructor() : super()
    constructor(name: String?) : super(name)
    constructor(name: String?, cause: Exception?) : super(name, cause)
}
