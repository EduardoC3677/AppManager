// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops

import android.content.Context
import android.content.Intent
import androidx.annotation.StringDef
import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.github.muntashirakon.AppManager.apk.installer.ApkQueueItem
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerService
import io.github.muntashirakon.AppManager.batchops.BatchOpsService
import io.github.muntashirakon.AppManager.batchops.BatchQueueItem
import io.github.muntashirakon.AppManager.db.AppsDb
import io.github.muntashirakon.AppManager.db.entity.OpHistory
import io.github.muntashirakon.AppManager.history.IJsonSerializer
import io.github.muntashirakon.AppManager.intercept.IntentCompat
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.profiles.ProfileApplierService
import io.github.muntashirakon.AppManager.profiles.ProfileQueueItem
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

object OpHistoryManager {
    @JvmField
    val TAG: String = OpHistoryManager::class.java.simpleName

    const val HISTORY_TYPE_BATCH_OPS = "batch_ops"
    const val HISTORY_TYPE_INSTALLER = "installer"
    const val HISTORY_TYPE_PROFILE = "profile"

    @Retention(RetentionPolicy.SOURCE)
    @StringDef(HISTORY_TYPE_BATCH_OPS, HISTORY_TYPE_INSTALLER, HISTORY_TYPE_PROFILE)
    annotation class HistoryType

    const val STATUS_SUCCESS = "success"
    const val STATUS_FAILURE = "failure"

    @Retention(RetentionPolicy.SOURCE)
    @StringDef(STATUS_SUCCESS, STATUS_FAILURE)
    annotation class Status

    private val sHistoryAddedLiveData = MutableLiveData<OpHistory>()

    @JvmStatic
    fun getHistoryAddedLiveData(): LiveData<OpHistory> {
        return sHistoryAddedLiveData
    }

    @JvmStatic
    @WorkerThread
    fun addHistoryItem(@HistoryType historyType: String,
                       item: IJsonSerializer,
                       success: Boolean): Long {
        return try {
            val opHistory = OpHistory().apply {
                type = historyType
                execTime = System.currentTimeMillis()
                serializedData = item.serializeToJson().toString()
                status = if (success) STATUS_SUCCESS else STATUS_FAILURE
                serializedExtra = null
            }
            val id = runBlocking { AppsDb.getInstance().opHistoryDao().insert(opHistory) }
            opHistory.id = id
            sHistoryAddedLiveData.postValue(opHistory)
            id
        } catch (e: JSONException) {
            Log.e(TAG, "Could not serialize " + item.javaClass, e)
            -1
        }
    }

    @JvmStatic
    @WorkerThread
    fun getAllHistoryItems(): List<OpHistory> {
        return runBlocking { AppsDb.getInstance().opHistoryDao().getAll() }
    }

    @JvmStatic
    @WorkerThread
    fun clearAllHistory() {
        runBlocking { AppsDb.getInstance().opHistoryDao().deleteAll() }
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun getExecutableIntent(context: Context, item: OpHistoryItem): Intent {
        when (item.type) {
            HISTORY_TYPE_BATCH_OPS -> {
                val batchQueueItem = BatchQueueItem.DESERIALIZER.deserialize(item.jsonData)
                return BatchOpsService.getServiceIntent(context, batchQueueItem)
            }
            HISTORY_TYPE_INSTALLER -> {
                val apkQueueItem = ApkQueueItem.DESERIALIZER.deserialize(item.jsonData)
                val intent = Intent(context, PackageInstallerService::class.java)
                IntentCompat.putWrappedParcelableExtra(intent, PackageInstallerService.EXTRA_QUEUE_ITEM, apkQueueItem)
                return intent
            }
            HISTORY_TYPE_PROFILE -> {
                val profileQueueItem = ProfileQueueItem.DESERIALIZER.deserialize(item.jsonData)
                return ProfileApplierService.getIntent(context, profileQueueItem, true)
            }
        }
        throw IllegalStateException("Invalid type: " + item.type)
    }
}
