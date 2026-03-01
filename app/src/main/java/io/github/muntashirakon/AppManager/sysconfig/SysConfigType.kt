// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sysconfig

import androidx.annotation.StringDef

@StringDef(value = [
    SysConfigType.TYPE_GROUP,
    SysConfigType.TYPE_PERMISSION,
    SysConfigType.TYPE_ASSIGN_PERMISSION,
    SysConfigType.TYPE_SPLIT_PERMISSION,
    SysConfigType.TYPE_LIBRARY,
    SysConfigType.TYPE_FEATURE,
    SysConfigType.TYPE_UNAVAILABLE_FEATURE,
    SysConfigType.TYPE_ALLOW_IN_POWER_SAVE_EXCEPT_IDLE,
    SysConfigType.TYPE_ALLOW_IN_POWER_SAVE,
    SysConfigType.TYPE_ALLOW_IN_DATA_USAGE_SAVE,
    SysConfigType.TYPE_ALLOW_UNTHROTTLED_LOCATION,
    SysConfigType.TYPE_ALLOW_IGNORE_LOCATION_SETTINGS,
    SysConfigType.TYPE_ALLOW_IMPLICIT_BROADCAST,
    SysConfigType.TYPE_APP_LINK,
    SysConfigType.TYPE_SYSTEM_USER_WHITELISTED_APP,
    SysConfigType.TYPE_SYSTEM_USER_BLACKLISTED_APP,
    SysConfigType.TYPE_DEFAULT_ENABLED_VR_APP,
    SysConfigType.TYPE_COMPONENT_OVERRIDE,
    SysConfigType.TYPE_BACKUP_TRANSPORT_WHITELISTED_SERVICE,
    SysConfigType.TYPE_DISABLED_UNTIL_USED_PREINSTALLED_CARRIER_ASSOCIATED_APP,
    SysConfigType.TYPE_DISABLED_UNTIL_USED_PREINSTALLED_CARRIER_APP,
    SysConfigType.TYPE_PRIVAPP_PERMISSIONS,
    SysConfigType.TYPE_OEM_PERMISSIONS,
    SysConfigType.TYPE_HIDDEN_API_WHITELISTED_APP,
    SysConfigType.TYPE_ALLOW_ASSOCIATION,
    SysConfigType.TYPE_APP_DATA_ISOLATION_WHITELISTED_APP,
    SysConfigType.TYPE_BUGREPORT_WHITELISTED,
    SysConfigType.TYPE_INSTALL_IN_USER_TYPE,
    SysConfigType.TYPE_NAMED_ACTOR,
    SysConfigType.TYPE_ROLLBACK_WHITELISTED_APP,
    SysConfigType.TYPE_WHITELISTED_STAGED_INSTALLER,
])
@Retention(AnnotationRetention.SOURCE)
annotation class SysConfigType {
    companion object {
        const val TYPE_GROUP = "group"
        const val TYPE_PERMISSION = "permission"
        const val TYPE_ASSIGN_PERMISSION = "assign-permission"
        const val TYPE_SPLIT_PERMISSION = "split-permission"
        const val TYPE_LIBRARY = "library"
        const val TYPE_FEATURE = "feature"
        const val TYPE_UNAVAILABLE_FEATURE = "unavailable-feature"
        const val TYPE_ALLOW_IN_POWER_SAVE_EXCEPT_IDLE = "allow-in-power-save-except-idle"
        const val TYPE_ALLOW_IN_POWER_SAVE = "allow-in-power-save"
        const val TYPE_ALLOW_IN_DATA_USAGE_SAVE = "allow-in-data-usage-save"
        const val TYPE_ALLOW_UNTHROTTLED_LOCATION = "allow-unthrottled-location"
        const val TYPE_ALLOW_IGNORE_LOCATION_SETTINGS = "allow-ignore-location-settings"
        const val TYPE_ALLOW_IMPLICIT_BROADCAST = "allow-implicit-broadcast"
        const val TYPE_APP_LINK = "app-link"
        const val TYPE_SYSTEM_USER_WHITELISTED_APP = "system-user-whitelisted-app"
        const val TYPE_SYSTEM_USER_BLACKLISTED_APP = "system-user-blacklisted-app"
        const val TYPE_DEFAULT_ENABLED_VR_APP = "default-enabled-vr-app"
        const val TYPE_COMPONENT_OVERRIDE = "component-override"
        const val TYPE_BACKUP_TRANSPORT_WHITELISTED_SERVICE = "backup-transport-whitelisted-service"
        const val TYPE_DISABLED_UNTIL_USED_PREINSTALLED_CARRIER_ASSOCIATED_APP = "disabled-until-used-preinstalled-carrier-associated-app"
        const val TYPE_DISABLED_UNTIL_USED_PREINSTALLED_CARRIER_APP = "disabled-until-used-preinstalled-carrier-app"
        const val TYPE_PRIVAPP_PERMISSIONS = "privapp-permissions"
        const val TYPE_OEM_PERMISSIONS = "oem-permissions"
        const val TYPE_HIDDEN_API_WHITELISTED_APP = "hidden-api-whitelisted-app"
        const val TYPE_ALLOW_ASSOCIATION = "allow-association"
        const val TYPE_APP_DATA_ISOLATION_WHITELISTED_APP = "app-data-isolation-whitelisted-app"
        const val TYPE_BUGREPORT_WHITELISTED = "bugreport-whitelisted"
        const val TYPE_INSTALL_IN_USER_TYPE = "install-in-user-type"
        const val TYPE_NAMED_ACTOR = "named-actor"
        const val TYPE_ROLLBACK_WHITELISTED_APP = "rollback-whitelisted-app"
        const val TYPE_WHITELISTED_STAGED_INSTALLER = "whitelisted-staged-installer"
    }
}
