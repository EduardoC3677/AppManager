// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.UserIdInt
import android.app.PendingIntent
import android.content.*
import android.content.pm.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import androidx.annotation.IntDef
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import androidx.core.app.PendingIntentCompat
import androidx.core.content.ContextCompat
import aosp.libcore.util.EmptyArray
import dev.rikka.tools.refine.Refine
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.apk.ApkFile
import io.github.muntashirakon.AppManager.apk.ApkUtils
import io.github.muntashirakon.AppManager.compat.ManifestCompat
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat
import io.github.muntashirakon.AppManager.compat.UserHandleHidden
import io.github.muntashirakon.AppManager.ipc.ProxyBinder
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.progress.ProgressHandler
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.settings.Ops
import io.github.muntashirakon.AppManager.types.UserPackagePair
import io.github.muntashirakon.AppManager.users.Users
import io.github.muntashirakon.AppManager.utils.*
import io.github.muntashirakon.io.Path
import java.io.IOException
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@SuppressLint("ShiftFlags")
class PackageInstallerCompat private constructor() {
    @IntDef(STATUS_SUCCESS, STATUS_FAILURE_ABORTED, STATUS_FAILURE_BLOCKED, STATUS_FAILURE_CONFLICT, STATUS_FAILURE_INCOMPATIBLE, STATUS_FAILURE_INVALID, STATUS_FAILURE_STORAGE, STATUS_FAILURE_SECURITY, STATUS_FAILURE_SESSION_CREATE, STATUS_FAILURE_SESSION_WRITE, STATUS_FAILURE_SESSION_COMMIT, STATUS_FAILURE_SESSION_ABANDON, STATUS_FAILURE_INCOMPATIBLE_ROM)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Status

    @IntDef(flag = true, value = [INSTALL_REPLACE_EXISTING, INSTALL_ALLOW_TEST, INSTALL_EXTERNAL, INSTALL_INTERNAL, INSTALL_FROM_ADB, INSTALL_ALL_USERS, INSTALL_REQUEST_DOWNGRADE, INSTALL_GRANT_ALL_REQUESTED_PERMISSIONS, INSTALL_ALL_WHITELIST_RESTRICTED_PERMISSIONS, INSTALL_FORCE_VOLUME_UUID, INSTALL_FORCE_PERMISSION_PROMPT, INSTALL_INSTANT_APP, INSTALL_DONT_KILL_APP, INSTALL_FULL_APP, INSTALL_ALLOCATE_AGGRESSIVE, INSTALL_VIRTUAL_PRELOAD, INSTALL_APEX, INSTALL_ENABLE_ROLLBACK, INSTALL_DISABLE_VERIFICATION, INSTALL_ALLOW_DOWNGRADE, INSTALL_ALLOW_DOWNGRADE_API29, INSTALL_STAGED, INSTALL_DRY_RUN, INSTALL_BYPASS_LOW_TARGET_SDK_BLOCK, INSTALL_REQUEST_UPDATE_OWNERSHIP, INSTALL_FROM_MANAGED_USER_OR_PROFILE, INSTALL_IGNORE_DEXOPT_PROFILE])
    @Retention(AnnotationRetention.SOURCE)
    annotation class InstallFlags

    @IntDef(flag = true, value = [DELETE_KEEP_DATA, DELETE_ALL_USERS, DELETE_SYSTEM_APP, DELETE_DONT_KILL_APP, DELETE_CHATTY])
    @Retention(AnnotationRetention.SOURCE)
    annotation class DeleteFlags

    interface OnInstallListener {
        @WorkerThread
        fun onStartInstall(sessionId: Int, packageName: String)

        @WorkerThread
        fun onAnotherAttemptInMiui(apkFile: ApkFile?) {}

        @WorkerThread
        fun onSecondAttemptInHyperOsWithoutInstaller(apkFile: ApkFile?) {}

        @WorkerThread
        fun onFinishedInstall(sessionId: Int, packageName: String, result: Int, blockingPackage: String?, statusMessage: String?)
    }

