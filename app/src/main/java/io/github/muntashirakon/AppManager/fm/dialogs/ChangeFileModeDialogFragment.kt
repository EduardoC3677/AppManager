// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.fm.FmUtils
import io.github.muntashirakon.widget.TextInputTextView

class ChangeFileModeDialogFragment : DialogFragment() {
    interface OnChangeFileModeInterface {
        fun onChangeMode(mode: Int, recursive: Boolean)
    }

    private var mOnChangeFileModeInterface: OnChangeFileModeInterface? = null
    private var mDialogView: View? = null
    private var mPreview: TextInputTextView? = null
    private var mRecursive: MaterialCheckBox? = null
    private var mOldMode: Int = 0
    private var mMode: Int = 0

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mOldMode = requireArguments().getInt(ARG_MODE)
        mMode = mOldMode
        val displayRecursive = requireArguments().getBoolean(ARG_DISPLAY_RECURSIVE)
        mDialogView = View.inflate(requireActivity(), R.layout.dialog_change_file_mode, null)

        setupCheckBox(R.id.user_read, 0x100) // 0400
        setupCheckBox(R.id.user_write, 0x80) // 0200
        setupCheckBox(R.id.user_exec, 0x40) // 0100
        setupCheckBox(R.id.group_read, 0x20) // 040
        setupCheckBox(R.id.group_write, 0x10) // 020
        setupCheckBox(R.id.group_exec, 0x8) // 010
        setupCheckBox(R.id.others_read, 0x4) // 04
        setupCheckBox(R.id.others_write, 0x2) // 02
        setupCheckBox(R.id.others_exec, 0x1) // 01

        setupSwitch(R.id.uid_bit, 0x800) // 04000
        setupSwitch(R.id.gid_bit, 0x400) // 02000
        setupSwitch(R.id.sticky_bit, 0x200) // 01000

        mPreview = mDialogView!!.findViewById(R.id.preview)
        mPreview!!.text = FmUtils.getFormattedMode(mOldMode)
        mRecursive = mDialogView!!.findViewById(R.id.checkbox)
        mRecursive!!.visibility = if (displayRecursive) View.VISIBLE else View.GONE

        return MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.change_mode)
            .setView(mDialogView)
            .setPositiveButton(R.string.ok) { _, _ ->
                mOnChangeFileModeInterface?.onChangeMode(mMode, mRecursive!!.isChecked)
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    private fun setupCheckBox(id: Int, flag: Int) {
        val checkBox: MaterialCheckBox = mDialogView!!.findViewById(id)
        checkBox.isChecked = (mOldMode and flag) != 0
        checkBox.setOnCheckedChangeListener { _, isChecked -> updateModePreview(flag, isChecked) }
    }

    private fun setupSwitch(id: Int, flag: Int) {
        val switch: MaterialSwitch = mDialogView!!.findViewById(id)
        switch.isChecked = (mOldMode and flag) != 0
        switch.setOnCheckedChangeListener { _, isChecked -> updateModePreview(flag, isChecked) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return mDialogView
    }

    fun setOnChangeFileModeInterface(changeFileModeInterface: OnChangeFileModeInterface?) {
        mOnChangeFileModeInterface = changeFileModeInterface
    }

    private fun updateModePreview(mode: Int, enabled: Boolean) {
        if (enabled) {
            mMode = mMode or mode
        } else {
            mMode = mMode and mode.inv()
        }
        mPreview!!.text = FmUtils.getFormattedMode(mMode)
    }

    companion object {
        val TAG: String = ChangeFileModeDialogFragment::class.java.simpleName
        private const val ARG_MODE = "mode"
        private const val ARG_DISPLAY_RECURSIVE = "recursive"

        @JvmStatic
        fun getInstance(
            mode: Int,
            recursive: Boolean,
            changeFileModeInterface: OnChangeFileModeInterface?
        ): ChangeFileModeDialogFragment {
            val fragment = ChangeFileModeDialogFragment()
            val args = Bundle()
            args.putInt(ARG_MODE, mode)
            args.putBoolean(ARG_DISPLAY_RECURSIVE, recursive)
            fragment.arguments = args
            fragment.setOnChangeFileModeInterface(changeFileModeInterface)
            return fragment
        }
    }
}
