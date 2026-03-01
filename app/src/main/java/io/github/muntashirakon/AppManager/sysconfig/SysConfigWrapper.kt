// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sysconfig

import android.content.ComponentName
import android.os.Build
import android.util.ArrayMap
import androidx.annotation.WorkerThread

@WorkerThread
object SysConfigWrapper {
    @JvmStatic
    fun getSysConfigs(@SysConfigType type: String): List<SysConfigInfo> {
        val list = mutableListOf<SysConfigInfo>()
        val config = SystemConfig.getInstance()
        when (type) {
            SysConfigType.TYPE_GROUP -> config.mGlobalGids?.forEach { list.add(SysConfigInfo(SysConfigType.TYPE_GROUP, it.toString(), false)) }
            SysConfigType.TYPE_PERMISSION -> config.mPermissions.forEach { (perm, entry) -> list.add(SysConfigInfo(SysConfigType.TYPE_PERMISSION, perm, false).apply { gids = entry.gids; perUser = entry.perUser }) }
            SysConfigType.TYPE_ASSIGN_PERMISSION -> {
                val up = config.mSystemPermissions
                for (i in 0 until up.size()) list.add(SysConfigInfo(SysConfigType.TYPE_ASSIGN_PERMISSION, up.keyAt(i).toString(), false).apply { permissions = up.valueAt(i).toTypedArray() })
            }
            SysConfigType.TYPE_SPLIT_PERMISSION -> config.mSplitPermissions.forEach { list.add(SysConfigInfo(SysConfigType.TYPE_SPLIT_PERMISSION, it.splitPermission, false).apply { permissions = it.newPermissions.toTypedArray(); targetSdk = it.targetSdk }) }
            SysConfigType.TYPE_LIBRARY -> config.mSharedLibraries.forEach { (name, entry) -> list.add(SysConfigInfo(SysConfigType.TYPE_LIBRARY, name, false).apply { filename = entry.filename; dependencies = entry.dependencies }) }
            SysConfigType.TYPE_FEATURE -> config.mAvailableFeatures.forEach { (name, info) -> list.add(SysConfigInfo(SysConfigType.TYPE_FEATURE, name, false).apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) version = info.version }) }
            SysConfigType.TYPE_UNAVAILABLE_FEATURE -> config.mUnavailableFeatures.forEach { list.add(SysConfigInfo(SysConfigType.TYPE_UNAVAILABLE_FEATURE, it, false)) }
            SysConfigType.TYPE_ALLOW_IN_POWER_SAVE_EXCEPT_IDLE -> config.mAllowInPowerSaveExceptIdle.forEach { list.add(SysConfigInfo(SysConfigType.TYPE_ALLOW_IN_POWER_SAVE_EXCEPT_IDLE, it, true)) }
            SysConfigType.TYPE_ALLOW_IN_POWER_SAVE -> config.mAllowInPowerSave.forEach { list.add(SysConfigInfo(SysConfigType.TYPE_ALLOW_IN_POWER_SAVE, it, true)) }
            SysConfigType.TYPE_ALLOW_IN_DATA_USAGE_SAVE -> config.mAllowInDataUsageSave.forEach { list.add(SysConfigInfo(SysConfigType.TYPE_ALLOW_IN_DATA_USAGE_SAVE, it, true)) }
            SysConfigType.TYPE_ALLOW_UNTHROTTLED_LOCATION -> config.mAllowUnthrottledLocation.forEach { list.add(SysConfigInfo(SysConfigType.TYPE_ALLOW_UNTHROTTLED_LOCATION, it, true)) }
            SysConfigType.TYPE_ALLOW_IGNORE_LOCATION_SETTINGS -> config.mAllowIgnoreLocationSettings.forEach { list.add(SysConfigInfo(SysConfigType.TYPE_ALLOW_IGNORE_LOCATION_SETTINGS, it, true)) }
            SysConfigType.TYPE_ALLOW_IMPLICIT_BROADCAST -> config.mAllowImplicitBroadcasts.forEach { list.add(SysConfigInfo(SysConfigType.TYPE_ALLOW_IMPLICIT_BROADCAST, it, false)) }
            SysConfigType.TYPE_APP_LINK -> config.mLinkedApps.forEach { list.add(SysConfigInfo(SysConfigType.TYPE_APP_LINK, it, true)) }
            SysConfigType.TYPE_SYSTEM_USER_WHITELISTED_APP -> config.mSystemUserWhitelistedApps.forEach { list.add(SysConfigInfo(SysConfigType.TYPE_SYSTEM_USER_WHITELISTED_APP, it, true)) }
            SysConfigType.TYPE_SYSTEM_USER_BLACKLISTED_APP -> config.mSystemUserBlacklistedApps.forEach { list.add(SysConfigInfo(SysConfigType.TYPE_SYSTEM_USER_BLACKLISTED_APP, it, true)) }
            SysConfigType.TYPE_DEFAULT_ENABLED_VR_APP -> {
                val map = ArrayMap<String, MutableSet<String>>()
                config.mDefaultVrComponents.forEach { map.getOrPut(it.packageName) { HashSet() }.add(it.className) }
                map.forEach { (pkg, comps) -> list.add(SysConfigInfo(SysConfigType.TYPE_DEFAULT_ENABLED_VR_APP, pkg, true).apply { classNames = comps.toTypedArray() }) }
            }
            SysConfigType.TYPE_COMPONENT_OVERRIDE -> config.mPackageComponentEnabledState.forEach { (pkg, state) -> list.add(SysConfigInfo(SysConfigType.TYPE_COMPONENT_OVERRIDE, pkg, true).apply { classNames = state.keys.toTypedArray(); whitelist = state.values.toBooleanArray() }) }
            SysConfigType.TYPE_BACKUP_TRANSPORT_WHITELISTED_SERVICE -> {
                val map = ArrayMap<String, MutableSet<String>>()
                config.mBackupTransportWhitelist.forEach { map.getOrPut(it.packageName) { HashSet() }.add(it.className) }
                map.forEach { (pkg, comps) -> list.add(SysConfigInfo(SysConfigType.TYPE_BACKUP_TRANSPORT_WHITELISTED_SERVICE, pkg, true).apply { classNames = comps.toTypedArray() }) }
            }
            SysConfigType.TYPE_DISABLED_UNTIL_USED_PREINSTALLED_CARRIER_ASSOCIATED_APP -> config.mDisabledUntilUsedPreinstalledCarrierAssociatedApps.forEach { (pkg, entries) -> list.add(SysConfigInfo(SysConfigType.TYPE_DISABLED_UNTIL_USED_PREINSTALLED_CARRIER_ASSOCIATED_APP, pkg, true).apply { packages = entries.map { it.packageName }.toTypedArray(); targetSdks = entries.map { it.addedInSdk }.toIntArray() }) }
            SysConfigType.TYPE_DISABLED_UNTIL_USED_PREINSTALLED_CARRIER_APP -> config.mDisabledUntilUsedPreinstalledCarrierApps.forEach { list.add(SysConfigInfo(SysConfigType.TYPE_DISABLED_UNTIL_USED_PREINSTALLED_CARRIER_APP, it, true)) }
            SysConfigType.TYPE_PRIVAPP_PERMISSIONS -> {
                val map = ArrayMap<String, ArrayMap<String, Boolean>>()
                convertToMap(map, config.mVendorPrivAppPermissions, config.mVendorPrivAppDenyPermissions)
                convertToMap(map, config.mProductPrivAppPermissions, config.mProductPrivAppDenyPermissions)
                convertToMap(map, config.mSystemExtPrivAppPermissions, config.mSystemExtPrivAppDenyPermissions)
                convertToMap(map, config.mPrivAppPermissions, config.mPrivAppDenyPermissions)
                map.forEach { (pkg, perms) -> list.add(SysConfigInfo(SysConfigType.TYPE_PRIVAPP_PERMISSIONS, pkg, true).apply { permissions = perms.keys.toTypedArray(); whitelist = perms.values.toBooleanArray() }) }
            }
            SysConfigType.TYPE_OEM_PERMISSIONS -> config.mOemPermissions.forEach { (pkg, perms) -> list.add(SysConfigInfo(SysConfigType.TYPE_OEM_PERMISSIONS, pkg, true).apply { permissions = perms.keys.toTypedArray(); whitelist = perms.values.toBooleanArray() }) }
            SysConfigType.TYPE_HIDDEN_API_WHITELISTED_APP -> config.mHiddenApiPackageWhitelist.forEach { list.add(SysConfigInfo(SysConfigType.TYPE_HIDDEN_API_WHITELISTED_APP, it, true)) }
            SysConfigType.TYPE_ALLOW_ASSOCIATION -> config.mAllowedAssociations.forEach { (pkg, perms) -> list.add(SysConfigInfo(SysConfigType.TYPE_ALLOW_ASSOCIATION, pkg, true).apply { packages = perms.toTypedArray() }) }
            SysConfigType.TYPE_APP_DATA_ISOLATION_WHITELISTED_APP -> config.mAppDataIsolationWhitelistedApps.forEach { list.add(SysConfigInfo(SysConfigType.TYPE_APP_DATA_ISOLATION_WHITELISTED_APP, it, true)) }
            SysConfigType.TYPE_BUGREPORT_WHITELISTED -> config.mBugreportWhitelistedPackages.forEach { list.add(SysConfigInfo(SysConfigType.TYPE_BUGREPORT_WHITELISTED, it, true)) }
            SysConfigType.TYPE_INSTALL_IN_USER_TYPE -> {
                val map = ArrayMap<String, ArrayMap<String, Boolean>>()
                convertToMap(map, config.mPackageToUserTypeWhitelist, config.mPackageToUserTypeBlacklist)
                map.forEach { (pkg, uts) -> list.add(SysConfigInfo(SysConfigType.TYPE_INSTALL_IN_USER_TYPE, pkg, true).apply { userTypes = uts.keys.toTypedArray(); whitelist = uts.values.toBooleanArray() }) }
            }
            SysConfigType.TYPE_NAMED_ACTOR -> config.getNamedActors().forEach { (ns, actors) -> list.add(SysConfigInfo(SysConfigType.TYPE_NAMED_ACTOR, ns, false).apply { this.actors = actors.keys.toTypedArray(); packages = actors.values.toTypedArray() }) }
            SysConfigType.TYPE_ROLLBACK_WHITELISTED_APP -> config.mRollbackWhitelistedPackages.forEach { list.add(SysConfigInfo(SysConfigType.TYPE_ROLLBACK_WHITELISTED_APP, it, true)) }
            SysConfigType.TYPE_WHITELISTED_STAGED_INSTALLER -> config.mWhitelistedStagedInstallers.forEach { list.add(SysConfigInfo(SysConfigType.TYPE_WHITELISTED_STAGED_INSTALLER, it, true)) }
            else -> throw IllegalStateException("Unexpected value: $type")
        }
        return list
    }

    private fun convertToMap(map: ArrayMap<String, ArrayMap<String, Boolean>>, grant: ArrayMap<String, MutableSet<String>>, deny: ArrayMap<String, MutableSet<String>>) {
        grant.forEach { (pkg, set) -> val perms = map.getOrPut(pkg) { ArrayMap() }; set.forEach { perms[it] = true } }
        deny.forEach { (pkg, set) -> val perms = map.getOrPut(pkg) { ArrayMap() }; set.forEach { perms[it] = false } }
    }
}
