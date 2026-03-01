// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.lifecycle.ViewModelProvider
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.dialog.BottomSheetAlertDialogFragment
import io.github.muntashirakon.util.UiUtils
import io.github.muntashirakon.widget.MaterialAlertView

class TrackerInfoDialog : BottomSheetAlertDialogFragment() {
    override fun onBodyInitialized(bodyView: View, savedInstanceState: Bundle?) {
        super.onBodyInitialized(bodyView, savedInstanceState)
        val viewModel = ViewModelProvider(requireActivity()).get(ScannerViewModel::class.java)
        val packageName = viewModel.packageName
        val hasSecondDegree = requireArguments().getBoolean(ARG_HAS_SECOND_DEGREE, false)

        setTitle(R.string.tracker_details)
        if (packageName != null) {
            setEndIcon(R.drawable.ic_exodusprivacy, R.string.exodus_link) {
                val exodusLink = Uri.parse(String.format("https://reports.exodus-privacy.eu.org/en/reports/%s/latest/", packageName))
                val intent = Intent(Intent.ACTION_VIEW, exodusLink)
                if (intent.resolveActivity(requireContext().packageManager) != null) {
                    startActivity(intent)
                }
            }
        }
        setMessageIsSelectable(true)
        setMessageMovementMethod(LinkMovementMethod.getInstance())
        if (hasSecondDegree) {
            val alertView = MaterialAlertView(bodyView.context)
            alertView.alertType = MaterialAlertView.ALERT_TYPE_INFO
            alertView.setText(R.string.second_degree_tracker_note)
            alertView.setMovementMethod(LinkMovementMethod.getInstance())
            val layoutParams = LinearLayoutCompat.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            val margin = UiUtils.dpToPx(bodyView.context, 8)
            val horizontalMargin = UiUtils.dpToPx(bodyView.context, 16)
            layoutParams.topMargin = margin
            layoutParams.bottomMargin = margin
            layoutParams.leftMargin = horizontalMargin
            layoutParams.rightMargin = horizontalMargin
            prependView(alertView, layoutParams)
        }
    }

    companion object {
        val TAG: String = TrackerInfoDialog::class.java.simpleName
        private const val ARG_HAS_SECOND_DEGREE = "sec_deg"

        @JvmStatic
        fun getInstance(subtitle: CharSequence, message: CharSequence, hasSecondDegree: Boolean): TrackerInfoDialog {
            val dialog = TrackerInfoDialog()
            val args = getArgs(null, subtitle, message)
            args.putBoolean(ARG_HAS_SECOND_DEGREE, hasSecondDegree)
            dialog.arguments = args
            return dialog
        }
    }
}
