// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.dialogs

import android.annotation.SuppressLint
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
import io.github.muntashirakon.lifecycle.SoftInputLifeCycleObserver
import java.lang.ref.WeakReference

class NewFolderDialogFragment : DialogFragment() {
    interface OnCreateNewFolderInterface {
        fun onCreate(name: String)
    }

    private var mOnCreateNewFolderInterface: OnCreateNewFolderInterface? = null
    private var mDialogView: View? = null
    private var mEditText: TextInputEditText? = null

    @SuppressLint("SetTextI18n")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mDialogView = View.inflate(requireActivity(), R.layout.dialog_rename, null)
        mEditText = mDialogView!!.findViewById(R.id.rename)
        mEditText!!.setText("New folder")
        mEditText!!.selectAll()
        return MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.create_new_folder)
            .setView(mDialogView)
            .setPositiveButton(R.string.ok) { _, _ ->
                val editable = mEditText!!.text
                if (!TextUtils.isEmpty(editable) && mOnCreateNewFolderInterface != null) {
                    mOnCreateNewFolderInterface!!.onCreate(editable.toString())
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

    fun setOnCreateNewFolderInterface(createNewFolderInterface: OnCreateNewFolderInterface?) {
        mOnCreateNewFolderInterface = createNewFolderInterface
    }

    companion object {
        val TAG: String = NewFolderDialogFragment::class.java.simpleName

        @JvmStatic
        fun getInstance(createNewFolderInterface: OnCreateNewFolderInterface?): NewFolderDialogFragment {
            val fragment = NewFolderDialogFragment()
            fragment.setOnCreateNewFolderInterface(createNewFolderInterface)
            return fragment
        }
    }
}
