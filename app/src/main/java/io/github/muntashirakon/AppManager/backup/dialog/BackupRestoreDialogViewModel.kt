// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.dialog

import android.app.Application
import android.os.UserHandleHidden
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.github.muntashirakon.AppManager.backup.BackupFlags
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager
import io.github.muntashirakon.AppManager.db.entity.App
import io.github.muntashirakon.AppManager.db.entity.Backup
import io.github.muntashirakon.AppManager.db.utils.AppDb
import io.github.muntashirakon.AppManager.types.UserPackagePair
import io.github.muntashirakon.AppManager.users.UserInfo
import io.github.muntashirakon.AppManager.users.Users
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import java.io.IOException
import java.util.*
import java.util.concurrent.Future

class BackupRestoreDialogViewModel(application: Application) : AndroidViewModel(application) {
    class OperationInfo {
        @BackupRestoreDialogFragment.ActionMode
        var mode: Int = 0
        @BatchOpsManager.OpType
        var op: Int = 0
        @BackupFlags.BackupFlag
        var flags: Int = 0
        var backupNames: Array<String>? = null
        var uuids: Array<String>? = null
        var selectedUsers: IntArray? = null

        // Others
        var handleMultipleUsers: Boolean = true
        var userInfoList: List<UserInfo>? = null

        var packageList: ArrayList<String>? = null
        var userIdListMappedToPackageList: ArrayList<Int>? = null
    }

    var worstBackupFlag: Int = 0
    private var mPreferredUsersForBackup: IntArray? = null
    private var mPreferredUsersForRestore: IntArray? = null
    private var mAllowCustomUsersInBackup: Boolean = true
    private var mProcessPackageFuture: Future<*>? = null
    private var mHandleUsersFuture: Future<*>? = null

    private val mBackupInfoList = mutableListOf<BackupInfo>()
    private val mAppsWithoutBackups = mutableSetOf<CharSequence>()
    private val mUninstalledApps = mutableSetOf<CharSequence>()
    private val mUserSelectionLiveData = MutableLiveData<OperationInfo>()
    private val mBackupOperationLiveData = MutableLiveData<OperationInfo>()
    private val mBackupInfoStateLiveData = MutableLiveData<Int>()

    override fun onCleared() {
        mProcessPackageFuture?.cancel(true)
        mHandleUsersFuture?.cancel(true)
        super.onCleared()
    }

    val backupInfoStateLiveData: LiveData<Int>
        get() = mBackupInfoStateLiveData

    val backupOperationLiveData: LiveData<OperationInfo>
        get() = mBackupOperationLiveData

    val userSelectionLiveData: MutableLiveData<OperationInfo>
        get() = mUserSelectionLiveData

    val backupInfo: BackupInfo
        get() = mBackupInfoList[0]

    val appsWithoutBackups: Set<CharSequence>
        get() = mAppsWithoutBackups

    val uninstalledApps: Set<CharSequence>
        get() = mUninstalledApps

    fun allowCustomUsersInBackup(): Boolean {
        return mAllowCustomUsersInBackup
    }

    fun setPreferredUserForRestore(userId: Int) {
        mPreferredUsersForRestore = intArrayOf(userId)
    }

    fun prepareForOperation(operationInfo: OperationInfo) {
        // Prepare for operation logic simplified
        mBackupOperationLiveData.postValue(operationInfo)
    }

    @AnyThread
    fun setPackageList(userPackagePairs: List<UserPackagePair>) {
        if (mProcessPackageFuture != null) return
        mProcessPackageFuture = ThreadUtils.runOnWorkerThread { processPackages(userPackagePairs) }
    }

    @WorkerThread
    private fun processPackages(userPackagePairs: List<UserPackagePair>) {
        val appDb = AppDb()
        val backupInfosMap = LinkedHashMap<String, BackupInfo>()
        for (pair in userPackagePairs) {
            var backupInfo = backupInfosMap[pair.packageName]
            if (backupInfo == null) {
                backupInfo = BackupInfo(pair.packageName, pair.userId)
                backupInfosMap[pair.packageName] = backupInfo
            } else {
                backupInfo.userIds.add(pair.userId)
            }
        }
        val currentUserId = UserHandleHidden.myUserId()
        for (backupInfo in backupInfosMap.values) {
            val app: App? = appDb.getApplication(backupInfo.packageName, currentUserId)
            if (app != null) {
                backupInfo.appLabel = app.label
                backupInfo.isInstalled = true
            } else {
                mUninstalledApps.add(backupInfo.packageName)
            }
            val backups: List<Backup> = appDb.getAllBackups(backupInfo.packageName)
            if (backups.isEmpty()) {
                mAppsWithoutBackups.add(backupInfo.appLabel)
            } else {
                val metadataList = mutableListOf<BackupMetadataV5>()
                for (backup in backups) {
                    try {
                        val metadata = backup.item.metadata
                        metadataList.add(metadata)
                        if (metadata.isBaseBackup) {
                            backupInfo.hasBaseBackup = true
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
                backupInfo.backupMetadataList = metadataList
            }
            mBackupInfoList.add(backupInfo)
        }
        val state = if (mBackupInfoList.size > 1) {
            when {
                mUninstalledApps.isEmpty() && mAppsWithoutBackups.isEmpty() -> BackupInfoState.BOTH_MULTIPLE
                mUninstalledApps.isEmpty() -> BackupInfoState.BACKUP_MULTIPLE
                mAppsWithoutBackups.isEmpty() -> BackupInfoState.RESTORE_MULTIPLE
                else -> BackupInfoState.BOTH_MULTIPLE
            }
        } else {
            when {
                mUninstalledApps.isEmpty() && mAppsWithoutBackups.isEmpty() -> BackupInfoState.BOTH_SINGLE
                mUninstalledApps.isEmpty() -> BackupInfoState.BACKUP_SINGLE
                mAppsWithoutBackups.isEmpty() -> BackupInfoState.RESTORE_SINGLE
                else -> BackupInfoState.NONE
            }
        }
        mBackupInfoStateLiveData.postValue(state)
    }

    fun startBackup(flags: Int, selectedUsers: IntArray?) {
        val info = OperationInfo()
        info.mode = BackupRestoreDialogFragment.MODE_BACKUP
        info.op = BatchOpsManager.BACKUP
        info.flags = flags
        info.selectedUsers = selectedUsers
        mBackupOperationLiveData.postValue(info)
    }

    fun startRestore(flags: Int, selectedUsers: IntArray?, uuids: Array<String>?) {
        val info = OperationInfo()
        info.mode = BackupRestoreDialogFragment.MODE_RESTORE
        info.op = BatchOpsManager.RESTORE
        info.flags = flags
        info.selectedUsers = selectedUsers
        info.uuids = uuids
        mBackupOperationLiveData.postValue(info)
    }
}
