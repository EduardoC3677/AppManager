// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.dialog.BottomSheetAlertDialogFragment

class VirusTotalDialog : BottomSheetAlertDialogFragment() {
    override fun onBodyInitialized(bodyView: View, savedInstanceState: Bundle?) {
        super.onBodyInitialized(bodyView, savedInstanceState)
        val permalink = requireArguments().getString(ARG_PERMALINK)
        if (permalink != null) {
            setEndIcon(R.drawable.ic_vt, R.string.vt_permalink) {
                val vtPermalink = Uri.parse(permalink)
                val linkIntent = Intent(Intent.ACTION_VIEW, vtPermalink)
                if (linkIntent.resolveActivity(requireContext().packageManager) != null) {
                    startActivity(linkIntent)
                }
            }
        }
        setMessageIsSelectable(true)
    }

    companion object {
        val TAG: String = VirusTotalDialog::class.java.simpleName
        private const val ARG_PERMALINK = "permalink"

        @JvmStatic
        fun getInstance(title: CharSequence, subtitle: CharSequence, message: CharSequence, permalink: String?): VirusTotalDialog {
            val dialog = VirusTotalDialog()
            val args = getArgs(title, subtitle, message)
            args.putString(ARG_PERMALINK, permalink)
            dialog.arguments = args
            return dialog
        }
    }
}
