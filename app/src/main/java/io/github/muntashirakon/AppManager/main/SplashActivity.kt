// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.widget.TextView
import androidx.activity.EdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuBuilder
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.color.DynamicColors
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.compat.BiometricAuthenticatorsCompat
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreActivity
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.self.life.BuildExpiryChecker
import io.github.muntashirakon.AppManager.settings.Ops
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.settings.SecurityAndOpsViewModel
import io.github.muntashirakon.AppManager.utils.UIUtils
import java.util.*

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    private var mStateNameView: TextView? = null
    private var mViewModel: SecurityAndOpsViewModel? = null
    private var mBiometricPrompt: BiometricPrompt? = null

    private val mKeyStoreActivity: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Need authentication and/or verify mode of operation
        ensureSecurityAndModeOfOp()
    }
    private val mPermissionCheckActivity: ActivityResultLauncher<Array<String>> = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Run authentication
        doAuthenticate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(if (Prefs.Appearance.isPureBlackTheme()) R.style.AppTheme_Splash_Black else R.style.AppTheme_Splash)
        SplashScreen.installSplashScreen(this)
        EdgeToEdge.enable(this)
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        setContentView(R.layout.activity_authentication)
        findViewById<TextView>(R.id.version).text = String.format(
            Locale.ROOT, "%s (%d)",
            BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE
        )
        mStateNameView = findViewById(R.id.state_name)
        if (Ops.isAuthenticated()) {
            Log.d(TAG, "Already authenticated.")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        if (BuildExpiryChecker.buildExpired() == java.lang.Boolean.TRUE) {
            // Build has expired
            BuildExpiryChecker.getBuildExpiredDialog(this) { _, _ -> doAuthenticate() }.show()
            return
        }
        // Init permission checks
        if (!initPermissionChecks()) {
            // Run authentication
            doAuthenticate()
        }
    }

    @SuppressLint("RestrictedApi")
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (menu is MenuBuilder) {
            menu.setOptionalIconsVisible(true)
        }
        return super.onCreateOptionsMenu(menu)
    }

    private fun doAuthenticate() {
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
        Log.d(TAG, "Waiting to be authenticated.")
        mViewModel!!.authenticationStatus().observe(this) { status ->
            when (status) {
                Ops.STATUS_AUTO_CONNECT_WIRELESS_DEBUGGING -> {
                    Log.d(TAG, "Try auto-connecting to wireless debugging.")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        mViewModel!!.autoConnectWirelessDebugging()
                        return@observe
                    }
                }
                Ops.STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED -> {
                    Log.d(TAG, "Display wireless debugging chooser (pair or connect)")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Ops.connectWirelessDebugging(this, mViewModel!!)
                        return@observe
                    }
                }
                Ops.STATUS_ADB_CONNECT_REQUIRED -> {
                    Log.d(TAG, "Display connect dialog.")
                    Ops.connectAdbInput(this, mViewModel!!)
                    return@observe
                }
                Ops.STATUS_ADB_PAIRING_REQUIRED -> {
                    Log.d(TAG, "Display pairing dialog.")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Ops.pairAdbInput(this, mViewModel!!)
                        return@observe
                    }
                }
                Ops.STATUS_FAILURE_ADB_NEED_MORE_PERMS -> Ops.displayIncompleteUsbDebuggingMessage(this)
                Ops.STATUS_SUCCESS, Ops.STATUS_FAILURE -> {
                    Log.d(TAG, "Authentication completed.")
                    mViewModel!!.setAuthenticating(false)
                    Ops.setAuthenticated(this, true)
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                else -> {}
            }
        }
        if (!mViewModel!!.isAuthenticating()) {
            mViewModel!!.setAuthenticating(true)
            if (KeyStoreManager.hasKeyStorePassword()) {
                ensureSecurityAndModeOfOp()
                return
            }
            val keyStoreIntent = Intent(this, KeyStoreActivity::class.java)
                .putExtra(KeyStoreActivity.EXTRA_KS, true)
            mKeyStoreActivity.launch(keyStoreIntent)
        }
    }

    private fun ensureSecurityAndModeOfOp() {
        if (!Prefs.Privacy.isScreenLockEnabled()) {
            handleMigrationAndModeOfOp()
            return
        }
        Log.d(TAG, "Security enabled.")
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
        mStateNameView?.setText(R.string.initializing)
        mViewModel?.setModeOfOps()
    }

    private fun initPermissionChecks(): Boolean {
        val permissionsToBeAsked = ArrayList<String>(BaseActivity.ASKED_PERMISSIONS.size)
        for (permission in BaseActivity.ASKED_PERMISSIONS.keys) {
            if (!SelfPermissions.checkSelfPermission(permission)) {
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
        val TAG: String = SplashActivity::class.java.simpleName
    }
}
