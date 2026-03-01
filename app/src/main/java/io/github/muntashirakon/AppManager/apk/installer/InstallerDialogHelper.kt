// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import com.google.android.material.textview.MaterialTextView
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.dialog.DialogTitleBuilder

class InstallerDialogHelper(fragment: InstallerDialogFragment, private val mDialog: AlertDialog) {
    interface OnClickButtonsListener {
        fun triggerInstall()
        fun triggerCancel()
    }

    private val mContext: Context = fragment.requireContext()
    private val mFragment: InstallerDialogFragment = fragment
    private val mTitleBuilder: DialogTitleBuilder = fragment.titleBuilder
    private val mFragmentContainer: FragmentContainerView
    private val mMessage: MaterialTextView
    private val mLayout: LinearLayoutCompat
    private val mFragmentId = R.id.fragment_container_view_tag
    private val mPositiveBtn: Button
    private val mNegativeBtn: Button
    private val mNeutralBtn: Button

    init {
        val view = mFragment.dialogView
        mFragmentContainer = view.findViewById(mFragmentId)
        mMessage = view.findViewById(R.id.message)
        mLayout = view.findViewById(R.id.layout)
        mPositiveBtn = mDialog.getButton(AlertDialog.BUTTON_POSITIVE)
        mNegativeBtn = mDialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        mNeutralBtn = mDialog.getButton(AlertDialog.BUTTON_NEUTRAL)
    }

    fun initProgress(cancelListener: View.OnClickListener?) {
        mTitleBuilder.setTitle(R.string._undefined)
            .setStartIcon(R.drawable.ic_get_app)
            .setSubtitle(null)
            .setEndIcon(null, null)
        mPositiveBtn.visibility = View.GONE
        mNeutralBtn.visibility = View.GONE
        mNegativeBtn.visibility = View.VISIBLE
        mNegativeBtn.setText(R.string.cancel)
        mNegativeBtn.setOnClickListener(cancelListener)
        val v = View.inflate(mContext, R.layout.dialog_progress2, null)
        val tv = v.findViewById<TextView>(android.R.id.text1)
        tv.setText(R.string.staging_apk_files)
        mLayout.visibility = View.VISIBLE
        mLayout.removeAllViews()
        mLayout.addView(v)
        mMessage.visibility = View.GONE
        mFragmentContainer.visibility = View.GONE
    }

    fun showParseFailedDialog(closeListener: View.OnClickListener?) {
        mTitleBuilder.setTitle(R.string._undefined)
            .setStartIcon(R.drawable.ic_get_app)
            .setSubtitle(null)
            .setEndIcon(null, null)
        mPositiveBtn.visibility = View.GONE
        mNeutralBtn.visibility = View.GONE
        mNegativeBtn.visibility = View.VISIBLE
        mNegativeBtn.setText(R.string.close)
        mNegativeBtn.setOnClickListener(closeListener)
        mLayout.visibility = View.GONE
        mMessage.visibility = View.VISIBLE
        mMessage.setText(R.string.failed_to_fetch_package_info)
        mFragmentContainer.visibility = View.GONE
    }

    fun onParseSuccess(title: CharSequence?, subtitle: CharSequence?, icon: Drawable?, optionsClickListener: View.OnClickListener?) {
        mTitleBuilder.setTitle(title)
            .setStartIcon(icon)
            .setSubtitle(subtitle)
        if (optionsClickListener != null) {
            mTitleBuilder.setEndIcon(R.drawable.ic_settings, optionsClickListener)
                .setEndIconContentDescription(R.string.installer_options)
        } else mTitleBuilder.setEndIcon(null, null)
    }

    fun showWhatsNewDialog(@StringRes installButtonRes: Int, fragment: Fragment, onClickButtonsListener: OnClickButtonsListener, appInfoButtonListener: View.OnClickListener?) {
        mNeutralBtn.visibility = View.VISIBLE
        mNeutralBtn.setText(R.string.app_info)
        mNeutralBtn.setOnClickListener(appInfoButtonListener)
        mPositiveBtn.visibility = View.VISIBLE
        mPositiveBtn.setText(installButtonRes)
        mPositiveBtn.setOnClickListener { onClickButtonsListener.triggerInstall() }
        mNegativeBtn.visibility = View.VISIBLE
        mNegativeBtn.setText(R.string.cancel)
        mNegativeBtn.setOnClickListener { onClickButtonsListener.triggerCancel() }
        mLayout.visibility = View.GONE
        mMessage.visibility = View.GONE
        mFragmentContainer.visibility = View.VISIBLE
        mFragment.childFragmentManager.beginTransaction().replace(mFragmentId, fragment).commit()
    }

    fun showInstallConfirmationDialog(@StringRes installButtonRes: Int, onClickButtonsListener: OnClickButtonsListener, appInfoButtonListener: View.OnClickListener?) {
        mNeutralBtn.visibility = View.VISIBLE
        mNeutralBtn.setText(R.string.app_info)
        mNeutralBtn.setOnClickListener(appInfoButtonListener)
        mPositiveBtn.visibility = View.VISIBLE
        mPositiveBtn.setText(installButtonRes)
        mPositiveBtn.setOnClickListener { onClickButtonsListener.triggerInstall() }
        mNegativeBtn.visibility = View.VISIBLE
        mNegativeBtn.setText(R.string.cancel)
        mNegativeBtn.setOnClickListener { onClickButtonsListener.triggerCancel() }
        mLayout.visibility = View.GONE
        mMessage.visibility = View.VISIBLE
        mMessage.setText(R.string.install_app_message)
        mFragmentContainer.visibility = View.GONE
    }

