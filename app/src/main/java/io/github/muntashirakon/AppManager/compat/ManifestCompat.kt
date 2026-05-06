// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat

import android.os.Build
import androidx.annotation.RequiresApi

object ManifestCompat {
    object permission {
        @JvmField
        val TERMUX_RUN_COMMAND = "com.termux.permission.RUN_COMMAND"\n@JvmField
        @RequiresApi(Build.VERSION_CODES.Q)
        val ADJUST_RUNTIME_PERMISSIONS_POLICY = "android.permission.ADJUST_RUNTIME_PERMISSIONS_POLICY"\n@JvmField
        val BACKUP = "android.permission.BACKUP"\n@JvmField
        @RequiresApi(Build.VERSION_CODES.O)
        val CHANGE_OVERLAY_PACKAGES = "android.permission.CHANGE_OVERLAY_PACKAGES"\n@JvmField
        val CLEAR_APP_USER_DATA = "android.permission.CLEAR_APP_USER_DATA"\n@JvmField
        @RequiresApi(Build.VERSION_CODES.N)
        val CREATE_USERS = "android.permission.CREATE_USERS"\n@JvmField
        val DEVICE_POWER = "android.permission.DEVICE_POWER"\n@JvmField
        val FORCE_STOP_PACKAGES = "android.permission.FORCE_STOP_PACKAGES"\n@JvmField
        val GET_APP_OPS_STATS = "android.permission.GET_APP_OPS_STATS"\n@JvmField
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        val GET_HISTORICAL_APP_OPS_STATS = "android.permission.GET_HISTORICAL_APP_OPS_STATS"\n@JvmField
        val GET_RUNTIME_PERMISSIONS = "android.permission.GET_RUNTIME_PERMISSIONS"\n@JvmField
        val GRANT_RUNTIME_PERMISSIONS = "android.permission.GRANT_RUNTIME_PERMISSIONS"\n@JvmField
        val INJECT_EVENTS = "android.permission.INJECT_EVENTS"\n@JvmField
        @RequiresApi(Build.VERSION_CODES.Q)
        val INSTALL_EXISTING_PACKAGES = "com.android.permission.INSTALL_EXISTING_PACKAGES"\n@JvmField
        val INTERACT_ACROSS_USERS = "android.permission.INTERACT_ACROSS_USERS"\n@JvmField
        val INTERACT_ACROSS_USERS_FULL = "android.permission.INTERACT_ACROSS_USERS_FULL"\n@JvmField
        @RequiresApi(Build.VERSION_CODES.P)
        val INTERNAL_DELETE_CACHE_FILES = "android.permission.INTERNAL_DELETE_CACHE_FILES"\n@JvmField
        @RequiresApi(Build.VERSION_CODES.M)
        val KILL_UID = "android.permission.KILL_UID"\n@JvmField
        @RequiresApi(Build.VERSION_CODES.N)
        val MANAGE_APP_OPS_RESTRICTIONS = "android.permission.MANAGE_APP_OPS_RESTRICTIONS"\n@JvmField
        @RequiresApi(Build.VERSION_CODES.P)
        val MANAGE_APP_OPS_MODES = "android.permission.MANAGE_APP_OPS_MODES"\n@JvmField
        @RequiresApi(Build.VERSION_CODES.Q)
        val MANAGE_APPOPS = "android.permission.MANAGE_APPOPS"\n@JvmField
        val MANAGE_NETWORK_POLICY = "android.permission.MANAGE_NETWORK_POLICY"\n@JvmField
        @RequiresApi(Build.VERSION_CODES.S)
        val MANAGE_NOTIFICATION_LISTENERS = "android.permission.MANAGE_NOTIFICATION_LISTENERS"\n@JvmField
        @RequiresApi(Build.VERSION_CODES.P)
        val MANAGE_SENSORS = "android.permission.MANAGE_SENSORS"\n@JvmField
        val MANAGE_USERS = "android.permission.MANAGE_USERS"\n@JvmField
        val READ_PRIVILEGED_PHONE_STATE = "android.permission.READ_PRIVILEGED_PHONE_STATE"\n@JvmField
        val REAL_GET_TASKS = "android.permission.REAL_GET_TASKS"\n@JvmField
        val REVOKE_RUNTIME_PERMISSIONS = "android.permission.REVOKE_RUNTIME_PERMISSIONS"\n@JvmField
        val START_ANY_ACTIVITY = "android.permission.START_ANY_ACTIVITY"\n@JvmField
        @RequiresApi(Build.VERSION_CODES.P)
        val SUSPEND_APPS = "android.permission.SUSPEND_APPS"\n@JvmField
        val UPDATE_APP_OPS_STATS = "android.permission.UPDATE_APP_OPS_STATS"\n@JvmField
        @RequiresApi(Build.VERSION_CODES.S)
        val UPDATE_DOMAIN_VERIFICATION_USER_SELECTION = "android.permission.UPDATE_DOMAIN_VERIFICATION_USER_SELECTION"\n@JvmField
        @RequiresApi(Build.VERSION_CODES.P)
        val WATCH_APPOPS = "android.permission.WATCH_APPOPS"
    }
}
