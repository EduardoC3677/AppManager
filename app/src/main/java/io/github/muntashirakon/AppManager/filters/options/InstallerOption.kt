// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options

import android.content.Context
import android.text.SpannableStringBuilder
import io.github.muntashirakon.AppManager.compat.InstallSourceInfoCompat
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo
import io.github.muntashirakon.AppManager.utils.LangUtils

class InstallerOption : FilterOption("installer") {
    private val mKeysWithType = linkedMapOf(
        KEY_ALL to TYPE_NONE,
        "installer" to TYPE_STR_SINGLE,
        "installer_any" to TYPE_STR_MULTIPLE,
        "installer_none" to TYPE_STR_MULTIPLE,
        "regex" to TYPE_REGEX
    )

    override fun getKeysWithType(): Map<String, Int> = mKeysWithType

    override fun test(info: IFilterableAppInfo, result: TestResult): TestResult {
        val installSourceInfo = info.installerInfo
        if (installSourceInfo == null) {
            return result.setMatched(key == KEY_ALL)
        }
        // There's at least one installer at this point
        val installers = getInstallers(installSourceInfo)
        return when (key) {
            KEY_ALL -> result.setMatched(false)
            "installer" -> result.setMatched(installers.contains(value))
            "installer_any" -> {
                for (installer in stringValues!!) {
                    if (installers.contains(installer)) {
                        return result.setMatched(true)
                    }
                }
                result.setMatched(false)
            }
            "installer_none" -> {
                for (installer in stringValues!!) {
                    if (installers.contains(installer)) {
                        return result.setMatched(false)
                    }
                }
                result.setMatched(true)
            }
            "regex" -> {
                for (installer in installers) {
                    if (regexValue!!.matcher(installer).matches()) {
                        return result.setMatched(true)
                    }
                }
                result.setMatched(false)
            }
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }

    override fun toLocalizedString(context: Context): CharSequence {
        val sb = SpannableStringBuilder("Only the apps with installer")
        return when (key) {
            KEY_ALL -> sb.append(LangUtils.getSeparatorString()).append("any")
            "installer" -> sb.append(" ").append(value)
            "installer_any" -> sb.append(" matching any of ").append(stringValues!!.joinToString(", "))
            "installer_none" -> sb.append(" matching none of ").append(stringValues!!.joinToString(", "))
            "regex" -> sb.append(" that matches '").append(value).append("'")
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }

    private fun getInstallers(installSourceInfo: InstallSourceInfoCompat): Set<String> {
        val installers = LinkedHashSet<String>()
        installSourceInfo.installingPackageName?.let { installers.add(it) }
        installSourceInfo.initiatingPackageName?.let { installers.add(it) }
        installSourceInfo.originatingPackageName?.let { installers.add(it) }
        return installers
    }
}
