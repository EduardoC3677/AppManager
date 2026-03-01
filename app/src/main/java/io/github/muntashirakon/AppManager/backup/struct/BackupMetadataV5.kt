// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.struct

import android.annotation.UserIdInt
import android.content.Context
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.format.Formatter
import androidx.annotation.WorkerThread
import aosp.libcore.util.HexEncoding
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.backup.BackupFlags
import io.github.muntashirakon.AppManager.backup.BackupItems
import io.github.muntashirakon.AppManager.backup.BackupUtils.getReadableTarType
import io.github.muntashirakon.AppManager.backup.CryptoUtils
import io.github.muntashirakon.AppManager.backup.MetadataManager
import io.github.muntashirakon.AppManager.crypto.*
import io.github.muntashirakon.AppManager.history.IJsonSerializer
import io.github.muntashirakon.AppManager.misc.VMRuntime
import io.github.muntashirakon.AppManager.utils.*
import io.github.muntashirakon.AppManager.utils.UIUtils.*
import io.github.muntashirakon.io.Paths
import io.github.muntashirakon.util.LocalizedString
import org.json.JSONException
import org.json.JSONObject
import java.util.*

class BackupMetadataV5(val info: Info, val metadata: Metadata) : LocalizedString {
    class Info : IJsonSerializer {
        private var mUuid: String? = null
        @JvmField
        var mBackupItem: BackupItems.BackupItem? = null // This isn't part of the json file and for internal use only

        val version: Int // version
        val backupTime: Long // backup_time
        val flags: BackupFlags // flags
        @UserIdInt
        val userId: Int // user_handle
        @TarUtils.TarType
        val tarType: String // tar_type
        @DigestUtils.Algorithm
        val checksumAlgo: String // checksum_algo
        @CryptoUtils.Mode
        val crypto: String // crypto
        val iv: ByteArray? // iv
        val aes: ByteArray? // aes (encrypted using RSA, for RSA only)
        val keyIds: String? // key_ids

        private var mCrypto: Crypto? = null

        constructor(
            backupTime: Long,
            flags: BackupFlags,
            @UserIdInt userId: Int,
            @TarUtils.TarType tarType: String,
            @DigestUtils.Algorithm checksumAlgo: String,
            @CryptoUtils.Mode crypto: String,
            iv: ByteArray?,
            aes: ByteArray?,
            keyIds: String?
        ) {
            version = MetadataManager.getCurrentBackupMetaVersion()
            this.backupTime = backupTime
            this.flags = flags
            this.userId = userId
            this.tarType = tarType
            this.checksumAlgo = checksumAlgo
            this.crypto = crypto
            this.iv = iv
            this.aes = aes
            this.keyIds = keyIds
            verifyCrypto()
        }

        @Throws(JSONException::class)
        constructor(rootObject: JSONObject) {
            version = rootObject.getInt("version")
            backupTime = rootObject.getLong("backup_time")
            flags = BackupFlags(rootObject.getInt("flags"))
            userId = rootObject.getInt("user_handle")
            tarType = rootObject.getString("tar_type")
            checksumAlgo = rootObject.getString("checksum_algo")
            crypto = rootObject.getString("crypto")
            keyIds = JSONUtils.optString(rootObject, "key_ids")
            val aesKey = JSONUtils.optString(rootObject, "aes")
            aes = if (aesKey != null) HexEncoding.decode(aesKey) else null
            val ivStr = JSONUtils.optString(rootObject, "iv")
            iv = if (ivStr != null) HexEncoding.decode(ivStr) else null
            verifyCrypto()
        }

        fun setBackupItem(backupItem: BackupItems.BackupItem) {
            mBackupItem = backupItem
            mUuid = backupItem.relativeDir
        }

        fun getBackupItem(): BackupItems.BackupItem? {
            return mBackupItem
        }

        fun getRelativeDir(): String? {
            return mUuid
        }

        @Throws(CryptoException::class)
        fun getCrypto(): Crypto {
            if (mCrypto == null) {
                mCrypto = getCryptoInternal()
            }
            return mCrypto!!
        }

        fun getBackupSize(): Long {
            if (mBackupItem == null) return 0L
            return Paths.size(mBackupItem!!.backupPath)
        }

