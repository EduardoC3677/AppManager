// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.dialogs

import android.app.Dialog
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.io.Paths
import io.github.muntashirakon.lifecycle.SoftInputLifeCycleObserver
import java.lang.ref.WeakReference

class RenameDialogFragment : DialogFragment() {
    interface OnRenameFilesInterface {
        fun onRename(prefix: String, extension: String?)
    }

    private var mOnRenameFilesInterface: OnRenameFilesInterface? = null
    private var mDialogView: View? = null
    private var mEditText: TextInputEditText? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val name = arguments?.getString(ARG_NAME)
        mDialogView = View.inflate(requireActivity(), R.layout.dialog_rename, null)
        mEditText = mDialogView!!.findViewById(R.id.rename)
        mEditText!!.setText(name)
        if (name != null) {
            val lastIndex = name.lastIndexOf('.')
            if (lastIndex != -1 || lastIndex == name.length - 1) {
                mEditText!!.setSelection(0, lastIndex)
            } else {
                mEditText!!.selectAll()
            }
        }
        return MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.rename)
            .setView(mDialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val editable = mEditText!!.text
                if (!TextUtils.isEmpty(editable) && mOnRenameFilesInterface != null) {
                    val newName = editable.toString()
                    val prefix = Paths.trimPathExtension(newName)
                    val extension = Paths.getPathExtension(newName, false)
                    mOnRenameFilesInterface!!.onRename(prefix, extension)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return mDialogView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        lifecycle.addObserver(SoftInputLifeCycleObserver(WeakReference(mEditText)))
    }

    fun setOnRenameFilesInterface(renameFilesInterface: OnRenameFilesInterface?) {
        mOnRenameFilesInterface = renameFilesInterface
    }

    companion object {
        val TAG: String = RenameDialogFragment::class.java.simpleName
        private const val ARG_NAME = "name"

        @JvmStatic
        fun getInstance(name: String?, renameFilesInterface: OnRenameFilesInterface?): RenameDialogFragment {
            val fragment = RenameDialogFragment()
            val args = Bundle()
            args.putString(ARG_NAME, name)
            fragment.arguments = args
            fragment.setOnRenameFilesInterface(renameFilesInterface)
            return fragment
        }
    }
}
