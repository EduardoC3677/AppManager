// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options

import android.content.Context
import android.text.SpannableStringBuilder
import io.github.muntashirakon.AppManager.backup.BackupFlags.BACKUP_ADB_DATA
import io.github.muntashirakon.AppManager.backup.BackupFlags.BACKUP_APK_FILES
import io.github.muntashirakon.AppManager.backup.BackupFlags.BACKUP_CACHE
import io.github.muntashirakon.AppManager.backup.BackupFlags.BACKUP_EXTRAS
import io.github.muntashirakon.AppManager.backup.BackupFlags.BACKUP_EXT_DATA
import io.github.muntashirakon.AppManager.backup.BackupFlags.BACKUP_EXT_OBB_MEDIA
import io.github.muntashirakon.AppManager.backup.BackupFlags.BACKUP_INT_DATA
import io.github.muntashirakon.AppManager.backup.BackupFlags.BACKUP_RULES
import io.github.muntashirakon.AppManager.db.entity.Backup
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo
import io.github.muntashirakon.AppManager.utils.DateUtils

class BackupOption : FilterOption("backup") {
    private val mKeysWithType = linkedMapOf(
        KEY_ALL to TYPE_NONE,
        "backups" to TYPE_NONE,
        "no_backups" to TYPE_NONE,
        "latest_backup" to TYPE_NONE,
        "outdated_backup" to TYPE_NONE,
        "made_before" to TYPE_TIME_MILLIS,
        "made_after" to TYPE_TIME_MILLIS,
        "with_flags" to TYPE_INT_FLAGS,
        "without_flags" to TYPE_INT_FLAGS
    )

    private val mBackupFlags = linkedMapOf(
        BACKUP_APK_FILES to "Apk files",
        BACKUP_INT_DATA to "Internal data",
        BACKUP_EXT_DATA to "External data",
        BACKUP_ADB_DATA to "ADB data",
        BACKUP_EXT_OBB_MEDIA to "OBB and media",
        BACKUP_CACHE to "Cache",
        BACKUP_EXTRAS to "Extras",
        BACKUP_RULES to "Rules"\n)

    override fun getKeysWithType(): Map<String, Int> {
        return mKeysWithType
    }

    override fun getFlags(key: String): Map<Int, CharSequence> {
        return if (key == "with_flags" || key == "without_flags") {
            mBackupFlags
        } else {
            super.getFlags(key)
        }
    }

    override fun test(info: IFilterableAppInfo, result: TestResult): TestResult {
        val backups = result.getMatchedBackups() ?: info.backups.toList()
        return when (key) {
            KEY_ALL -> result.setMatched(true).setMatchedBackups(backups)
            "backups" -> {
                if (backups.isNotEmpty()) {
                    result.setMatched(true).setMatchedBackups(backups)
                } else {
                    result.setMatched(false).setMatchedBackups(emptyList())
                }
            }
            "no_backups" -> {
                if (backups.isEmpty()) {
                    result.setMatched(true).setMatchedBackups(emptyList())
                } else {
                    result.setMatched(false).setMatchedBackups(backups)
                }
            }
            "latest_backup" -> {
                if (!info.isInstalled) {
                    // If the app isn't install, all backups are latest
                    result.setMatched(true).setMatchedBackups(backups)
                } else {
                    val matchedBackups = mutableListOf<Backup>()
                    val versionCode = info.versionCode
                    for (backup in backups) {
                        if (backup.versionCode >= versionCode) {
                            matchedBackups.add(backup)
                        }
                    }
                    result.setMatched(matchedBackups.isNotEmpty())
                        .setMatchedBackups(matchedBackups)
                }
            }
            "outdated_backup" -> {
                if (!info.isInstalled) {
                    // If the app isn't install, no backups are outdated
                    result.setMatched(false)
                } else {
                    val matchedBackups = mutableListOf<Backup>()
                    val versionCode = info.versionCode
                    for (backup in backups) {
                        if (backup.versionCode < versionCode) {
                            matchedBackups.add(backup)
                        }
                    }
                    result.setMatched(matchedBackups.isNotEmpty())
                        .setMatchedBackups(matchedBackups)
                }
            }
            "made_before" -> {
                val matchedBackups = mutableListOf<Backup>()
                for (backup in backups) {
                    if (backup.backupTime <= longValue) {
                        matchedBackups.add(backup)
                    }
                }
                result.setMatched(matchedBackups.isNotEmpty())
                    .setMatchedBackups(matchedBackups)
            }
            "made_after" -> {
                val matchedBackups = mutableListOf<Backup>()
                for (backup in backups) {
                    if (backup.backupTime >= longValue) {
                        matchedBackups.add(backup)
                    }
                }
                result.setMatched(matchedBackups.isNotEmpty())
                    .setMatchedBackups(matchedBackups)
            }
            "with_flags" -> {
                val matchedBackups = mutableListOf<Backup>()
                for (backup in backups) {
                    if (backup.flags and intValue == intValue) {
                        matchedBackups.add(backup)
                    }
                }
                result.setMatched(matchedBackups.isNotEmpty())
                    .setMatchedBackups(matchedBackups)
            }
            "without_flags" -> {
                val matchedBackups = mutableListOf<Backup>()
                for (backup in backups) {
                    if (backup.flags and intValue != intValue) {
                        matchedBackups.add(backup)
                    }
                }
                result.setMatched(matchedBackups.isNotEmpty())
                    .setMatchedBackups(matchedBackups)
            }
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }

    override fun toLocalizedString(context: Context): CharSequence {
        val sb = SpannableStringBuilder()
        return when (key) {
            KEY_ALL -> "Apps with or without backups"\n"backups" -> "Only the apps with backups"\n"no_backups" -> "only the apps without backups"\n"latest_backup" -> "Only the apps having the latest backups"\n"outdated_backup" -> "Only the apps having some outdated backups"\n"made_before" -> sb.append("Only the apps with backups made before ").append(DateUtils.formatDate(context, longValue))
            "made_after" -> sb.append("Only the apps with backups made after ").append(DateUtils.formatDate(context, longValue))
            "with_flags" -> sb.append("Only the apps having backups with the flags ").append(flagsToString("with_flags", intValue))
            "without_flags" -> sb.append("Only the apps having backups without the flags ").append(flagsToString("without_flags", intValue))
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }
}
