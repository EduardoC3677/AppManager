// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.compontents

import android.annotation.SuppressLint
import android.content.pm.*
import android.net.Uri
import android.os.RemoteException
import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat
import io.github.muntashirakon.AppManager.rules.RuleType
import io.github.muntashirakon.AppManager.types.UserPackagePair
import io.github.muntashirakon.AppManager.utils.PackageUtils
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import org.json.JSONObject
import java.io.IOException

object ExternalComponentsImporter {
    @JvmStatic
    @Throws(RemoteException::class)
    fun setModeToFilteredAppOps(appOpsManager: io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat, pair: UserPackagePair, appOps: IntArray, mode: Int) {
        val appOpList = PackageUtils.getFilteredAppOps(pair.packageName, pair.userId, appOps, mode)
        ComponentsBlocker.getMutableInstance(pair.packageName, pair.userId).use { cb ->
            for (appOp in appOpList) {
                appOpsManager.setMode(appOp, PackageUtils.getAppUid(pair), pair.packageName, mode)
                cb.setAppOp(appOp, mode)
            }
            cb.applyRules(true)
        }
    }

    @JvmStatic
    @WorkerThread
    fun applyFromExistingBlockList(packageNames: List<String>, userHandle: Int): List<String> {
        val failedPkgList = mutableListOf<String>()
        val rulesPath = Paths.get(ComponentsBlocker.SYSTEM_RULES_PATH)
        for (packageName in packageNames) {
            val components = PackageUtils.getUserDisabledComponentsForPackage(packageName, userHandle)
            try {
                ComponentsBlocker.getMutableInstance(packageName, userHandle).use { cb ->
                    for ((key, value) in components) {
                        cb.addComponent(key, value)
                    }
                    val rulesFiles = rulesPath.listFiles { _, name -> name.startsWith(packageName) && name.endsWith("xml") }
                    rulesFiles.forEach { it.delete() }
                    cb.applyRules(true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                failedPkgList.add(packageName)
            }
        }
        return failedPkgList
    }

    @JvmStatic
    @WorkerThread
    fun applyFromBlocker(uriList: List<Uri>, userHandles: IntArray): List<String> {
        val failedFiles = mutableListOf<String>()
        for (uri in uriList) {
            val filename = Paths.get(uri).name
            try {
                for (userHandle in userHandles) {
                    applyFromBlockerInternal(uri, userHandle)
                }
            } catch (e: Exception) {
                failedFiles.add(filename)
                e.printStackTrace()
            }
        }
        return failedFiles
    }

    @JvmStatic
    @WorkerThread
    fun applyFromWatt(uriList: List<Uri>, userHandles: IntArray): List<String> {
        val failedFiles = mutableListOf<String>()
        for (uri in uriList) {
            val path = Paths.get(uri)
            val filename = path.name
            try {
                for (userHandle in userHandles) {
                    applyFromWattInternal(Paths.trimPathExtension(filename), path, userHandle)
                }
            } catch (e: IOException) {
                failedFiles.add(filename)
                e.printStackTrace()
            }
        }
        return failedFiles
    }

    @WorkerThread
    private fun applyFromWattInternal(packageName: String, path: Path, userHandle: Int) {
        path.openInputStream().use { isStream ->
            ComponentsBlocker.getMutableInstance(packageName, userHandle).use { cb ->
                val components = ComponentUtils.readIFWRules(isStream, packageName)
                for ((key, value) in components) {
                    cb.addComponent(key, value)
                }
                cb.applyRules(true)
            }
        }
    }

    @WorkerThread
    @SuppressLint("WrongConstant")
    private fun applyFromBlockerInternal(uri: Uri, userHandle: Int) {
        val jsonString = Paths.get(uri).contentAsString
        val packageComponents = mutableMapOf<String, HashMap<String, RuleType>>()
        val packageInfoList = mutableMapOf<String, PackageInfo>()
        val jsonObject = JSONObject(jsonString)
        val components = jsonObject.getJSONArray("components")
        val uninstalledApps = mutableListOf<String>()
        for (i in 0 until components.length()) {
            val component = components.getJSONObject(i)
            val packageName = component.getString("packageName")
            if (uninstalledApps.contains(packageName)) continue
            if (!packageInfoList.containsKey(packageName)) {
                try {
                    packageInfoList[packageName] = PackageManagerCompat.getPackageInfo(packageName,
                        PackageManager.GET_ACTIVITIES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
                                or PackageManager.GET_SERVICES or PackageManagerCompat.MATCH_DISABLED_COMPONENTS
                                or PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userHandle)
                } catch (e: Exception) {
                    uninstalledApps.add(packageName)
                    continue
                }
            }
            val componentName = component.getString("name")
            val comps = packageComponents.getOrPut(packageName) { HashMap() }
            getType(componentName, packageInfoList[packageName]!!)?.let { comps[componentName] = it }
        }
        packageComponents.forEach { (pkg, comps) ->
            if (comps.isNotEmpty()) {
                ComponentsBlocker.getMutableInstance(pkg, userHandle).use { cb ->
                    comps.forEach { (name, type) -> cb.addComponent(name, type) }
                    cb.applyRules(true)
                    if (!cb.isRulesApplied) throw Exception("Rules not applied for package $pkg")
                }
            }
        }
    }

    private fun getType(name: String, packageInfo: PackageInfo): RuleType? {
        packageInfo.activities?.forEach { if (it.name == name) return RuleType.ACTIVITY }
        packageInfo.providers?.forEach { if (it.name == name) return RuleType.PROVIDER }
        packageInfo.receivers?.forEach { if (it.name == name) return RuleType.RECEIVER }
        packageInfo.services?.forEach { if (it.name == name) return RuleType.SERVICE }
        return null
    }
}
