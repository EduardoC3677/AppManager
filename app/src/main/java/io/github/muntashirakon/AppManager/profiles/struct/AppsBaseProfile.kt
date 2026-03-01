// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles.struct

import android.app.AppOpsManager
import android.content.Context
import android.text.TextUtils
import androidx.annotation.CallSuper
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.backup.BackupFlags
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager
import io.github.muntashirakon.AppManager.batchops.struct.*
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.profiles.ProfileLogger
import io.github.muntashirakon.AppManager.progress.ProgressHandler
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.utils.JSONUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*

abstract class AppsBaseProfile : BaseProfile {
    class BackupInfo {
        var name: String? = null
        @BackupFlags.BackupFlag
        var flags: Int = Prefs.BackupRestore.getBackupFlags()

        constructor()
        constructor(backupInfo: BackupInfo) {
            name = backupInfo.name
            flags = backupInfo.flags
        }
    }

    var version: Int = 1
    var allowRoutine: Boolean = true
    var users: IntArray? = null
    var comment: String? = null
    var components: Array<String>? = null
    var appOps: IntArray? = null
    var permissions: Array<String>? = null
    var backupData: BackupInfo? = null
    var exportRules: Int? = null
    var freeze: Boolean = false
    var forceStop: Boolean = false
    var clearCache: Boolean = false
    var clearData: Boolean = false
    var blockTrackers: Boolean = false
    var saveApk: Boolean = false

    protected constructor(profileId: String, profileName: String, profileType: Int) : super(profileId, profileName, profileType)

    protected constructor(profileId: String, profileName: String, profile: AppsBaseProfile) : super(profileId, profileName, profile.type) {
        version = profile.version
        allowRoutine = profile.allowRoutine
        state = profile.state
        users = profile.users?.clone()
        comment = profile.comment
        components = profile.components?.clone()
        appOps = profile.appOps?.clone()
        permissions = profile.permissions?.clone()
        backupData = profile.backupData?.let { BackupInfo(it) }
        exportRules = profile.exportRules
        freeze = profile.freeze
        forceStop = profile.forceStop
        clearCache = profile.clearCache
        clearData = profile.clearData
        blockTrackers = profile.blockTrackers
        saveApk = profile.saveApk
    }

