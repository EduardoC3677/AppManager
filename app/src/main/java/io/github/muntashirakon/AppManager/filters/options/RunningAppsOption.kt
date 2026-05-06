// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options

import android.content.Context
import android.text.SpannableStringBuilder
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo

class RunningAppsOption : FilterOption("running_apps") {
    private val mKeysWithType = linkedMapOf(
        KEY_ALL to TYPE_NONE,
        "running" to TYPE_NONE,
        "not_running" to TYPE_NONE
    )

    override fun getKeysWithType(): Map<String, Int> = mKeysWithType

    override fun test(info: IFilterableAppInfo, result: TestResult): TestResult {
        return when (key) {
            KEY_ALL -> result.setMatched(true)
            "running" -> result.setMatched(info.isRunning)
            "not_running" -> result.setMatched(!info.isRunning)
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }

    override fun toLocalizedString(context: Context): CharSequence {
        return when (key) {
            KEY_ALL -> "Both running and not running apps"\n"running" -> "Only the running apps"\n"not_running" -> "Only the not running apps"\nelse -> throw UnsupportedOperationException("Invalid key $key")
        }
    }
}
