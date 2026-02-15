// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct

import io.github.muntashirakon.AppManager.rules.RuleType
import java.util.StringTokenizer

class SsaidRule : RuleEntry {
    var ssaid: String

    constructor(
        packageName: String,
        ssaid: String
    ) : super(packageName, STUB, RuleType.SSAID) {
        this.ssaid = ssaid
    }

    constructor(
        packageName: String,
        tokenizer: StringTokenizer
    ) : super(packageName, STUB, RuleType.SSAID) {
        if (tokenizer.hasMoreElements()) {
            this.ssaid = tokenizer.nextElement().toString()
        } else {
            throw IllegalArgumentException("Invalid format: ssaid not found")
        }
    }

    override fun toString(): String {
        return "SsaidRule{packageName='$packageName', ssaid='$ssaid'}"
    }

    override fun flattenToString(isExternal: Boolean): String {
        return addPackageWithTab(isExternal) + "$name\t${type.name}\t$ssaid"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SsaidRule) return false
        if (!super.equals(other)) return false
        return ssaid == other.ssaid
    }

    override fun hashCode(): Int {
        return 31 * super.hashCode() + ssaid.hashCode()
    }
}
