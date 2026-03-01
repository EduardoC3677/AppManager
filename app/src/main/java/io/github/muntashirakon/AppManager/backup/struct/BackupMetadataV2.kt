// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.struct

import android.os.Build
import aosp.libcore.util.HexEncoding
import io.github.muntashirakon.AppManager.backup.BackupFlags
import io.github.muntashirakon.AppManager.backup.BackupItems
import io.github.muntashirakon.AppManager.backup.CryptoUtils
import io.github.muntashirakon.AppManager.backup.MetadataManager
import io.github.muntashirakon.AppManager.history.IJsonSerializer
import io.github.muntashirakon.AppManager.misc.VMRuntime
import io.github.muntashirakon.AppManager.utils.DigestUtils
import io.github.muntashirakon.AppManager.utils.JSONUtils
import io.github.muntashirakon.AppManager.utils.TarUtils
import org.json.JSONException
import org.json.JSONObject

/**
 * For an extended documentation, see https://github.com/MuntashirAkon/AppManager/issues/30
 * All the attributes must be non-null
 */
open class BackupMetadataV2 : IJsonSerializer {
    @JvmField
    var backupName: String? = null // This isn't part of the json file and for internal use only
    @JvmField
    var backupItem: BackupItems.BackupItem? = null // This isn't part of the json file and for internal use only

    lateinit var label: String // label
    lateinit var packageName: String // package_name
    lateinit var versionName: String // version_name
    var versionCode: Long = 0 // version_code
    lateinit var dataDirs: Array<String> // data_dirs
    var isSystem: Boolean = false // is_system
    var isSplitApk: Boolean = false // is_split_apk
    lateinit var splitConfigs: Array<String> // split_configs
    var hasRules: Boolean = false // has_rules
    var backupTime: Long = 0 // backup_time
    @DigestUtils.Algorithm
    var checksumAlgo: String = DigestUtils.SHA_256 // checksum_algo
    @CryptoUtils.Mode
    lateinit var crypto: String // crypto
    var iv: ByteArray? = null // iv
    var aes: ByteArray? = null // aes (encrypted using RSA/ECC, for RSA/ECC only)
    var keyIds: String? = null // key_ids

    /**
     * Metadata version.
     * <ul>
     *     <li>{@code 1} - Alpha version, no longer supported</li>
     *     <li>{@code 2} - Beta version (v2.5.2x), permissions aren't preserved (special action needed)</li>
     *     <li>{@code 3} - From v2.6.x to v3.0.2 and v3.1.0-alpha01, permissions are preserved, AES GCM MAC size is 32 bits</li>
     *     <li>{@code 4} - Since v3.0.3 and v3.1.0-alpha02, AES GCM MAC size is 128 bits</li>
     * </ul>
     */
    var version: Int = 0 // version
    lateinit var apkName: String // apk_name
    var instructionSet: String = VMRuntime.getInstructionSet(Build.SUPPORTED_ABIS[0]) // instruction_set
    lateinit var flags: BackupFlags // flags
    var userId: Int = 0 // user_handle
    @TarUtils.TarType
    lateinit var tarType: String // tar_type
    var keyStore: Boolean = false // key_store
    lateinit var installer: String // installer

    constructor() {
        version = MetadataManager.getCurrentBackupMetaVersion()
    }

    constructor(metadata: BackupMetadataV2) {
        backupName = metadata.backupName
        backupItem = metadata.backupItem

        label = metadata.label
        packageName = metadata.packageName
        versionName = metadata.versionName
        versionCode = metadata.versionCode
        dataDirs = metadata.dataDirs.clone()
        isSystem = metadata.isSystem
        isSplitApk = metadata.isSplitApk
        splitConfigs = metadata.splitConfigs.clone()
        hasRules = metadata.hasRules
        backupTime = metadata.backupTime
        checksumAlgo = metadata.checksumAlgo
        crypto = metadata.crypto
        iv = metadata.iv?.clone()
        aes = metadata.aes?.clone()
        keyIds = metadata.keyIds
        version = metadata.version
        apkName = metadata.apkName
        instructionSet = metadata.instructionSet
        flags = BackupFlags(metadata.flags.flags)
        userId = metadata.userId
        tarType = metadata.tarType
        keyStore = metadata.keyStore
        installer = metadata.installer
    }

    @Throws(JSONException::class)
    override fun serializeToJson(): JSONObject {
        val rootObject = JSONObject()
        rootObject.put("label", label)
        rootObject.put("package_name", packageName)
        rootObject.put("version_name", versionName)
        rootObject.put("version_code", versionCode)
        rootObject.put("data_dirs", JSONUtils.getJSONArray(dataDirs))
        rootObject.put("is_system", isSystem)
        rootObject.put("is_split_apk", isSplitApk)
        rootObject.put("split_configs", JSONUtils.getJSONArray(splitConfigs))
        rootObject.put("has_rules", hasRules)
        rootObject.put("backup_time", backupTime)
        rootObject.put("checksum_algo", checksumAlgo)
        rootObject.put("crypto", crypto)
        rootObject.put("key_ids", keyIds)
        rootObject.put("iv", if (iv == null) null else HexEncoding.encodeToString(iv!!))
        rootObject.put("aes", if (aes == null) null else HexEncoding.encodeToString(aes!!))
        rootObject.put("version", version)
        rootObject.put("apk_name", apkName)
        rootObject.put("instruction_set", instructionSet)
        rootObject.put("flags", flags.flags)
        rootObject.put("user_handle", userId)
        rootObject.put("tar_type", tarType)
        rootObject.put("key_store", keyStore)
        rootObject.put("installer", installer)
        return rootObject
    }
}
