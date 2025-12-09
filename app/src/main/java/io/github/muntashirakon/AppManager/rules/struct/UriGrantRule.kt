// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct

import io.github.muntashirakon.AppManager.rules.RuleType
import io.github.muntashirakon.AppManager.uri.UriManager
import java.util.StringTokenizer

class UriGrantRule : RuleEntry {
    val uriGrant: UriManager.UriGrant

    constructor(
        packageName: String,
        uriGrant: UriManager.UriGrant
    ) : super(packageName, STUB, RuleType.URI_GRANT) {
        this.uriGrant = uriGrant
    }

    constructor(
        packageName: String,
        tokenizer: StringTokenizer
    ) : super(packageName, STUB, RuleType.URI_GRANT) {
        if (tokenizer.hasMoreElements()) {
            this.uriGrant = UriManager.UriGrant.unflattenFromString(tokenizer.nextElement().toString())
        } else {
            throw IllegalArgumentException("Invalid format: uriGrant not found")
        }
    }

    override fun toString(): String {
        return "UriGrantRule{packageName='$packageName', uriGrant=$uriGrant}"
    }

    override fun flattenToString(isExternal: Boolean): String {
        return addPackageWithTab(isExternal) + "$name\t${type.name}\t${uriGrant.flattenToString()}"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UriGrantRule) return false
        if (!super.equals(other)) return false
        return uriGrant == other.uriGrant
    }

    override fun hashCode(): Int {
        return 31 * super.hashCode() + uriGrant.hashCode()
    }
}
