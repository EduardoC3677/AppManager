// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.format.Formatter
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo
import io.github.muntashirakon.AppManager.utils.LangUtils

class TotalSizeOption : FilterOption("total_size") {
    private val mKeysWithType = LinkedHashMap<String, Int>().apply {
        put(KEY_ALL, TYPE_NONE)
        put("eq", TYPE_SIZE_BYTES)
        put("le", TYPE_SIZE_BYTES)
        put("ge", TYPE_SIZE_BYTES)
    }

    override fun getKeysWithType(): Map<String, Int> = mKeysWithType

    override fun test(info: IFilterableAppInfo, result: TestResult): TestResult {
        return when (key) {
            KEY_ALL -> result.setMatched(true)
            "eq" -> result.setMatched(info.totalSize == longValue)
            "le" -> result.setMatched(info.totalSize <= longValue)
            "ge" -> result.setMatched(info.totalSize >= longValue)
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }

    override fun toLocalizedString(context: Context): CharSequence {
        val sb = SpannableStringBuilder("Total size")
        return when (key) {
            KEY_ALL -> sb.append(LangUtils.getSeparatorString()).append("any")
            "eq" -> sb.append(" = ").append(Formatter.formatFileSize(context, longValue))
            "le" -> sb.append(" ≤ ").append(Formatter.formatFileSize(context, longValue))
            "ge" -> sb.append(" ≥ ").append(Formatter.formatFileSize(context, longValue))
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }
}
