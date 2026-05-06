// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options

import android.content.Context
import android.text.SpannableStringBuilder
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo
import io.github.muntashirakon.AppManager.utils.DateUtils
import io.github.muntashirakon.AppManager.utils.LangUtils

class InstalledOption : FilterOption("installed") {
    private val mKeysWithType = linkedMapOf(
        KEY_ALL to TYPE_NONE,
        "installed" to TYPE_NONE,
        "uninstalled" to TYPE_NONE,
        "installed_before" to TYPE_TIME_MILLIS,
        "installed_after" to TYPE_TIME_MILLIS
    )

    override fun getKeysWithType(): Map<String, Int> = mKeysWithType

    override fun test(info: IFilterableAppInfo, result: TestResult): TestResult {
        val installed = info.isInstalled
        return when (key) {
            KEY_ALL -> result.setMatched(true)
            "installed" -> result.setMatched(installed)
            "uninstalled" -> result.setMatched(!installed)
            "installed_before" -> result.setMatched(installed && info.firstInstallTime <= longValue)
            "installed_after" -> result.setMatched(installed && info.firstInstallTime >= longValue)
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }

    override fun toLocalizedString(context: Context): CharSequence {
        val sb = SpannableStringBuilder("Installed")
        return when (key) {
            KEY_ALL -> sb.append(LangUtils.getSeparatorString()).append("any")
            "installed" -> "Installed apps"\n"uninstalled" -> "Uninstalled apps"\n"installed_before" -> sb.append(" before ").append(DateUtils.formatDateTime(context, longValue))
            "installed_after" -> sb.append(" after ").append(DateUtils.formatDateTime(context, longValue))
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }
}
