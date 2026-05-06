// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import android.os.Build
import android.os.UserHandleHidden
import androidx.annotation.RequiresApi
import io.github.muntashirakon.AppManager.runner.Runner
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import java.io.FileNotFoundException

object KeyStoreUtils {
    @JvmStatic
    fun hasKeyStore(uid: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return hasKeyStoreV2(uid)
        }
        return hasKeyStoreV1(uid)
    }

    @JvmStatic
    fun hasMasterKey(uid: Int): Boolean {
        return try {
            getMasterKey(UserHandleHidden.getUserId(uid)).exists()
        } catch (e: FileNotFoundException) {
            false
        }
    }

    @JvmStatic
    fun getKeyStorePath(userHandle: Int): Path {
        return Paths.get("/data/misc/keystore/user_$userHandle")
    }

    @JvmStatic
    fun getKeyStoreFiles(uid: Int, userHandle: Int): List<String> {
        // For any app, the key path is as follows:
        // /data/misc/keystore/user_{user_handle}/{uid}_{KEY_NAME}_{alias}
        val keyStorePath = getKeyStorePath(userHandle)
        val fileNames = keyStorePath.listFileNames()
        val keyStoreFiles = ArrayList<String>()
        val uidStr = "${uid}_"\nfor (fileName in fileNames) {
            if (fileName.startsWith(uidStr) || fileName.startsWith(".$uidStr")) {
                keyStoreFiles.add(fileName)
            }
        }
        return keyStoreFiles
    }

    @JvmStatic
    @Throws(FileNotFoundException::class)
    fun getMasterKey(userHandle: Int): Path {
        return getKeyStorePath(userHandle).findFile(".masterkey")
    }

    @JvmStatic
    @RequiresApi(Build.VERSION_CODES.S)
    fun hasKeyStoreV2(uid: Int): Boolean {
        return Runner.runCommand(arrayOf("su", "$uid", "-c", "keystore_cli_v2", "list"))
            .getOutputAsList().size > 1
    }

    @JvmStatic
    fun hasKeyStoreV1(uid: Int): Boolean {
        val keyStorePath = getKeyStorePath(UserHandleHidden.getUserId(uid))
        val fileNames = keyStorePath.listFileNames()
        val uidStr = "${uid}_"
        for (fileName in fileNames) {
            if (fileName.startsWith(uidStr)) return true
        }
        return false
    }
}
