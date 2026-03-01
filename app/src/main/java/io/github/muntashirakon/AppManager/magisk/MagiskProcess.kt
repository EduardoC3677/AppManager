// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.magisk

import java.util.*

class MagiskProcess {
    val packageName: String
    val name: String

    var isIsolatedProcess: Boolean = false
    var isRunning: Boolean = false
    var isEnabled: Boolean = false
    var isAppZygote: Boolean = false

    constructor(packageName: String, name: String) {
        this.packageName = packageName
        this.name = name
    }

    constructor(packageName: String) {
        this.packageName = packageName
        this.name = packageName
    }

    constructor(magiskProcess: MagiskProcess) {
        packageName = magiskProcess.packageName
        name = magiskProcess.name
        isIsolatedProcess = magiskProcess.isIsolatedProcess
        isRunning = magiskProcess.isRunning
        isEnabled = magiskProcess.isEnabled
        isAppZygote = magiskProcess.isAppZygote
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MagiskProcess) return false
        return name == other.name
    }

    override fun hashCode(): Int = Objects.hash(name)
}
