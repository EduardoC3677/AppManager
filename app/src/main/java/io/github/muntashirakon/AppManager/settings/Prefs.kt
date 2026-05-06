// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings

import android.Manifest
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.annotation.StyleRes
import androidx.core.util.Pair
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.apk.signing.Signer
import io.github.muntashirakon.AppManager.apk.signing.SigSchemes
import io.github.muntashirakon.AppManager.backup.BackupFlags
import io.github.muntashirakon.AppManager.backup.BackupUtils.TAR_TYPES
import io.github.muntashirakon.AppManager.backup.CryptoUtils
import io.github.muntashirakon.AppManager.compat.ManifestCompat
import io.github.muntashirakon.AppManager.details.AppDetailsFragment
import io.github.muntashirakon.AppManager.fm.FmActivity
import io.github.muntashirakon.AppManager.fm.FmListOptions
import io.github.muntashirakon.AppManager.logcat.helper.LogcatHelper
import io.github.muntashirakon.AppManager.main.MainListOptions
import io.github.muntashirakon.AppManager.rules.struct.ComponentRule
import io.github.muntashirakon.AppManager.runningapps.RunningAppsActivity
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.utils.*
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.util.*

// Why this class?
//
// This class is just an abstract over the AppPref to make life a bit easier. In the future, however, it might be
// possible to deliver the changes to the settings using lifecycle where required. For example, in the log viewer page,
// changes to the settings are not immediately reflected unless the settings page is opened from the page itself.
object Prefs {
    object AppDetailsPage {
        @JvmStatic
        fun displayDefaultAppOps(): Boolean {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_APP_OP_SHOW_DEFAULT_BOOL)
        }

        @JvmStatic
        fun setDisplayDefaultAppOps(display: Boolean) {
            AppPref.set(AppPref.PrefKey.PREF_APP_OP_SHOW_DEFAULT_BOOL, display)
        }

        @JvmStatic
        @AppDetailsFragment.SortOrder
        fun getAppOpsSortOrder(): Int {
            return AppPref.getInt(AppPref.PrefKey.PREF_APP_OP_SORT_ORDER_INT)
        }

        @JvmStatic
        fun setAppOpsSortOrder(@AppDetailsFragment.SortOrder sortOrder: Int) {
            AppPref.set(AppPref.PrefKey.PREF_APP_OP_SORT_ORDER_INT, sortOrder)
        }

        @JvmStatic
        @AppDetailsFragment.SortOrder
        fun getComponentsSortOrder(): Int {
            return AppPref.getInt(AppPref.PrefKey.PREF_COMPONENTS_SORT_ORDER_INT)
        }

        @JvmStatic
        fun setComponentsSortOrder(@AppDetailsFragment.SortOrder sortOrder: Int) {
            AppPref.set(AppPref.PrefKey.PREF_COMPONENTS_SORT_ORDER_INT, sortOrder)
        }

        @JvmStatic
        @AppDetailsFragment.SortOrder
        fun getPermissionsSortOrder(): Int {
            return AppPref.getInt(AppPref.PrefKey.PREF_PERMISSIONS_SORT_ORDER_INT)
        }

        @JvmStatic
        fun setPermissionsSortOrder(@AppDetailsFragment.SortOrder sortOrder: Int) {
            AppPref.set(AppPref.PrefKey.PREF_PERMISSIONS_SORT_ORDER_INT, sortOrder)
        }

        @JvmStatic
        @AppDetailsFragment.SortOrder
        fun getOverlaysSortOrder(): Int {
            return AppPref.getInt(AppPref.PrefKey.PREF_OVERLAYS_SORT_ORDER_INT)
        }

