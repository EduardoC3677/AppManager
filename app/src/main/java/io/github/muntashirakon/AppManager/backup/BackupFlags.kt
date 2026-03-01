// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup

import android.content.Context
import android.text.TextUtils
import androidx.annotation.IntDef
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.utils.AppPref
import io.github.muntashirakon.util.LocalizedString

class BackupFlags(@BackupFlag var flags: Int) : LocalizedString {
    @IntDef(
        flag = true, value = [
            BACKUP_APK_FILES,
            BACKUP_INT_DATA,
            BACKUP_EXT_DATA,
            BACKUP_EXT_OBB_MEDIA,
            BACKUP_RULES,
            BACKUP_EXTRAS,
            BACKUP_CACHE,
            BACKUP_ADB_DATA,
            BACKUP_CUSTOM_USERS,
            SKIP_SIGNATURE_CHECK
        ]
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class BackupFlag

    fun addFlag(@BackupFlag flag: Int) {
        flags = flags or flag
    }

    fun removeFlag(@BackupFlag flag: Int) {
        flags = flags and flag.inv()
    }

    fun hasFlag(@BackupFlag flag: Int): Boolean {
        return flags and flag == flag
    }

    fun backupApkFiles(): Boolean {
        return hasFlag(BACKUP_APK_FILES)
    }

    fun backupInternalData(): Boolean {
        return hasFlag(BACKUP_INT_DATA)
    }

    fun backupExternalData(): Boolean {
        return hasFlag(BACKUP_EXT_DATA)
    }

    fun backupMediaObb(): Boolean {
        return hasFlag(BACKUP_EXT_OBB_MEDIA)
    }

    fun backupData(): Boolean {
        return backupInternalData() || backupExternalData() || backupMediaObb() || backupAdbData()
    }

    fun backupRules(): Boolean {
        return hasFlag(BACKUP_RULES)
    }

    fun backupExtras(): Boolean {
        return hasFlag(BACKUP_EXTRAS)
    }

    fun backupCache(): Boolean {
        return hasFlag(BACKUP_CACHE)
    }

    fun backupAdbData(): Boolean {
        return hasFlag(BACKUP_ADB_DATA)
    }

    fun backupCustomUsers(): Boolean {
        return hasFlag(BACKUP_CUSTOM_USERS)
    }

    fun skipSignatureCheck(): Boolean {
        return hasFlag(SKIP_SIGNATURE_CHECK)
    }

    fun isEmpty(): Boolean {
        return flags == 0
    }

    override fun toLocalizedString(context: Context): CharSequence {
        val strings = mutableListOf<String>()
        if (backupApkFiles()) strings.add(context.getString(R.string.apk_files))
        if (backupInternalData()) strings.add(context.getString(R.string.internal_data))
        if (backupExternalData()) strings.add(context.getString(R.string.external_data))
        if (backupMediaObb()) strings.add(context.getString(R.string.media_obb))
        if (backupAdbData()) strings.add(context.getString(R.string.adb_backup))
        if (backupRules()) strings.add(context.getString(R.string.rules))
        if (backupExtras()) strings.add(context.getString(R.string.extras))
        return if (strings.isEmpty()) "" else TextUtils.join(", ", strings)
    }

    companion object {
        const val BACKUP_APK_FILES = 1
        const val BACKUP_INT_DATA = 1 shl 1
        const val BACKUP_EXT_DATA = 1 shl 2
        const val BACKUP_EXT_OBB_MEDIA = 1 shl 3
        const val BACKUP_RULES = 1 shl 4
        const val BACKUP_EXTRAS = 1 shl 5
        const val BACKUP_CACHE = 1 shl 6
        const val BACKUP_ADB_DATA = 1 shl 7
        const val BACKUP_CUSTOM_USERS = 1 shl 8
        const val SKIP_SIGNATURE_CHECK = 1 shl 9

        const val BACKUP_MULTIPLE = BACKUP_APK_FILES or BACKUP_INT_DATA or BACKUP_EXT_DATA or
                BACKUP_EXT_OBB_MEDIA or BACKUP_RULES or BACKUP_EXTRAS or BACKUP_CACHE

        @JvmStatic
        fun fromPref(): BackupFlags {
            return BackupFlags(AppPref.getInt(AppPref.PrefKey.PREF_BACKUP_FLAGS_INT))
        }

        @JvmStatic
        fun getSupportedBackupFlags(): Int {
            return BACKUP_MULTIPLE or BACKUP_ADB_DATA or BACKUP_CUSTOM_USERS or SKIP_SIGNATURE_CHECK
        }

        @JvmStatic
        fun getBackupFlagsAsArray(@BackupFlag flags: Int): List<Int> {
            val flagList = mutableListOf<Int>()
            if (flags and BACKUP_APK_FILES != 0) flagList.add(BACKUP_APK_FILES)
            if (flags and BACKUP_INT_DATA != 0) flagList.add(BACKUP_INT_DATA)
            if (flags and BACKUP_EXT_DATA != 0) flagList.add(BACKUP_EXT_DATA)
            if (flags and BACKUP_EXT_OBB_MEDIA != 0) flagList.add(BACKUP_EXT_OBB_MEDIA)
            if (flags and BACKUP_ADB_DATA != 0) flagList.add(BACKUP_ADB_DATA)
            if (flags and BACKUP_RULES != 0) flagList.add(BACKUP_RULES)
            if (flags and BACKUP_EXTRAS != 0) flagList.add(BACKUP_EXTRAS)
            if (flags and BACKUP_CACHE != 0) flagList.add(BACKUP_CACHE)
            if (flags and BACKUP_CUSTOM_USERS != 0) flagList.add(BACKUP_CUSTOM_USERS)
            if (flags and SKIP_SIGNATURE_CHECK != 0) flagList.add(SKIP_SIGNATURE_CHECK)
            return flagList
        }

        @JvmStatic
        fun getFormattedFlagNames(context: Context, flags: List<Int>): Array<CharSequence> {
            val strings = arrayOfNulls<CharSequence>(flags.size)
            for (i in flags.indices) {
                val flag = flags[i]
                strings[i] = when (flag) {
                    BACKUP_APK_FILES -> context.getString(R.string.apk_files)
                    BACKUP_INT_DATA -> context.getString(R.string.internal_data)
                    BACKUP_EXT_DATA -> context.getString(R.string.external_data)
                    BACKUP_EXT_OBB_MEDIA -> context.getString(R.string.media_obb)
                    BACKUP_ADB_DATA -> context.getString(R.string.adb_backup)
                    BACKUP_RULES -> context.getString(R.string.rules)
                    BACKUP_EXTRAS -> context.getString(R.string.extras)
                    BACKUP_CACHE -> context.getString(R.string.cache)
                    BACKUP_CUSTOM_USERS -> context.getString(R.string.custom_users)
                    SKIP_SIGNATURE_CHECK -> context.getString(R.string.skip_signature_check)
                    else -> ""
                }
            }
            return strings.requireNoNulls()
        }
    }
}
