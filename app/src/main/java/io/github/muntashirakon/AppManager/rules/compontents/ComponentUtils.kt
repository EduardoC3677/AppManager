// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.compontents

import android.annotation.UserIdInt
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageInfo
import android.os.RemoteException
import android.util.Xml
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.StaticDataset
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat
import io.github.muntashirakon.AppManager.compat.PermissionCompat
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.rules.RuleType
import io.github.muntashirakon.AppManager.rules.RulesStorageManager
import io.github.muntashirakon.AppManager.rules.struct.AppOpRule
import io.github.muntashirakon.AppManager.rules.struct.ComponentRule
import io.github.muntashirakon.AppManager.rules.struct.PermissionRule
import io.github.muntashirakon.AppManager.rules.struct.RuleEntry
import io.github.muntashirakon.AppManager.types.UserPackagePair
import io.github.muntashirakon.AppManager.utils.PackageUtils
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import java.util.stream.Collectors

object ComponentUtils {
    @JvmStatic
    fun isTracker(componentName: String): Boolean {
        return StaticDataset.getSearchableTrackerSignatures().search(componentName).isNotEmpty()
    }

    @JvmStatic
    fun getTrackerComponentsCountForPackage(packageInfo: PackageInfo): Int {
        val components = PackageUtils.collectComponentClassNames(packageInfo)
        return components.keys.count { isTracker(it) }
    }

    @JvmStatic
    fun getTrackerComponentsForPackage(packageInfo: PackageInfo): Map<String, RuleType> {
        val components = PackageUtils.collectComponentClassNames(packageInfo)
        return components.filter { isTracker(it.key) }
    }

    @JvmStatic
    fun getTrackerComponentsForPackage(packageName: String, @UserIdInt userHandle: Int): Map<String, RuleType> {
        val components = PackageUtils.collectComponentClassNames(packageName, userHandle)
        return components.filter { isTracker(it.key) }
    }

    @JvmStatic
    fun blockTrackingComponents(pair: UserPackagePair) {
        val components = getTrackerComponentsForPackage(pair.packageName, pair.userId)
        ComponentsBlocker.getMutableInstance(pair.packageName, pair.userId).use { cb ->
            for (componentName in components.keys) {
                cb.addComponent(componentName, components[componentName]!!)
            }
            cb.applyRules(true)
        }
    }

    @JvmStatic
    @WorkerThread
    fun blockTrackingComponents(userPackagePairs: Collection<UserPackagePair>): List<UserPackagePair> {
        val failedPkgList = mutableListOf<UserPackagePair>()
        for (pair in userPackagePairs) {
            try {
                blockTrackingComponents(pair)
            } catch (e: Exception) {
                e.printStackTrace()
                failedPkgList.add(pair)
            }
        }
        return failedPkgList
    }

    @JvmStatic
    fun unblockTrackingComponents(pair: UserPackagePair) {
        val components = getTrackerComponentsForPackage(pair.packageName, pair.userId)
        ComponentsBlocker.getMutableInstance(pair.packageName, pair.userId).use { cb ->
            for (componentName in components.keys) {
                cb.removeComponent(componentName)
            }
            cb.applyRules(true)
        }
    }

    @JvmStatic
    @WorkerThread
    fun unblockTrackingComponents(userPackagePairs: Collection<UserPackagePair>): List<UserPackagePair> {
        val failedPkgList = mutableListOf<UserPackagePair>()
        for (pair in userPackagePairs) {
            try {
                unblockTrackingComponents(pair)
            } catch (e: Exception) {
                e.printStackTrace()
                failedPkgList.add(pair)
            }
        }
        return failedPkgList
    }

    @JvmStatic
    fun blockFilteredComponents(pair: UserPackagePair, signatures: Array<String>) {
        val components = PackageUtils.getFilteredComponents(pair.packageName, pair.userId, signatures)
        ComponentsBlocker.getMutableInstance(pair.packageName, pair.userId).use { cb ->
            for (componentName in components.keys) {
                cb.addComponent(componentName, components[componentName]!!)
            }
            cb.applyRules(true)
        }
    }

