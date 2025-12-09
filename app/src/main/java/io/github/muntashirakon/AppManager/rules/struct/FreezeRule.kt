// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct

import io.github.muntashirakon.AppManager.rules.RuleType
import io.github.muntashirakon.AppManager.utils.FreezeUtils.FreezeMethod
import java.util.StringTokenizer

class FreezeRule : RuleEntry {
    @FreezeMethod
    var freezeType: Int
        private set

    constructor(
        packageName: String,
        @FreezeMethod freezeType: Int
    ) : super(packageName, STUB, RuleType.FREEZE) {
        this.freezeType = freezeType
    }

    constructor(
        packageName: String,
        tokenizer: StringTokenizer
    ) : super(packageName, STUB, RuleType.FREEZE) {
        if (tokenizer.hasMoreElements()) {
            this.freezeType = tokenizer.nextElement().toString().toInt()
        } else {
            throw IllegalArgumentException("Invalid format: freeze_type not found")
        }
    }

    fun setFreezeType(@FreezeMethod freezeType: Int) {
        this.freezeType = freezeType
    }

    override fun toString(): String {
        return "FreezeRule{freezeType=$freezeType, packageName='$packageName'}"
    }

    override fun flattenToString(isExternal: Boolean): String {
        return addPackageWithTab(isExternal) + "$name\t${type.name}\t$freezeType"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FreezeRule) return false
        if (!super.equals(other)) return false
        return freezeType == other.freezeType
    }

    override fun hashCode(): Int {
        return 31 * super.hashCode() + freezeType
    }
}
