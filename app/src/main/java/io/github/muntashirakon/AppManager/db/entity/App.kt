// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.entity

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.UserHandleHidden
import androidx.core.content.pm.PackageInfoCompat
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils
import io.github.muntashirakon.AppManager.utils.FreezeUtils
import io.github.muntashirakon.AppManager.utils.Utils
import java.io.Serializable

@Suppress("NotNullFieldNotInitialized")
@Entity(
    tableName = "app",
    primaryKeys = ["package_name", "user_id"],
    indices = [
        // OPTIMIZATION: Add indices for common filter queries
        // Speeds up filtering by flags (system/user apps, disabled apps, etc.)
        Index(name = "index_app_flags", value = ["flags"]),
        // Speeds up filtering by installed status
        Index(name = "index_app_is_installed", value = ["is_installed"]),
        // Speeds up filtering by enabled/disabled status
        Index(name = "index_app_is_enabled", value = ["is_enabled"]),
        // Speeds up filtering and sorting by last update time
        Index(name = "index_app_last_update_time", value = ["last_update_time"]),
        // Speeds up filtering by target SDK
        Index(name = "index_app_target_sdk", value = ["target_sdk"]),
        // Speeds up filtering by tracker count
        Index(name = "index_app_tracker_count", value = ["tracker_count"]),
        // Speeds up filtering by tags
        Index(name = "index_app_tags", value = ["tags"]),
        // Composite index for common combined filters
        Index(name = "index_app_is_installed_user_id", value = ["is_installed", "user_id"]),
        Index(name = "index_app_flags_is_installed", value = ["flags", "is_installed"])
    ]
)
data class App(
    @ColumnInfo(name = "package_name")
    var packageName: String = "",

    @ColumnInfo(name = "user_id", defaultValue = "" + UserHandleHidden.USER_NULL)
    var userId: Int = 0,

    @ColumnInfo(name = "label")
    var packageLabel: String? = null,

    @ColumnInfo(name = "version_name")
    var versionName: String? = null,

    @ColumnInfo(name = "version_code")
    var versionCode: Long = 0,

    @ColumnInfo(name = "flags", defaultValue = "0")
    var flags: Int = 0,

    @ColumnInfo(name = "uid", defaultValue = "0")
    var uid: Int = 0,

    @ColumnInfo(name = "shared_uid", defaultValue = "NULL")
    var sharedUserId: String? = null,

    @ColumnInfo(name = "first_install_time", defaultValue = "0")
    var firstInstallTime: Long = 0,

    @ColumnInfo(name = "last_update_time", defaultValue = "0")
    var lastUpdateTime: Long = 0,

    @ColumnInfo(name = "target_sdk", defaultValue = "0")
    var sdk: Int = 0,

    @ColumnInfo(name = "cert_name", defaultValue = "''")
    var certName: String? = null,

    @ColumnInfo(name = "cert_algo", defaultValue = "''")
    var certAlgo: String? = null,

    @ColumnInfo(name = "is_installed", defaultValue = "true")
    var isInstalled: Boolean = true,

    @ColumnInfo(name = "is_only_data_installed", defaultValue = "0")
    var isOnlyDataInstalled: Boolean = false,

    @ColumnInfo(name = "is_enabled", defaultValue = "false")
    var isEnabled: Boolean = false,

    @ColumnInfo(name = "has_activities", defaultValue = "false")
    var hasActivities: Boolean = false,

    @ColumnInfo(name = "has_splits", defaultValue = "false")
    var hasSplits: Boolean = false,

    @ColumnInfo(name = "has_keystore", defaultValue = "false")
    var hasKeystore: Boolean = false,

    @ColumnInfo(name = "uses_saf", defaultValue = "false")
    var usesSaf: Boolean = false,

    @ColumnInfo(name = "ssaid", defaultValue = "")
    var ssaid: String? = null,

    @ColumnInfo(name = "code_size", defaultValue = "0")
    var codeSize: Long = 0,

    @ColumnInfo(name = "data_size", defaultValue = "0")
    var dataSize: Long = 0,

    @ColumnInfo(name = "mobile_data", defaultValue = "0")
    var mobileDataUsage: Long = 0,

    @ColumnInfo(name = "wifi_data", defaultValue = "0")
    var wifiDataUsage: Long = 0,

    @ColumnInfo(name = "rules_count", defaultValue = "0")
    var rulesCount: Int = 0,

    @ColumnInfo(name = "tracker_count", defaultValue = "0")
    var trackerCount: Int = 0,

    @ColumnInfo(name = "open_count", defaultValue = "0")
    var openCount: Int = 0,

    @ColumnInfo(name = "screen_time", defaultValue = "0")
    var screenTime: Long = 0,

    @ColumnInfo(name = "last_usage_time", defaultValue = "0")
    var lastUsageTime: Long = 0,

    @ColumnInfo(name = "last_action_time", defaultValue = "0")
    var lastActionTime: Long = 0,

    @ColumnInfo(name = "tags", defaultValue = "''")
    var tags: String? = ""
) : Serializable {

    fun isSystemApp(): Boolean {
        return (flags and ApplicationInfo.FLAG_SYSTEM) != 0
    }

    fun isDebuggable(): Boolean {
        return (flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is App) return false
        return userId == other.userId && packageName == other.packageName
    }

    override fun hashCode(): Int {
        var result = packageName.hashCode()
        result = 31 * result + userId
        return result
    }

    companion object {
        @JvmStatic
        fun fromPackageInfo(context: Context, packageInfo: PackageInfo): App {
            val app = App()
            val applicationInfo = packageInfo.applicationInfo!!
            app.packageName = applicationInfo.packageName
            app.uid = applicationInfo.uid
            app.userId = UserHandleHidden.getUserId(app.uid)
            app.isInstalled = ApplicationInfoCompat.isInstalled(applicationInfo)
            app.isOnlyDataInstalled = ApplicationInfoCompat.isOnlyDataInstalled(applicationInfo)
            app.flags = applicationInfo.flags
            app.isEnabled = !FreezeUtils.isFrozen(applicationInfo)
            app.packageLabel = ApplicationInfoCompat.loadLabelSafe(applicationInfo, context.packageManager).toString()
            app.sdk = applicationInfo.targetSdkVersion
            app.versionName = packageInfo.versionName
            app.versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
            app.sharedUserId = packageInfo.sharedUserId
            val issuerAndAlgoPair = Utils.getIssuerAndAlg(packageInfo)
            app.certName = issuerAndAlgoPair.first
            app.certAlgo = issuerAndAlgoPair.second
            app.firstInstallTime = packageInfo.firstInstallTime
            app.lastUpdateTime = packageInfo.lastUpdateTime
            app.hasActivities = packageInfo.activities != null
            app.hasSplits = applicationInfo.splitSourceDirs != null
            app.rulesCount = 0
            app.trackerCount = ComponentUtils.getTrackerComponentsCountForPackage(packageInfo)
            app.lastActionTime = System.currentTimeMillis()
            return app
        }

        @JvmStatic
        fun fromBackup(backup: Backup): App {
            val app = App()
            app.packageName = backup.packageName
            app.uid = 0
            app.userId = backup.userId
            app.isInstalled = false
            app.isOnlyDataInstalled = false
            if (backup.isSystem) {
                app.flags = app.flags or ApplicationInfo.FLAG_SYSTEM
            }
            app.isEnabled = true
            app.packageLabel = backup.label
            app.sdk = 0
            app.versionName = backup.versionName
            app.versionCode = backup.versionCode
            app.sharedUserId = null
            app.certName = ""
            app.certAlgo = ""
            app.firstInstallTime = backup.backupTime
            app.lastUpdateTime = backup.backupTime
            app.hasActivities = false
            app.hasSplits = backup.hasSplits
            app.rulesCount = 0
            app.trackerCount = 0
            app.lastActionTime = backup.backupTime
            app.hasKeystore = backup.hasKeyStore
            return app
        }
    }
}