    @JvmStatic
    fun unblockFilteredComponents(pair: UserPackagePair, signatures: Array<String>) {
        val components = PackageUtils.getFilteredComponents(pair.packageName, pair.userId, signatures)
        ComponentsBlocker.getMutableInstance(pair.packageName, pair.userId).use { cb ->
            for (componentName in components.keys) {
                cb.removeComponent(componentName)
            }
            cb.applyRules(true)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun storeRules(os: OutputStream, rules: List<RuleEntry>, isExternal: Boolean) {
        for (entry in rules) {
            os.write((entry.flattenToString(isExternal) + "
").toByteArray())
        }
    }

    @JvmStatic
    fun getAllPackagesWithRules(context: Context): List<String> {
        val packages = mutableListOf<String>()
        val confDir = RulesStorageManager.getConfDir(context)
        val paths = confDir.listFiles { _, name -> name.endsWith(".tsv") }
        for (path in paths) {
            packages.add(Paths.trimPathExtension(path.uri.lastPathSegment))
        }
        return packages
    }

    @JvmStatic
    @WorkerThread
    fun removeAllRules(packageName: String, userHandle: Int) {
        val uid = PackageUtils.getAppUid(UserPackagePair(packageName, userHandle))
        ComponentsBlocker.getMutableInstance(packageName, userHandle).use { cb ->
            for (entry in cb.getAllComponents()) {
                cb.removeComponent(entry.name)
            }
            cb.applyRules(true)
            val appOpsManager = AppOpsManagerCompat()
            try {
                appOpsManager.resetAllModes(userHandle, packageName)
                for (entry in cb.getAll(AppOpRule::class.java)) {
                    try {
                        appOpsManager.setMode(entry.op, uid, packageName, AppOpsManager.MODE_DEFAULT)
                        cb.removeEntry(entry)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            for (entry in cb.getAll(PermissionRule::class.java)) {
                try {
                    PermissionCompat.grantPermission(packageName, entry.name, userHandle)
                    cb.removeEntry(entry)
                } catch (e: RemoteException) {
                    Log.e("ComponentUtils", "Cannot revoke permission ${entry.name} for package $packageName", e)
                }
            }
        }
    }

    @JvmStatic
    fun getIFWRulesForPackage(packageName: String): HashMap<String, RuleType> {
        return getIFWRulesForPackage(packageName, Paths.get(ComponentsBlocker.SYSTEM_RULES_PATH))
    }

    @JvmStatic
    @VisibleForTesting
    fun getIFWRulesForPackage(packageName: String, path: Path): HashMap<String, RuleType> {
        val rules = HashMap<String, RuleType>()
        val files = path.listFiles { _, name -> name.startsWith(packageName) && name.endsWith(".xml") }
        for (ifwRulesFile in files) {
            try {
                ifwRulesFile.openInputStream().use { inputStream ->
                    rules.putAll(readIFWRules(inputStream, packageName))
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return rules
    }

    const val TAG_RULES = "rules"
    const val TAG_ACTIVITY = "activity"
    const val TAG_BROADCAST = "broadcast"
    const val TAG_SERVICE = "service"

    @JvmStatic
    fun readIFWRules(inputStream: InputStream, packageName: String): HashMap<String, RuleType> {
        val rules = HashMap<String, RuleType>()
        val parser = Xml.newPullParser()
        try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream, null)
            parser.nextTag()
            parser.require(XmlPullParser.START_TAG, null, TAG_RULES)
            var event = parser.nextTag()
            var componentType: RuleType? = null
            while (event != XmlPullParser.END_DOCUMENT) {
                val name = parser.name
                when (event) {
                    XmlPullParser.START_TAG -> if (name == TAG_ACTIVITY || name == TAG_BROADCAST || name == TAG_SERVICE) {
                        componentType = getComponentType(name)
                    }
                    XmlPullParser.END_TAG -> if (name == "component-filter") {
                        val fullKey = parser.getAttributeValue(null, "name")
                        val cn = ComponentName.unflattenFromString(fullKey)
                        if (cn != null && cn.packageName == packageName) {
                            rules[cn.className] = componentType!!
                        }
                    }
                }
                event = parser.nextTag()
            }
        } catch (ignore: Throwable) {}
        return rules
    }

    @JvmStatic
    internal fun getComponentType(componentTag: String): RuleType? {
        return when (componentTag) {
            TAG_ACTIVITY -> RuleType.ACTIVITY
            TAG_BROADCAST -> RuleType.RECEIVER
            TAG_SERVICE -> RuleType.SERVICE
            else -> null
        }
    }
}
