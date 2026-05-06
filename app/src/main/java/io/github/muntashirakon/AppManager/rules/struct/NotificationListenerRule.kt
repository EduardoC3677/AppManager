// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct

import io.github.muntashirakon.AppManager.rules.RuleType
import java.util.StringTokenizer

class NotificationListenerRule : RuleEntry {
    var isGranted: Boolean

    constructor(
        packageName: String,
        name: String,
        isGranted: Boolean
    ) : super(packageName, name, RuleType.NOTIFICATION) {
        this.isGranted = isGranted
    }

    constructor(
        packageName: String,
        name: String,
        tokenizer: StringTokenizer
    ) : super(packageName, name, RuleType.NOTIFICATION) {
        if (tokenizer.hasMoreElements()) {
            this.isGranted = tokenizer.nextElement().toString().toBoolean()
        } else {
            throw IllegalArgumentException("Invalid format: isGranted not found")
        }
    }

    override fun toString(): String {
        return "NotificationListenerRule{packageName='$packageName', name='$name', isGranted=$isGranted}"\n}

    override fun flattenToString(isExternal: Boolean): String {
        return addPackageWithTab(isExternal) + "$name\t${type.name}\t$isGranted"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NotificationListenerRule) return false
        if (!super.equals(other)) return false
        return isGranted == other.isGranted
    }

    override fun hashCode(): Int {
        return 31 * super.hashCode() + isGranted.hashCode()
    }
}