        fun isFrozen(): Boolean {
            return mBackupItem != null && mBackupItem!!.isFrozen
        }

        private fun verifyCrypto() {
            when (crypto) {
                CryptoUtils.MODE_OPEN_PGP -> {
                    Objects.requireNonNull(keyIds)
                    assert(keyIds!!.isNotEmpty())
                }
                CryptoUtils.MODE_RSA, CryptoUtils.MODE_ECC -> {
                    Objects.requireNonNull(aes)
                    assert(aes!!.isNotEmpty())
                    Objects.requireNonNull(iv)
                    assert(iv!!.isNotEmpty())
                }
                CryptoUtils.MODE_AES -> {
                    Objects.requireNonNull(iv)
                    assert(iv!!.isNotEmpty())
                }
                CryptoUtils.MODE_NO_ENCRYPTION -> {}
                else -> {}
            }
        }

        @Throws(CryptoException::class)
        private fun getCryptoInternal(): Crypto {
            return when (crypto) {
                CryptoUtils.MODE_OPEN_PGP -> {
                    Objects.requireNonNull(keyIds)
                    OpenPGPCrypto(ContextUtils.getContext(), keyIds!!)
                }
                CryptoUtils.MODE_AES -> {
                    Objects.requireNonNull(iv)
                    val aesCrypto = AESCrypto(iv!!)
                    if (version < 4) {
                        // Old backups use 32 bit MAC
                        aesCrypto.setMacSizeBits(AESCrypto.MAC_SIZE_BITS_OLD)
                    }
                    aesCrypto
                }
                CryptoUtils.MODE_RSA -> {
                    Objects.requireNonNull(iv)
                    Objects.requireNonNull(aes)
                    val rsaCrypto = RSACrypto(iv!!, aes!!)
                    if (version < 4) {
                        // Old backups use 32 bit MAC
                        rsaCrypto.setMacSizeBits(AESCrypto.MAC_SIZE_BITS_OLD)
                    }
                    rsaCrypto
                }
                CryptoUtils.MODE_ECC -> {
                    Objects.requireNonNull(iv)
                    Objects.requireNonNull(aes)
                    val eccCrypto = ECCCrypto(iv!!, aes!!)
                    if (version < 4) {
                        // Old backups use 32 bit MAC
                        eccCrypto.setMacSizeBits(AESCrypto.MAC_SIZE_BITS_OLD)
                    }
                    eccCrypto
                }
                CryptoUtils.MODE_NO_ENCRYPTION -> DummyCrypto()
                else -> DummyCrypto()
            }
        }

        @Throws(JSONException::class)
        override fun serializeToJson(): JSONObject {
            val rootObject = JSONObject()
            rootObject.put("backup_time", backupTime)
            rootObject.put("checksum_algo", checksumAlgo)
            rootObject.put("crypto", crypto)
            rootObject.put("key_ids", keyIds)
            rootObject.put("iv", if (iv == null) null else HexEncoding.encodeToString(iv))
            rootObject.put("aes", if (aes == null) null else HexEncoding.encodeToString(aes))
            rootObject.put("version", version)
            rootObject.put("flags", flags.flags)
            rootObject.put("user_handle", userId)
            rootObject.put("tar_type", tarType)
            return rootObject
        }
    }

    class Metadata : IJsonSerializer {
        val version: Int // version
        val backupName: String? // backup_name
        var hasRules: Boolean = false // has_rules
        lateinit var label: String // label
        lateinit var packageName: String // package_name
        lateinit var versionName: String // version_name
        var versionCode: Long = 0 // version_code
        var dataDirs: Array<String>? = null // data_dirs
        var isSystem: Boolean = false // is_system
        var isSplitApk: Boolean = false // is_split_apk
        var splitConfigs: Array<String>? = null // split_configs
        lateinit var apkName: String // apk_name
        var instructionSet: String = VMRuntime.getInstructionSet(Build.SUPPORTED_ABIS[0]) // instruction_set
        var keyStore: Boolean = false // key_store
        var installer: String? = null // installer

        constructor(backupName: String?) {
            version = MetadataManager.getCurrentBackupMetaVersion()
            this.backupName = backupName
        }

