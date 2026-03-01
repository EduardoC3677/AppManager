// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner

import java.util.*

class SignatureInfo {
    val signature: String
    val label: String
    val type: String
    val classes: MutableList<String> = ArrayList()

    var count: Int = 0

    constructor(signature: String, label: String) {
        this.signature = signature
        this.label = label
        this.type = "Tracker" // Arbitrary type
    }

    constructor(signature: String, label: String, type: String) {
        this.signature = signature
        this.label = label
        this.type = type
    }

    fun addClass(className: String) {
        classes.add(className)
    }

    override fun toString(): String {
        return "SignatureInfo(signature='$signature', label='$label', type='$type', classes=$classes, count=$count)"
    }
}
