// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules

import android.content.Context
import android.net.Uri
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker
import io.github.muntashirakon.AppManager.utils.ContextUtils
import java.io.IOException

/**
 * Export rules to external directory either for a single package or multiple packages.
 *
 * @see RulesImporter
 */
class RulesExporter(private val mTypesToExport: List<RuleType>, private var mPackagesToExport: List<String>?, private val mUserIds: IntArray) {
    private val mContext: Context = ContextUtils.getContext()

    @Throws(IOException::class)
    fun saveRules(uri: Uri) {
        if (mPackagesToExport == null) mPackagesToExport = ComponentUtils.getAllPackagesWithRules(mContext)
        mContext.contentResolver.openOutputStream(uri)?.use { os ->
            for (packageName in mPackagesToExport!!) {
                for (userHandle in mUserIds) {
                    // Get a read-only instance
                    ComponentsBlocker.getInstance(packageName, userHandle).use { cb ->
                        ComponentUtils.storeRules(os, cb.getAll(mTypesToExport), true)
                    }
                }
            }
        } ?: throw IOException("Content provider has crashed.")
    }
}