    protected fun apply(packageList: List<String>, assocUsers: List<Int>, state: String, logger: ProfileLogger?, progressHandler: ProgressHandler?): ProfileApplierResult {
        progressHandler?.postUpdate(calculateMaxProgress(packageList), 0)
        val profileApplierResult = ProfileApplierResult()
        val batchOpsManager = BatchOpsManager(logger)
        var result: BatchOpsManager.Result

        components?.let { comps ->
            log(logger, "====> Started block/unblock components. State: $state")
            val op = when (state) {
                STATE_ON -> BatchOpsManager.OP_BLOCK_COMPONENTS
                STATE_OFF -> BatchOpsManager.OP_UNBLOCK_COMPONENTS
                else -> BatchOpsManager.OP_NONE
            }
            val info = BatchOpsManager.BatchOpsInfo.getInstance(op, packageList, assocUsers, BatchComponentOptions(comps))
            result = batchOpsManager.performOp(info, progressHandler)
            if (!result.isSuccessful) Log.d(TAG, "Failed packages: $result")
        } ?: Log.d(TAG, "Skipped components.")

        appOps?.let { ops ->
            log(logger, "====> Started ignore/default components. State: $state")
            val mode = if (state == STATE_ON) AppOpsManager.MODE_IGNORED else AppOpsManager.MODE_DEFAULT
            val info = BatchOpsManager.BatchOpsInfo.getInstance(BatchOpsManager.OP_SET_APP_OPS, packageList, assocUsers, BatchAppOpsOptions(ops, mode))
            result = batchOpsManager.performOp(info, progressHandler)
            if (!result.isSuccessful) Log.d(TAG, "Failed packages: $result")
        } ?: Log.d(TAG, "Skipped app ops.")

        permissions?.let { perms ->
            log(logger, "====> Started grant/revoke permissions.")
            val op = when (state) {
                STATE_ON -> BatchOpsManager.OP_REVOKE_PERMISSIONS
                STATE_OFF -> BatchOpsManager.OP_GRANT_PERMISSIONS
                else -> BatchOpsManager.OP_NONE
            }
            val info = BatchOpsManager.BatchOpsInfo.getInstance(op, packageList, assocUsers, BatchPermissionOptions(perms))
            result = batchOpsManager.performOp(info, progressHandler)
            if (!result.isSuccessful) Log.d(TAG, "Failed packages: $result")
        } ?: Log.d(TAG, "Skipped permissions.")

        exportRules?.let {
            log(logger, "====> Not implemented export rules.")
        } ?: Log.d(TAG, "Skipped export rules.")

        if (freeze) {
            log(logger, "====> Started freeze/unfreeze. State: $state")
            val op = when (state) {
                STATE_ON -> BatchOpsManager.OP_FREEZE
                STATE_OFF -> BatchOpsManager.OP_UNFREEZE
                else -> BatchOpsManager.OP_NONE
            }
            val info = BatchOpsManager.BatchOpsInfo.getInstance(op, packageList, assocUsers, null)
            result = batchOpsManager.performOp(info, progressHandler)
            if (!result.isSuccessful) Log.d(TAG, "Failed packages: $result")
        } else Log.d(TAG, "Skipped disable/enable.")

        if (forceStop) {
            log(logger, "====> Started force-stop.")
            val info = BatchOpsManager.BatchOpsInfo.getInstance(BatchOpsManager.OP_FORCE_STOP, packageList, assocUsers, null)
            result = batchOpsManager.performOp(info, progressHandler)
            if (!result.isSuccessful) Log.d(TAG, "Failed packages: $result")
        } else Log.d(TAG, "Skipped force stop.")

        if (clearCache) {
            log(logger, "====> Started clear cache.")
            val info = BatchOpsManager.BatchOpsInfo.getInstance(BatchOpsManager.OP_CLEAR_CACHE, packageList, assocUsers, null)
            result = batchOpsManager.performOp(info, progressHandler)
            if (!result.isSuccessful) Log.d(TAG, "Failed packages: $result")
        } else Log.d(TAG, "Skipped clear cache.")

        if (clearData) {
            log(logger, "====> Started clear data.")
            val info = BatchOpsManager.BatchOpsInfo.getInstance(BatchOpsManager.OP_CLEAR_DATA, packageList, assocUsers, null)
            result = batchOpsManager.performOp(info, progressHandler)
            if (!result.isSuccessful) Log.d(TAG, "Failed packages: $result")
        } else Log.d(TAG, "Skipped clear data.")

        if (blockTrackers) {
            log(logger, "====> Started block trackers. State: $state")
            val op = when (state) {
                STATE_ON -> BatchOpsManager.OP_BLOCK_TRACKERS
                STATE_OFF -> BatchOpsManager.OP_UNBLOCK_TRACKERS
                else -> BatchOpsManager.OP_NONE
            }
            val info = BatchOpsManager.BatchOpsInfo.getInstance(op, packageList, assocUsers, null)
            result = batchOpsManager.performOp(info, progressHandler)
            if (!result.isSuccessful) Log.d(TAG, "Failed packages: $result")
        } else Log.d(TAG, "Skipped block trackers.")

        if (saveApk) {
            log(logger, "====> Started backup apk.")
            val info = BatchOpsManager.BatchOpsInfo.getInstance(BatchOpsManager.OP_BACKUP_APK, packageList, assocUsers, null)
            result = batchOpsManager.performOp(info, progressHandler)
            if (!result.isSuccessful) Log.d(TAG, "Failed packages: $result")
        } else Log.d(TAG, "Skipped backup apk.")

        backupData?.let { bInfo ->
            log(logger, "====> Started backup/restore.")
            val bFlags = BackupFlags(bInfo.flags)
            val backupNames = if (bFlags.backupMultiple() && bInfo.name != null) arrayOf(bInfo.name!!) else null
            bFlags.addFlag(BackupFlags.BACKUP_CUSTOM_USERS)
            val op = when (state) {
                STATE_ON -> BatchOpsManager.OP_BACKUP
                STATE_OFF -> BatchOpsManager.OP_RESTORE_BACKUP
                else -> BatchOpsManager.OP_NONE
            }
            val info = BatchOpsManager.BatchOpsInfo.getInstance(op, packageList, assocUsers, BatchBackupOptions(bFlags.flags, backupNames, null))
            result = batchOpsManager.performOp(info, progressHandler)
            profileApplierResult.setRequiresRestart(profileApplierResult.requiresRestart() or result.requiresRestart())
            if (!result.isSuccessful) Log.d(TAG, "Failed packages: $result")
        } ?: Log.d(TAG, "Skipped backup/restore.")

        batchOpsManager.conclude()
        return profileApplierResult
    }