        constructor(metadata: Metadata) {
            version = metadata.version
            backupName = metadata.backupName
            label = metadata.label
            packageName = metadata.packageName
            versionName = metadata.versionName
            versionCode = metadata.versionCode
            dataDirs = metadata.dataDirs?.clone()
            isSystem = metadata.isSystem
            isSplitApk = metadata.isSplitApk
            splitConfigs = metadata.splitConfigs?.clone()
            hasRules = metadata.hasRules
            apkName = metadata.apkName
            instructionSet = metadata.instructionSet
            keyStore = metadata.keyStore
            installer = metadata.installer
        }

        @Throws(JSONException::class)
        constructor(rootObject: JSONObject) {
            version = rootObject.getInt("version")
            backupName = JSONUtils.optString(rootObject, "backup_name")
            label = rootObject.getString("label")
            packageName = rootObject.getString("package_name")
            versionName = rootObject.getString("version_name")
            versionCode = rootObject.getLong("version_code")
            dataDirs = JSONUtils.getArray(String::class.java, rootObject.getJSONArray("data_dirs"))
            isSystem = rootObject.getBoolean("is_system")
            isSplitApk = rootObject.getBoolean("is_split_apk")
            splitConfigs = JSONUtils.getArray(String::class.java, rootObject.getJSONArray("split_configs"))
            hasRules = rootObject.getBoolean("has_rules")
            apkName = rootObject.getString("apk_name")
            instructionSet = rootObject.getString("instruction_set")
            keyStore = rootObject.getBoolean("key_store")
            installer = JSONUtils.optString(rootObject, "installer")
        }

        @Throws(JSONException::class)
        override fun serializeToJson(): JSONObject {
            val rootObject = JSONObject()
            rootObject.put("version", version)
            rootObject.put("backup_name", backupName)
            rootObject.put("label", label)
            rootObject.put("package_name", packageName)
            rootObject.put("version_name", versionName)
            rootObject.put("version_code", versionCode)
            rootObject.put("data_dirs", JSONUtils.getJSONArray(dataDirs))
            rootObject.put("is_system", isSystem)
            rootObject.put("is_split_apk", isSplitApk)
            rootObject.put("split_configs", JSONUtils.getJSONArray(splitConfigs))
            rootObject.put("has_rules", hasRules)
            rootObject.put("apk_name", apkName)
            rootObject.put("instruction_set", instructionSet)
            rootObject.put("key_store", keyStore)
            rootObject.put("installer", installer)
            return rootObject
        }
    }

    fun isBaseBackup(): Boolean {
        return TextUtils.isEmpty(metadata.backupName)
    }

    @WorkerThread
    override fun toLocalizedString(context: Context): CharSequence {
        val titleText: CharSequence = if (isBaseBackup()) context.getText(R.string.base_backup) else metadata.backupName!!

        val subtitleText = StringBuilder()
            .append(DateUtils.formatDateTime(context, info.backupTime))
            .append(", ")
            .append(info.flags.toLocalisedString(context))
            .append(", ")
            .append(context.getString(R.string.version)).append(LangUtils.getSeparatorString()).append(metadata.versionName)
            .append(", ")
            .append(context.getString(R.string.user_id)).append(LangUtils.getSeparatorString()).append(info.userId)
        if (info.crypto == CryptoUtils.MODE_NO_ENCRYPTION) {
            subtitleText.append(", ").append(context.getString(R.string.no_encryption))
        } else {
            subtitleText.append(", ").append(
                context.getString(
                    R.string.pgp_aes_rsa_encrypted,
                    info.crypto.uppercase(Locale.ROOT)
                )
            )
        }
        subtitleText.append(", ").append(context.getString(R.string.gz_bz2_compressed, getReadableTarType(info.tarType)))
        subtitleText.append(", ")
            .append(context.getString(R.string.size)).append(LangUtils.getSeparatorString()).append(
                Formatter.formatFileSize(context, info.getBackupSize())
            )

        if (info.isFrozen()) {
            subtitleText.append(", ").append(context.getText(R.string.frozen))
        }

        return SpannableStringBuilder(getTitleText(context, titleText)).append("
")
            .append(getSmallerText(getSecondaryText(context, subtitleText)))
    }
}
