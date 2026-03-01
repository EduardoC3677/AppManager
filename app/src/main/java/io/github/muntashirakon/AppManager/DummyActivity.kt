// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager

import android.app.Activity
import android.os.Bundle

class DummyActivity : Activity() {
    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        finish()
    }
}
