// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup

import android.text.TextUtils
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5
import io.github.muntashirakon.AppManager.crypto.CryptoException
import io.github.muntashirakon.AppManager.utils.DigestUtils
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

object MetadataManager {
    @JvmField
    val TAG: String = MetadataManager::class.java.simpleName
    private var currentBackupMetaVersion = 5

    const val META_V2_FILE = "meta_v2.am.json"

    // New scheme
    const val INFO_V5_FILE = "info_v5.am.json" // unencrypted
    const val META_V5_FILE = "meta_v5.am.json" // encrypted

    @JvmStatic
    @VisibleForTesting
    fun setCurrentBackupMetaVersion(version: Int) {
        currentBackupMetaVersion = version
    }

    @JvmStatic
    fun getCurrentBackupMetaVersion(): Int {
        return currentBackupMetaVersion
    }

    @JvmStatic
    @WorkerThread
    @Throws(IOException::class)
    fun readInfo(backupItem: BackupItems.BackupItem): BackupMetadataV5.Info {
        val v5AndUp = backupItem.isV5AndUp()
        val infoFile = if (v5AndUp) backupItem.infoFile else backupItem.metadataV2File
        val infoString = infoFile.getContentAsString()
        val jsonObject: JSONObject
        if (TextUtils.isEmpty(infoString)) {
            throw IOException("Empty JSON string for path $infoFile")
        }
        return try {
            jsonObject = JSONObject(infoString!!)
            val info = BackupMetadataV5.Info(jsonObject)
            info.setBackupItem(backupItem)
            info
        } catch (e: JSONException) {
            throw IOException(e.message + " for path " + infoFile, e)
        }
    }

    @JvmStatic
    @WorkerThread
    @Throws(IOException::class)
    fun readMetadata(backupItem: BackupItems.BackupItem): BackupMetadataV5 {
        val info = readInfo(backupItem)
        return readMetadata(backupItem, info)
    }

    @JvmStatic
    @WorkerThread
    @Throws(IOException::class)
    fun readMetadata(
        backupItem: BackupItems.BackupItem,
        backupInfo: BackupMetadataV5.Info
    ): BackupMetadataV5 {
        val v5AndUp = backupItem.isV5AndUp()
        if (v5AndUp) {
            // Need to setup crypto in order to decrypt meta_v5.am.json
            setCrypto(backupItem, backupInfo)
        }
        val metadataFile = if (v5AndUp) backupItem.getMetadataV5File(true) else backupItem.metadataV2File
        val metadataString = metadataFile.getContentAsString()
        val jsonObject: JSONObject
        if (TextUtils.isEmpty(metadataString)) {
            throw IOException("Empty JSON string for path $metadataFile")
        }
        return try {
            jsonObject = JSONObject(metadataString!!)
            if (!v5AndUp) {
                // Meta is a subset of meta_v2.am.json except for backup_name
                jsonObject.put("backup_name", backupItem.backupName)
            }
            val metadata = BackupMetadataV5.Metadata(jsonObject)
            BackupMetadataV5(backupInfo, metadata)
        } catch (e: JSONException) {
            throw IOException(e.message + " for path " + metadataFile, e)
        }
    }

    @JvmStatic
    @WorkerThread
    @Throws(IOException::class)
    fun writeMetadata(
        backupMetadata: BackupMetadataV5,
        backupItem: BackupItems.BackupItem
    ): Map<String, String> {
        return try {
            val checksums = mutableMapOf<String, String>()
            val v5AndUp = currentBackupMetaVersion >= 5
            val infoFile = if (v5AndUp) backupItem.infoFile else backupItem.metadataV2File
            val infoJson = backupMetadata.info.serializeToJson()
            if (!v5AndUp) {
                // Info is meta_v2.am.json
                val metadataJson = backupMetadata.metadata.serializeToJson()
                val keys = metadataJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    infoJson.put(key, metadataJson.get(key))
                }
            }
            infoFile.openOutputStream().use { os ->
                os.write(infoJson.toString(4).toByteArray())
            }
            checksums[infoFile.getName()] = DigestUtils.getHexDigest(backupMetadata.info.checksumAlgo, infoFile)

            if (v5AndUp) {
                val metadataFile = backupItem.getMetadataV5File(false)
                val metadataJson = backupMetadata.metadata.serializeToJson()
                metadataFile.openOutputStream().use { os ->
                    os.write(metadataJson.toString(4).toByteArray())
                }
                val encryptedMetadata = backupItem.encrypt(arrayOf(metadataFile))[0]
                checksums[encryptedMetadata.getName()] =
                    DigestUtils.getHexDigest(backupMetadata.info.checksumAlgo, encryptedMetadata)
            }
            checksums
        } catch (e: JSONException) {
            throw IOException(e)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    private fun setCrypto(backupItem: BackupItems.BackupItem, info: BackupMetadataV5.Info) {
        if (!CryptoUtils.isAvailable(info.crypto)) {
            throw IOException("Crypto " + info.crypto + " is not available.")
        }
        try {
            backupItem.setCrypto(info.getCrypto())
        } catch (e: CryptoException) {
            throw IOException(e)
        }
    }
}
