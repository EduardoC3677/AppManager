// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.UserHandleHidden
import android.util.Log
import android.view.View
import androidx.annotation.IntDef
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatDelegate
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.apk.signing.SigSchemes
import io.github.muntashirakon.AppManager.backup.BackupFlags
import io.github.muntashirakon.AppManager.backup.CryptoUtils
import io.github.muntashirakon.AppManager.crypto.auth.AuthManager
import io.github.muntashirakon.AppManager.debloat.DebloaterListOptions
import io.github.muntashirakon.AppManager.details.AppDetailsFragment
import io.github.muntashirakon.AppManager.fm.FmListOptions
import io.github.muntashirakon.AppManager.logcat.helper.LogcatHelper
import io.github.muntashirakon.AppManager.main.MainListOptions
import io.github.muntashirakon.AppManager.rules.struct.ComponentRule
import io.github.muntashirakon.AppManager.runningapps.RunningAppsActivity
import io.github.muntashirakon.AppManager.settings.Ops
import java.util.Locale

class AppPref {
    /**
     * Preference keys. It's necessary to do things manually as the shared prefs in Android is
     * literary unusable.
     *
     * Keep these in sync with [getDefaultValue].
     */
    @Keep
    enum class PrefKey {
@JvmField PREF_ADB_LOCAL_SERVER_PORT_INT,
@JvmField PREF_APP_OP_SHOW_DEFAULT_BOOL,
@JvmField PREF_APP_OP_SORT_ORDER_INT,
@JvmField PREF_APP_THEME_INT,
@JvmField PREF_APP_THEME_CUSTOM_INT,
        // This is just a placeholder to prevent crash
@JvmField PREF_APP_THEME_PURE_BLACK_BOOL,
        // We store this in plain text because if the attackers attack us, they can also attack the other apps
@JvmField PREF_AUTHORIZATION_KEY_STR,

@JvmField PREF_BACKUP_ANDROID_KEYSTORE_BOOL,
@JvmField PREF_BACKUP_COMPRESSION_METHOD_STR,
@JvmField PREF_BACKUP_FLAGS_INT,
@JvmField PREF_BACKUP_VOLUME_STR,

@JvmField PREF_COMPONENTS_SORT_ORDER_INT,
@JvmField PREF_CONCURRENCY_THREAD_COUNT_INT,
@JvmField PREF_CUSTOM_LOCALE_STR,

@JvmField PREF_DISPLAY_CHANGELOG_BOOL,
@JvmField PREF_DISPLAY_CHANGELOG_LAST_VERSION_LONG,

@JvmField PREF_DEBLOATER_FILTER_FLAGS_INT,

@JvmField PREF_ENABLE_KILL_FOR_SYSTEM_BOOL,
@JvmField PREF_ENABLE_AUTO_LOCK_BOOL,
@JvmField PREF_ENABLE_PERSISTENT_SESSION_BOOL,
@JvmField PREF_ENABLE_SCREEN_LOCK_BOOL,
@JvmField PREF_ENABLED_FEATURES_INT,
@JvmField PREF_ENCRYPTION_STR,

@JvmField PREF_FREEZE_TYPE_INT,
@JvmField PREF_FM_DISPLAY_IN_LAUNCHER_BOOL,
@JvmField PREF_FM_HOME_STR,
@JvmField PREF_FM_LAST_PATH_STR,
@JvmField PREF_FM_OPTIONS_INT,
@JvmField PREF_FM_REMEMBER_LAST_PATH_BOOL,
@JvmField PREF_FM_SORT_ORDER_INT,
@JvmField PREF_FM_SORT_REVERSE_BOOL,

@JvmField PREF_CORNER_RADIUS_PRESET_STR,
@JvmField PREF_CORNER_RADIUS_CUSTOM_INT,

@JvmField PREF_GLOBAL_BLOCKING_ENABLED_BOOL,
@JvmField PREF_DEFAULT_BLOCKING_METHOD_STR,

@JvmField PREF_INSTALLER_BLOCK_TRACKERS_BOOL,
@JvmField PREF_INSTALLER_ALWAYS_ON_BACKGROUND_BOOL,
@JvmField PREF_INSTALLER_DEFAULT_PKG_SOURCE_INT,
@JvmField PREF_INSTALLER_DISABLE_VERIFICATION_BOOL,
@JvmField PREF_INSTALLER_DISPLAY_CHANGES_BOOL,
@JvmField PREF_INSTALLER_FORCE_DEX_OPT_BOOL,
@JvmField PREF_INSTALLER_INSTALL_LOCATION_INT,
@JvmField PREF_INSTALLER_INSTALLER_APP_STR,
@JvmField PREF_INSTALLER_SET_ORIGIN_BOOL,
@JvmField PREF_INSTALLER_SIGN_APK_BOOL,
@JvmField PREF_INSTALLER_UPDATE_OWNERSHIP_BOOL,

@JvmField PREF_LAST_VERSION_CODE_LONG,
@JvmField PREF_LAYOUT_ORIENTATION_INT,

@JvmField PREF_LOG_VIEWER_BUFFER_INT,
@JvmField PREF_LOG_VIEWER_DEFAULT_LOG_LEVEL_INT,
@JvmField PREF_LOG_VIEWER_DISPLAY_LIMIT_INT,
@JvmField PREF_LOG_VIEWER_EXPAND_BY_DEFAULT_BOOL,
@JvmField PREF_LOG_VIEWER_FILTER_PATTERN_STR,
@JvmField PREF_LOG_VIEWER_OMIT_SENSITIVE_INFO_BOOL,
@JvmField PREF_LOG_VIEWER_SHOW_PID_TID_TIMESTAMP_BOOL,
@JvmField PREF_LOG_VIEWER_WRITE_PERIOD_INT,

@JvmField PREF_MAIN_WINDOW_FILTER_FLAGS_INT,
@JvmField PREF_MAIN_WINDOW_FILTER_PROFILE_STR,
@JvmField PREF_MAIN_WINDOW_SORT_ORDER_INT,
@JvmField PREF_MAIN_WINDOW_SORT_REVERSE_BOOL,

@JvmField PREF_MODE_OF_OPS_STR,
@JvmField PREF_OPEN_PGP_PACKAGE_STR,
@JvmField PREF_OPEN_PGP_USER_ID_STR,
@JvmField PREF_PERMISSIONS_SORT_ORDER_INT,
@JvmField PREF_OVERLAYS_SORT_ORDER_INT,

@JvmField PREF_RUNNING_APPS_FILTER_FLAGS_INT,
@JvmField PREF_RUNNING_APPS_SORT_ORDER_INT,

@JvmField PREF_SAVED_APK_FORMAT_STR,
@JvmField PREF_SELECTED_USERS_STR,
@JvmField PREF_SEND_NOTIFICATIONS_TO_CONNECTED_DEVICES_BOOL,
@JvmField PREF_SIGNATURE_SCHEMES_INT,
@JvmField PREF_SHOW_DISCLAIMER_BOOL,

@JvmField PREF_TIPS_PREFS_INT,

@JvmField PREF_VIRUS_TOTAL_API_KEY_STR,
@JvmField PREF_VIRUS_TOTAL_PROMPT_BEFORE_UPLOADING_BOOL,

@JvmField PREF_USE_SYSTEM_FONT_BOOL,
@JvmField PREF_ZIP_ALIGN_BOOL;

