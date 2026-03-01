// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm

import android.net.Uri
import android.os.Bundle
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.fm.dialogs.OpenWithDialogFragment

class OpenWithActivity : BaseActivity() {
    override fun getTransparentBackground(): Boolean = true

    override fun onAuthenticated(savedInstanceState: Bundle?) {
        val uri: Uri? = intent.data
        if (uri == null) {
            finish()
            return
        }
        val fragment = OpenWithDialogFragment.getInstance(uri, intent.type, true)
        fragment.show(supportFragmentManager, OpenWithDialogFragment.TAG)
    }
}
