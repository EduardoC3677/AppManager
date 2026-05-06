// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.whatsnew

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import androidx.annotation.IntDef
import androidx.annotation.WorkerThread
import androidx.core.content.pm.PackageInfoCompat
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils
import io.github.muntashirakon.AppManager.utils.LangUtils
import io.github.muntashirakon.AppManager.utils.PackageUtils
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.AppManager.utils.Utils
import java.util.*

class ApkWhatsNewFinder {
    @IntDef(value = [CHANGE_ADD, CHANGE_REMOVED, CHANGE_INFO])
    @Retention(AnnotationRetention.SOURCE)
    annotation class ChangeType

    class Change(val changeType: Int, var value: String) {
        override fun toString(): String = "Change{changeType=$changeType, value='$value'}"\n}

    private val mTmpInfo = mutableSetOf<String>()

    @WorkerThread
    fun getWhatsNew(context: Context, newPkgInfo: PackageInfo, oldPkgInfo: PackageInfo): Array<Array<Change>?> {
        val newAppInfo = newPkgInfo.applicationInfo!!
        val oldAppInfo = oldPkgInfo.applicationInfo!!
        val changes = arrayOfNulls<Array<Change>>(INFO_COUNT)
        val componentInfo = context.resources.getStringArray(R.array.whats_new_titles)

        // Version info
        val newVersionCode = PackageInfoCompat.getLongVersionCode(newPkgInfo)
        val oldVersionCode = PackageInfoCompat.getLongVersionCode(oldPkgInfo)
        if (newVersionCode != oldVersionCode) {
            val newVersionInfo = "${newPkgInfo.versionName} ($newVersionCode)"\nval oldVersionInfo = "${oldPkgInfo.versionName} ($oldVersionCode)"\nchanges[VERSION_INFO] = arrayOf(
                Change(CHANGE_INFO, componentInfo[VERSION_INFO]),
                Change(CHANGE_ADD, newVersionInfo),
                Change(CHANGE_REMOVED, oldVersionInfo)
            )
        } else changes[VERSION_INFO] = emptyArray()
        if (ThreadUtils.isInterrupted()) return changes

        // Tracker info
        val newPkgComponents = PackageUtils.collectComponentClassNames(newPkgInfo)
        val oldPkgComponents = PackageUtils.collectComponentClassNames(oldPkgInfo)
        val componentChangesList = mutableListOf<Change>()
        componentChangesList.add(Change(CHANGE_INFO, componentInfo[COMPONENT_INFO]))
        componentChangesList.addAll(findChanges(newPkgComponents.keys, oldPkgComponents.keys))
        var newTrackerCount = 0; var oldTrackerCount = 0
        for (component in componentChangesList) {
            if (ComponentUtils.isTracker(component.value)) {
                if (component.changeType == CHANGE_ADD) newTrackerCount++
                else if (component.changeType == CHANGE_REMOVED) oldTrackerCount++
            }
        }
        if (newTrackerCount == 0 && oldTrackerCount == 0) {
            changes[TRACKER_INFO] = emptyArray()
        } else {
            val res = context.resources
            val newTrackers = Change(CHANGE_ADD, res.getQuantityString(R.plurals.no_of_trackers, newTrackerCount, newTrackerCount))
            val oldTrackers = Change(CHANGE_REMOVED, res.getQuantityString(R.plurals.no_of_trackers, oldTrackerCount, oldTrackerCount))
            changes[TRACKER_INFO] = arrayOf(Change(CHANGE_INFO, componentInfo[TRACKER_INFO]), newTrackers, oldTrackers)
        }
        if (ThreadUtils.isInterrupted()) return changes

        // Certs
        val newCertSha256 = PackageUtils.getSigningCertSha256Checksum(newPkgInfo, true).toSet()
        val oldCertSha256 = PackageUtils.getSigningCertSha256Checksum(oldPkgInfo).toSet()
        val certChanges = mutableListOf(Change(CHANGE_INFO, componentInfo[SIGNING_CERT_SHA256]))
        certChanges.addAll(findChanges(newCertSha256, oldCertSha256))
        changes[SIGNING_CERT_SHA256] = if (certChanges.size == 1) emptyArray() else certChanges.toTypedArray()
        if (ThreadUtils.isInterrupted()) return changes

        // Permissions
        val newPerms = mutableSetOf<String>().apply {
            newPkgInfo.permissions?.forEach { add(it.name) }
            newPkgInfo.requestedPermissions?.let { addAll(it) }
        }
        val oldPerms = mutableSetOf<String>().apply {
            oldPkgInfo.permissions?.forEach { add(it.name) }
            oldPkgInfo.requestedPermissions?.let { addAll(it) }
        }
        val permChanges = mutableListOf(Change(CHANGE_INFO, componentInfo[PERMISSION_INFO]))
        permChanges.addAll(findChanges(newPerms, oldPerms))
        changes[PERMISSION_INFO] = if (permChanges.size == 1) emptyArray() else permChanges.toTypedArray()

        // Component info
        changes[COMPONENT_INFO] = if (componentChangesList.size == 1) emptyArray() else componentChangesList.toTypedArray()
        if (ThreadUtils.isInterrupted()) return changes

        // Features
        val newFeats = mutableSetOf<String>().apply { newPkgInfo.reqFeatures?.forEach { add(it.name ?: "OpenGL ES v${Utils.getGlEsVersion(it.reqGlEsVersion)}") } }
        val oldFeats = mutableSetOf<String>().apply { oldPkgInfo.reqFeatures?.forEach { add(it.name ?: "OpenGL ES v${Utils.getGlEsVersion(it.reqGlEsVersion)}") } }
        val featChanges = mutableListOf(Change(CHANGE_INFO, componentInfo[FEATURE_INFO]))
        featChanges.addAll(findChanges(newFeats, oldFeats))
        changes[FEATURE_INFO] = if (featChanges.size == 1) emptyArray() else featChanges.toTypedArray()
        if (ThreadUtils.isInterrupted()) return changes

        // SDK
        val sep = LangUtils.getSeparatorString()
        val newSdk = "${context.getString(R.string.sdk_max)}$sep${newAppInfo.targetSdkVersion}" + (if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) ", ${context.getString(R.string.sdk_min)}$sep${newAppInfo.minSdkVersion}" else "")
        val oldSdk = "${context.getString(R.string.sdk_max)}$sep${oldAppInfo.targetSdkVersion}" + (if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) ", ${context.getString(R.string.sdk_min)}$sep${oldAppInfo.minSdkVersion}" else "")
        if (newSdk != oldSdk) {
            changes[SDK_INFO] = arrayOf(Change(CHANGE_INFO, componentInfo[SDK_INFO]), Change(CHANGE_ADD, newSdk), Change(CHANGE_REMOVED, oldSdk))
        } else changes[SDK_INFO] = emptyArray()

        return changes
    }

    private fun findChanges(newInfo: Set<String>, oldInfo: Set<String>): List<Change> {
        val changeList = mutableListOf<Change>()
        val added = newInfo.toMutableSet().apply { removeAll(oldInfo) }
        added.forEach { changeList.add(Change(CHANGE_ADD, it)) }
        val removed = oldInfo.toMutableSet().apply { removeAll(newInfo) }
        removed.forEach { changeList.add(Change(CHANGE_REMOVED, it)) }
        return changeList
    }

    companion object {
        const val CHANGE_ADD = 1
        const val CHANGE_REMOVED = 2
        const val CHANGE_INFO = 3
        const val VERSION_INFO = 0
        const val TRACKER_INFO = 1
        const val SIGNING_CERT_SHA256 = 2
        const val PERMISSION_INFO = 3
        const val COMPONENT_INFO = 4
        const val FEATURE_INFO = 5
        const val SDK_INFO = 6
        private const val INFO_COUNT = 7

        private var sInstance: ApkWhatsNewFinder? = null
        @JvmStatic fun getInstance(): ApkWhatsNewFinder { if (sInstance == null) sInstance = ApkWhatsNewFinder(); return sInstance!! }
    }
}