        companion object {
            @JvmField
            val sKeys: Array<String> = Array(values().size) { "" }

            @JvmField
            @Type
            val sTypes: IntArray = IntArray(values().size)

            @JvmField
            val sPrefKeyList: List<PrefKey> = values().toList()

            init {
                val keyValues = values()
                for (i in keyValues.indices) {
                    val keyStr = keyValues[i].name
                    val typeSeparator = keyStr.lastIndexOf('_')
                    sKeys[i] = keyStr.substring(PREF_SKIP, typeSeparator).lowercase(Locale.ROOT)
                    sTypes[i] = inferType(keyStr.substring(typeSeparator + 1))
                }
            }

            @JvmStatic
            fun indexOf(key: PrefKey): Int {
                return sPrefKeyList.indexOf(key)
            }

            @JvmStatic
            fun indexOf(key: String): Int {
                return ArrayUtils.indexOf(sKeys, key)
            }

            @Type
            private fun inferType(typeName: String): Int {
                return when (typeName) {
                    "BOOL" -> TYPE_BOOLEAN
                    "FLOAT" -> TYPE_FLOAT
                    "INT" -> TYPE_INTEGER
                    "LONG" -> TYPE_LONG
                    "STR" -> TYPE_STRING
                    else -> throw IllegalArgumentException("Unsupported type.")
                }
            }
        }
    }

