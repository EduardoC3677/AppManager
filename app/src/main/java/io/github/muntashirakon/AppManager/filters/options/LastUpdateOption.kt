// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options

import android.content.Context
import android.text.SpannableStringBuilder
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo
import io.github.muntashirakon.AppManager.utils.DateUtils
import io.github.muntashirakon.AppManager.utils.LangUtils

class LastUpdateOption : FilterOption("last_update") {
    private val mKeysWithType = linkedMapOf(
        KEY_ALL to TYPE_NONE,
        "before" to TYPE_TIME_MILLIS,
        "after" to TYPE_TIME_MILLIS
    )

    override fun getKeysWithType(): Map<String, Int> = mKeysWithType

    override fun test(info: IFilterableAppInfo, result: TestResult): TestResult {
        val installed = info.isInstalled
        return when (key) {
            KEY_ALL -> result.setMatched(true)
            "before" -> result.setMatched(installed && info.lastUpdateTime <= longValue)
            "after" -> result.setMatched(installed && info.lastUpdateTime >= longValue)
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }

    override fun toLocalizedString(context: Context): CharSequence {
        val sb = SpannableStringBuilder("Last update")
        return when (key) {
            KEY_ALL -> sb.append(LangUtils.getSeparatorString()).append("any")
            "before" -> sb.append(" before ").append(DateUtils.formatDateTime(context, longValue))
            "after" -> sb.append(" after ").append(DateUtils.formatDateTime(context, longValue))
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }
}