    fun showApkChooserDialog(@StringRes installButtonRes: Int, fragment: Fragment, onClickButtonsListener: OnClickButtonsListener, appInfoButtonListener: View.OnClickListener?) {
        mNeutralBtn.visibility = View.VISIBLE
        mNeutralBtn.setText(R.string.app_info)
        mNeutralBtn.setOnClickListener(appInfoButtonListener)
        mPositiveBtn.visibility = View.VISIBLE
        mPositiveBtn.setText(installButtonRes)
        mPositiveBtn.setOnClickListener { onClickButtonsListener.triggerInstall() }
        mNegativeBtn.visibility = View.VISIBLE
        mNegativeBtn.setText(R.string.cancel)
        mNegativeBtn.setOnClickListener { onClickButtonsListener.triggerCancel() }
        mLayout.visibility = View.GONE
        mMessage.visibility = View.GONE
        mFragmentContainer.visibility = View.VISIBLE
        mFragment.childFragmentManager.beginTransaction().replace(mFragmentId, fragment).commit()
    }

    fun showDowngradeReinstallWarning(msg: CharSequence?, onClickButtonsListener: OnClickButtonsListener, appInfoButtonListener: View.OnClickListener?) {
        mNeutralBtn.visibility = View.VISIBLE
        mNeutralBtn.setText(R.string.app_info)
        mNeutralBtn.setOnClickListener(appInfoButtonListener)
        mPositiveBtn.visibility = View.VISIBLE
        mPositiveBtn.setText(R.string.yes)
        mPositiveBtn.setOnClickListener { onClickButtonsListener.triggerInstall() }
        mNegativeBtn.visibility = View.VISIBLE
        mNegativeBtn.setText(R.string.cancel)
        mNegativeBtn.setOnClickListener { onClickButtonsListener.triggerCancel() }
        mLayout.visibility = View.GONE
        mMessage.visibility = View.VISIBLE
        mMessage.setText(msg)
        mFragmentContainer.visibility = View.GONE
    }

    fun showSignatureMismatchReinstallWarning(msg: CharSequence?, onClickButtonsListener: OnClickButtonsListener, installOnlyButtonListener: View.OnClickListener?, isSystem: Boolean) {
        mNeutralBtn.visibility = View.VISIBLE
        mNeutralBtn.setText(R.string.only_install)
        mNeutralBtn.setOnClickListener(installOnlyButtonListener)
        mPositiveBtn.visibility = if (isSystem) View.GONE else View.VISIBLE
        mPositiveBtn.setText(R.string.yes)
        mPositiveBtn.setOnClickListener { onClickButtonsListener.triggerInstall() }
        mNegativeBtn.visibility = View.VISIBLE
        mNegativeBtn.setText(R.string.cancel)
        mNegativeBtn.setOnClickListener { onClickButtonsListener.triggerCancel() }
        mLayout.visibility = View.GONE
        mMessage.visibility = View.VISIBLE
        mMessage.setText(msg)
        mFragmentContainer.visibility = View.GONE
    }

    fun showInstallProgressDialog(backgroundButtonListener: View.OnClickListener?) {
        mTitleBuilder.setEndIcon(null, null)
        mNeutralBtn.visibility = View.GONE
        if (backgroundButtonListener != null) {
            mPositiveBtn.visibility = View.VISIBLE
            mPositiveBtn.setText(R.string.background)
            mPositiveBtn.setOnClickListener(backgroundButtonListener)
        } else {
            mPositiveBtn.visibility = View.GONE
        }
        mNegativeBtn.visibility = View.GONE
        mLayout.visibility = View.VISIBLE
        val v = View.inflate(mContext, R.layout.dialog_progress2, null)
        val tv = v.findViewById<TextView>(android.R.id.text1)
        tv.setText(R.string.install_in_progress)
        mLayout.removeAllViews()
        mLayout.addView(v)
        mMessage.visibility = View.GONE
        mFragmentContainer.visibility = View.GONE
    }

    fun showInstallFinishedDialog(msg: CharSequence?, @StringRes cancelOrNextRes: Int, cancelClickListener: View.OnClickListener?, openButtonClickListener: View.OnClickListener?, appInfoButtonClickListener: View.OnClickListener?) {
        if (appInfoButtonClickListener != null) {
            mNeutralBtn.visibility = View.VISIBLE
            mNeutralBtn.setText(R.string.app_info)
            mNeutralBtn.setOnClickListener(appInfoButtonClickListener)
        } else mNeutralBtn.visibility = View.GONE
        if (openButtonClickListener != null) {
            mPositiveBtn.visibility = View.VISIBLE
            mPositiveBtn.setText(R.string.open)
            mPositiveBtn.setOnClickListener(openButtonClickListener)
        } else mPositiveBtn.visibility = View.GONE
        mNegativeBtn.visibility = View.VISIBLE
        mNegativeBtn.setText(cancelOrNextRes)
        mNegativeBtn.setOnClickListener(cancelClickListener)
        mLayout.visibility = View.GONE
        mMessage.visibility = View.VISIBLE
        mMessage.setText(msg)
        mFragmentContainer.visibility = View.GONE
    }

    fun dismiss() {
        mDialog.dismiss()
    }
}
