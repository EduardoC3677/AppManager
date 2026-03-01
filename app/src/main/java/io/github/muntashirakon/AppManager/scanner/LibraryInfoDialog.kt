// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner

import android.os.Bundle
import android.view.View
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.dialog.BottomSheetAlertDialogFragment

class LibraryInfoDialog : BottomSheetAlertDialogFragment() {
    override fun onBodyInitialized(bodyView: View, savedInstanceState: Bundle?) {
        super.onBodyInitialized(bodyView, savedInstanceState)
        setTitle(R.string.lib_details)
        setMessageIsSelectable(true)
    }

    companion object {
        val TAG: String = LibraryInfoDialog::class.java.simpleName

        @JvmStatic
        fun getInstance(subtitle: CharSequence, message: CharSequence): LibraryInfoDialog {
            val dialog = LibraryInfoDialog()
            dialog.arguments = getArgs(null, subtitle, message)
            return dialog
        }
    }
}
