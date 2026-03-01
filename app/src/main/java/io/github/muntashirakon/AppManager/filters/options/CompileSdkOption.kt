// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options

import android.content.Context
import android.os.Build
import android.text.SpannableStringBuilder
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo
import io.github.muntashirakon.AppManager.utils.LangUtils

class CompileSdkOption : FilterOption("compile_sdk") {
    private val mKeysWithType = LinkedHashMap<String, Int>().apply {
        put(KEY_ALL, TYPE_NONE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            put("eq", TYPE_INT)
            put("le", TYPE_INT)
            put("ge", TYPE_INT)
        }
    }

    override fun getKeysWithType(): Map<String, Int> = mKeysWithType

    override fun test(info: IFilterableAppInfo, result: TestResult): TestResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return result.setMatched(true)
        }
        return when (key) {
            KEY_ALL -> result.setMatched(true)
            "eq" -> result.setMatched(info.compileSdk == intValue)
            "le" -> result.setMatched(info.compileSdk <= intValue)
            "ge" -> result.setMatched(info.compileSdk >= intValue)
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }

    override fun toLocalizedString(context: Context): CharSequence {
        val sb = SpannableStringBuilder("Compile SDK")
        return when (key) {
            KEY_ALL -> sb.append(LangUtils.getSeparatorString()).append("any")
            "eq" -> sb.append(" = ").append(intValue.toString())
            "le" -> sb.append(" ≤ ").append(intValue.toString())
            "ge" -> sb.append(" ≥ ").append(intValue.toString())
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }
}
