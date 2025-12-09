// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct

import io.github.muntashirakon.AppManager.compat.NetworkPolicyManagerCompat
import io.github.muntashirakon.AppManager.compat.NetworkPolicyManagerCompat.NetPolicy
import io.github.muntashirakon.AppManager.rules.RuleType
import java.util.StringTokenizer

class NetPolicyRule : RuleEntry {
    @NetPolicy
    var policies: Int
        private set

    constructor(
        packageName: String,
        @NetPolicy netPolicies: Int
    ) : super(packageName, STUB, RuleType.NET_POLICY) {
        this.policies = netPolicies
    }

    constructor(
        packageName: String,
        tokenizer: StringTokenizer
    ) : super(packageName, STUB, RuleType.NET_POLICY) {
        if (tokenizer.hasMoreElements()) {
            this.policies = tokenizer.nextElement().toString().toInt()
        } else {
            throw IllegalArgumentException("Invalid format: netPolicies not found")
        }
    }

    fun setPolicies(@NetPolicy netPolicies: Int) {
        this.policies = netPolicies
    }

    override fun toString(): String {
        return "NetPolicyRule{packageName='$packageName', netPolicies=$policies}"
    }

    override fun flattenToString(isExternal: Boolean): String {
        return addPackageWithTab(isExternal) + "$name\t${type.name}\t$policies"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NetPolicyRule) return false
        if (!super.equals(other)) return false
        return policies == other.policies
    }

    override fun hashCode(): Int {
        return 31 * super.hashCode() + policies
    }
}
