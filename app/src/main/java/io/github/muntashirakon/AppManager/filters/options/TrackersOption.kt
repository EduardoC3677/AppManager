// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options

import android.content.Context
import android.text.SpannableStringBuilder
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo
import io.github.muntashirakon.AppManager.utils.LangUtils

class TrackersOption : FilterOption("trackers") {
    private val mKeysWithType = linkedMapOf(
        KEY_ALL to TYPE_NONE,
        "eq" to TYPE_INT,
        "le" to TYPE_INT,
        "ge" to TYPE_INT
        // TODO: 7/2/24 Enhance this to include more curated options such as regex and find by tracker name
    )

    override fun getKeysWithType(): Map<String, Int> = mKeysWithType

    override fun test(info: IFilterableAppInfo, result: TestResult): TestResult {
        return when (key) {
            KEY_ALL -> result.setMatched(true)
            "eq" -> result.setMatched(info.trackerComponents.size == intValue)
            "le" -> result.setMatched(info.trackerComponents.size <= intValue)
            "ge" -> result.setMatched(info.trackerComponents.size >= intValue)
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }

    override fun toLocalizedString(context: Context): CharSequence {
        val sb = SpannableStringBuilder("Trackers")
        return when (key) {
            KEY_ALL -> sb.append(LangUtils.getSeparatorString()).append("any")
            "eq" -> sb.append(" = ").append(intValue.toString())
            "le" -> sb.append(" ≤ ").append(intValue.toString())
            "ge" -> sb.append(" ≥ ").append(intValue.toString())
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }
}
