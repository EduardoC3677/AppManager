// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct

import io.github.muntashirakon.AppManager.rules.RuleType
import java.util.StringTokenizer

abstract class RuleEntry(
    val packageName: String,
    val name: String,
    val type: RuleType
) {
    override fun toString(): String {
        return "Entry{name='$name', type=$type}"
    }

    abstract fun flattenToString(isExternal: Boolean): String

    protected fun addPackageWithTab(isExternal: Boolean): String {
        return if (isExternal) "$packageName\t" else ""
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RuleEntry) return false
        return name == other.name && packageName == other.packageName && type == other.type
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + packageName.hashCode()
        result = 31 * result + type.hashCode()
        return result
    }

    companion object {
        const val STUB = "STUB"

        @JvmStatic
        @Throws(IllegalArgumentException::class)
        fun unflattenFromString(
            packageName: String?,
            ruleLine: String,
            isExternal: Boolean
        ): RuleEntry {
            val tokenizer = StringTokenizer(ruleLine, "\t")
            var pkgName = packageName

            if (isExternal) {
                // External rules, the first part is the package name
                if (tokenizer.hasMoreElements()) {
                    // Match package name
                    val newPackageName = tokenizer.nextElement().toString()
                    if (pkgName == null) pkgName = newPackageName
                    if (pkgName != newPackageName) {
                        throw IllegalArgumentException("Invalid format: package names do not match.")
                    }
                } else {
                    throw IllegalArgumentException("Invalid format: packageName not found for external rule.")
                }
            }

            if (pkgName == null) {
                // packageName can't be empty
                throw IllegalArgumentException("Package name cannot be empty.")
            }

            val name = if (tokenizer.hasMoreElements()) {
                tokenizer.nextElement().toString()
            } else {
                throw IllegalArgumentException("Invalid format: name not found")
            }

            val type = if (tokenizer.hasMoreElements()) {
                try {
                    RuleType.valueOf(tokenizer.nextElement().toString())
                } catch (e: Exception) {
                    throw IllegalArgumentException("Invalid format: Invalid type")
                }
            } else {
                throw IllegalArgumentException("Invalid format: entryType not found")
            }

            return getRuleEntry(pkgName, name, type, tokenizer)
        }

        @Throws(IllegalArgumentException::class)
        private fun getRuleEntry(
            packageName: String,
            name: String,
            type: RuleType,
            tokenizer: StringTokenizer
        ): RuleEntry {
            return when (type) {
                RuleType.ACTIVITY, RuleType.PROVIDER, RuleType.RECEIVER, RuleType.SERVICE ->
                    ComponentRule(packageName, name, type, tokenizer)
                RuleType.APP_OP -> AppOpRule(packageName, name, tokenizer)
                RuleType.PERMISSION -> PermissionRule(packageName, name, tokenizer)
                RuleType.MAGISK_HIDE -> MagiskHideRule(packageName, name, tokenizer)
                RuleType.MAGISK_DENY_LIST -> MagiskDenyListRule(packageName, name, tokenizer)
                RuleType.BATTERY_OPT -> BatteryOptimizationRule(packageName, tokenizer)
                RuleType.NET_POLICY -> NetPolicyRule(packageName, tokenizer)
                RuleType.NOTIFICATION -> NotificationListenerRule(packageName, name, tokenizer)
                RuleType.URI_GRANT -> UriGrantRule(packageName, tokenizer)
                RuleType.SSAID -> SsaidRule(packageName, tokenizer)
                RuleType.FREEZE -> FreezeRule(packageName, tokenizer)
                else -> throw IllegalArgumentException("Invalid type=${type.name}")
            }
        }
    }
}
