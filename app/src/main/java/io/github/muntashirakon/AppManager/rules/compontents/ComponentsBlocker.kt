// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.compontents

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat
import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.permission.PermUtils
import io.github.muntashirakon.AppManager.rules.RuleType
import io.github.muntashirakon.AppManager.rules.RulesStorageManager
import io.github.muntashirakon.AppManager.rules.struct.AppOpRule
import io.github.muntashirakon.AppManager.rules.struct.ComponentRule
import io.github.muntashirakon.AppManager.rules.struct.PermissionRule
import io.github.muntashirakon.AppManager.rules.struct.RuleEntry
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.utils.PackageUtils
import io.github.muntashirakon.io.AtomicExtendedFile
import io.github.muntashirakon.io.Paths
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

class ComponentsBlocker private constructor(packageName: String, userHandle: Int) : RulesStorageManager(packageName, userHandle) {
    private val mRulesFile: AtomicExtendedFile
    private var mComponents: Set<String>? = null
    private var mPackageInfo: PackageInfo? = null

    init {
        mRulesFile = AtomicExtendedFile(Paths.get(SYSTEM_RULES_PATH).file!!.getChildFile("$packageName.xml"))
        try {
            mPackageInfo = PackageManagerCompat.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES
                    or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or PackageManagerCompat.MATCH_DISABLED_COMPONENTS
                    or PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES or PackageManager.GET_SERVICES
                    or PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userHandle)
        } catch (e: Throwable) {
            Log.e(TAG, e.message, e)
        }
        mComponents = mPackageInfo?.let { PackageUtils.collectComponentClassNames(it).keys }
    }

    fun reloadComponents() {
        mComponents = mPackageInfo?.let { PackageUtils.collectComponentClassNames(it).keys }
    }

    fun isComponentBlocked(componentName: String): Boolean {
        val cr = getComponent(componentName)
        return cr != null && cr.isBlocked
    }

    fun hasComponentName(componentName: String): Boolean {
        return getAllComponents().any { it.name == componentName }
    }

    fun componentCount(): Int {
        return getAllComponents().count { !it.toBeRemoved() }
    }

    fun getComponent(componentName: String): ComponentRule? {
        return getAllComponents().find { it.name == componentName }
    }

    fun addComponent(componentName: String, componentType: RuleType) {
        if (!readOnly) setComponent(componentName, componentType, Prefs.Blocking.getDefaultBlockingMethod())
    }

    fun addComponent(componentName: String, componentType: RuleType, @ComponentRule.ComponentStatus componentStatus: String) {
        if (!readOnly) setComponent(componentName, componentType, componentStatus)
    }

    fun removeComponent(componentName: String) {
        if (readOnly) return
        getComponent(componentName)?.let { setComponent(componentName, it.type, ComponentRule.COMPONENT_TO_BE_DEFAULTED) }
    }

    fun deleteComponent(componentName: String) {
        if (readOnly) return
        getComponent(componentName)?.let { removeEntries(componentName, it.type) }
    }

    private fun saveDisabledComponents(apply: Boolean): Boolean {
        if (readOnly) {
            Log.e(TAG, "Read-only instance.")
            return false
        }
        if (!apply || componentCount() == 0) {
            mRulesFile.delete()
            return true
        }
        val activities = StringBuilder()
        val services = StringBuilder()
        val receivers = StringBuilder()
        for (component in getAllComponents()) {
            if (!component.isIfw) continue
            val filter = "  <component-filter name="$packageName/${component.name}"/>
"\nwhen (component.type) {
                RuleType.ACTIVITY -> activities.append(filter)
                RuleType.RECEIVER -> receivers.append(filter)
                RuleType.SERVICE -> services.append(filter)
                else -> {}
            }
        }
        val rules = "<rules>
