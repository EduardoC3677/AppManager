// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct

import io.github.muntashirakon.AppManager.rules.RuleType
import java.util.StringTokenizer

class BatteryOptimizationRule : RuleEntry {
    var isEnabled: Boolean

    constructor(
        packageName: String,
        enabled: Boolean
    ) : super(packageName, STUB, RuleType.BATTERY_OPT) {
        this.isEnabled = enabled
    }

    constructor(
        packageName: String,
        tokenizer: StringTokenizer
    ) : super(packageName, STUB, RuleType.BATTERY_OPT) {
        if (tokenizer.hasMoreElements()) {
            this.isEnabled = tokenizer.nextElement().toString().toBoolean()
        } else {
            throw IllegalArgumentException("Invalid format: enabled not found")
        }
    }

    override fun toString(): String {
        return "BatteryOptimizationRule{packageName='$packageName', enabled=$isEnabled}"
    }

    override fun flattenToString(isExternal: Boolean): String {
        return addPackageWithTab(isExternal) + "$name\t${type.name}\t$isEnabled"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BatteryOptimizationRule) return false
        if (!super.equals(other)) return false
        return isEnabled == other.isEnabled
    }

    override fun hashCode(): Int {
        return 31 * super.hashCode() + isEnabled.hashCode()
    }
}
