// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options

import android.content.Context
import android.text.SpannableStringBuilder
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo
import io.github.muntashirakon.AppManager.utils.DateUtils
import io.github.muntashirakon.AppManager.utils.LangUtils

class ScreenTimeOption : FilterOption("screen_time") {
    private val mKeysWithType = linkedMapOf(
        KEY_ALL to TYPE_NONE,
        "eq" to TYPE_DURATION_MILLIS,
        "le" -> TYPE_DURATION_MILLIS,
        "ge" -> TYPE_DURATION_MILLIS
    )

    override fun getKeysWithType(): Map<String, Int> = mKeysWithType

    override fun test(info: IFilterableAppInfo, result: TestResult): TestResult {
        return when (key) {
            KEY_ALL -> result.setMatched(true)
            "eq" -> result.setMatched(info.totalScreenTime == longValue)
            "le" -> result.setMatched(info.totalScreenTime <= longValue)
            "ge" -> result.setMatched(info.totalScreenTime >= longValue)
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }

    override fun toLocalizedString(context: Context): CharSequence {
        val sb = SpannableStringBuilder("Screentime")
        return when (key) {
            KEY_ALL -> sb.append(LangUtils.getSeparatorString()).append("any")
            "eq" -> sb.append(" = ").append(DateUtils.getFormattedDuration(context, longValue, false, true))
            "le" -> sb.append(" ≤ ").append(DateUtils.getFormattedDuration(context, longValue, false, true))
            "ge" -> sb.append(" ≥ ").append(DateUtils.getFormattedDuration(context, longValue, false, true))
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }
}