" +
                (if (activities.isEmpty()) "" else "<activity block="true" log="false">
$activities</activity>
") +
                (if (services.isEmpty()) "" else "<service block="true" log="false">
$services</service>
") +
                (if (receivers.isEmpty()) "" else "<broadcast block="true" log="false">
$receivers</broadcast>
") +
                "</rules>"\nvar rulesStream: FileOutputStream? = null
        return try {
            rulesStream = mRulesFile.startWrite()
            Log.d(TAG, "Rules: $rules")
            rulesStream.write(rules.toByteArray())
            mRulesFile.finishWrite(rulesStream)
            mRulesFile.baseFile.setMode(438) // 0666 octal
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write rules for package $packageName", e)
            mRulesFile.failWrite(rulesStream)
            false
        }
    }

    val isRulesApplied: Boolean
        get() = getAllComponents().all { it.isApplied }

    fun applyRules(apply: Boolean): Boolean {
        if (!SelfPermissions.canModifyAppComponentStates(userId, packageName, mPackageInfo != null && ApplicationInfoCompat.isTestOnly(mPackageInfo!!.applicationInfo))) return false
        validateComponentsInternal()
        if (SelfPermissions.canBlockByIFW() && !saveDisabledComponents(apply)) return false
        val allEntries = getAllComponents()
        Log.d(TAG, "All: $allEntries")
        var isSuccessful = true
        if (apply) {
            for (entry in allEntries) {
                if (entry.applyDefaultState()) {
                    try {
                        PackageManagerCompat.setComponentEnabledSetting(entry.componentName, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP, userId)
                        removeEntry(entry)
                    } catch (e: Throwable) {
                        isSuccessful = false
                        Log.e(TAG, "Could not enable component: $packageName/${entry.name}", e)
                    }
                }
                when (entry.componentStatus) {
                    ComponentRule.COMPONENT_TO_BE_DEFAULTED -> {
                        try {
                            PackageManagerCompat.setComponentEnabledSetting(entry.componentName, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP, userId)
                            removeEntry(entry)
                        } catch (e: Throwable) {
                            isSuccessful = false
                            Log.e(TAG, "Could not enable component: $packageName/${entry.name}", e)
                        }
                    }
                    ComponentRule.COMPONENT_TO_BE_ENABLED -> {
                        try {
                            PackageManagerCompat.setComponentEnabledSetting(entry.componentName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP, userId)
                            setComponent(entry.name, entry.type, ComponentRule.COMPONENT_ENABLED)
                        } catch (e: Throwable) {
                            isSuccessful = false
                            Log.e(TAG, "Could not disable component: $packageName/${entry.name}", e)
                        }
                    }
                    ComponentRule.COMPONENT_TO_BE_BLOCKED_IFW -> setComponent(entry.name, entry.type, ComponentRule.COMPONENT_BLOCKED_IFW)
                    ComponentRule.COMPONENT_TO_BE_BLOCKED_IFW_DISABLE, ComponentRule.COMPONENT_TO_BE_DISABLED -> {
                        try {
                            PackageManagerCompat.setComponentEnabledSetting(entry.componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP, userId)
                            setComponent(entry.name, entry.type, entry.getCounterpartOfToBe())
                        } catch (e: Throwable) {
                            isSuccessful = false
                            Log.e(TAG, "Could not disable component: $packageName/${entry.name}", e)
                        }
                    }
                    else -> setComponent(entry.name, entry.type, entry.getCounterpartOfToBe())
                }
            }
        } else {
            for (entry in allEntries) {
                try {
                    PackageManagerCompat.setComponentEnabledSetting(entry.componentName, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP, userId)
                    if (entry.toBeRemoved()) removeEntry(entry) else setComponent(entry.name, entry.type, entry.getToBe())
                } catch (e: Throwable) {
                    isSuccessful = false
                    Log.e(TAG, "Could not enable component: $packageName/${entry.name}", e)
                }
            }
        }
        return isSuccessful
    }

    fun applyAppOpsAndPerms(): Boolean {
        if (mPackageInfo == null) return false
        var isSuccessful = true
        val uid = mPackageInfo!!.applicationInfo.uid
        val appOpsManager = AppOpsManagerCompat()
        for (appOp in getAll(AppOpRule::class.java)) {
            try {
                appOpsManager.setMode(appOp.op, uid, packageName, appOp.mode)
            } catch (e: Throwable) {
                isSuccessful = false
                Log.e(TAG, "Could not set mode ${appOp.mode} for app op ${appOp.op}", e)
            }
        }
        for (permissionRule in getAll(PermissionRule::class.java)) {
            val permission = permissionRule.getPermission(true)
            try {
                permission.setAppOpAllowed(permission.getAppOp() != AppOpsManagerCompat.OP_NONE && appOpsManager.checkOperation(permission.getAppOp(), uid, packageName) == android.app.AppOpsManager.MODE_ALLOWED)
                if (permission.isGranted()) PermUtils.grantPermission(mPackageInfo!!, permission, appOpsManager, true, true)
                else PermUtils.revokePermission(mPackageInfo!!, permission, appOpsManager, true)
            } catch (e: Throwable) {
                isSuccessful = false
                Log.e(TAG, "Could not ${if (permission.isGranted()) "grant" else "revoke"} ${permissionRule.name}", e)
            }
        }
        return isSuccessful
    }

    private fun validateComponentsInternal() {
        mComponents?.let { comps ->
            getAllComponents().forEach { if (!comps.contains(it.name)) removeEntry(it) }
        }
    }

    fun invalidateComponents(): Int {
        var invalidated = 0
        val canCheck = mComponents != null
        for (entry in getAllComponents()) {
            if (canCheck && !mComponents!!.contains(entry.name)) {
                removeEntry(entry)
                invalidated++
                continue
            }
            try {
                val s = PackageManagerCompat.getComponentEnabledSetting(ComponentName(entry.packageName, entry.name), userId)
                when (entry.componentStatus) {
                    ComponentRule.COMPONENT_BLOCKED_IFW_DISABLE, ComponentRule.COMPONENT_DISABLED -> if (s == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || s == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
                        addComponent(entry.name, entry.type, entry.getToBe())
                        invalidated++
                    }
                    ComponentRule.COMPONENT_ENABLED -> if (s != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                        addComponent(entry.name, entry.type, entry.getToBe())
                        invalidated++
                    }
                }
            } catch (ignore: Throwable) {}
        }
        return invalidated
    }

    private fun retrieveDisabledComponents() {
        Log.d(TAG, "Retrieving disabled components for package $packageName")
        if (!mRulesFile.exists() || mRulesFile.baseFile.length() == 0L) {
            getAllComponents().forEach { setComponent(it.name, it.type, it.getToBe()) }
            return
        }
        try {
            mRulesFile.openRead().use { isStream ->
                val comps = ComponentUtils.readIFWRules(isStream, packageName)
                for ((key, value) in comps) {
                    setComponent(key, value, ComponentRule.COMPONENT_BLOCKED_IFW_DISABLE)
                }
            }
            Log.d(TAG, "Retrieved components for package $packageName")
        } catch (ignore: Exception) {}
    }

    companion object {
        const val TAG = "ComponentBlocker"\nval SYSTEM_RULES_PATH: String = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) "/data/secure/system/ifw" else "/data/system/ifw"\n@JvmStatic
        fun getInstance(packageName: String, userHandle: Int): ComponentsBlocker = getInstance(packageName, userHandle, true)

        @JvmStatic
        fun getMutableInstance(packageName: String, userHandle: Int): ComponentsBlocker {
            val blocker = getInstance(packageName, userHandle, false)
            blocker.setMutable()
            return blocker
        }

        @JvmStatic
        fun getInstance(packageName: String, userHandle: Int, reloadFromDisk: Boolean): ComponentsBlocker {
            val blocker = ComponentsBlocker(packageName, userHandle)
            if (reloadFromDisk && SelfPermissions.canBlockByIFW()) {
                blocker.retrieveDisabledComponents()
                blocker.invalidateComponents()
            }
            blocker.setReadOnly()
            return blocker
        }

        @JvmStatic
        @WorkerThread
        fun applyAllRules(context: Context, userHandle: Int): Boolean {
            val confPath = File(context.filesDir, "conf")
            val packages = confPath.list { _, name -> name.endsWith(".tsv") }
            var success = true
            packages?.forEach { p ->
                getMutableInstance(Paths.trimPathExtension(p), userHandle).use { success = success and it.applyRules(true) }
            }
            return success
        }
    }
}
