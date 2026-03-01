// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.format.Formatter
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo
import io.github.muntashirakon.AppManager.utils.LangUtils

class ApkSizeOption : FilterOption("apk_size") {
    private val mKeysWithType = linkedMapOf(
        KEY_ALL to TYPE_NONE,
        "eq" to TYPE_SIZE_BYTES,
        "le" to TYPE_SIZE_BYTES,
        "ge" to TYPE_SIZE_BYTES
    )

    override fun getKeysWithType(): Map<String, Int> {
        return mKeysWithType
    }

    override fun test(info: IFilterableAppInfo, result: TestResult): TestResult {
        return when (key) {
            KEY_ALL -> result.setMatched(true)
            "eq" -> result.setMatched(info.apkSize == longValue)
            "le" -> result.setMatched(info.apkSize <= longValue)
            "ge" -> result.setMatched(info.apkSize >= longValue)
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }

    override fun toLocalizedString(context: Context): CharSequence {
        val sb = SpannableStringBuilder("APK size")
        return when (key) {
            KEY_ALL -> sb.append(LangUtils.getSeparatorString()).append("any")
            "eq" -> sb.append(" = ").append(Formatter.formatFileSize(context, longValue))
            "le" -> sb.append(" ≤ ").append(Formatter.formatFileSize(context, longValue))
            "ge" -> sb.append(" ≥ ").append(Formatter.formatFileSize(context, longValue))
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }
}
