// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.adb

import android.os.Build
import java.io.File

object Constants {
    const val BACKUP_FILE_HEADER_MAGIC = "ANDROID BACKUP
"
    const val BACKUP_FILE_VERSION = 5
    const val BACKUP_MANIFEST_VERSION = 1
    const val BACKUP_MANIFEST_FILENAME = "_manifest"
    const val ENCRYPTION_ALGORITHM_NAME = "AES-256"
    const val PBKDF2_HASH_ROUNDS = 10000
    const val PBKDF2_KEY_SIZE = 256
    const val PBKDF2_SALT_SIZE = 512
    const val PBKDF_CURRENT = "PBKDF2WithHmacSHA1"
    const val PBKDF_FALLBACK = "PBKDF2WithHmacSHA1" // FIXME: same as current?

    const val APPS_PREFIX = "apps/"
    const val SHARED_PREFIX = "shared/"

    const val APK_TREE_TOKEN = "a"
    const val OBB_TREE_TOKEN = "obb"
    const val MANAGED_EXTERNAL_TREE_TOKEN = "ef"
    const val ROOT_TREE_TOKEN = "r"
    const val FILES_TREE_TOKEN = "f"
    const val NO_BACKUP_TREE_TOKEN = "nb"
    const val DATABASE_TREE_TOKEN = "db"
    const val SHAREDPREFS_TREE_TOKEN = "sp"
    const val CACHE_TREE_TOKEN = "c"

    const val DEVICE_ROOT_TREE_TOKEN = "dr"
    const val DEVICE_FILES_TREE_TOKEN = "df"
    const val DEVICE_NO_BACKUP_TREE_TOKEN = "dnb"
    const val DEVICE_DATABASE_TREE_TOKEN = "ddb"
    const val DEVICE_SHAREDPREFS_TREE_TOKEN = "dsp"
    const val DEVICE_CACHE_TREE_TOKEN = "dc"

    @JvmStatic
    fun getBackupFileVersionFromApi(api: Int): Int {
        return if (api >= Build.VERSION_CODES.N) 5 else 4
    }
}
