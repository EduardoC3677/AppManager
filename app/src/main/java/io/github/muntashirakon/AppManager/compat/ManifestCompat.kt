// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat

import android.os.Build
import androidx.annotation.RequiresApi

object ManifestCompat {
    object permission {
        @JvmField
        val TERMUX_RUN_COMMAND = "com.termux.permission.RUN_COMMAND"

        @JvmField
        @RequiresApi(Build.VERSION_CODES.Q)
        val ADJUST_RUNTIME_PERMISSIONS_POLICY = "android.permission.ADJUST_RUNTIME_PERMISSIONS_POLICY"

        @JvmField
        val BACKUP = "android.permission.BACKUP"

        @JvmField
        @RequiresApi(Build.VERSION_CODES.O)
        val CHANGE_OVERLAY_PACKAGES = "android.permission.CHANGE_OVERLAY_PACKAGES"

        @JvmField
        val CLEAR_APP_USER_DATA = "android.permission.CLEAR_APP_USER_DATA"

        @JvmField
        @RequiresApi(Build.VERSION_CODES.N)
        val CREATE_USERS = "android.permission.CREATE_USERS"

        @JvmField
        val DEVICE_POWER = "android.permission.DEVICE_POWER"

        @JvmField
        val FORCE_STOP_PACKAGES = "android.permission.FORCE_STOP_PACKAGES"

        @JvmField
        val GET_APP_OPS_STATS = "android.permission.GET_APP_OPS_STATS"

        @JvmField
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        val GET_HISTORICAL_APP_OPS_STATS = "android.permission.GET_HISTORICAL_APP_OPS_STATS"

        @JvmField
        val GET_RUNTIME_PERMISSIONS = "android.permission.GET_RUNTIME_PERMISSIONS"

        @JvmField
        val GRANT_RUNTIME_PERMISSIONS = "android.permission.GRANT_RUNTIME_PERMISSIONS"

        @JvmField
        val INJECT_EVENTS = "android.permission.INJECT_EVENTS"

        @JvmField
        @RequiresApi(Build.VERSION_CODES.Q)
        val INSTALL_EXISTING_PACKAGES = "com.android.permission.INSTALL_EXISTING_PACKAGES"

        @JvmField
        val INTERACT_ACROSS_USERS = "android.permission.INTERACT_ACROSS_USERS"

        @JvmField
        val INTERACT_ACROSS_USERS_FULL = "android.permission.INTERACT_ACROSS_USERS_FULL"

        @JvmField
        @RequiresApi(Build.VERSION_CODES.P)
        val INTERNAL_DELETE_CACHE_FILES = "android.permission.INTERNAL_DELETE_CACHE_FILES"

        @JvmField
        @RequiresApi(Build.VERSION_CODES.M)
        val KILL_UID = "android.permission.KILL_UID"

        @JvmField
        @RequiresApi(Build.VERSION_CODES.N)
        val MANAGE_APP_OPS_RESTRICTIONS = "android.permission.MANAGE_APP_OPS_RESTRICTIONS"

        @JvmField
        @RequiresApi(Build.VERSION_CODES.P)
        val MANAGE_APP_OPS_MODES = "android.permission.MANAGE_APP_OPS_MODES"

        @JvmField
        @RequiresApi(Build.VERSION_CODES.Q)
        val MANAGE_APPOPS = "android.permission.MANAGE_APPOPS"

        @JvmField
        val MANAGE_NETWORK_POLICY = "android.permission.MANAGE_NETWORK_POLICY"

        @JvmField
        @RequiresApi(Build.VERSION_CODES.S)
        val MANAGE_NOTIFICATION_LISTENERS = "android.permission.MANAGE_NOTIFICATION_LISTENERS"

        @JvmField
        @RequiresApi(Build.VERSION_CODES.P)
        val MANAGE_SENSORS = "android.permission.MANAGE_SENSORS"

        @JvmField
        val MANAGE_USERS = "android.permission.MANAGE_USERS"

        @JvmField
        val READ_PRIVILEGED_PHONE_STATE = "android.permission.READ_PRIVILEGED_PHONE_STATE"

        @JvmField
        val REAL_GET_TASKS = "android.permission.REAL_GET_TASKS"

        @JvmField
        val REVOKE_RUNTIME_PERMISSIONS = "android.permission.REVOKE_RUNTIME_PERMISSIONS"

        @JvmField
        val START_ANY_ACTIVITY = "android.permission.START_ANY_ACTIVITY"

        @JvmField
        @RequiresApi(Build.VERSION_CODES.P)
        val SUSPEND_APPS = "android.permission.SUSPEND_APPS"

        @JvmField
        val UPDATE_APP_OPS_STATS = "android.permission.UPDATE_APP_OPS_STATS"

        @JvmField
        @RequiresApi(Build.VERSION_CODES.S)
        val UPDATE_DOMAIN_VERIFICATION_USER_SELECTION = "android.permission.UPDATE_DOMAIN_VERIFICATION_USER_SELECTION"

        @JvmField
        @RequiresApi(Build.VERSION_CODES.P)
        val WATCH_APPOPS = "android.permission.WATCH_APPOPS"
    }
}
