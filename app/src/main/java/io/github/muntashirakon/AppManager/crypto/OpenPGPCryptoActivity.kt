// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.intercept.IntentCompat
import org.openintents.openpgp.util.OpenPgpApi

class OpenPGPCryptoActivity : BaseActivity() {
    private val mConfirmationLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        val broadcastIntent = Intent(OpenPGPCrypto.ACTION_OPEN_PGP_INTERACTION_END).apply { `package` = packageName }
        sendBroadcast(broadcastIntent)
        finish()
    }

    override fun getTransparentBackground(): Boolean = true

    override fun onAuthenticated(savedInstanceState: Bundle?) {
        if (intent != null) onNewIntent(intent) else finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val pi = IntentCompat.getParcelableExtra(intent, OpenPgpApi.RESULT_INTENT, PendingIntent::class.java)!!
        mConfirmationLauncher.launch(IntentSenderRequest.Builder(pi).build())
    }
}
