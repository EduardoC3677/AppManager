// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options

object FilterOptions {
    @JvmStatic
    fun create(filterName: String): FilterOption {
        return when (filterName) {
            "archivable" -> ArchivableOption()
            "apk_size" -> ApkSizeOption()
            "app_label" -> AppLabelOption()
            "app_type" -> AppTypeOption()
            "backup" -> BackupOption()
            "bloatware" -> BloatwareOption()
            "cache_size" -> CacheSizeOption()
            "compile_sdk" -> CompileSdkOption()
            "components" -> ComponentsOption()
            "data_size" -> DataSizeOption()
            "data_usage" -> DataUsageOption()
            "freeze_unfreeze" -> FreezeOption()
            "installed" -> InstalledOption()
            "installer" -> InstallerOption()
            "last_update" -> LastUpdateOption()
            "min_sdk" -> MinSdkOption()
            "permissions" -> PermissionsOption()
            "pkg_name" -> PackageNameOption()
            "running_apps" -> RunningAppsOption()
            "screen_time" -> ScreenTimeOption()
            "shared_uid" -> SharedUidOption()
            "signature" -> SignatureOption()
            "target_sdk" -> TargetSdkOption()
            "times_opened" -> TimesOpenedOption()
            "total_size" -> TotalSizeOption()
            "trackers" -> TrackersOption()
            "version_name" -> VersionNameOption()
            else -> throw IllegalArgumentException("Invalid filter: $filterName")
        }
    }
}
