// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules

import android.net.Uri
import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker
import io.github.muntashirakon.AppManager.rules.struct.RuleEntry
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.io.IoUtils
import io.github.muntashirakon.io.Path
import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.io.InputStreamReader

/**
 * Rules importer is used to import internal rules to App Manager. Rules should only be imported
 * from settings and app data restore sections (although can be exported from various places).
 * <br>
 * Format: `package_name component_name type mode|is_applied|is_granted`
 *
 * @see RulesExporter
 * @see RuleType
 */
class RulesImporter(private val mTypesToImport: List<RuleType>, private val mUserIds: IntArray) : Closeable {
    private val mComponentsBlockers: Array<HashMap<String, ComponentsBlocker>>

    private var mPackagesToImport: List<String>? = null

    init {
        require(mUserIds.isNotEmpty()) { "Input must contain one or more user handles" }
        mComponentsBlockers = Array(mUserIds.size) { HashMap() }
    }

    @Throws(IOException::class)
    fun addRulesFromUri(uri: Uri) {
        ContextUtils.getContext().contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var dataRow: String?
                while (reader.readLine().also { dataRow = it } != null) {
                    val entry = RuleEntry.unflattenFromString(null, dataRow!!, true)
                    for (i in mUserIds.indices) {
                        mComponentsBlockers[i].getOrPut(entry.packageName) { ComponentsBlocker.getInstance(entry.packageName, mUserIds[i]) }.let { cb ->
                            if (mTypesToImport.contains(entry.type)) cb.addEntry(entry)
                        }
                    }
                }
            }
        } ?: throw IOException("Content provider has crashed.")
    }

    @Throws(IOException::class)
    fun addRulesFromPath(path: Path) {
        path.openInputStream().use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var dataRow: String?
                while (reader.readLine().also { dataRow = it } != null) {
                    val entry = RuleEntry.unflattenFromString(null, dataRow!!, true)
                    for (i in mUserIds.indices) {
                        mComponentsBlockers[i].getOrPut(entry.packageName) { ComponentsBlocker.getInstance(entry.packageName, mUserIds[i]) }.let { cb ->
                            if (mTypesToImport.contains(entry.type)) cb.addEntry(entry)
                        }
                    }
                }
            }
        }
    }

    val packages: List<String>
        get() = ArrayList(mComponentsBlockers[0].keys)

    fun setPackagesToImport(packageNames: List<String>) {
        mPackagesToImport = packageNames
    }

    @WorkerThread
    fun applyRules(commitChanges: Boolean) {
        val packageNames = mPackagesToImport ?: packages
        for (i in mUserIds.indices) {
            for (packageName in packageNames) {
                mComponentsBlockers[i][packageName]?.let { cb ->
                    cb.setMutable()
                    cb.applyRules(true)
                    cb.applyAppOpsAndPerms()
                    if (commitChanges) cb.commit() else cb.setReadOnly()
                }
            }
        }
    }

    override fun close() {
        for (i in mUserIds.indices) {
            mComponentsBlockers[i].values.forEach { IoUtils.closeQuietly(it) }
        }
    }
}
