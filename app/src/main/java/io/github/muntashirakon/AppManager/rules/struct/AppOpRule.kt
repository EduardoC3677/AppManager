// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat.Mode
import io.github.muntashirakon.AppManager.rules.RuleType
import java.util.StringTokenizer

class AppOpRule : RuleEntry {
    val op: Int
    @Mode
    var mode: Int

    constructor(
        packageName: String,
        op: Int,
        @Mode mode: Int
    ) : super(packageName, op.toString(), RuleType.APP_OP) {
        this.op = op
        this.mode = mode
    }

    @Throws(RuntimeException::class)
    constructor(
        packageName: String,
        opInt: String,
        tokenizer: StringTokenizer
    ) : super(packageName, opInt, RuleType.APP_OP) {
        this.op = opInt.toInt()
        if (tokenizer.hasMoreElements()) {
            this.mode = tokenizer.nextElement().toString().toInt()
        } else {
            throw IllegalArgumentException("Invalid format: mode not found")
        }
    }

    fun setMode(@Mode mode: Int) {
        this.mode = mode
    }

    override fun toString(): String {
        return "AppOpRule{packageName='$packageName', op=$op, mode=$mode}"
    }

    override fun flattenToString(isExternal: Boolean): String {
        return addPackageWithTab(isExternal) + "$op\t${type.name}\t$mode"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AppOpRule) return false
        if (!super.equals(other)) return false
        return op == other.op && mode == other.mode
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + op
        result = 31 * result + mode
        return result
    }
}
