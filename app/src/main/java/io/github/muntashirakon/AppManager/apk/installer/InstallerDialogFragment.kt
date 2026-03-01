// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.dialog.DialogTitleBuilder

class InstallerDialogFragment : DialogFragment() {
    fun interface FragmentStartedCallback {
        fun onStart(fragment: InstallerDialogFragment, dialog: AlertDialog)
    }

    private var mFragmentStartedCallback: FragmentStartedCallback? = null
    lateinit var dialogView: View
    lateinit var titleBuilder: DialogTitleBuilder

    fun setFragmentStartedCallback(fragmentStartedCallback: FragmentStartedCallback?) {
        mFragmentStartedCallback = fragmentStartedCallback
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        dialogView = View.inflate(requireContext(), R.layout.dialog_installer, null)
        titleBuilder = DialogTitleBuilder(requireContext())
        val titleView = titleBuilder.build()
        return MaterialAlertDialogBuilder(requireContext())
            .setCustomTitle(titleView)
            .setView(dialogView)
            .setPositiveButton(" ", null)
            .setNegativeButton(" ", null)
            .setNeutralButton(" ", null)
            .setCancelable(false)
            .create()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return dialogView
    }

    override fun onStart() {
        super.onStart()
        mFragmentStartedCallback?.onStart(this, requireDialog() as AlertDialog)
    }

    companion object {
        val TAG: String = InstallerDialogFragment::class.java.simpleName
    }
}
