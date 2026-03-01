// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options

import android.content.Context
import android.os.Build
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo
import io.github.muntashirakon.AppManager.utils.LangUtils.getSeparatorString

class FreezeOption : FilterOption("freeze_unfreeze") {
    companion object {
        const val FREEZE_TYPE_DISABLED = 1 shl 0
        const val FREEZE_TYPE_HIDDEN = 1 shl 1
        const val FREEZE_TYPE_SUSPENDED = 1 shl 2
    }

    private val mKeysWithType = linkedMapOf(
        KEY_ALL to TYPE_NONE,
        "frozen" to TYPE_NONE,
        "unfrozen" to TYPE_NONE,
        "with_flags" to TYPE_INT_FLAGS,
        "without_flags" to TYPE_INT_FLAGS
    )

    private val mFrozenFlags = linkedMapOf<Int, CharSequence>().apply {
        put(FREEZE_TYPE_DISABLED, "Disabled")
        put(FREEZE_TYPE_HIDDEN, "Hidden")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            put(FREEZE_TYPE_SUSPENDED, "Suspended")
        }
    }

    override fun getKeysWithType(): Map<String, Int> = mKeysWithType

    override fun getFlags(key: String): Map<Int, CharSequence> {
        return if (key == "with_flags" || key == "without_flags") {
            mFrozenFlags
        } else {
            super.getFlags(key)
        }
    }

    override fun test(info: IFilterableAppInfo, result: TestResult): TestResult {
        val freezeFlags = info.freezeFlags
        return when (key) {
            KEY_ALL -> result.setMatched(true)
            "frozen" -> result.setMatched(freezeFlags != 0)
            "unfrozen" -> result.setMatched(freezeFlags == 0)
            "with_flags" -> result.setMatched((freezeFlags and intValue) == intValue)
            "without_flags" -> result.setMatched((freezeFlags and intValue) != intValue)
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }

    override fun toLocalizedString(context: Context): CharSequence {
        return when (key) {
            KEY_ALL -> "Frozen" + getSeparatorString() + " any"
            "frozen" -> "Frozen apps only"
            "unfrozen" -> "Unfrozen apps only"
            "with_flags" -> "Frozen apps with types " + flagsToString("with_flags", intValue)
            "without_flags" -> "Frozen apps without types " + flagsToString("without_flags", intValue)
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }
}