        @JvmStatic
        fun setOverlaysSortOrder(@AppDetailsFragment.SortOrder sortOrder: Int) {
            AppPref.set(AppPref.PrefKey.PREF_OVERLAYS_SORT_ORDER_INT, sortOrder)
        }
    }

    object Appearance {
        @JvmStatic
        fun getLanguage(): String {
            return AppPref.getString(AppPref.PrefKey.PREF_CUSTOM_LOCALE_STR)
        }

        @JvmStatic
        fun getLanguage(context: Context): String {
            // Required when application isn't initialised properly
            val appPref = AppPref.getNewInstance(context)
            return appPref.getValue(AppPref.PrefKey.PREF_CUSTOM_LOCALE_STR) as String
        }

        @JvmStatic
        fun setLanguage(language: String) {
            AppPref.set(AppPref.PrefKey.PREF_CUSTOM_LOCALE_STR, language)
        }

        @JvmStatic
        fun getLayoutDirection(): Int {
            return AppPref.getInt(AppPref.PrefKey.PREF_LAYOUT_ORIENTATION_INT)
        }

        @JvmStatic
        fun setLayoutDirection(layoutDirection: Int) {
            AppPref.set(AppPref.PrefKey.PREF_LAYOUT_ORIENTATION_INT, layoutDirection)
        }

        @JvmStatic
        @StyleRes
        fun getAppTheme(): Int {
            return when (AppPref.getInt(AppPref.PrefKey.PREF_APP_THEME_CUSTOM_INT)) {
                1 -> io.github.muntashirakon.ui.R.style.AppTheme_Black
                else -> io.github.muntashirakon.ui.R.style.AppTheme
            }
        }

        @JvmStatic
        @StyleRes
        fun getTransparentAppTheme(): Int {
            return when (AppPref.getInt(AppPref.PrefKey.PREF_APP_THEME_CUSTOM_INT)) {
                1 -> io.github.muntashirakon.ui.R.style.AppTheme_TransparentBackground_Black
                else -> io.github.muntashirakon.ui.R.style.AppTheme_TransparentBackground
            }
        }

        @JvmStatic
        fun isPureBlackTheme(): Boolean {
            return AppPref.getInt(AppPref.PrefKey.PREF_APP_THEME_CUSTOM_INT) == 1
        }

        @JvmStatic
        fun setPureBlackTheme(enabled: Boolean) {
            AppPref.set(AppPref.PrefKey.PREF_APP_THEME_CUSTOM_INT, if (enabled) 1 else 0)
        }

        @JvmStatic
        fun getNightMode(): Int {
            return AppPref.getInt(AppPref.PrefKey.PREF_APP_THEME_INT)
        }

        @JvmStatic
        fun setNightMode(nightMode: Int) {
            AppPref.set(AppPref.PrefKey.PREF_APP_THEME_INT, nightMode)
        }

        @JvmStatic
        fun useSystemFont(): Boolean {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_USE_SYSTEM_FONT_BOOL)
        }

        @JvmStatic
        fun getCornerRadiusPreset(): String {
            return AppPref.getString(AppPref.PrefKey.PREF_CORNER_RADIUS_PRESET_STR)
        }

        /**
         * Get the effective corner radius to use based on preset or custom value
         * @return corner radius in dp
         */
        @JvmStatic
        fun getEffectiveCornerRadius(): Int {
            return when (val preset = getCornerRadiusPreset()) {
                "squared" -> 0
                "subtle" -> 4
                "rounded" -> 8
                "full" -> 16
                "custom" -> AppPref.getInt(AppPref.PrefKey.PREF_CORNER_RADIUS_CUSTOM_INT)
                else -> 4
            }
        }
    }

    object BackupRestore {
        @JvmStatic
        fun backupAppsWithKeyStore(): Boolean {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_BACKUP_ANDROID_KEYSTORE_BOOL)
        }

        @JvmStatic
        @TarUtils.TarType
        fun getCompressionMethod(): String {
            var tarType = AppPref.getString(AppPref.PrefKey.PREF_BACKUP_COMPRESSION_METHOD_STR)
            // Verify tar type
            if (ArrayUtils.indexOf(TAR_TYPES, tarType) == -1) {
                // Unknown tar type, set default
                tarType = TarUtils.TAR_GZIP
            }
            return tarType
        }

        @JvmStatic
        fun setCompressionMethod(@TarUtils.TarType tarType: String) {
            AppPref.set(AppPref.PrefKey.PREF_BACKUP_COMPRESSION_METHOD_STR, tarType)
        }

        @JvmStatic
        @BackupFlags.BackupFlag
        fun getBackupFlags(): Int {
            return AppPref.getInt(AppPref.PrefKey.PREF_BACKUP_FLAGS_INT)
        }

        @JvmStatic
        fun setBackupFlags(@BackupFlags.BackupFlag flags: Int) {
            AppPref.set(AppPref.PrefKey.PREF_BACKUP_FLAGS_INT, flags)
        }

        @JvmStatic
        fun backupDirectoryExists(): Boolean {
            val uri = Storage.getVolumePath()
            val path: Path = if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
                Paths.get(uri)
            } else {
                // Append AppManager only if storage permissions are granted
                var newPath = uri.path!!
                if (SelfPermissions.checkStoragePermission()) {
                    newPath += File.separator + "AppManager"\n}
                Paths.get(newPath)
            }
            return path.exists()
        }
    }

    object Blocking {
        @JvmStatic
        fun globalBlockingEnabled(): Boolean {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_GLOBAL_BLOCKING_ENABLED_BOOL)
        }

        @JvmStatic
        @ComponentRule.ComponentStatus
        fun getDefaultBlockingMethod(): String {
            val selectedStatus = AppPref.getString(AppPref.PrefKey.PREF_DEFAULT_BLOCKING_METHOD_STR)
            if (!SelfPermissions.canBlockByIFW()) {
                if (selectedStatus == ComponentRule.COMPONENT_TO_BE_BLOCKED_IFW_DISABLE
                    || selectedStatus == ComponentRule.COMPONENT_TO_BE_BLOCKED_IFW
                ) {
                    // Lower the status
                    return ComponentRule.COMPONENT_TO_BE_DISABLED
                }
            }
            return selectedStatus
        }

        @JvmStatic
        fun setDefaultBlockingMethod(@ComponentRule.ComponentStatus blockingMethod: String) {
            AppPref.set(AppPref.PrefKey.PREF_DEFAULT_BLOCKING_METHOD_STR, blockingMethod)
        }

        @JvmStatic
        @FreezeUtils.FreezeMethod
        fun getDefaultFreezingMethod(): Int {
            val freezeType = AppPref.getInt(AppPref.PrefKey.PREF_FREEZE_TYPE_INT)
            if (freezeType == FreezeUtils.FREEZE_HIDE) {
                // Requires MANAGE_USERS permission
                if (!SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_USERS)) {
                    return FreezeUtils.FREEZE_DISABLE
                }
            } else if (freezeType == FreezeUtils.FREEZE_SUSPEND || freezeType == FreezeUtils.FREEZE_ADV_SUSPEND) {
                // 7+ only. Requires MANAGE_USERS permission until P. Requires SUSPEND_APPS permission after that.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N
                    || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.SUSPEND_APPS))
                    || (Build.VERSION.SDK_INT < Build.VERSION_CODES.P && !SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_USERS))
                ) {
                    return FreezeUtils.FREEZE_DISABLE
                }
            }
            return freezeType
        }

        @JvmStatic
        fun setDefaultFreezingMethod(@FreezeUtils.FreezeMethod freezeType: Int) {
            AppPref.set(AppPref.PrefKey.PREF_FREEZE_TYPE_INT, freezeType)
        }
    }

    object Encryption {
        @JvmStatic
        @CryptoUtils.Mode
        fun getEncryptionMode(): String {
            return AppPref.getString(AppPref.PrefKey.PREF_ENCRYPTION_STR)
        }

        @JvmStatic
        fun setEncryptionMode(@CryptoUtils.Mode mode: String) {
            AppPref.set(AppPref.PrefKey.PREF_ENCRYPTION_STR, mode)
        }

        @JvmStatic
        fun getOpenPgpProvider(): String {
            return AppPref.getString(AppPref.PrefKey.PREF_OPEN_PGP_PACKAGE_STR)
        }

        @JvmStatic
        fun setOpenPgpProvider(providerPackage: String) {
            AppPref.set(AppPref.PrefKey.PREF_OPEN_PGP_PACKAGE_STR, providerPackage)
        }

        @JvmStatic
        fun getOpenPgpKeyIds(): String {
            return AppPref.getString(AppPref.PrefKey.PREF_OPEN_PGP_USER_ID_STR)
        }

        @JvmStatic
        fun setOpenPgpKeyIds(keyIds: String) {
            AppPref.set(AppPref.PrefKey.PREF_OPEN_PGP_USER_ID_STR, keyIds)
        }
    }

    object FileManager {
        @JvmStatic
        fun displayInLauncher(): Boolean {
            val componentName = ComponentName(BuildConfig.APPLICATION_ID, FmActivity.LAUNCHER_ALIAS)
            val state = ContextUtils.getContext().packageManager.getComponentEnabledSetting(componentName)
            return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }

        @JvmStatic
        fun getHome(): Uri {
            return Uri.parse(AppPref.getString(AppPref.PrefKey.PREF_FM_HOME_STR))
        }

        @JvmStatic
        fun setHome(uri: Uri) {
            AppPref.set(AppPref.PrefKey.PREF_FM_HOME_STR, uri.toString())
        }

        @JvmStatic
        fun isRememberLastOpenedPath(): Boolean {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_FM_REMEMBER_LAST_PATH_BOOL)
        }

        @JvmStatic
        fun getLastOpenedPath(): Pair<FmActivity.Options, Pair<Uri?, Int>>? {
            val jsonString = AppPref.getString(AppPref.PrefKey.PREF_FM_LAST_PATH_STR)
            return try {
                val `object` = JSONObject(jsonString)
                if (`object`.has("path") && `object`.has("pos")) {
                    val vfs = `object`.has("vfs") && `object`.getBoolean("vfs")
                    val options = FmActivity.Options(
                        Uri.parse(`object`.getString("path")),
                        vfs,
                        isTask = false,
                        isShortInfo = false
                    )
                    if (!Paths.getStrict(options.uri).exists()) {
                        // Do not bother if path does not exist
                        return null
                    }
                    val initUri = if (vfs && `object`.has("init")) {
                        Uri.parse(`object`.getString("init"))
                    } else {
                        null
                    }
                    val uriPositionPair = Pair(initUri, `object`.getInt("pos"))
                    Pair(options, uriPositionPair)
                } else {
                    null
                }
            } catch (e: JSONException) {
                e.printStackTrace()
                null
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
                null
            }
        }

        @JvmStatic
        fun setLastOpenedPath(options: FmActivity.Options, initUri: Uri, position: Int) {
            try {
                if (options.isVfs) {
                    // Ignore VFS for now
                    return
                }
                val `object` = JSONObject()
                `object`.put("pos", position)
                if (options.isVfs) {
                    `object`.put("vfs", true)
                    `object`.put("path", options.uri.toString())
                    `object`.put("init", initUri.toString())
                } else {
                    `object`.put("path", initUri.toString())
                }
                AppPref.set(AppPref.PrefKey.PREF_FM_LAST_PATH_STR, `object`.toString())
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

        @JvmStatic
        @FmListOptions.Options
        fun getOptions(): Int {
            return AppPref.getInt(AppPref.PrefKey.PREF_FM_OPTIONS_INT)
        }

        @JvmStatic
        fun setOptions(@FmListOptions.Options options: Int) {
            AppPref.set(AppPref.PrefKey.PREF_FM_OPTIONS_INT, options)
        }

        @JvmStatic
        @FmListOptions.SortOrder
        fun getSortOrder(): Int {
            return AppPref.getInt(AppPref.PrefKey.PREF_FM_SORT_ORDER_INT)
        }

        @JvmStatic
        fun setSortOrder(@FmListOptions.SortOrder sortOrder: Int) {
            AppPref.set(AppPref.PrefKey.PREF_FM_SORT_ORDER_INT, sortOrder)
        }

        @JvmStatic
        fun isReverseSort(): Boolean {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_FM_SORT_REVERSE_BOOL)
        }

        @JvmStatic
        fun setReverseSort(reverseSort: Boolean) {
            AppPref.set(AppPref.PrefKey.PREF_FM_SORT_REVERSE_BOOL, reverseSort)
        }
    }

    object Installer {
        @JvmStatic
        fun installInBackground(): Boolean {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_INSTALLER_ALWAYS_ON_BACKGROUND_BOOL)
        }

        @JvmStatic
        fun displayChanges(): Boolean {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_INSTALLER_DISPLAY_CHANGES_BOOL)
        }

        @JvmStatic
        fun blockTrackers(): Boolean {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_INSTALLER_BLOCK_TRACKERS_BOOL)
        }

        @JvmStatic
        fun forceDexOpt(): Boolean {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    && AppPref.getBoolean(AppPref.PrefKey.PREF_INSTALLER_FORCE_DEX_OPT_BOOL)
        }

        @JvmStatic
        fun canSignApk(): Boolean {
            if (!AppPref.getBoolean(AppPref.PrefKey.PREF_INSTALLER_SIGN_APK_BOOL)) {
                // Signing not enabled
                return false
            }
            return Signer.canSign()
        }

        @JvmStatic
        fun getInstallLocation(): Int {
            return AppPref.getInt(AppPref.PrefKey.PREF_INSTALLER_INSTALL_LOCATION_INT)
        }

        @JvmStatic
        fun setInstallLocation(installLocation: Int) {
            AppPref.set(AppPref.PrefKey.PREF_INSTALLER_INSTALL_LOCATION_INT, installLocation)
        }

        @JvmStatic
        fun getInstallerPackageName(): String {
            if (!SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.INSTALL_PACKAGES)) {
                return BuildConfig.APPLICATION_ID
            }
            return AppPref.getString(AppPref.PrefKey.PREF_INSTALLER_INSTALLER_APP_STR)
        }

        @JvmStatic
        fun setInstallerPackageName(packageName: String) {
            AppPref.set(AppPref.PrefKey.PREF_INSTALLER_INSTALLER_APP_STR, packageName)
        }

        @JvmStatic
        fun isSetOriginatingPackage(): Boolean {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_INSTALLER_SET_ORIGIN_BOOL)
        }

        @JvmStatic
        fun getPackageSource(): Int {
            return AppPref.getInt(AppPref.PrefKey.PREF_INSTALLER_DEFAULT_PKG_SOURCE_INT)
        }

        @JvmStatic
        fun setPackageSource(source: Int) {
            AppPref.set(AppPref.PrefKey.PREF_INSTALLER_DEFAULT_PKG_SOURCE_INT, source)
        }

        @JvmStatic
        fun requestUpdateOwnership(): Boolean {
            // Shell default is false
            return AppPref.getBoolean(AppPref.PrefKey.PREF_INSTALLER_UPDATE_OWNERSHIP_BOOL)
        }

        @JvmStatic
        fun isDisableApkVerification(): Boolean {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_INSTALLER_DISABLE_VERIFICATION_BOOL)
        }
    }

    object LogViewer {
        @JvmStatic
        @LogcatHelper.LogBufferId
        fun getBuffers(): Int {
            return AppPref.getInt(AppPref.PrefKey.PREF_LOG_VIEWER_BUFFER_INT)
        }

        @JvmStatic
        fun setBuffers(@LogcatHelper.LogBufferId buffers: Int) {
            AppPref.set(AppPref.PrefKey.PREF_LOG_VIEWER_BUFFER_INT, buffers)
        }

        @JvmStatic
        fun getLogLevel(): Int {
            return AppPref.getInt(AppPref.PrefKey.PREF_LOG_VIEWER_DEFAULT_LOG_LEVEL_INT)
        }

        @JvmStatic
        fun setLogLevel(logLevel: Int) {
            AppPref.set(AppPref.PrefKey.PREF_LOG_VIEWER_DEFAULT_LOG_LEVEL_INT, logLevel)
        }

        @JvmStatic
        fun getDisplayLimit(): Int {
            return AppPref.getInt(AppPref.PrefKey.PREF_LOG_VIEWER_DISPLAY_LIMIT_INT)
        }

        @JvmStatic
        fun setDisplayLimit(displayLimit: Int) {
            AppPref.set(AppPref.PrefKey.PREF_LOG_VIEWER_DISPLAY_LIMIT_INT, displayLimit)
        }

        @JvmStatic
        fun getFilterPattern(): String {
            return AppPref.getString(AppPref.PrefKey.PREF_LOG_VIEWER_FILTER_PATTERN_STR)
        }

        @JvmStatic
        fun setFilterPattern(filterPattern: String) {
            AppPref.set(AppPref.PrefKey.PREF_LOG_VIEWER_FILTER_PATTERN_STR, filterPattern)
        }

        @JvmStatic
        fun getLogWritingInterval(): Int {
            return AppPref.getInt(AppPref.PrefKey.PREF_LOG_VIEWER_WRITE_PERIOD_INT)
        }

        @JvmStatic
        fun setLogWritingInterval(logWritingInterval: Int) {
            AppPref.set(AppPref.PrefKey.PREF_LOG_VIEWER_WRITE_PERIOD_INT, logWritingInterval)
        }

        @JvmStatic
        fun expandByDefault(): Boolean {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_LOG_VIEWER_EXPAND_BY_DEFAULT_BOOL)
        }

        @JvmStatic
        fun omitSensitiveInfo(): Boolean {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_LOG_VIEWER_OMIT_SENSITIVE_INFO_BOOL)
        }

        @JvmStatic
        fun showPidTidTimestamp(): Boolean {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_LOG_VIEWER_SHOW_PID_TID_TIMESTAMP_BOOL)
        }
    }

    object MainPage {
        @JvmStatic
        @MainListOptions.SortOrder
        fun getSortOrder(): Int {
            return AppPref.getInt(AppPref.PrefKey.PREF_MAIN_WINDOW_SORT_ORDER_INT)
        }

        @JvmStatic
        fun setSortOrder(@RunningAppsActivity.SortOrder sortOrder: Int) {
            AppPref.set(AppPref.PrefKey.PREF_MAIN_WINDOW_SORT_ORDER_INT, sortOrder)
        }

        @JvmStatic
        fun isReverseSort(): Boolean {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_MAIN_WINDOW_SORT_REVERSE_BOOL)
        }

        @JvmStatic
        fun setReverseSort(reverseSort: Boolean) {
            AppPref.set(AppPref.PrefKey.PREF_MAIN_WINDOW_SORT_REVERSE_BOOL, reverseSort)
        }

        @JvmStatic
        @MainListOptions.Filter
        fun getFilters(): Int {
            return AppPref.getInt(AppPref.PrefKey.PREF_MAIN_WINDOW_FILTER_FLAGS_INT)
        }

        @JvmStatic
        fun setFilters(@MainListOptions.Filter filters: Int) {
            AppPref.set(AppPref.PrefKey.PREF_MAIN_WINDOW_FILTER_FLAGS_INT, filters)
        }

        @JvmStatic
        fun getFilteredProfileName(): String? {
            val profileName = AppPref.getString(AppPref.PrefKey.PREF_MAIN_WINDOW_FILTER_PROFILE_STR)
            return if (TextUtils.isEmpty(profileName)) {
                null
            } else {
                profileName
            }
        }

        @JvmStatic
        fun setFilteredProfileName(profileName: String?) {
            AppPref.set(AppPref.PrefKey.PREF_MAIN_WINDOW_FILTER_PROFILE_STR, profileName ?: "")
        }
    }

    object Misc {
        @JvmStatic
        fun getSelectedUsers(): IntArray? {
            val usersStr = AppPref.getString(AppPref.PrefKey.PREF_SELECTED_USERS_STR)
            if (usersStr.isEmpty()) return null
            val usersSplitStr = usersStr.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val users = IntArray(usersSplitStr.size)
            for (i in users.indices) {
                users[i] = Integer.decode(usersSplitStr[i])
            }
            return users
        }

        @JvmStatic
        fun setSelectedUsers(users: IntArray?) {
            if (users == null) {
                AppPref.set(AppPref.PrefKey.PREF_SELECTED_USERS_STR, "")
                return
            }
            val userString = Array(users.size) { i -> users[i].toString() }
            AppPref.set(AppPref.PrefKey.PREF_SELECTED_USERS_STR, TextUtils.join(",", userString))
        }

        @JvmStatic
        fun sendNotificationsToConnectedDevices(): Boolean {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_SEND_NOTIFICATIONS_TO_CONNECTED_DEVICES_BOOL)
        }

        @JvmStatic
        fun setAdbLocalServerPort(port: Int) {
            AppPref.set(AppPref.PrefKey.PREF_ADB_LOCAL_SERVER_PORT_INT, port)
        }

        @JvmStatic
        fun getAdbLocalServerPort(): Int {
            return AppPref.getInt(AppPref.PrefKey.PREF_ADB_LOCAL_SERVER_PORT_INT)
        }
    }

    object RunningApps {
        @JvmStatic
        @RunningAppsActivity.SortOrder
        fun getSortOrder(): Int {
            return AppPref.getInt(AppPref.PrefKey.PREF_RUNNING_APPS_SORT_ORDER_INT)
        }

        @JvmStatic
        fun setSortOrder(@RunningAppsActivity.SortOrder sortOrder: Int) {
            AppPref.set(AppPref.PrefKey.PREF_RUNNING_APPS_SORT_ORDER_INT, sortOrder)
        }

        @JvmStatic
        @RunningAppsActivity.Filter
        fun getFilters(): Int {
            return AppPref.getInt(AppPref.PrefKey.PREF_RUNNING_APPS_FILTER_FLAGS_INT)
        }

        @JvmStatic
        fun setFilters(@RunningAppsActivity.Filter filters: Int) {
            AppPref.set(AppPref.PrefKey.PREF_RUNNING_APPS_FILTER_FLAGS_INT, filters)
        }

        @JvmStatic
        fun enableKillForSystemApps(): Boolean {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_ENABLE_KILL_FOR_SYSTEM_BOOL)
        }

        @JvmStatic
        fun setEnableKillForSystemApps(enable: Boolean) {
            AppPref.set(AppPref.PrefKey.PREF_ENABLE_KILL_FOR_SYSTEM_BOOL, enable)
        }
    }

    object Privacy {
        @JvmStatic
        fun isScreenLockEnabled(): Boolean {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_ENABLE_SCREEN_LOCK_BOOL)
        }

        @JvmStatic
        fun isAutoLockEnabled(): Boolean {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_ENABLE_AUTO_LOCK_BOOL)
        }

        @JvmStatic
        fun isPersistentSessionAllowed(): Boolean {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_ENABLE_PERSISTENT_SESSION_BOOL)
        }
    }

    object Signing {
        @JvmStatic
        fun getSigSchemes(): SigSchemes {
            val sigSchemes = SigSchemes(AppPref.getInt(AppPref.PrefKey.PREF_SIGNATURE_SCHEMES_INT))
            if (sigSchemes.isEmpty) {
                // Use default if no flag is set
                return SigSchemes(SigSchemes.DEFAULT_SCHEMES)
            }
            return sigSchemes
        }

        @JvmStatic
        fun setSigSchemes(flags: Int) {
            AppPref.set(AppPref.PrefKey.PREF_SIGNATURE_SCHEMES_INT, flags)
        }

        @JvmStatic
        fun zipAlign(): Boolean {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_ZIP_ALIGN_BOOL)
        }
    }

    object Storage {
        @JvmStatic
        fun getAppManagerDirectory(): Path {
            val uri = getVolumePath()
            val path: Path = if (uri.scheme == ContentResolver.SCHEME_FILE) {
                // Append AppManager
                val newPath = uri.path + File.separator + "AppManager"\nPaths.get(newPath)
            } else {
                Paths.get(uri)
            }
            if (!path.exists()) path.mkdirs()
            return path
        }

        @JvmStatic
        fun getVolumePath(): Uri {
            val uriOrBareFile = AppPref.getString(AppPref.PrefKey.PREF_BACKUP_VOLUME_STR)
            if (uriOrBareFile.startsWith("/")) {
                // A good URI starts with file:// or content://, if not, migrate
                val uri = Uri.Builder().scheme(ContentResolver.SCHEME_FILE).path(uriOrBareFile).build()
                AppPref.set(AppPref.PrefKey.PREF_BACKUP_VOLUME_STR, uri.toString())
                return uri
            }
            return Uri.parse(uriOrBareFile)
        }

        @JvmStatic
        fun setVolumePath(path: String) {
            AppPref.set(AppPref.PrefKey.PREF_BACKUP_VOLUME_STR, path)
        }

        @JvmStatic
        fun getTempPath(): Path {
            // This path is intended for storing temporary data for backup/restore and similar operations
            return Paths.get(FileUtils.getCachePath())
        }
    }

    object VirusTotal {
        @JvmStatic
        fun getApiKey(): String? {
            val apiKey = AppPref.getString(AppPref.PrefKey.PREF_VIRUS_TOTAL_API_KEY_STR)
            return if (TextUtils.isEmpty(apiKey)) {
                null
            } else {
                apiKey
            }
        }

        @JvmStatic
        fun setApiKey(apiKey: String?) {
            AppPref.set(AppPref.PrefKey.PREF_VIRUS_TOTAL_API_KEY_STR, apiKey)
        }

        @JvmStatic
        fun promptBeforeUpload(): Boolean {
            return AppPref.getBoolean(AppPref.PrefKey.PREF_VIRUS_TOTAL_PROMPT_BEFORE_UPLOADING_BOOL)
        }
    }
}
