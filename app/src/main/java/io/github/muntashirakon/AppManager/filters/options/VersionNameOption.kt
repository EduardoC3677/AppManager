// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options

import android.content.Context
import android.text.SpannableStringBuilder
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo
import io.github.muntashirakon.AppManager.utils.LangUtils

class VersionNameOption : FilterOption("version_name") {
    private val mKeysWithType = linkedMapOf(
        KEY_ALL to TYPE_NONE,
        "eq" to TYPE_STR_SINGLE,
        "contains" to TYPE_STR_SINGLE,
        "starts_with" to TYPE_STR_SINGLE,
        "ends_with" to TYPE_STR_SINGLE,
        "regex" to TYPE_REGEX
    )

    override fun getKeysWithType(): Map<String, Int> = mKeysWithType

    override fun test(info: IFilterableAppInfo, result: TestResult): TestResult {
        val versionName = info.versionName
        if (versionName == null) {
            return result.setMatched(key == KEY_ALL)
        }
        return when (key) {
            KEY_ALL -> result.setMatched(true)
            "eq" -> result.setMatched(versionName == value)
            "contains" -> result.setMatched(versionName.contains(value!!))
            "starts_with" -> result.setMatched(versionName.startsWith(value!!))
            "ends_with" -> result.setMatched(versionName.endsWith(value!!))
            "regex" -> result.setMatched(regexValue!!.matcher(versionName).matches())
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }

    override fun toLocalizedString(context: Context): CharSequence {
        val sb = SpannableStringBuilder("Version name")
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
