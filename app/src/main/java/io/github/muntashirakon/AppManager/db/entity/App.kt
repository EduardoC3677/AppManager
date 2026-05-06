// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.entity

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.UserHandleHidden
import androidx.core.content.pm.PackageInfoCompat
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils
import io.github.muntashirakon.AppManager.utils.FreezeUtils
import io.github.muntashirakon.AppManager.utils.Utils
import java.io.Serializable
import java.util.Objects

@Entity(
    tableName = "app",
    primaryKeys = ["package_name", "user_id"],
    indices = [
        Index(value = ["flags"], name = "index_app_flags"),
        Index(value = ["is_installed"], name = "index_app_is_installed"),
        Index(value = ["is_enabled"], name = "index_app_is_enabled"),
        Index(value = ["last_update_time"], name = "index_app_last_update_time"),
        Index(value = ["target_sdk"], name = "index_app_target_sdk"),
        Index(value = ["tracker_count"], name = "index_app_tracker_count"),
        Index(value = ["is_installed", "user_id"], name = "index_app_is_installed_user_id"),
        Index(value = ["flags", "is_installed"], name = "index_app_flags_is_installed")
    ]
)
class App : Serializable {
    @JvmField
    @ColumnInfo(name = "package_name")
    var packageName: String = ""\n@JvmField
    @ColumnInfo(name = "user_id", defaultValue = "-10000")
    var userId: Int = 0

    @JvmField
    @ColumnInfo(name = "package_label")
    var packageLabel: String? = null

    @JvmField
    @ColumnInfo(name = "version_name")
    var versionName: String? = null

    @JvmField
    @ColumnInfo(name = "version_code", defaultValue = "0")
    var versionCode: Long = 0

    @JvmField
    @ColumnInfo(name = "shared_user_id")
    var sharedUserId: String? = null

    @JvmField
    @ColumnInfo(name = "flags", defaultValue = "0")
    var flags: Int = 0

    @JvmField
    @ColumnInfo(name = "uid", defaultValue = "0")
    var uid: Int = 0

    @JvmField
    @ColumnInfo(name = "first_install_time", defaultValue = "0")
    var firstInstallTime: Long = 0

    @JvmField
    @ColumnInfo(name = "last_update_time", defaultValue = "0")
    var lastUpdateTime: Long = 0

    @JvmField
    @ColumnInfo(name = "target_sdk", defaultValue = "0")
    var sdk: Int = 0

    @JvmField
    @ColumnInfo(name = "cert_name", defaultValue = "''")
    var certName: String? = null

    @JvmField
    @ColumnInfo(name = "cert_algo", defaultValue = "''")
    var certAlgo: String? = null

    @JvmField
    @ColumnInfo(name = "is_installed", defaultValue = "true")
    var isInstalled: Boolean = true

    @JvmField
    @ColumnInfo(name = "is_only_data_installed", defaultValue = "0")
    var isOnlyDataInstalled: Boolean = false

    @JvmField
    @ColumnInfo(name = "is_enabled", defaultValue = "false")
    var isEnabled: Boolean = false

    @JvmField
    @ColumnInfo(name = "has_activities", defaultValue = "false")
    var hasActivities: Boolean = false

    @JvmField
    @ColumnInfo(name = "has_splits", defaultValue = "false")
    var hasSplits: Boolean = false

    @JvmField
    @ColumnInfo(name = "has_keystore", defaultValue = "false")
    var hasKeystore: Boolean = false

    @JvmField
    @ColumnInfo(name = "uses_saf", defaultValue = "false")
    var usesSaf: Boolean = false

    @JvmField
    @ColumnInfo(name = "ssaid", defaultValue = "")
    var ssaid: String? = null

    @JvmField
    @ColumnInfo(name = "code_size", defaultValue = "0")
    var codeSize: Long = 0

    @JvmField
    @ColumnInfo(name = "data_size", defaultValue = "0")
    var dataSize: Long = 0

    @JvmField
    @ColumnInfo(name = "mobile_data", defaultValue = "0")
    var mobileDataUsage: Long = 0

    @JvmField
    @ColumnInfo(name = "wifi_data", defaultValue = "0")
    var wifiDataUsage: Long = 0

    @JvmField
    @ColumnInfo(name = "rules_count", defaultValue = "0")
    var rulesCount: Int = 0

    @JvmField
    @ColumnInfo(name = "tracker_count", defaultValue = "0")
    var trackerCount: Int = 0

    @JvmField
    @ColumnInfo(name = "open_count", defaultValue = "0")
    var openCount: Int = 0

    @JvmField
    @ColumnInfo(name = "screen_time", defaultValue = "0")
    var screenTime: Long = 0

    @JvmField
    @ColumnInfo(name = "last_usage_time", defaultValue = "0")
    var lastUsageTime: Long = 0

    @JvmField
    @ColumnInfo(name = "last_action_time", defaultValue = "0")
    var lastActionTime: Long = 0

    @JvmField
    @ColumnInfo(name = "tags")
    var tags: String? = null

    fun isSystemApp(): Boolean {
        return (flags and ApplicationInfo.FLAG_SYSTEM) != 0
    }

    fun isDebuggable(): Boolean {
        return (flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val app = other as App
        return userId == app.userId && packageName == app.packageName
    }

    override fun hashCode(): Int {
        return Objects.hash(packageName, userId)
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
            app.certName = ""\napp.certAlgo = ""
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
