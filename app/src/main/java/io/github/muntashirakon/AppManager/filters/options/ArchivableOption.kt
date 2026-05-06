// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options

import android.content.Context
import android.text.SpannableStringBuilder
import io.github.muntashirakon.AppManager.db.AppsDb
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo
import io.github.muntashirakon.AppManager.utils.LangUtils

class ArchivableOption : FilterOption("archivable") {
    private val mKeysWithType = linkedMapOf(
        KEY_ALL to TYPE_NONE,
        "archivable" to TYPE_NONE,
        "not_archivable" to TYPE_NONE
    )

    // Lazily loaded on first test() call — a snapshot of already-archived package names.
    // Safe because filter instances are created fresh per filter pass.
    private var mArchivedPackages: Set<String>? = null

    private fun getArchivedPackages(): Set<String> {
        return mArchivedPackages ?: run {
            val packages = AppsDb.getInstance().archivedAppDao()
                .getAllPackageNamesSync()
                .toHashSet()
            mArchivedPackages = packages
            packages
        }
    }

    override fun getKeysWithType(): Map<String, Int> = mKeysWithType

    override fun test(info: IFilterableAppInfo, result: TestResult): TestResult {
        val archivable = info.isInstalled && !info.isSystemApp() &&
                info.packageName !in getArchivedPackages()
        return when (key) {
            KEY_ALL -> result.setMatched(true)
            "archivable" -> result.setMatched(archivable)
            "not_archivable" -> result.setMatched(!archivable)
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }

    override fun toLocalizedString(context: Context): CharSequence {
        val sb = SpannableStringBuilder("Archivable")
        return when (key) {
            KEY_ALL -> sb.append(LangUtils.getSeparatorString()).append("any")
            "archivable" -> "Archivable apps"\n"not_archivable" -> "Non-archivable apps"\nelse -> throw UnsupportedOperationException("Invalid key $key")
        }
    }
}
