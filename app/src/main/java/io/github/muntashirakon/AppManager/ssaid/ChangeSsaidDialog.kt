// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ssaid

import android.app.Dialog
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.os.UserHandleHidden
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import java.io.IOException
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference

@RequiresApi(Build.VERSION_CODES.O)
class ChangeSsaidDialog : DialogFragment() {
    private var mSsaid: String? = null
    private var mOldSsaid: String? = null
    private var mSsaidChangedInterface: SsaidChangedInterface? = null
    private var mSsaidChangedResult: Future<*>? = null

    interface SsaidChangedInterface {
        @MainThread
        fun onSsaidChanged(newSsaid: String?, isSuccessful: Boolean)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity = requireActivity()
        mSsaid = requireArguments().getString(ARG_OPTIONAL_SSAID)
        mOldSsaid = mSsaid
        val packageName = requireArguments().getString(ARG_PACKAGE_NAME)!!
        val uid = requireArguments().getInt(ARG_UID)
        val sizeByte = if (packageName == "android") 32 else 8
        val view = layoutInflater.inflate(R.layout.dialog_ssaid_info, null)
        val alertDialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.ssaid)
            .setView(view)
            .setPositiveButton(R.string.apply, null)
            .setNegativeButton(R.string.close, null)
            .setNeutralButton(R.string.reset_to_default, null)
            .create()
        val ssaidEditText: TextInputEditText = view.findViewById(android.R.id.text1)
        val ssaidInputLayout: TextInputLayout = view.findViewById(R.id.ssaid_layout)
        val applyButtonRef = AtomicReference<Button>()
        val resetButtonRef = AtomicReference<Button>()

        alertDialog.setOnShowListener {
            val applyBtn = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val resetBtn = alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            applyButtonRef.set(applyBtn)
            resetButtonRef.set(resetBtn)
            applyBtn.visibility = View.GONE
            applyBtn.setOnClickListener {
                mSsaidChangedResult = ThreadUtils.postOnBackgroundThread {
                    try {
                        val editable = ssaidEditText.text ?: throw IOException("Empty SSAID field.")
                        mSsaid = editable.toString()
                        if (mSsaid!!.length != sizeByte * 2) throw IOException("Invalid SSAID size ${mSsaid!!.length}")
                        if (!mSsaid!!.matches("[0-9A-Fa-f]+".toRegex())) throw IOException("Invalid SSAID")
                        val success = SsaidSettings(UserHandleHidden.getUserId(uid)).setSsaid(packageName, uid, mSsaid)
                        if (success) alertDialog.dismiss()
                        mSsaidChangedInterface?.let { i -> ThreadUtils.postOnMainThread { i.onSsaidChanged(mSsaid, success) } }
                    } catch (e: IOException) {
                        e.printStackTrace()
                        mSsaidChangedInterface?.let { i -> ThreadUtils.postOnMainThread { i.onSsaidChanged(mSsaid, false) } }
                    }
                }
            }
            resetBtn.visibility = View.GONE
            resetBtn.setOnClickListener {
                mSsaid = mOldSsaid; ssaidEditText.setText(mSsaid)
                applyBtn.performClick()
                resetBtn.visibility = View.GONE; applyBtn.visibility = View.GONE
            }
        }
        ssaidEditText.setText(mSsaid)
        ssaidEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val valid = s?.toString() != mSsaid && s?.length == 2 * sizeByte
                resetButtonRef.get()?.visibility = if (valid && mOldSsaid != s?.toString()) View.VISIBLE else View.GONE
                applyButtonRef.get()?.visibility = if (valid) View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        ssaidInputLayout.setEndIconOnClickListener {
            mSsaid = SsaidSettings.generateSsaid(packageName); ssaidEditText.setText(mSsaid)
            if (mOldSsaid != mSsaid) {
                resetButtonRef.get()?.visibility = View.VISIBLE
                applyButtonRef.get()?.visibility = View.VISIBLE
            }
        }
        ssaidInputLayout.helperText = getString(R.string.input_ssaid_instruction, sizeByte, sizeByte * 2)
        ssaidInputLayout.counterMaxLength = sizeByte * 2
        return alertDialog
    }

    override fun onDismiss(dialog: DialogInterface) {
        mSsaidChangedResult?.cancel(true)
        super.onDismiss(dialog)
    }

    fun setSsaidChangedInterface(ssaidChangedInterface: SsaidChangedInterface?) {
        mSsaidChangedInterface = ssaidChangedInterface
    }

    companion object {
        val TAG: String = ChangeSsaidDialog::class.java.simpleName
        const val ARG_PACKAGE_NAME = "pkg"\nconst val ARG_UID = "uid"\nconst val ARG_OPTIONAL_SSAID = "ssaid"

        @JvmStatic
        fun getInstance(packageName: String, uid: Int, ssaid: String?): ChangeSsaidDialog {
            return ChangeSsaidDialog().apply { arguments = Bundle().apply { putString(ARG_PACKAGE_NAME, packageName); putInt(ARG_UID, uid); putString(ARG_OPTIONAL_SSAID, ssaid) } }
        }
    }
}
