// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.UserIdInt
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.ViewModelProvider
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.accessibility.AccessibilityMultiplexer
import io.github.muntashirakon.AppManager.apk.ApkSource
import io.github.muntashirakon.AppManager.apk.CachedApkSource
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.*
import io.github.muntashirakon.AppManager.apk.splitapk.SplitApkChooser
import io.github.muntashirakon.AppManager.apk.whatsnew.WhatsNewFragment
import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat
import io.github.muntashirakon.AppManager.compat.UserHandleHidden
import io.github.muntashirakon.AppManager.details.AppDetailsActivity
import io.github.muntashirakon.AppManager.intercept.IntentCompat
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.types.ForegroundService
import io.github.muntashirakon.AppManager.utils.PackageUtils
import io.github.muntashirakon.AppManager.utils.StoragePermission
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.AppManager.utils.Utils
import java.util.*

class PackageInstallerActivity : BaseActivity(), InstallerDialogHelper.OnClickButtonsListener {
    private var mSessionId = -1
    private var mCurrentItem: ApkQueueItem? = null
    private var mPackageName: String = ""
    private var mIsDealingWithApk = false
    @UserIdInt
    private var mLastUserId: Int = 0
    private var mDialogHelper: InstallerDialogHelper? = null
    private var mModel: PackageInstallerViewModel? = null
    private var mService: PackageInstallerService? = null
    private var mInstallerDialogFragment: InstallerDialogFragment? = null
    private var initiated = false
    private val mAppInfoClickListener = View.OnClickListener {
        val currentItem = mCurrentItem!!
        try {
            val apkSource = currentItem.apkSource ?: mModel!!.getApkSource()
            val appDetailsIntent = AppDetailsActivity.getIntent(this, apkSource, true)
            appDetailsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(appDetailsIntent)
        } finally {
            goToNext()
        }
    }
    private val mInstallerOptions = InstallerOptions.getDefault()
    private val mApkQueue: Queue<ApkQueueItem> = LinkedList()
    private val mConfirmIntentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val broadcastIntent = Intent(ACTION_INSTALL_INTERACTION_END).apply {
            `package` = packageName
            putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, mPackageName)
            putExtra(PackageInstaller.EXTRA_SESSION_ID, mSessionId)
        }
        applicationContext.sendBroadcast(broadcastIntent)
        if (!hasNext() && !mIsDealingWithApk) finish()
    }

    private val mMultiplexer = AccessibilityMultiplexer.getInstance()
    private val mStoragePermission = StoragePermission.init(this)
    private val mServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            mService = (service as ForegroundService.Binder).service as PackageInstallerService
        }

        override fun onServiceDisconnected(name: ComponentName) {
            mService = null
        }
    }

    override fun getTransparentBackground(): Boolean = true

    override fun onAuthenticated(savedInstanceState: Bundle?) {
        val intent = intent
        if (intent == null) {
            triggerCancel()
            return
        }
        Log.d(TAG, "On create, intent: $intent")
        if (ACTION_PACKAGE_INSTALLED == intent.action) {
            onNewIntent(intent)
            return
        }
        mModel = ViewModelProvider(this).get(PackageInstallerViewModel::class.java)
        if (!bindService(Intent(this, PackageInstallerService::class.java), mServiceConnection, BIND_AUTO_CREATE)) {
            throw RuntimeException("Unable to bind PackageInstallerService")
        }
        synchronized(mApkQueue) {
            mApkQueue.addAll(ApkQueueItem.fromIntent(intent, Utils.getRealReferrer(this)))
        }
        val apkSource = IntentCompat.getUnwrappedParcelableExtra(intent, EXTRA_APK_FILE_LINK, ApkSource::class.java)
        if (apkSource != null) {
            synchronized(mApkQueue) {
                mApkQueue.add(ApkQueueItem.fromApkSource(apkSource))
            }
        }
        mModel!!.packageInfoLiveData().observe(this) { newPackageInfo ->
            if (newPackageInfo == null) {
                mDialogHelper!!.showParseFailedDialog { triggerCancel() }
                return@observe
            }
            mDialogHelper!!.onParseSuccess(mModel!!.getAppLabel(), getVersionInfoWithTrackers(newPackageInfo),
                mModel!!.getAppIcon()) {
                displayInstallerOptions { _, _, options ->
                    if (options != null) mInstallerOptions.copy(options)
                }
            }
            displayChangesOrInstallationPrompt()
        }
        mModel!!.packageUninstalledLiveData().observe(this) { success ->
            if (success) install()
            else showInstallationFinishedDialog(mModel!!.getPackageName()!!, getString(R.string.failed_to_uninstall_app), null, false)
        }
        mInstallerDialogFragment = InstallerDialogFragment().apply {
            isCancelable = false
            setFragmentStartedCallback { f, d -> init(f, d) }
        }
        mInstallerDialogFragment!!.showNow(supportFragmentManager, InstallerDialogFragment.TAG)
    }

    override fun onDestroy() {
        mService?.let { unbindService(mServiceConnection) }
        unsetInstallFinishedListener()
        mCurrentItem?.apkSource?.let { if (it is CachedApkSource) it.cleanup() }
        super.onDestroy()
    }

    private fun init(fragment: InstallerDialogFragment, dialog: AlertDialog) {
        if (initiated) return
        initiated = true
        mDialogHelper = InstallerDialogHelper(fragment, dialog)
        mDialogHelper!!.initProgress { triggerCancel() }
        goToNext()
    }

    private fun displayChangesOrInstallationPrompt() {
        val installedPackageInfo = mModel!!.getInstalledPackageInfo()
        val actionRes: Int
        var displayChanges = false
        if (installedPackageInfo == null) {
            actionRes = R.string.install
        } else {
            val installedVersionCode = PackageInfoCompat.getLongVersionCode(installedPackageInfo)
            val thisVersionCode = PackageInfoCompat.getLongVersionCode(mModel!!.getNewPackageInfo()!!)
            displayChanges = Prefs.Installer.displayChanges()
            actionRes = when {
                installedVersionCode < thisVersionCode -> R.string.update
                installedVersionCode == thisVersionCode -> R.string.reinstall
                else -> R.string.downgrade
            }
        }
        if (displayChanges) {
            val dialogFragment = WhatsNewFragment.getInstance(mModel!!.getNewPackageInfo()!!, mModel!!.getInstalledPackageInfo())
            mDialogHelper!!.showWhatsNewDialog(actionRes, dialogFragment, object : InstallerDialogHelper.OnClickButtonsListener {
                override fun triggerInstall() { displayInstallationPrompt(actionRes, true) }
                override fun triggerCancel() { this@PackageInstallerActivity.triggerCancel() }
            }, mAppInfoClickListener)
            return
        }
        displayInstallationPrompt(actionRes, false)
    }

    private fun displayInstallationPrompt(actionRes: Int, splitOnly: Boolean) {
        if (mModel!!.getApkFile()!!.isSplit) {
            val fragment = SplitApkChooser.getNewInstance(getVersionInfoWithTrackers(mModel!!.getNewPackageInfo()!!), getString(actionRes))
            mDialogHelper!!.showApkChooserDialog(actionRes, fragment, this, mAppInfoClickListener)
            return
        }
        if (!splitOnly) {
            mDialogHelper!!.showInstallConfirmationDialog(actionRes, this, mAppInfoClickListener)
        } else triggerInstall()
    }

    private fun displayInstallerOptions(clickListener: InstallerOptionsFragment.OnClickListener) {
        val packageInfo = mModel!!.getNewPackageInfo()!!
        val dialog = InstallerOptionsFragment.getInstance(packageInfo.packageName,
            ApplicationInfoCompat.isTestOnly(packageInfo.applicationInfo), mInstallerOptions, clickListener)
        dialog.show(supportFragmentManager, InstallerOptionsFragment.TAG)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.clear()
        super.onSaveInstanceState(outState)
    }

    private fun install() {
        if (mModel!!.getApkFile()!!.hasObb() && !SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.INSTALL_PACKAGES)) {
            mStoragePermission.request { granted -> if (granted) launchInstallerService() }
        } else launchInstallerService()
    }

    private fun launchInstallerService() {
        val currentItem = mCurrentItem!!
        val userId = mInstallerOptions.userId
        currentItem.installerOptions = mInstallerOptions
        currentItem.selectedSplits = mModel!!.getSelectedSplitsForInstallation()
        mLastUserId = if (userId == UserHandleHidden.USER_ALL) UserHandleHidden.myUserId() else userId
        val canDisplayNotification = Utils.canDisplayNotification(this)
        val alwaysOnBackground = canDisplayNotification && Prefs.Installer.installInBackground()
        val intent = Intent(this, PackageInstallerService::class.java)
        IntentCompat.putWrappedParcelableExtra(intent, PackageInstallerService.EXTRA_QUEUE_ITEM, currentItem)
        if (!SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.INSTALL_PACKAGES)) {
            mMultiplexer.enableInstall(true)
        }
        ContextCompat.startForegroundService(this, intent)
        if (!alwaysOnBackground && mService != null) {
            setInstallFinishedListener()
            mDialogHelper!!.showInstallProgressDialog(if (canDisplayNotification) View.OnClickListener {
                unsetInstallFinishedListener()
                goToNext()
            } else null)
        } else {
            unsetInstallFinishedListener()
            goToNext()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "New intent called: $intent")
        setIntent(intent)
        if (ACTION_PACKAGE_INSTALLED == intent.action) {
            mSessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
            mPackageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME) ?: ""
            val confirmIntent = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
            try {
                if (mPackageName.isEmpty() || confirmIntent == null) throw Exception("Empty confirmation intent.")
                Log.d(TAG, "Requesting user confirmation for package $mPackageName")
                mConfirmIntentLauncher.launch(confirmIntent)
            } catch (e: Exception) {
                e.printStackTrace()
                PackageInstallerCompat.sendCompletedBroadcast(this, mPackageName, STATUS_FAILURE_INCOMPATIBLE_ROM, mSessionId)
                if (!hasNext() && !mIsDealingWithApk) finish()
            }
            return
        }
        synchronized(mApkQueue) {
            mApkQueue.addAll(ApkQueueItem.fromIntent(intent, Utils.getRealReferrer(this)))
        }
        UIUtils.displayShortToast(R.string.added_to_queue)
    }

    override fun triggerInstall() {
        if (mModel!!.getInstalledPackageInfo() == null) {
            install()
            return
        }
        val reinstallListener = object : InstallerDialogHelper.OnClickButtonsListener {
            override fun triggerInstall() { reinstall() }
            override fun triggerCancel() { this@PackageInstallerActivity.triggerCancel() }
        }
        val installedVersionCode = PackageInfoCompat.getLongVersionCode(mModel!!.getInstalledPackageInfo()!!)
        val thisVersionCode = PackageInfoCompat.getLongVersionCode(mModel!!.getNewPackageInfo()!!)
        if (installedVersionCode > thisVersionCode && !SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.INSTALL_PACKAGES)) {
            val builder = SpannableStringBuilder()
                .append(getString(R.string.do_you_want_to_uninstall_and_install)).append(" ")
                .append(UIUtils.getItalicString(getString(R.string.app_data_will_be_lost)))
                .append("

")
            mDialogHelper!!.showDowngradeReinstallWarning(builder, reinstallListener, mAppInfoClickListener)
            return
        }
        if (!mModel!!.isSignatureDifferent) {
            install()
            return
        }
        val info = mModel!!.getInstalledPackageInfo()!!.applicationInfo
        val isSystem = ApplicationInfoCompat.isSystemApp(info)
        val builder = SpannableStringBuilder()
        if (isSystem) {
            builder.append(getString(R.string.app_signing_signature_mismatch_for_system_apps))
        } else {
            builder.append(getString(R.string.do_you_want_to_uninstall_and_install)).append(" ")
                .append(UIUtils.getItalicString(getString(R.string.app_data_will_be_lost)))
        }
        builder.append("

")
        val start = builder.length
        builder.append(getText(R.string.app_signing_install_without_data_loss))
        builder.setSpan(RelativeSizeSpan(0.8f), start, builder.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        mDialogHelper!!.showSignatureMismatchReinstallWarning(builder, reinstallListener, { install() }, isSystem)
    }

    override fun triggerCancel() {
        mCurrentItem?.apkSource?.let { if (it is CachedApkSource) it.cleanup() }
        goToNext()
    }

    private fun reinstall() {
        if (!SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.DELETE_PACKAGES)) {
            mMultiplexer.enableUninstall(true)
        }
        mModel!!.uninstallPackage()
    }

    private fun goToNext() {
        mCurrentItem = null
        mMultiplexer.enableInstall(false)
        mMultiplexer.enableUninstall(false)
        if (hasNext()) {
            mIsDealingWithApk = true
            mDialogHelper!!.initProgress { goToNext() }
            synchronized(mApkQueue) {
                mCurrentItem = mApkQueue.poll()
                mModel!!.getPackageInfo(mCurrentItem!!)
            }
        } else {
            mIsDealingWithApk = false
            mDialogHelper!!.dismiss()
            finish()
        }
    }

    private fun hasNext(): Boolean {
        synchronized(mApkQueue) { return mApkQueue.isNotEmpty() }
    }

    private fun getVersionInfoWithTrackers(newPackageInfo: PackageInfo): String {
        val res = application.resources
        val newVersionCode = PackageInfoCompat.getLongVersionCode(newPackageInfo)
        val newVersionName = newPackageInfo.versionName
        val trackers = mModel!!.getTrackerCount()
        val sb = StringBuilder(res.getString(R.string.version_name_with_code, newVersionName, newVersionCode))
        if (trackers > 0) {
            sb.append(", ").append(res.getQuantityString(R.plurals.no_of_trackers, trackers, trackers))
        }
        return sb.toString()
    }

    fun showInstallationFinishedDialog(packageName: String, result: Int, blockingPackage: String?, statusMessage: String?) {
        showInstallationFinishedDialog(packageName, getStringFromStatus(result, blockingPackage), statusMessage, result == STATUS_SUCCESS)
    }

    fun showInstallationFinishedDialog(packageName: String, message: CharSequence, statusMessage: String?, displayOpenAndAppInfo: Boolean) {
        val ssb = SpannableStringBuilder(message)
        if (statusMessage != null) {
            ssb.append("

").append(UIUtils.getItalicString(statusMessage))
        }
        val intent = PackageManagerCompat.getLaunchIntentForPackage(packageName, UserHandleHidden.myUserId())
        mDialogHelper!!.showInstallFinishedDialog(ssb, if (hasNext()) R.string.next else R.string.close, { goToNext() },
            if (displayOpenAndAppInfo && intent != null) View.OnClickListener {
                try {
                    startActivity(intent)
                } catch (th: Throwable) {
                    UIUtils.displayLongToast(th.message)
                } finally {
                    goToNext()
                }
            } else null, if (displayOpenAndAppInfo) View.OnClickListener {
                try {
                    val appDetailsIntent = AppDetailsActivity.getIntent(this, packageName, mLastUserId, true)
                    appDetailsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(appDetailsIntent)
                } finally {
                    goToNext()
                }
            } else null)
    }

    private fun getStringFromStatus(@Status status: Int, blockingPackage: String?): String {
        return when (status) {
            STATUS_SUCCESS -> getString(R.string.installer_app_installed)
            STATUS_FAILURE_ABORTED -> getString(R.string.installer_error_aborted)
            STATUS_FAILURE_BLOCKED -> {
                val blocker = blockingPackage?.let { PackageUtils.getPackageLabel(packageManager, it) } ?: getString(R.string.installer_error_blocked_device)
                getString(R.string.installer_error_blocked, blocker)
            }
            STATUS_FAILURE_CONFLICT -> getString(R.string.installer_error_conflict)
            STATUS_FAILURE_INCOMPATIBLE -> getString(R.string.installer_error_incompatible)
            STATUS_FAILURE_INVALID -> getString(R.string.installer_error_bad_apks)
            STATUS_FAILURE_STORAGE -> getString(R.string.installer_error_storage)
            STATUS_FAILURE_SECURITY -> getString(R.string.installer_error_security)
            STATUS_FAILURE_SESSION_CREATE -> getString(R.string.installer_error_session_create)
            STATUS_FAILURE_SESSION_WRITE -> getString(R.string.installer_error_session_write)
            STATUS_FAILURE_SESSION_COMMIT -> getString(R.string.installer_error_session_commit)
            STATUS_FAILURE_SESSION_ABANDON -> getString(R.string.installer_error_session_abandon)
            STATUS_FAILURE_INCOMPATIBLE_ROM -> getString(R.string.installer_error_lidl_rom)
            else -> getString(R.string.installer_error_generic)
        }
    }

    fun setInstallFinishedListener() {
        mService?.let {
            it.setOnInstallFinished { packageName, status, blockingPackage, statusMessage ->
                if (!isFinishing) showInstallationFinishedDialog(packageName, status, blockingPackage, statusMessage)
            }
        }
    }

    fun unsetInstallFinishedListener() {
        mService?.setOnInstallFinished(null)
    }

    companion object {
        val TAG: String = PackageInstallerActivity::class.java.simpleName
        private const val EXTRA_APK_FILE_LINK = "link"
        const val ACTION_PACKAGE_INSTALLED = "${BuildConfig.APPLICATION_ID}.action.PACKAGE_INSTALLED"

        @JvmStatic
        fun getLaunchableInstance(context: Context, uri: Uri): Intent {
            return Intent(context, PackageInstallerActivity::class.java).apply { data = uri }
        }

        @JvmStatic
        fun getLaunchableInstance(context: Context, apkSource: ApkSource?): Intent {
            return Intent(context, PackageInstallerActivity::class.java).apply {
                IntentCompat.putWrappedParcelableExtra(this, EXTRA_APK_FILE_LINK, apkSource)
            }
        }

        @JvmStatic
        fun getLaunchableInstance(context: Context, packageName: String): Intent {
            return Intent(context, PackageInstallerActivity::class.java).apply { data = Uri.parse("package:$packageName") }
        }
    }
}
