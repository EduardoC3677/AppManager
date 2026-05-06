// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details

import android.Manifest
import android.annotation.UserIdInt
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.apk.behavior.FreezeUnfreeze
import io.github.muntashirakon.AppManager.apk.behavior.FreezeUnfreezeService
import io.github.muntashirakon.AppManager.compat.ActivityManagerCompat
import io.github.muntashirakon.AppManager.compat.ManifestCompat
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat
import io.github.muntashirakon.AppManager.compat.UserHandleHidden
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.utils.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ActivityLauncherShortcutActivity : BaseActivity() {
    private var mIntent: Intent? = null
    private var mPackageName: String? = null
    private var mComponentName: ComponentName? = null
    private var mUserId: Int = 0
    private var mCanLaunchViaAssist: Boolean = false
    private var mIsLaunchViaAssist: Boolean = false

    override fun onAuthenticated(savedInstanceState: Bundle?) {
        val intent = intent
        if (Intent.ACTION_CREATE_SHORTCUT != intent.action || !intent.hasExtra(EXTRA_PKG) || !intent.hasExtra(EXTRA_CLS)) {
            finishActivity(0)
            return
        }
        unfreezeAndLaunchActivity(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        unfreezeAndLaunchActivity(intent)
    }

    override fun getTransparentBackground(): Boolean = true

    private fun unfreezeAndLaunchActivity(intent: Intent) {
        mIntent = Intent(intent)
        mPackageName = mIntent!!.getStringExtra(EXTRA_PKG)!!
        val className = mIntent!!.getStringExtra(EXTRA_CLS)!!
        mComponentName = ComponentName(mPackageName!!, className)
        mIntent!!.action = null
        mIntent!!.component = mComponentName
        mUserId = mIntent!!.getIntExtra(EXTRA_USR, UserHandleHidden.myUserId())
        mCanLaunchViaAssist = SelfPermissions.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
        mIsLaunchViaAssist = mIntent!!.getBooleanExtra(EXTRA_AST, false) && mCanLaunchViaAssist
        mIntent!!.removeExtra(EXTRA_PKG)
        mIntent!!.removeExtra(EXTRA_CLS)
        mIntent!!.removeExtra(EXTRA_AST)
        mIntent!!.removeExtra(EXTRA_USR)

        val info = ExUtils.exceptionAsNull { PackageManagerCompat.getApplicationInfo(mPackageName!!, 0, mUserId) }
        if (info != null && FreezeUtils.isFrozen(info)) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_shortcut_for_frozen_app)
                .setMessage(R.string.message_shortcut_for_frozen_app)
                .setCancelable(false)
                .setPositiveButton(R.string.yes) { _, _ ->
                    ThreadUtils.postOnBackgroundThread {
                        try {
                            FreezeUtils.unfreeze(mPackageName!!, mUserId)
                            ThreadUtils.postOnMainThread {
                                val service = Intent(FreezeUnfreeze.getShortcutIntent(this, mPackageName!!, mUserId, 0))
                                    .setClassName(this, FreezeUnfreezeService::class.java.name)
                                ContextCompat.startForegroundService(this, service)
                                launchActivity()
                            }
                        } catch (e: Throwable) {
                            ThreadUtils.postOnMainThread {
                                UIUtils.displayShortToast(R.string.failed)
                                finishActivity(0)
                            }
                        }
                    }
                }
                .setNegativeButton(R.string.cancel) { _, _ -> finishActivity(0) }
                .show()
        } else {
            launchActivity()
        }
    }

    private fun launchActivity() {
        if (mIsLaunchViaAssist && !SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.START_ANY_ACTIVITY)) {
            launchActivityViaAssist()
        } else {
            try {
                finishActivity(0)
                ActivityManagerCompat.startActivity(mIntent!!, mUserId)
            } catch (e: Throwable) {
                e.printStackTrace()
                UIUtils.displayLongToast("Error: ${e.message}")
                if (mCanLaunchViaAssist) launchActivityViaAssist() else finishActivity(0)
            }
        }
    }

    private fun launchActivityViaAssist() {
        val launched = ActivityManagerCompat.startActivityViaAssist(ContextUtils.getContext(), mComponentName!!) {
            val waitForInteraction = CountDownLatch(1)
            ThreadUtils.postOnMainThread {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.launch_activity_dialog_title)
                    .setMessage(R.string.launch_activity_dialog_message)
                    .setCancelable(false)
                    .setOnDismissListener {
                        waitForInteraction.countDown()
                        finishActivity(0)
                    }
                    .setNegativeButton(R.string.close, null)
                    .show()
            }
            try {
                waitForInteraction.await(10, TimeUnit.MINUTES)
            } catch (ignore: InterruptedException) {}
        }
        if (launched) finishActivity(0)
    }

    companion object {
        private const val EXTRA_PKG = "${BuildConfig.APPLICATION_ID}.intent.EXTRA.shortcut.pkg"\nprivate const val EXTRA_CLS = "${BuildConfig.APPLICATION_ID}.intent.EXTRA.shortcut.cls"\nprivate const val EXTRA_AST = "${BuildConfig.APPLICATION_ID}.intent.EXTRA.shortcut.ast"\nprivate const val EXTRA_USR = "${BuildConfig.APPLICATION_ID}.intent.EXTRA.shortcut.usr"

        @JvmStatic
        fun getShortcutIntent(context: Context, pkg: String, clazz: String, @UserIdInt userId: Int, launchViaAssist: Boolean): Intent {
            return Intent().apply {
                setClass(context, ActivityLauncherShortcutActivity::class.java)
                putExtra(EXTRA_PKG, pkg)
                putExtra(EXTRA_CLS, clazz)
                putExtra(EXTRA_USR, userId)
                putExtra(EXTRA_AST, launchViaAssist)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
    }
}
