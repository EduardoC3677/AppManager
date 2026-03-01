// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.format.Formatter
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo
import io.github.muntashirakon.AppManager.utils.LangUtils

class DataUsageOption : FilterOption("data_usage") {
    private val mKeysWithType = linkedMapOf(
        KEY_ALL to TYPE_NONE,
        "eq" to TYPE_SIZE_BYTES,
        "le" to TYPE_SIZE_BYTES,
        "ge" to TYPE_SIZE_BYTES
        // TODO: 11/19/24 Add more curated options, e.g., mobile and wifi
    )

    override fun getKeysWithType(): Map<String, Int> = mKeysWithType

    override fun test(info: IFilterableAppInfo, result: TestResult): TestResult {
        return when (key) {
            KEY_ALL -> result.setMatched(true)
            "eq" -> result.setMatched(info.dataUsage.total == longValue)
            "le" -> result.setMatched(info.dataUsage.total <= longValue)
            "ge" -> result.setMatched(info.dataUsage.total >= longValue)
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }

    override fun toLocalizedString(context: Context): CharSequence {
        val sb = SpannableStringBuilder("Data usage")
        return when (key) {
            KEY_ALL -> sb.append(LangUtils.getSeparatorString()).append("any")
            "eq" -> sb.append(" = ").append(Formatter.formatFileSize(context, longValue))
            "le" -> sb.append(" ≤ ").append(Formatter.formatFileSize(context, longValue))
            "ge" -> sb.append(" ≥ ").append(Formatter.formatFileSize(context, longValue))
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }
}
