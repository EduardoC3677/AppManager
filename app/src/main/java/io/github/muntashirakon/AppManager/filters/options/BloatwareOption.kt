// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options

import android.content.Context
import android.text.SpannableStringBuilder
import io.github.muntashirakon.AppManager.debloat.DebloatObject.REMOVAL_CAUTION
import io.github.muntashirakon.AppManager.debloat.DebloatObject.REMOVAL_REPLACE
import io.github.muntashirakon.AppManager.debloat.DebloatObject.REMOVAL_SAFE
import io.github.muntashirakon.AppManager.debloat.DebloatObject.REMOVAL_UNSAFE
import io.github.muntashirakon.AppManager.debloat.DebloaterListOptions.*
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo
import io.github.muntashirakon.AppManager.utils.LangUtils

class BloatwareOption : FilterOption("bloatware") {
    private val mKeysWithType = linkedMapOf(
        KEY_ALL to TYPE_NONE,
        "type" to TYPE_INT_FLAGS,
        "removal" to TYPE_INT_FLAGS
    )

    private val mBloatwareTypeFlags = linkedMapOf(
        FILTER_LIST_AOSP to "AOSP",
        FILTER_LIST_CARRIER to "Carrier",
        FILTER_LIST_GOOGLE to "Google",
        FILTER_LIST_MISC to "Misc",
        FILTER_LIST_OEM to "OEM"
    )

    private val mRemovalFlags = linkedMapOf(
        REMOVAL_SAFE to "Safe",
        REMOVAL_REPLACE to "Replace",
        REMOVAL_CAUTION to "Caution",
        REMOVAL_UNSAFE to "Unsafe"
    )

    override fun getKeysWithType(): Map<String, Int> {
        return mKeysWithType
    }

    override fun getFlags(key: String): Map<Int, CharSequence> {
        return when (key) {
            "type" -> mBloatwareTypeFlags
            "removal" -> mRemovalFlags
            else -> super.getFlags(key)
        }
    }

    override fun test(info: IFilterableAppInfo, result: TestResult): TestResult {
        val `object` = info.bloatwareInfo ?: return result.setMatched(false)
        // Must be a bloatware
        return when (key) {
            KEY_ALL -> result.setMatched(true)
            "type" -> result.setMatched((typeToFlag(`object`.type) and intValue) != 0)
            "removal" -> result.setMatched((`object`.removal and intValue) != 0)
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }

    fun typeToFlag(type: String): Int {
        return when (type) {
            "aosp" -> FILTER_LIST_AOSP
            "carrier" -> FILTER_LIST_CARRIER
            "google" -> FILTER_LIST_GOOGLE
            "misc" -> FILTER_LIST_MISC
            "oem" -> FILTER_LIST_OEM
            else -> throw IllegalArgumentException("Unknown type: $type")
        }
    }

    override fun toLocalizedString(context: Context): CharSequence {
        val sb = SpannableStringBuilder("Bloatware")
        return when (key) {
            KEY_ALL -> sb.append(LangUtils.getSeparatorString()).append("any")
            "type" -> sb.append(" with type: ").append(flagsToString("type", intValue))
            "removal" -> sb.append(" with removal: ").append(flagsToString("removal", intValue))
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }
}
