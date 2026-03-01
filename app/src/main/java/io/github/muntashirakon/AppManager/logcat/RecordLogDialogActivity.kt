// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat

import android.os.Bundle
import io.github.muntashirakon.AppManager.BaseActivity

// Copyright 2012 Nolan Lawson
// Copyright 2021 Muntashir Al-Islam
class RecordLogDialogActivity : BaseActivity() {
    override fun getTransparentBackground(): Boolean = true

    override fun onAuthenticated(savedInstanceState: Bundle?) {
        val dialog = RecordLogDialogFragment.getInstance(intent.getStringArrayExtra(EXTRA_QUERY_SUGGESTIONS), null)
        dialog.show(supportFragmentManager, RecordLogDialogFragment.TAG)
        dialog.setOnDismissListener { finish() }
    }

    companion object {
        const val EXTRA_QUERY_SUGGESTIONS = "suggestions"
    }
}
