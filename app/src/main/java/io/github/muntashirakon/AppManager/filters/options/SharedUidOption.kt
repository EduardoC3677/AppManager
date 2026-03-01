// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options

import android.content.Context
import android.text.SpannableStringBuilder
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo
import io.github.muntashirakon.AppManager.utils.ArrayUtils
import io.github.muntashirakon.AppManager.utils.LangUtils

class SharedUidOption : FilterOption("shared_uid") {
    private val mKeysWithType = linkedMapOf(
        KEY_ALL to TYPE_NONE,
        "with_shared" to TYPE_NONE,
        "without_shared" to TYPE_NONE,
        "uid_name" to TYPE_STR_SINGLE,
        "uid_names" to TYPE_STR_MULTIPLE,
        "uid_name_regex" to TYPE_REGEX
    )

    override fun getKeysWithType(): Map<String, Int> = mKeysWithType

    override fun test(info: IFilterableAppInfo, result: TestResult): TestResult {
        val sharedUid = info.sharedUserId
        return when (key) {
            KEY_ALL -> result.setMatched(false)
            "with_shared" -> result.setMatched(sharedUid != null)
            "without_shared" -> result.setMatched(sharedUid == null)
            "uid_name" -> result.setMatched(sharedUid == value)
            "uid_names" -> result.setMatched(ArrayUtils.contains(stringValues, sharedUid))
            "uid_name_regex" -> result.setMatched(sharedUid != null && regexValue!!.matcher(sharedUid).matches())
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }

    override fun toLocalizedString(context: Context): CharSequence {
        val sb = SpannableStringBuilder("Only the apps with Shared UID")
        return when (key) {
            KEY_ALL -> sb.append(LangUtils.getSeparatorString()).append("ignore")
            "with_shared" -> "Only the apps with a shared UID"
            "without_shared" -> "Only the apps without a shared UID"
            "uid_name" -> sb.append(" ").append(value)
            "uid_names" -> sb.append(" (exclusive) ").append(stringValues!!.joinToString(", "))
            "uid_name_regex" -> sb.append(" that matches '").append(value).append("'")
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }
}
