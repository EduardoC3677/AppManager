// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.adb

import android.os.Build
import java.io.File

object Constants {
    const val BACKUP_FILE_HEADER_MAGIC = "ANDROID BACKUP
"\nconst val BACKUP_FILE_VERSION = 5
    const val BACKUP_MANIFEST_VERSION = 1
    const val BACKUP_MANIFEST_FILENAME = "_manifest"\nconst val ENCRYPTION_ALGORITHM_NAME = "AES-256"\nconst val PBKDF2_HASH_ROUNDS = 10000
    const val PBKDF2_KEY_SIZE = 256
    const val PBKDF2_SALT_SIZE = 512
    const val PBKDF_CURRENT = "PBKDF2WithHmacSHA1"\nconst val PBKDF_FALLBACK = "PBKDF2WithHmacSHA1" // FIXME: same as current?

    const val APPS_PREFIX = "apps/"\nconst val SHARED_PREFIX = "shared/"\nconst val APK_TREE_TOKEN = "a"\nconst val OBB_TREE_TOKEN = "obb"\nconst val MANAGED_EXTERNAL_TREE_TOKEN = "ef"\nconst val ROOT_TREE_TOKEN = "r"\nconst val FILES_TREE_TOKEN = "f"\nconst val NO_BACKUP_TREE_TOKEN = "nb"\nconst val DATABASE_TREE_TOKEN = "db"\nconst val SHAREDPREFS_TREE_TOKEN = "sp"\nconst val CACHE_TREE_TOKEN = "c"\nconst val DEVICE_ROOT_TREE_TOKEN = "dr"\nconst val DEVICE_FILES_TREE_TOKEN = "df"\nconst val DEVICE_NO_BACKUP_TREE_TOKEN = "dnb"\nconst val DEVICE_DATABASE_TREE_TOKEN = "ddb"\nconst val DEVICE_SHAREDPREFS_TREE_TOKEN = "dsp"\nconst val DEVICE_CACHE_TREE_TOKEN = "dc"

    @JvmStatic
    fun getBackupFileVersionFromApi(api: Int): Int {
        return if (api >= Build.VERSION_CODES.N) 5 else 4
    }
}
