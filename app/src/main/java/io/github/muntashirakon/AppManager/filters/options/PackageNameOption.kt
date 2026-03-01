// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options

import android.content.Context
import android.text.SpannableStringBuilder
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo
import io.github.muntashirakon.AppManager.utils.LangUtils

class PackageNameOption : FilterOption("pkg_name") {
    private val mKeysWithType = linkedMapOf(
        KEY_ALL to TYPE_NONE,
        "eq" to TYPE_STR_SINGLE,
        "eq_any" to TYPE_STR_MULTIPLE,
        "eq_none" to TYPE_STR_MULTIPLE,
        "contains" to TYPE_STR_SINGLE,
        "starts_with" to TYPE_STR_SINGLE,
        "ends_with" to TYPE_STR_SINGLE,
        "regex" to TYPE_REGEX
    )

    override fun getKeysWithType(): Map<String, Int> = mKeysWithType

    override fun test(info: IFilterableAppInfo, result: TestResult): TestResult {
        return when (key) {
            KEY_ALL -> result.setMatched(true)
            "eq" -> result.setMatched(info.packageName == value)
            "eq_any" -> {
                for (packageName in stringValues!!) {
                    if (info.packageName == packageName) {
                        return result.setMatched(true)
                    }
                }
                result.setMatched(false)
            }
            "eq_none" -> {
                for (packageName in stringValues!!) {
                    if (info.packageName == packageName) {
                        return result.setMatched(false)
                    }
                }
                result.setMatched(true)
            }
            "contains" -> result.setMatched(info.packageName.contains(value!!))
            "starts_with" -> result.setMatched(info.packageName.startsWith(value!!))
            "ends_with" -> result.setMatched(info.packageName.endsWith(value!!))
            "regex" -> result.setMatched(regexValue!!.matcher(info.packageName).matches())
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }

    override fun toLocalizedString(context: Context): CharSequence {
        val sb = SpannableStringBuilder("Package name")
        return when (key) {
            KEY_ALL -> sb.append(LangUtils.getSeparatorString()).append("any")
            "eq" -> sb.append(" = '").append(value).append("'")
            "eq_any" -> sb.append(" matching any of ").append(stringValues!!.joinToString(", "))
            "eq_none" -> sb.append(" matching none of ").append(stringValues!!.joinToString(", "))
            "contains" -> sb.append(" contains '").append(value).append("'")
            "starts_with" -> sb.append(" starts with '").append(value).append("'")
            "ends_with" -> sb.append(" ends with '").append(value).append("'")
            "regex" -> sb.append(" matches '").append(value).append("'")
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }
}
