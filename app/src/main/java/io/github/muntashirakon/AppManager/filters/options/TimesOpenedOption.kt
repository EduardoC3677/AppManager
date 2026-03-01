// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options

import android.content.Context
import android.text.SpannableStringBuilder
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo
import io.github.muntashirakon.AppManager.utils.LangUtils

class TimesOpenedOption : FilterOption("times_opened") {
    private val mKeysWithType = linkedMapOf(
        KEY_ALL to TYPE_NONE,
        "eq" to TYPE_INT,
        "le" to TYPE_INT,
        "ge" to TYPE_INT
    )

    override fun getKeysWithType(): Map<String, Int> = mKeysWithType

    override fun test(info: IFilterableAppInfo, result: TestResult): TestResult {
        return when (key) {
            KEY_ALL -> result.setMatched(true)
            "eq" -> result.setMatched(info.timesOpened == intValue)
            "le" -> result.setMatched(info.timesOpened <= intValue)
            "ge" -> result.setMatched(info.timesOpened >= intValue)
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }

    override fun toLocalizedString(context: Context): CharSequence {
        val sb = SpannableStringBuilder("Times opened")
        return when (key) {
            KEY_ALL -> sb.append(LangUtils.getSeparatorString()).append("any")
            "eq" -> sb.append(" = ").append(intValue.toString())
            "le" -> sb.append(" ≤ ").append(intValue.toString())
            "ge" -> sb.append(" ≥ ").append(intValue.toString())
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }
}
