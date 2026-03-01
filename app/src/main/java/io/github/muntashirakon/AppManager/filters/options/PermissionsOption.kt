// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options

import android.content.Context
import android.text.SpannableStringBuilder
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo
import io.github.muntashirakon.AppManager.utils.LangUtils

class PermissionsOption : FilterOption("permissions") {
    private val mKeysWithType = linkedMapOf(
        KEY_ALL to TYPE_NONE,
        "eq" to TYPE_STR_SINGLE,
        "contains" to TYPE_STR_SINGLE,
        "starts_with" to TYPE_STR_SINGLE,
        "ends_with" to TYPE_STR_SINGLE,
        "regex" to TYPE_REGEX
        // TODO: 11/19/24 Add more curated options such as permission flags, private flags, grant
    )

    override fun getKeysWithType(): Map<String, Int> = mKeysWithType

    override fun test(info: IFilterableAppInfo, result: TestResult): TestResult {
        val permissions = result.getMatchedPermissions() ?: info.allPermissions
        return when (key) {
            KEY_ALL -> result.setMatched(true).setMatchedPermissions(permissions)
            "eq" -> {
                val filteredPermissions = permissions.filter { it == value }
                result.setMatched(filteredPermissions.isNotEmpty()).setMatchedPermissions(filteredPermissions)
            }
            "contains" -> {
                val filteredPermissions = permissions.filter { it.contains(value!!) }
                result.setMatched(filteredPermissions.isNotEmpty()).setMatchedPermissions(filteredPermissions)
            }
            "starts_with" -> {
                val filteredPermissions = permissions.filter { it.startsWith(value!!) }
                result.setMatched(filteredPermissions.isNotEmpty()).setMatchedPermissions(filteredPermissions)
            }
            "ends_with" -> {
                val filteredPermissions = permissions.filter { it.endsWith(value!!) }
                result.setMatched(filteredPermissions.isNotEmpty()).setMatchedPermissions(filteredPermissions)
            }
            "regex" -> {
                val filteredPermissions = permissions.filter { regexValue!!.matcher(it).matches() }
                result.setMatched(filteredPermissions.isNotEmpty()).setMatchedPermissions(filteredPermissions)
            }
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }

    override fun toLocalizedString(context: Context): CharSequence {
        val sb = SpannableStringBuilder("Permissions")
        return when (key) {
            KEY_ALL -> sb.append(LangUtils.getSeparatorString()).append("any")
            "eq" -> sb.append(" = '").append(value).append("'")
            "contains" -> sb.append(" contains '").append(value).append("'")
            "starts_with" -> sb.append(" starts with '").append(value).append("'")
            "ends_with" -> sb.append(" ends with '").append(value).append("'")
            "regex" -> sb.append(" matches '").append(value).append("'")
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }
}
