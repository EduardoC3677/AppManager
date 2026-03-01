// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto.auth

import android.content.Intent
import android.os.Bundle
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.profiles.ProfileApplierActivity

class AuthFeatureDemultiplexer : BaseActivity() {
    override fun onAuthenticated(savedInstanceState: Bundle?) {
        val intent = intent
        if (!intent.hasExtra(EXTRA_AUTH) || !intent.hasExtra(EXTRA_FEATURE)) {
            finishAndRemoveTask()
            return
        }
        handleRequest(intent)
    }

    override fun getTransparentBackground(): Boolean = true

    private fun handleRequest(intent: Intent) {
        val auth = intent.getStringExtra(EXTRA_AUTH)
        val feature = intent.getStringExtra(EXTRA_FEATURE)
        intent.removeExtra(EXTRA_AUTH)
        intent.removeExtra(EXTRA_FEATURE)
        if (AuthManager.getKey() != auth) {
            finishAndRemoveTask()
            return
        }
        when (feature) {
            "profile" -> launchProfile(intent)
            else -> throw RuntimeException("Invalid feature: $feature")
        }
        finish()
    }

    fun launchProfile(intent: Intent) {
        val profileId = intent.getStringExtra(ProfileApplierActivity.EXTRA_PROFILE_ID)
        val state = intent.getStringExtra(ProfileApplierActivity.EXTRA_STATE)
        startActivity(ProfileApplierActivity.getAutomationIntent(applicationContext, profileId!!, state))
    }

    companion object {
        const val EXTRA_FEATURE = "feature"
    }
}
