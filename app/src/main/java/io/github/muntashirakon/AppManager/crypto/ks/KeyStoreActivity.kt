// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto.ks

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import androidx.activity.EdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.self.life.BuildExpiryChecker
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.utils.Utils
import io.github.muntashirakon.dialog.TextInputDialogBuilder

class KeyStoreActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Prefs.Appearance.getTransparentAppTheme())
        EdgeToEdge.enable(this)
        super.onCreate(savedInstanceState)
        if (java.lang.Boolean.TRUE == BuildExpiryChecker.buildExpired()) {
            BuildExpiryChecker.getBuildExpiredDialog(this) { _, _ -> processIntentAndFinish(intent) }.show()
            return
        }
        processIntentAndFinish(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        processIntentAndFinish(intent)
    }

    private fun processIntentAndFinish(intent: Intent?) {
        if (intent == null) {
            finish()
            return
        }
        val alias = intent.getStringExtra(EXTRA_ALIAS)
        if (alias != null) {
            displayInputKeyStoreAliasPassword(alias)
            return
        }
        if (intent.hasExtra(EXTRA_KS)) {
            val ksDialog: AlertDialog = if (KeyStoreManager.hasKeyStore()) {
                KeyStoreManager.inputKeyStorePassword(this) { finish() }
            } else {
                KeyStoreManager.generateAndDisplayKeyStorePassword(this) { finish() }
            }
            ksDialog.show()
            return
        }
        finish()
    }

    @Deprecated("Kept for migratory purposes only")
    private fun displayInputKeyStoreAliasPassword(alias: String) {
        TextInputDialogBuilder(this, getString(R.string.input_keystore_alias_pass, alias))
            .setTitle(getString(R.string.input_keystore_alias_pass, alias))
            .setHelperText(getString(R.string.input_keystore_alias_pass_description, alias))
            .setPositiveButton(R.string.ok) { _, _, inputText, _ -> savePass(KeyStoreManager.getPrefAlias(alias), inputText) }
            .setCancelable(false)
            .setOnDismissListener { finish() }
            .show()
    }

    private fun savePass(prefKey: String, rawPassword: Editable?) {
        val password: CharArray
        if (TextUtils.isEmpty(rawPassword)) {
            try {
                password = KeyStoreManager.getInstance().getAmKeyStorePassword()
            } catch (e: Exception) {
                Log.e(KeyStoreManager.TAG, "Could not get KeyStore password", e)
                val broadcastIntent = Intent(KeyStoreManager.ACTION_KS_INTERACTION_END)
                broadcastIntent.setPackage(packageName)
                sendBroadcast(broadcastIntent)
                return
            }
        } else {
            password = CharArray(rawPassword!!.length)
            rawPassword.getChars(0, rawPassword.length, password, 0)
        }
        KeyStoreManager.savePass(this, prefKey, password)
        Utils.clearChars(password)
        val broadcastIntent = Intent(KeyStoreManager.ACTION_KS_INTERACTION_END)
        broadcastIntent.setPackage(packageName)
        sendBroadcast(broadcastIntent)
    }

    companion object {
        const val EXTRA_ALIAS = "key"\nconst val EXTRA_KS = "ks"
    }
}
