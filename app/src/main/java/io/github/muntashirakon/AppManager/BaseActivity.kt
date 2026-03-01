// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager

import android.Manifest
import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.CallSuper
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import io.github.muntashirakon.AppManager.compat.BiometricAuthenticatorsCompat
import io.github.muntashirakon.AppManager.crypto.auth.AuthManager
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreActivity
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.self.filecache.InternalCacheCleanerService
import io.github.muntashirakon.AppManager.self.life.BuildExpiryChecker
import io.github.muntashirakon.AppManager.settings.Ops
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.settings.SecurityAndOpsViewModel
import io.github.muntashirakon.AppManager.utils.UIUtils

abstract class BaseActivity : PerProcessActivity() {
    private var mAlertDialog: AlertDialog? = null
    private var mViewModel: SecurityAndOpsViewModel? = null
    private var mDisplayLoader = true
    private var mBiometricPrompt: BiometricPrompt? = null
    private var mSavedInstanceState: Bundle? = null

    private val mKeyStoreActivity = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        ensureSecurityAndModeOfOp()
    }
    private val mPermissionCheckActivity = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        doAuthenticate(mSavedInstanceState)
        mSavedInstanceState = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Ops.isAuthenticated()) {
            Log.d(TAG, "Already authenticated.")
            onAuthenticated(savedInstanceState)
            initPermissionChecks(false)
            return
        }
        if (java.lang.Boolean.TRUE == BuildExpiryChecker.buildExpired()) {
            BuildExpiryChecker.getBuildExpiredDialog(this) { _, _ -> doAuthenticate(savedInstanceState) }.show()
            return
        }
        mSavedInstanceState = savedInstanceState
        if (!initPermissionChecks(true)) {
            mSavedInstanceState = null
            doAuthenticate(savedInstanceState)
        }
    }

    protected abstract fun onAuthenticated(savedInstanceState: Bundle?)

    @CallSuper
    override fun onStart() {
        super.onStart()
        if (mViewModel != null && mViewModel!!.isAuthenticating && mAlertDialog != null) {
            if (mDisplayLoader) mAlertDialog!!.show() else mAlertDialog!!.hide()
        }
    }

    @CallSuper
    override fun onStop() {
        mAlertDialog?.dismiss()
        super.onStop()
    }

    private fun doAuthenticate(savedInstanceState: Bundle?) {
        mViewModel = ViewModelProvider(this).get(SecurityAndOpsViewModel::class.java)
        mBiometricPrompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    finishAndRemoveTask()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    handleMigrationAndModeOfOp()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            })
        mAlertDialog = UIUtils.getProgressDialog(this, getString(R.string.initializing), true)
        Log.d(TAG, "Waiting to be authenticated.")
        mViewModel!!.authenticationStatus().observe(this) { status ->
            when (status) {
                Ops.STATUS_AUTO_CONNECT_WIRELESS_DEBUGGING -> {
                    Log.d(TAG, "Try auto-connecting to wireless debugging.")
                    mDisplayLoader = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        mViewModel!!.autoConnectWirelessDebugging()
                        return@observe
                    }
                }
                Ops.STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED -> {
                    Log.d(TAG, "Display wireless debugging chooser (pair or connect)")
                    mDisplayLoader = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Ops.connectWirelessDebugging(this, mViewModel)
                        return@observe
                    }
                }
                Ops.STATUS_ADB_CONNECT_REQUIRED -> {
                    Log.d(TAG, "Display connect dialog.")
                    mDisplayLoader = false
                    Ops.connectAdbInput(this, mViewModel)
                    return@observe
                }
                Ops.STATUS_ADB_PAIRING_REQUIRED -> {
                    Log.d(TAG, "Display pairing dialog.")
                    mDisplayLoader = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Ops.pairAdbInput(this, mViewModel)
                        return@observe
                    }
                }
                Ops.STATUS_FAILURE_ADB_NEED_MORE_PERMS -> Ops.displayIncompleteUsbDebuggingMessage(this)
                Ops.STATUS_SUCCESS, Ops.STATUS_FAILURE -> {
                    Log.d(TAG, "Authentication completed.")
                    mViewModel!!.isAuthenticating = false
                    mAlertDialog?.dismiss()
                    Ops.setAuthenticated(this, true)
                    onAuthenticated(savedInstanceState)
                    InternalCacheCleanerService.scheduleAlarm(applicationContext)
                }
            }
        }
        if (!mViewModel!!.isAuthenticating) {
            mViewModel!!.isAuthenticating = true
            if (KeyStoreManager.hasKeyStorePassword()) {
                ensureSecurityAndModeOfOp()
                return
            }
            mKeyStoreActivity.launch(Intent(this, KeyStoreActivity::class.java).putExtra(KeyStoreActivity.EXTRA_KS, true))
        }
    }

    private fun ensureSecurityAndModeOfOp() {
        if (!Prefs.Privacy.isScreenLockEnabled()) {
            handleMigrationAndModeOfOp()
            return
        }
        val auth = intent.getStringExtra(EXTRA_AUTH)
        if (auth != null && AuthManager.getKey() == auth) {
            handleMigrationAndModeOfOp()
            return
        }
        Log.i(TAG, "Screen lock enabled.")
        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if (keyguardManager.isKeyguardSecure) {
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.unlock_app_manager))
                .setAllowedAuthenticators(BiometricAuthenticatorsCompat.Builder().allowEverything(true).build())
                .build()
            mBiometricPrompt!!.authenticate(promptInfo)
        } else {
            UIUtils.displayLongToast(R.string.screen_lock_not_enabled)
            finishAndRemoveTask()
        }
    }

    private fun handleMigrationAndModeOfOp() {
        Log.d(TAG, "Authenticated")
        mViewModel?.setModeOfOps()
    }

    private fun initPermissionChecks(checkAll: Boolean): Boolean {
        val permissionsToBeAsked = mutableListOf<String>()
        for ((permission, required) in ASKED_PERMISSIONS) {
            if (!SelfPermissions.checkSelfPermission(permission) && (required || checkAll)) {
                permissionsToBeAsked.add(permission)
            }
        }
        if (permissionsToBeAsked.isNotEmpty()) {
            mPermissionCheckActivity.launch(permissionsToBeAsked.toTypedArray())
            return true
        }
        return false
    }

    companion object {
        val TAG: String = BaseActivity::class.java.simpleName
        @JvmField
        val ASKED_PERMISSIONS = mutableMapOf<String, Boolean>().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                put(Manifest.permission.POST_NOTIFICATIONS, false)
            }
        }
        const val EXTRA_AUTH = "auth"
    }
}