    private var mInstallWatcher: CountDownLatch? = null
    private var mInteractionWatcher: CountDownLatch? = null
    private var mCloseApkFile = true
    private var mInstallCompleted = false
    private var mApkFile: ApkFile? = null
    private var mPackageName: String = ""
    private var mAppLabel: CharSequence? = null
    private var mSessionId = -1
    @Status
    private var mFinalStatus = STATUS_FAILURE_INVALID
    private var mStatusMessage: String? = null
    private var mPkgInstallerReceiver: PackageInstallerBroadcastReceiver? = null
    private val mBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
            Log.d(TAG, "Action: $action")
            Log.d(TAG, "Session ID: $sessionId")
            when (action) {
                ACTION_INSTALL_STARTED -> mOnInstallListener?.onStartInstall(sessionId, mPackageName)
                ACTION_INSTALL_INTERACTION_BEGIN -> {}
                ACTION_INSTALL_INTERACTION_END -> if (mSessionId == sessionId) mInteractionWatcher!!.countDown()
                ACTION_INSTALL_COMPLETED -> {
                    mFinalStatus = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, STATUS_FAILURE_INVALID)
                    val blockingPackage = intent.getStringExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME)
                    mStatusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    mInstallCompleted = true
                    ThreadUtils.postOnBackgroundThread { installCompleted(sessionId, mFinalStatus, blockingPackage, mStatusMessage) }
                }
            }
        }
    }

    private var mOnInstallListener: OnInstallListener? = null
    private var mPackageInstaller: IPackageInstaller? = null
    private var mSession: PackageInstaller.Session? = null
    private var mAttempts = 1
    private val mContext: Context = ContextUtils.getContext()
    private val mHasInstallPackagePermission: Boolean = SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.INSTALL_PACKAGES)
    private var mLastVerifyAdbInstallsResult = -1

    fun setOnInstallListener(onInstallListener: OnInstallListener?) {
        mOnInstallListener = onInstallListener
    }

    fun setAppLabel(appLabel: CharSequence?) {
        mAppLabel = appLabel
    }

    fun install(apkFile: ApkFile, selectedSplitIds: List<String>, options: InstallerOptions, progressHandler: ProgressHandler?): Boolean {
        ThreadUtils.ensureWorkerThread()
        return try {
            mApkFile = apkFile
            mPackageName = apkFile.packageName!!
            initBroadcastReceiver()
            val userId = options.userId
            var installFlags = getInstallFlags(userId)
            val allRequestedUsers = getAllRequestedUsers(userId)
            if (allRequestedUsers.isEmpty()) {
                callFinish(STATUS_FAILURE_INVALID)
                return false
            }
            for (u in allRequestedUsers) {
                if (!SelfPermissions.checkCrossUserPermission(u, true)) {
                    installCompleted(mSessionId, STATUS_FAILURE_BLOCKED, "android", "STATUS_FAILURE_BLOCKED: Insufficient permission.")
                    return false
                }
            }
            ThreadUtils.postOnBackgroundThread { for (u in allRequestedUsers) copyObb(apkFile, u) }
            val firstUserId = allRequestedUsers[0]
            val originatingPackage = if (options.isSetOriginatingPackage) options.originatingPackage else null
            val originatingUri = if (options.isSetOriginatingPackage) options.originatingUri else null
            if (!openSession(firstUserId, installFlags, options.getInstallerNameNonNull(), options.installLocation, originatingPackage, originatingUri, options.installScenario, options.packageSource, options.requestUpdateOwnership, options.isDisableApkVerification)) return false
            val selectedEntries = apkFile.entries.filter { selectedSplitIds.contains(it.id) }
            var totalSize = 0L
            for (entry in selectedEntries) {
                try {
                    totalSize += entry.getFile(options.isSignApkFiles).length()
                } catch (e: IOException) {
                    callFinish(STATUS_FAILURE_INVALID)
                    return abandon()
                }
            }
            for (entry in selectedEntries) {
                val entrySize = entry.getFileSize(options.isSignApkFiles)
                try {
                    entry.getInputStream(options.isSignApkFiles).use { apkInputStream ->
                        mSession!!.openWrite(entry.fileName, 0, entrySize).use { apkOutputStream ->
                            FileUtils.copy(apkInputStream, apkOutputStream, totalSize, progressHandler)
                            mSession!!.fsync(apkOutputStream)
                        }
                    }
                } catch (e: Exception) {
                    callFinish(if (e is SecurityException) STATUS_FAILURE_SECURITY else STATUS_FAILURE_SESSION_WRITE)
                    return abandon()
                }
            }
            commit(firstUserId)
        } finally {
            unregisterReceiver()
            restoreVerifySettings()
        }
    }

    fun install(apkFiles: Array<Path>, packageName: String, options: InstallerOptions, progressHandler: ProgressHandler? = null): Boolean {
        ThreadUtils.ensureWorkerThread()
        return try {
            mApkFile = null
            mPackageName = packageName
            initBroadcastReceiver()
            val userId = options.userId
            var installFlags = getInstallFlags(userId)
            val allRequestedUsers = getAllRequestedUsers(userId)
            if (allRequestedUsers.isEmpty()) {
                callFinish(STATUS_FAILURE_INVALID)
                return false
            }
            for (u in allRequestedUsers) {
                if (!SelfPermissions.checkCrossUserPermission(u, true)) {
                    installCompleted(mSessionId, STATUS_FAILURE_BLOCKED, "android", "STATUS_FAILURE_BLOCKED: Insufficient permission.")
                    return false
                }
            }
            val firstUserId = allRequestedUsers[0]
            val originatingPackage = if (options.isSetOriginatingPackage) options.originatingPackage else null
            val originatingUri = if (options.isSetOriginatingPackage) options.originatingUri else null
            if (!openSession(firstUserId, installFlags, options.getInstallerNameNonNull(), options.installLocation, originatingPackage, originatingUri, options.installScenario, options.packageSource, options.requestUpdateOwnership, options.isDisableApkVerification)) return false
            var totalSize = apkFiles.sumOf { it.length() }
            for (apkFile in apkFiles) {
                try {
                    apkFile.openInputStream().use { apkInputStream ->
                        mSession!!.openWrite(apkFile.name, 0, apkFile.length()).use { apkOutputStream ->
                            FileUtils.copy(apkInputStream, apkOutputStream, totalSize, progressHandler)
                            mSession!!.fsync(apkOutputStream)
                        }
                    }
                } catch (e: Exception) {
                    callFinish(if (e is SecurityException) STATUS_FAILURE_SECURITY else STATUS_FAILURE_SESSION_WRITE)
                    return abandon()
                }
            }
            commit(firstUserId)
        } finally {
            unregisterReceiver()
            restoreVerifySettings()
        }
    }

    private fun commit(userId: Int): Boolean {
        val sender: IntentSender
        val intentReceiver: LocalIntentReceiver?
        if (mHasInstallPackagePermission) {
            try {
                intentReceiver = LocalIntentReceiver()
                sender = intentReceiver.intentSender
            } catch (e: Exception) {
                callFinish(STATUS_FAILURE_SESSION_COMMIT)
                return false
            }
        } else {
            intentReceiver = null
            val callbackIntent = Intent(PackageInstallerBroadcastReceiver.ACTION_PI_RECEIVER).apply { `package` = BuildConfig.APPLICATION_ID }
            val pendingIntent = PendingIntentCompat.getBroadcast(mContext, 0, callbackIntent, 0, true)
            sender = pendingIntent!!.intentSender
        }
        try {
            mSession!!.commit(sender)
        } catch (e: Throwable) {
            callFinish(STATUS_FAILURE_SESSION_COMMIT)
            return false
        }
        if (intentReceiver == null) {
            try {
                mInteractionWatcher!!.await()
                mInstallWatcher!!.await(1, TimeUnit.MINUTES)
            } catch (ignore: InterruptedException) {}
        } else {
            val resultIntent = intentReceiver.result
            mFinalStatus = resultIntent.getIntExtra(PackageInstaller.EXTRA_STATUS, 0)
            mStatusMessage = resultIntent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        }
        if (!mInstallCompleted) installCompleted(mSessionId, mFinalStatus, null, mStatusMessage)
        if (mFinalStatus == PackageInstaller.STATUS_SUCCESS && userId != UserHandleHidden.myUserId()) {
            BroadcastUtils.sendPackageAltered(ContextUtils.getContext(), arrayOf(mPackageName))
        }
        return mFinalStatus == PackageInstaller.STATUS_SUCCESS
    }

    private fun openSession(userId: Int, @InstallFlags installFlags: Int, installerName: String, installLocation: Int, originatingPackage: String?, originatingUri: Uri?, installScenario: Int, packageSource: Int, requestUpdateOwnership: Boolean, disableVerification: Boolean): Boolean {
        var flags = installFlags
        val canChangeInstaller = mHasInstallPackagePermission && (!HuaweiUtils.isStockHuawei() || Users.getSelfOrRemoteUid() != Ops.SHELL_UID)
        val requestedInstallerPackageName = if (canChangeInstaller) installerName else null
        val installerPackageName = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P && canChangeInstaller) installerName else BuildConfig.APPLICATION_ID
        try {
            mPackageInstaller = PackageManagerCompat.getPackageInstaller()
        } catch (e: RemoteException) {
            callFinish(STATUS_FAILURE_SESSION_CREATE)
            return false
        }
        cleanOldSessions()
        val sessionParams = SessionParams(SessionParams.MODE_FULL_INSTALL)
        if (disableVerification) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && SelfPermissions.isSystemOrRootOrShell()) {
                ExUtils.exceptionAsIgnored { mPackageInstaller!!.disableVerificationForUid(Users.getSelfOrRemoteUid()) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) flags = flags or INSTALL_DISABLE_VERIFICATION
            if (SelfPermissions.isShell()) {
                mLastVerifyAdbInstallsResult = Settings.Global.getInt(mContext.contentResolver, SETTINGS_VERIFIER_VERIFY_ADB_INSTALLS, 1)
                if (mLastVerifyAdbInstallsResult != 0) Settings.Global.putInt(mContext.contentResolver, SETTINGS_VERIFIER_VERIFY_ADB_INSTALLS, 0)
            }
        }
        Refine.unsafeCast<PackageInstallerHidden.SessionParams>(sessionParams).installFlags = Refine.unsafeCast<PackageInstallerHidden.SessionParams>(sessionParams).installFlags or flags
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Refine.unsafeCast<PackageInstallerHidden.SessionParams>(sessionParams).installerPackageName = requestedInstallerPackageName
        sessionParams.setInstallLocation(installLocation)
        originatingUri?.let { sessionParams.setOriginatingUri(it) }
        if (originatingPackage != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val uid = PackageUtils.getAppUid(UserPackagePair(originatingPackage, UserHandleHidden.myUserId()))
            if (uid >= 0) sessionParams.setOriginatingUid(uid)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) sessionParams.setInstallReason(PackageManager.INSTALL_REASON_USER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            sessionParams.setRequireUserAction(SessionParams.USER_ACTION_NOT_REQUIRED)
            sessionParams.setInstallScenario(installScenario)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) sessionParams.setPackageSource(packageSource)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            sessionParams.setApplicationEnabledSettingPersistent()
            sessionParams.setRequestUpdateOwnership(requestUpdateOwnership)
        }
        try {
            mSessionId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) mPackageInstaller!!.createSession(sessionParams, installerPackageName, null, userId)
            else mPackageInstaller!!.createSession(sessionParams, installerPackageName, userId)
        } catch (e: RemoteException) {
            callFinish(STATUS_FAILURE_SESSION_CREATE)
            return false
        }
        try {
            mSession = Refine.unsafeCast(PackageInstallerHidden.Session(IPackageInstallerSession.Stub.asInterface(ProxyBinder(mPackageInstaller!!.openSession(mSessionId).asBinder()))))
        } catch (e: RemoteException) {
            callFinish(STATUS_FAILURE_SESSION_CREATE)
            return false
        }
        sendStartedBroadcast(mPackageName, mSessionId)
        return true
    }

    private fun restoreVerifySettings() {
        if (mLastVerifyAdbInstallsResult == 1) {
            if (Settings.Global.getInt(mContext.contentResolver, SETTINGS_VERIFIER_VERIFY_ADB_INSTALLS, 1) != 1) {
                Settings.Global.putInt(mContext.contentResolver, SETTINGS_VERIFIER_VERIFY_ADB_INSTALLS, 1)
            }
        }
    }

    fun installExisting(packageName: String, userId: Int): Boolean {
        ThreadUtils.ensureWorkerThread()
        mPackageName = packageName
        mOnInstallListener?.onStartInstall(mSessionId, packageName)
        mInstallWatcher = CountDownLatch(0)
        mInteractionWatcher = CountDownLatch(0)
        if (!SelfPermissions.canInstallExistingPackages()) {
            installCompleted(mSessionId, STATUS_FAILURE_BLOCKED, "android", "STATUS_FAILURE_BLOCKED: Insufficient permission.")
            return false
        }
        val userIdWithoutInstalledPkg = mutableListOf<Int>()
        when (userId) {
            UserHandleHidden.USER_ALL -> {
                for (u in Users.getUsersIds()) {
                    try {
                        PackageManagerCompat.getPackageInfo(packageName, PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, u)
                    } catch (ignore: Throwable) {
                        userIdWithoutInstalledPkg.add(u)
                    }
                }
            }
            UserHandleHidden.USER_NULL -> {
                installCompleted(mSessionId, STATUS_FAILURE_INVALID, null, "STATUS_FAILURE_INVALID: No user is selected.")
                return false
            }
            else -> {
                try {
                    PackageManagerCompat.getPackageInfo(packageName, PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId)
                    installCompleted(mSessionId, STATUS_FAILURE_ABORTED, null, "STATUS_FAILURE_ABORTED: Already installed.")
                    return false
                } catch (ignore: Throwable) {
                    userIdWithoutInstalledPkg.add(userId)
                }
            }
        }
        if (userIdWithoutInstalledPkg.isEmpty()) {
            installCompleted(mSessionId, STATUS_FAILURE_INVALID, null, "STATUS_FAILURE_INVALID: Could not find a valid user to perform install-existing.")
            return false
        }
        var installFlags = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) installFlags = installFlags or INSTALL_ALL_WHITELIST_RESTRICTED_PERMISSIONS
        val installReason = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) PackageManager.INSTALL_REASON_USER else 0
        for (u in userIdWithoutInstalledPkg) {
            if (!SelfPermissions.checkCrossUserPermission(u, true)) {
                installCompleted(mSessionId, STATUS_FAILURE_BLOCKED, "android", "STATUS_FAILURE_BLOCKED: Insufficient permission.")
                return false
            }
            try {
                val res = PackageManagerCompat.installExistingPackageAsUser(packageName, u, installFlags, installReason, null)
                if (res != 1) {
                    installCompleted(mSessionId, res, null, null)
                    return false
                }
                if (u != UserHandleHidden.myUserId()) BroadcastUtils.sendPackageAdded(ContextUtils.getContext(), arrayOf(packageName))
            } catch (th: Throwable) {
                installCompleted(mSessionId, STATUS_FAILURE_ABORTED, null, "STATUS_FAILURE_ABORTED: ${th.message}")
                return false
            }
        }
        installCompleted(mSessionId, STATUS_SUCCESS, null, null)
        return true
    }

    private fun copyObb(apkFile: ApkFile, userId: Int) {
        if (!apkFile.hasObb()) return
        val tmpCloseApkFile = mCloseApkFile
        mCloseApkFile = false
        try {
            val writableObbDir = ApkUtils.getOrCreateObbDir(mPackageName, userId)
            writableObbDir.listFiles().forEach { it.delete() }
            apkFile.extractObb(writableObbDir)
            ThreadUtils.postOnMainThread { UIUtils.displayLongToast(R.string.obb_files_extracted_successfully) }
        } catch (e: Exception) {
            ThreadUtils.postOnMainThread { UIUtils.displayLongToast(R.string.failed_to_extract_obb_files) }
        } finally {
            if (mInstallWatcher!!.count != 0L) mCloseApkFile = tmpCloseApkFile
            else if (tmpCloseApkFile) apkFile.close()
        }
    }

    private fun cleanOldSessions() {
        if (Users.getSelfOrRemoteUid() != Process.myUid()) return
        val sessionInfoList = try {
            mPackageInstaller!!.getMySessions(mContext.packageName, UserHandleHidden.myUserId()).list
        } catch (e: Throwable) { return }
        sessionInfoList.forEach { ExUtils.exceptionAsIgnored { mPackageInstaller!!.abandonSession(it.sessionId) } }
    }

    private fun abandon(): Boolean {
        mSession?.let { ExUtils.exceptionAsIgnored { it.close() } }
        return false
    }

    private fun callFinish(result: Int) {
        sendCompletedBroadcast(mContext, mPackageName, result, mSessionId)
    }

    private fun installCompleted(sessionId: Int, finalStatus: Int, blockingPackage: String?, statusMessage: String?) {
        ThreadUtils.ensureWorkerThread()
        if (finalStatus == STATUS_FAILURE_ABORTED && mSessionId == sessionId && mOnInstallListener != null) {
            val privileged = SelfPermissions.checkSelfPermission(Manifest.permission.INSTALL_PACKAGES)
            if (!privileged && MiuiUtils.isActualMiuiVersionAtLeast("12.5", "20.2.0") && statusMessage == "INSTALL_FAILED_ABORTED: Permission denied" && mAttempts <= 3) {
                mAttempts++
                mInteractionWatcher!!.countDown()
                mInstallWatcher!!.countDown()
                unregisterReceiver()
                mOnInstallListener!!.onAnotherAttemptInMiui(mApkFile)
                return
            }
            if (privileged && statusMessage?.startsWith("INSTALL_FAILED_HYPEROS_ISOLATION_VIOLATION: ") == true && mAttempts <= 2) {
                mAttempts++
                mInteractionWatcher!!.countDown()
                mInstallWatcher!!.countDown()
                unregisterReceiver()
                mOnInstallListener!!.onSecondAttemptInHyperOsWithoutInstaller(mApkFile)
                return
            }
        }
        if (finalStatus == STATUS_FAILURE_SESSION_CREATE || mSessionId == sessionId) {
            mOnInstallListener?.onFinishedInstall(sessionId, mPackageName, finalStatus, blockingPackage, statusMessage)
            if (mCloseApkFile) mApkFile?.close()
            mInteractionWatcher!!.countDown()
            mInstallWatcher!!.countDown()
        }
    }

    fun uninstall(packageName: String, userId: Int, keepData: Boolean): Boolean {
        ThreadUtils.ensureWorkerThread()
        val hasDeletePackagesPermission = SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.DELETE_PACKAGES)
        mPackageName = packageName
        val callerPackageName = SelfPermissions.getCallingPackage(Users.getSelfOrRemoteUid())
        initBroadcastReceiver()
        return try {
            if (userId == UserHandleHidden.USER_ALL && Users.getAllUserIds().size > 1 && !SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.INTERACT_ACROSS_USERS_FULL)) {
                installCompleted(mSessionId, STATUS_FAILURE_BLOCKED, "android", "STATUS_FAILURE_BLOCKED: Insufficient permission.")
                return false
            }
            val flags = try { getDeleteFlags(packageName, userId, keepData) } catch (e: Exception) {
                callFinish(STATUS_FAILURE_SESSION_CREATE)
                return false
            }
            val finalUserId = getCorrectUserIdForUninstallation(packageName, userId)
            try { mPackageInstaller = PackageManagerCompat.getPackageInstaller() } catch (e: RemoteException) {
                callFinish(STATUS_FAILURE_SESSION_CREATE)
                return false
            }
            val sender: IntentSender
            val intentReceiver: LocalIntentReceiver?
            if (hasDeletePackagesPermission) {
                try {
                    intentReceiver = LocalIntentReceiver()
                    sender = intentReceiver.intentSender
                } catch (e: Exception) {
                    callFinish(STATUS_FAILURE_SESSION_COMMIT)
                    return false
                }
            } else {
                intentReceiver = null
                val callbackIntent = Intent(PackageInstallerBroadcastReceiver.ACTION_PI_RECEIVER).apply { `package` = BuildConfig.APPLICATION_ID }
                val pendingIntent = PendingIntentCompat.getBroadcast(mContext, 0, callbackIntent, 0, true)
                sender = pendingIntent!!.intentSender
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mPackageInstaller!!.uninstall(VersionedPackage(packageName, PackageManager.VERSION_CODE_HIGHEST), callerPackageName, flags, sender, finalUserId)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    mPackageInstaller!!.uninstall(packageName, callerPackageName, flags, sender, finalUserId)
                } else mPackageInstaller!!.uninstall(packageName, flags, sender, finalUserId)
            } catch (th: Throwable) {
                callFinish(STATUS_FAILURE_SESSION_COMMIT)
                return false
            }
            if (intentReceiver == null) {
                try {
                    mInteractionWatcher!!.await()
                    mInstallWatcher!!.await(1, TimeUnit.MINUTES)
                } catch (ignore: InterruptedException) {}
            } else {
                val resultIntent = intentReceiver.result
                mFinalStatus = resultIntent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                mStatusMessage = resultIntent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            }
            if (!mInstallCompleted) installCompleted(mSessionId, mFinalStatus, null, mStatusMessage)
            if (mFinalStatus == PackageInstaller.STATUS_SUCCESS && finalUserId != UserHandleHidden.myUserId()) {
                BroadcastUtils.sendPackageAltered(ContextUtils.getContext(), arrayOf(packageName))
            }
            mFinalStatus == PackageInstaller.STATUS_SUCCESS
        } finally {
            unregisterReceiver()
        }
    }

    private class LocalIntentReceiver {
        private val mResult = LinkedBlockingQueue<Intent>()
        private val mLocalSender = object : IIntentSender.Stub() {
            override fun send(code: Int, intent: Intent, resolvedType: String?, finishedReceiver: IIntentReceiver?, requiredPermission: String?) { send(intent) }
            override fun send(code: Int, intent: Intent, resolvedType: String?, finishedReceiver: IIntentReceiver?, requiredPermission: String?, options: Bundle?) { send(intent) }
            override fun send(code: Int, intent: Intent, resolvedType: String?, whitelistToken: IBinder?, finishedReceiver: IIntentReceiver?, requiredPermission: String?, options: Bundle?) { send(intent) }
            fun send(intent: Intent) { try { mResult.offer(intent, 5, TimeUnit.SECONDS) } catch (ignore: InterruptedException) {} }
        }
        val intentSender: IntentSender
            get() = IntentSender::class.java.getConstructor(IBinder::class.java).newInstance(mLocalSender.asBinder())
        val result: Intent
            get() = try { mResult.take() } catch (e: InterruptedException) { throw RuntimeException(e) }
    }

    private fun unregisterReceiver() {
        mPkgInstallerReceiver?.let { ContextUtils.unregisterReceiver(mContext, it) }
        ContextUtils.unregisterReceiver(mContext, mBroadcastReceiver)
    }

    private fun initBroadcastReceiver() {
        mInstallWatcher = CountDownLatch(1)
        mInteractionWatcher = CountDownLatch(1)
        mPkgInstallerReceiver = PackageInstallerBroadcastReceiver().apply {
            setAppLabel(mAppLabel)
            setPackageName(mPackageName)
        }
        ContextCompat.registerReceiver(mContext, mPkgInstallerReceiver!!, IntentFilter(PackageInstallerBroadcastReceiver.ACTION_PI_RECEIVER), ContextCompat.RECEIVER_NOT_EXPORTED)
        val intentFilter = IntentFilter().apply {
            addAction(ACTION_INSTALL_COMPLETED)
            addAction(ACTION_INSTALL_STARTED)
            addAction(ACTION_INSTALL_INTERACTION_BEGIN)
            addAction(ACTION_INSTALL_INTERACTION_END)
        }
        ContextCompat.registerReceiver(mContext, mBroadcastReceiver, intentFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun sendStartedBroadcast(packageName: String, sessionId: Int) {
        val broadcastIntent = Intent(ACTION_INSTALL_STARTED).apply {
            `package` = mContext.packageName
            putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, packageName)
            putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId)
        }
        mContext.sendBroadcast(broadcastIntent)
    }

    companion object {
        val TAG: String = PackageInstallerCompat::class.java.simpleName
        const val ACTION_INSTALL_STARTED = "${BuildConfig.APPLICATION_ID}.action.INSTALL_STARTED"
        const val ACTION_INSTALL_COMPLETED = "${BuildConfig.APPLICATION_ID}.action.INSTALL_COMPLETED"
        const val ACTION_INSTALL_INTERACTION_BEGIN = "${BuildConfig.APPLICATION_ID}.action.INSTALL_INTERACTION_BEGIN"
        const val ACTION_INSTALL_INTERACTION_END = "${BuildConfig.APPLICATION_ID}.action.INSTALL_INTERACTION_END"

        const val STATUS_SUCCESS = PackageInstaller.STATUS_SUCCESS
        const val STATUS_FAILURE_ABORTED = PackageInstaller.STATUS_FAILURE_ABORTED
        const val STATUS_FAILURE_BLOCKED = PackageInstaller.STATUS_FAILURE_BLOCKED
        const val STATUS_FAILURE_CONFLICT = PackageInstaller.STATUS_FAILURE_CONFLICT
        const val STATUS_FAILURE_INCOMPATIBLE = PackageInstaller.STATUS_FAILURE_INCOMPATIBLE
        const val STATUS_FAILURE_INVALID = PackageInstaller.STATUS_FAILURE_INVALID
        const val STATUS_FAILURE_STORAGE = PackageInstaller.STATUS_FAILURE_STORAGE
        const val STATUS_FAILURE_SECURITY = -2
        const val STATUS_FAILURE_SESSION_CREATE = -3
        const val STATUS_FAILURE_SESSION_WRITE = -4
        const val STATUS_FAILURE_SESSION_COMMIT = -5
        const val STATUS_FAILURE_SESSION_ABANDON = -6
        const val STATUS_FAILURE_INCOMPATIBLE_ROM = -7

        const val INSTALL_REPLACE_EXISTING = 0x00000002
        const val INSTALL_ALLOW_TEST = 0x00000004
        @Deprecated("Removed in API 29")
        const val INSTALL_EXTERNAL = 0x00000008
        const val INSTALL_INTERNAL = 0x00000010
        const val INSTALL_FROM_ADB = 0x00000020
        const val INSTALL_ALL_USERS = 0x00000040
        const val INSTALL_REQUEST_DOWNGRADE = 0x00000080
        const val INSTALL_GRANT_ALL_REQUESTED_PERMISSIONS = 0x00000100
        const val INSTALL_ALL_WHITELIST_RESTRICTED_PERMISSIONS = 0x00400000
        const val INSTALL_FORCE_VOLUME_UUID = 0x00000200
        const val INSTALL_FORCE_PERMISSION_PROMPT = 0x00000400
        const val INSTALL_INSTANT_APP = 0x00000800
        const val INSTALL_DONT_KILL_APP = 0x00001000
        const val INSTALL_FULL_APP = 0x00004000
        const val INSTALL_ALLOCATE_AGGRESSIVE = 0x00008000
        const val INSTALL_VIRTUAL_PRELOAD = 0x00010000
        const val INSTALL_APEX = 0x00020000
        const val INSTALL_ENABLE_ROLLBACK = 0x00040000
        const val INSTALL_DISABLE_VERIFICATION = 0x00080000
        const val INSTALL_ALLOW_DOWNGRADE_API29 = 0x00100000
        const val INSTALL_STAGED = 0x00200000
        @Deprecated("Removed in API 30")
        const val INSTALL_DRY_RUN = 0x00800000
        @Deprecated("Replaced by INSTALL_ALLOW_DOWNGRADE_API29")
        const val INSTALL_ALLOW_DOWNGRADE = 0x00000080
        const val INSTALL_BYPASS_LOW_TARGET_SDK_BLOCK = 0x01000000
        const val INSTALL_REQUEST_UPDATE_OWNERSHIP = 1 shl 25
        const val INSTALL_FROM_MANAGED_USER_OR_PROFILE = 1 shl 26
        const val INSTALL_IGNORE_DEXOPT_PROFILE = 1 shl 28

        const val DELETE_KEEP_DATA = 0x00000001
        const val DELETE_ALL_USERS = 0x00000002
        const val DELETE_SYSTEM_APP = 0x00000004
        const val DELETE_DONT_KILL_APP = 0x00000008
        const val DELETE_CHATTY = 0x80000000.toInt()

        const val SETTINGS_VERIFIER_VERIFY_ADB_INSTALLS = "verifier_verify_adb_installs"

        @JvmStatic
        fun getNewInstance(): PackageInstallerCompat = PackageInstallerCompat()

        private fun getAllRequestedUsers(userId: Int): IntArray = when (userId) {
            UserHandleHidden.USER_ALL -> Users.getAllUserIds()
            UserHandleHidden.USER_NULL -> EmptyArray.INT
            else -> intArrayOf(userId)
        }

        private fun getInstallFlags(userId: Int): Int {
            var flags = INSTALL_FROM_ADB or INSTALL_ALLOW_TEST or INSTALL_REPLACE_EXISTING
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) flags = flags or INSTALL_FULL_APP
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) flags = flags or (INSTALL_REQUEST_DOWNGRADE or INSTALL_ALLOW_DOWNGRADE_API29)
            else flags = flags or INSTALL_ALLOW_DOWNGRADE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) flags = flags or INSTALL_BYPASS_LOW_TARGET_SDK_BLOCK
            if (userId == UserHandleHidden.USER_ALL) flags = flags or INSTALL_ALL_USERS
            return flags
        }

        private fun getDeleteFlags(packageName: String, userId: Int, keepData: Boolean): Int {
            var flags = 0
            if (userId != UserHandleHidden.USER_ALL) {
                val info = PackageManagerCompat.getPackageInfo(packageName, MATCH_UNINSTALLED_PACKAGES or PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId)
                if ((info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) flags = flags or DELETE_SYSTEM_APP
            } else flags = flags or DELETE_ALL_USERS
            if (keepData) flags = flags or DELETE_KEEP_DATA
            return flags
        }

        private fun getCorrectUserIdForUninstallation(packageName: String, userId: Int): Int {
            if (userId == UserHandleHidden.USER_ALL) {
                for (user in Users.getAllUserIds()) {
                    try {
                        PackageManagerCompat.getPackageInfo(packageName, MATCH_UNINSTALLED_PACKAGES or PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, user)
                        return user
                    } catch (ignore: Throwable) {}
                }
            }
            return userId
        }

        @JvmStatic
        fun sendCompletedBroadcast(context: Context, packageName: String, @Status status: Int, sessionId: Int) {
            val broadcastIntent = Intent(ACTION_INSTALL_COMPLETED).apply {
                `package` = context.packageName
                putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, packageName)
                putExtra(PackageInstaller.EXTRA_STATUS, status)
                putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId)
            }
            context.sendBroadcast(broadcastIntent)
        }
    }
}