    private fun calculateMaxProgress(userPackagePairs: List<String>): Int {
        val packageCount = userPackagePairs.size
        var opCount = 0
        if (components != null) opCount++
        if (appOps != null) opCount++
        if (permissions != null) opCount++
        if (freeze) opCount++
        if (forceStop) opCount++
        if (clearCache) opCount++
        if (clearData) opCount++
        if (blockTrackers) opCount++
        if (saveApk) opCount++
        if (backupData != null) opCount++
        return opCount * packageCount
    }

    private fun log(logger: ProfileLogger?, message: String?) {
        logger?.println(message)
    }

    private fun getLocalisedSummaryOrComment(context: Context): List<String> {
        comment?.let { return listOf(it) }
        val summaries = mutableListOf<String>()
        if (components != null) summaries.add(context.getString(R.string.components))
        if (appOps != null) summaries.add(context.getString(R.string.app_ops))
        if (permissions != null) summaries.add(context.getString(R.string.permissions))
        if (backupData != null) summaries.add(context.getString(R.string.backup_restore))
        if (exportRules != null) summaries.add(context.getString(R.string.blocking_rules))
        if (freeze) summaries.add(context.getString(R.string.freeze))
        if (forceStop) summaries.add(context.getString(R.string.force_stop))
        if (clearCache) summaries.add(context.getString(R.string.clear_cache))
        if (clearData) summaries.add(context.getString(R.string.clear_data))
        if (blockTrackers) summaries.add(context.getString(R.string.trackers))
        if (saveApk) summaries.add(context.getString(R.string.save_apk))
        return summaries
    }

    override fun toLocalizedString(context: Context): CharSequence {
        val summaries = getLocalisedSummaryOrComment(context)
        return if (summaries.isEmpty()) context.getString(R.string.no_configurations) else TextUtils.join(", ", summaries)
    }

    @CallSuper
    @Throws(JSONException::class)
    override fun serializeToJson(): JSONObject {
        return super.serializeToJson().apply {
            put("version", version)
            if (!allowRoutine) put("allow_routine", false)
            put("comment", comment)
            put("users", JSONUtils.getJSONArray(users))
            put("components", JSONUtils.getJSONArray(components))
            put("app_ops", JSONUtils.getJSONArray(appOps))
            put("permissions", JSONUtils.getJSONArray(permissions))
            backupData?.let {
                put("backup_data", JSONObject().apply {
                    put("name", it.name)
                    put("flags", it.flags)
                })
            }
            put("export_rules", exportRules)
            val jsonArray = JSONArray()
            if (freeze) jsonArray.put("freeze")
            if (forceStop) jsonArray.put("force_stop")
            if (clearCache) jsonArray.put("clear_cache")
            if (clearData) jsonArray.put("clear_data")
            if (blockTrackers) jsonArray.put("block_trackers")
            if (saveApk) jsonArray.put("save_apk")
            if (jsonArray.length() > 0) put("misc", jsonArray)
        }
    }

    @Throws(JSONException::class)
    protected constructor(profileObj: JSONObject) : super(profileObj) {
        comment = JSONUtils.getString(profileObj, "comment", null)
        version = profileObj.getInt("version")
        allowRoutine = profileObj.optBoolean("allow_routine", true)
        users = try { JSONUtils.getIntArray(profileObj.getJSONArray("users")) } catch (e: JSONException) { null }
        components = try { JSONUtils.getArray(String::class.java, profileObj.getJSONArray("components")) } catch (e: JSONException) { null }
        appOps = try { JSONUtils.getIntArray(profileObj.getJSONArray("app_ops")) } catch (e: JSONException) { null }
        permissions = try { JSONUtils.getArray(String::class.java, profileObj.getJSONArray("permissions")) } catch (e: JSONException) { null }
        backupData = try {
            val bInfo = profileObj.getJSONObject("backup_data")
            BackupInfo().apply {
                name = JSONUtils.getString(bInfo, "name", null)
                flags = bInfo.getInt("flags")
            }
        } catch (e: JSONException) { null }
        exportRules = JSONUtils.getIntOrNull(profileObj, "export_rules")
        try {
            val miscConfig = JSONUtils.getArray(profileObj.getJSONArray("misc"))
            freeze = miscConfig.contains("disable") || miscConfig.contains("freeze")
            forceStop = miscConfig.contains("force_stop")
            clearCache = miscConfig.contains("clear_cache")
            clearData = miscConfig.contains("clear_data")
            blockTrackers = miscConfig.contains("block_trackers")
            saveApk = miscConfig.contains("save_apk")
        } catch (ignore: Exception) {}
    }

    companion object {
        private val TAG = AppsBaseProfile::class.java.simpleName
    }
}