    @IntDef(
        value = [
            TYPE_BOOLEAN,
            TYPE_FLOAT,
            TYPE_INTEGER,
            TYPE_LONG,
            TYPE_STRING
        ]
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class Type

    companion object {
        private const val PREF_NAME = "preferences"\nprivate const val PREF_SKIP = 5

        const val TYPE_BOOLEAN = 0
        const val TYPE_FLOAT = 1
        const val TYPE_INTEGER = 2
        const val TYPE_LONG = 3
        const val TYPE_STRING = 4

        @SuppressLint("StaticFieldLeak")
        private var sAppPref: AppPref? = null

        @JvmStatic
        fun getInstance(): AppPref {
            if (sAppPref == null) {
                sAppPref = AppPref(ContextUtils.getContext())
            }
            return sAppPref!!
        }

        @JvmStatic
        fun getNewInstance(context: Context): AppPref {
            return AppPref(context)
        }

        @JvmStatic
        fun get(key: PrefKey): Any {
            val index = PrefKey.indexOf(key)
            val appPref = getInstance()
            return when (PrefKey.sTypes[index]) {
                TYPE_BOOLEAN -> appPref.mPreferences.getBoolean(
                    PrefKey.sKeys[index],
                    appPref.getDefaultValue(key) as Boolean
                )
                TYPE_FLOAT -> appPref.mPreferences.getFloat(
                    PrefKey.sKeys[index],
                    appPref.getDefaultValue(key) as Float
                )
                TYPE_INTEGER -> appPref.mPreferences.getInt(
                    PrefKey.sKeys[index],
                    appPref.getDefaultValue(key) as Int
                )
                TYPE_LONG -> appPref.mPreferences.getLong(
                    PrefKey.sKeys[index],
                    appPref.getDefaultValue(key) as Long
                )
                TYPE_STRING -> appPref.mPreferences.getString(
                    PrefKey.sKeys[index],
                    appPref.getDefaultValue(key) as String
                )!!
                else -> throw IllegalArgumentException("Unknown key or type.")
            }
        }

        @JvmStatic
        fun getBoolean(key: PrefKey): Boolean {
            return get(key) as Boolean
        }

        @JvmStatic
        fun getInt(key: PrefKey): Int {
            return get(key) as Int
        }

        @JvmStatic
        fun getLong(key: PrefKey): Long {
            return get(key) as Long
        }

        @JvmStatic
        fun getString(key: PrefKey): String {
            return get(key) as String
        }

        @JvmStatic
        fun set(key: PrefKey, value: Any) {
            getInstance().setPref(key, value)
        }
    }

    private val mPreferences: SharedPreferences
    private val mEditor: SharedPreferences.Editor
    private val mContext: Context

    @SuppressLint("CommitPrefEdits")
    private constructor(context: Context) {
        mContext = context
        mPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        mEditor = mPreferences.edit()
        // Don't init() here - lazy initialization improves startup performance
        // Defaults are provided on-demand in get() methods
    }

    fun setPref(key: PrefKey, value: Any) {
        val index = PrefKey.indexOf(key)
        when (value) {
            is Boolean -> mEditor.putBoolean(PrefKey.sKeys[index], value)
            is Float -> mEditor.putFloat(PrefKey.sKeys[index], value)
            is Int -> mEditor.putInt(PrefKey.sKeys[index], value)
            is Long -> mEditor.putLong(PrefKey.sKeys[index], value)
            is String -> mEditor.putString(PrefKey.sKeys[index], value)
        }
        mEditor.apply()
        mEditor.commit()
    }

    fun setPref(key: String, value: Any?) {
        val index = PrefKey.indexOf(key)
        if (index == -1) throw IllegalArgumentException("Invalid key: $key")
        // Set default value if the requested value is null
        val actualValue = value ?: getDefaultValue(PrefKey.sPrefKeyList[index])
        when (actualValue) {
            is Boolean -> mEditor.putBoolean(key, actualValue)
            is Float -> mEditor.putFloat(key, actualValue)
            is Int -> mEditor.putInt(key, actualValue)
            is Long -> mEditor.putLong(key, actualValue)
            is String -> mEditor.putString(key, actualValue)
        }
        mEditor.apply()
        mEditor.commit()
    }

    fun get(key: String): Any {
        val index = PrefKey.indexOf(key)
        if (index == -1) throw IllegalArgumentException("Invalid key: $key")
        val defaultValue = getDefaultValue(PrefKey.sPrefKeyList[index])
        return when (PrefKey.sTypes[index]) {
            TYPE_BOOLEAN -> mPreferences.getBoolean(key, defaultValue as Boolean)
            TYPE_FLOAT -> mPreferences.getFloat(key, defaultValue as Float)
            TYPE_INTEGER -> mPreferences.getInt(key, defaultValue as Int)
            TYPE_LONG -> mPreferences.getLong(key, defaultValue as Long)
            TYPE_STRING -> mPreferences.getString(key, defaultValue as String)!!
            else -> throw IllegalArgumentException("Unknown key or type.")
        }
    }

    fun getValue(key: PrefKey): Any {
        val index = PrefKey.indexOf(key)
        return when (PrefKey.sTypes[index]) {
            TYPE_BOOLEAN -> mPreferences.getBoolean(
                PrefKey.sKeys[index],
                getDefaultValue(key) as Boolean
            )
            TYPE_FLOAT -> mPreferences.getFloat(
                PrefKey.sKeys[index],
                getDefaultValue(key) as Float
            )
            TYPE_INTEGER -> mPreferences.getInt(
                PrefKey.sKeys[index],
                getDefaultValue(key) as Int
            )
            TYPE_LONG -> mPreferences.getLong(
                PrefKey.sKeys[index],
                getDefaultValue(key) as Long
            )
            TYPE_STRING -> mPreferences.getString(
                PrefKey.sKeys[index],
                getDefaultValue(key) as String
            )!!
            else -> throw IllegalArgumentException("Unknown key or type.")
        }
    }

    private fun init() {
        for (i in PrefKey.sKeys.indices) {
            if (!mPreferences.contains(PrefKey.sKeys[i])) {
                when (PrefKey.sTypes[i]) {
                    TYPE_BOOLEAN -> mEditor.putBoolean(
                        PrefKey.sKeys[i],
                        getDefaultValue(PrefKey.sPrefKeyList[i]) as Boolean
                    )
                    TYPE_FLOAT -> mEditor.putFloat(
                        PrefKey.sKeys[i],
                        getDefaultValue(PrefKey.sPrefKeyList[i]) as Float
                    )
                    TYPE_INTEGER -> mEditor.putInt(
                        PrefKey.sKeys[i],
                        getDefaultValue(PrefKey.sPrefKeyList[i]) as Int
                    )
                    TYPE_LONG -> mEditor.putLong(
                        PrefKey.sKeys[i],
                        getDefaultValue(PrefKey.sPrefKeyList[i]) as Long
                    )
                    TYPE_STRING -> mEditor.putString(
                        PrefKey.sKeys[i],
                        getDefaultValue(PrefKey.sPrefKeyList[i]) as String
                    )
                }
            }
        }
        mEditor.apply()
    }

    fun getDefaultValue(key: PrefKey): Any {
        return when (key) {
            PrefKey.PREF_ADB_LOCAL_SERVER_PORT_INT ->
                UserHandleHidden.myUserId() + 60001
            PrefKey.PREF_BACKUP_FLAGS_INT ->
                BackupFlags.BACKUP_INT_DATA or BackupFlags.BACKUP_RULES or
                        BackupFlags.BACKUP_APK_FILES or BackupFlags.BACKUP_EXTRAS
            PrefKey.PREF_BACKUP_COMPRESSION_METHOD_STR ->
                TarUtils.TAR_GZIP
            PrefKey.PREF_ENABLE_KILL_FOR_SYSTEM_BOOL,
            PrefKey.PREF_GLOBAL_BLOCKING_ENABLED_BOOL,
            PrefKey.PREF_INSTALLER_ALWAYS_ON_BACKGROUND_BOOL,
            PrefKey.PREF_INSTALLER_BLOCK_TRACKERS_BOOL,
            PrefKey.PREF_INSTALLER_DISABLE_VERIFICATION_BOOL,
            PrefKey.PREF_INSTALLER_FORCE_DEX_OPT_BOOL,
            PrefKey.PREF_INSTALLER_SIGN_APK_BOOL,
            PrefKey.PREF_BACKUP_ANDROID_KEYSTORE_BOOL,
            PrefKey.PREF_ENABLE_SCREEN_LOCK_BOOL,
            PrefKey.PREF_MAIN_WINDOW_SORT_REVERSE_BOOL,
            PrefKey.PREF_LOG_VIEWER_EXPAND_BY_DEFAULT_BOOL,
            PrefKey.PREF_APP_THEME_PURE_BLACK_BOOL,
            PrefKey.PREF_DISPLAY_CHANGELOG_BOOL,
            PrefKey.PREF_FM_DISPLAY_IN_LAUNCHER_BOOL,
            PrefKey.PREF_FM_REMEMBER_LAST_PATH_BOOL,
            PrefKey.PREF_FM_SORT_REVERSE_BOOL,
            PrefKey.PREF_ENABLE_PERSISTENT_SESSION_BOOL,
            PrefKey.PREF_USE_SYSTEM_FONT_BOOL ->
                false
            PrefKey.PREF_APP_OP_SHOW_DEFAULT_BOOL,
            PrefKey.PREF_SHOW_DISCLAIMER_BOOL,
            PrefKey.PREF_LOG_VIEWER_SHOW_PID_TID_TIMESTAMP_BOOL,
            PrefKey.PREF_LOG_VIEWER_OMIT_SENSITIVE_INFO_BOOL,
            PrefKey.PREF_INSTALLER_DISPLAY_CHANGES_BOOL,
            PrefKey.PREF_INSTALLER_SET_ORIGIN_BOOL,
            PrefKey.PREF_INSTALLER_UPDATE_OWNERSHIP_BOOL,
            PrefKey.PREF_VIRUS_TOTAL_PROMPT_BEFORE_UPLOADING_BOOL,
            PrefKey.PREF_ZIP_ALIGN_BOOL,
            PrefKey.PREF_SEND_NOTIFICATIONS_TO_CONNECTED_DEVICES_BOOL,
            PrefKey.PREF_ENABLE_AUTO_LOCK_BOOL ->
                true
            PrefKey.PREF_CONCURRENCY_THREAD_COUNT_INT,
            PrefKey.PREF_APP_THEME_CUSTOM_INT,
            PrefKey.PREF_CORNER_RADIUS_CUSTOM_INT,
            PrefKey.PREF_TIPS_PREFS_INT ->
                0
            PrefKey.PREF_LAST_VERSION_CODE_LONG,
            PrefKey.PREF_DISPLAY_CHANGELOG_LAST_VERSION_LONG ->
                0L
            PrefKey.PREF_ENABLED_FEATURES_INT ->
                0xffff_ffff  /* All features enabled */
            PrefKey.PREF_APP_THEME_INT ->
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            PrefKey.PREF_MAIN_WINDOW_FILTER_FLAGS_INT ->
                MainListOptions.FILTER_NO_FILTER
            PrefKey.PREF_MAIN_WINDOW_SORT_ORDER_INT ->
                MainListOptions.SORT_BY_APP_LABEL
            PrefKey.PREF_CUSTOM_LOCALE_STR ->
                LangUtils.LANG_AUTO
            PrefKey.PREF_CORNER_RADIUS_PRESET_STR ->
                "subtle"\nPrefKey.PREF_APP_OP_SORT_ORDER_INT,
            PrefKey.PREF_COMPONENTS_SORT_ORDER_INT,
            PrefKey.PREF_PERMISSIONS_SORT_ORDER_INT ->
                AppDetailsFragment.SORT_BY_NAME
            PrefKey.PREF_OVERLAYS_SORT_ORDER_INT ->
                AppDetailsFragment.SORT_BY_PRIORITY
            PrefKey.PREF_RUNNING_APPS_SORT_ORDER_INT ->
                RunningAppsActivity.SORT_BY_PID
            PrefKey.PREF_RUNNING_APPS_FILTER_FLAGS_INT ->
                RunningAppsActivity.FILTER_NONE
            PrefKey.PREF_ENCRYPTION_STR ->
                CryptoUtils.MODE_NO_ENCRYPTION
            PrefKey.PREF_OPEN_PGP_PACKAGE_STR,
            PrefKey.PREF_OPEN_PGP_USER_ID_STR,
            PrefKey.PREF_MAIN_WINDOW_FILTER_PROFILE_STR,
            PrefKey.PREF_SELECTED_USERS_STR,
            PrefKey.PREF_VIRUS_TOTAL_API_KEY_STR ->
                ""\nPrefKey.PREF_MODE_OF_OPS_STR ->
                Ops.MODE_AUTO
            PrefKey.PREF_INSTALLER_DEFAULT_PKG_SOURCE_INT ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Shell default
                    PackageInstaller.PACKAGE_SOURCE_OTHER
                } else 0
            PrefKey.PREF_INSTALLER_INSTALL_LOCATION_INT ->
                PackageInfo.INSTALL_LOCATION_AUTO
            PrefKey.PREF_INSTALLER_INSTALLER_APP_STR ->
                BuildConfig.APPLICATION_ID
            PrefKey.PREF_SIGNATURE_SCHEMES_INT ->
                SigSchemes.DEFAULT_SCHEMES
            PrefKey.PREF_BACKUP_VOLUME_STR,
            PrefKey.PREF_FM_HOME_STR ->
                Uri.fromFile(Environment.getExternalStorageDirectory()).toString()
            PrefKey.PREF_LOG_VIEWER_FILTER_PATTERN_STR ->
                mContext.resources.getString(R.string.pref_filter_pattern_default)
            PrefKey.PREF_LOG_VIEWER_DISPLAY_LIMIT_INT ->
                LogcatHelper.DEFAULT_DISPLAY_LIMIT
            PrefKey.PREF_LOG_VIEWER_WRITE_PERIOD_INT ->
                LogcatHelper.DEFAULT_LOG_WRITE_INTERVAL
            PrefKey.PREF_LOG_VIEWER_DEFAULT_LOG_LEVEL_INT ->
                Log.VERBOSE
            PrefKey.PREF_LOG_VIEWER_BUFFER_INT ->
                LogcatHelper.LOG_ID_DEFAULT
            PrefKey.PREF_LAYOUT_ORIENTATION_INT ->
                View.LAYOUT_DIRECTION_LTR
            PrefKey.PREF_DEFAULT_BLOCKING_METHOD_STR ->
                // This is default for root
                ComponentRule.COMPONENT_TO_BE_BLOCKED_IFW_DISABLE
            PrefKey.PREF_SAVED_APK_FORMAT_STR ->
                "%label%_%version%"\nPrefKey.PREF_AUTHORIZATION_KEY_STR ->
                AuthManager.generateKey()
            PrefKey.PREF_FREEZE_TYPE_INT ->
                FreezeUtils.FREEZE_DISABLE
            PrefKey.PREF_FM_OPTIONS_INT ->
                FmListOptions.OPTIONS_DISPLAY_DOT_FILES or FmListOptions.OPTIONS_FOLDERS_FIRST
            PrefKey.PREF_FM_SORT_ORDER_INT ->
                FmListOptions.SORT_BY_NAME
            PrefKey.PREF_DEBLOATER_FILTER_FLAGS_INT ->
                DebloaterListOptions.getDefaultFilterFlags()
            PrefKey.PREF_FM_LAST_PATH_STR ->
                "{}"
        }
    }
}
