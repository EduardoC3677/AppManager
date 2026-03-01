// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.dialogs

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.io.Paths
import io.github.muntashirakon.lifecycle.SoftInputLifeCycleObserver
import java.lang.ref.WeakReference

class NewSymbolicLinkDialogFragment : DialogFragment() {
    interface OnCreateNewLinkInterface {
        fun onCreate(prefix: String, extension: String?, targetPath: String)
    }

    private var mOnCreateNewLinkInterface: OnCreateNewLinkInterface? = null
    private var mDialogView: View? = null
    private var mNameField: TextInputEditText? = null
    private var mTargetPathLayout: TextInputLayout? = null
    private var mTargetPathField: TextInputEditText? = null
    private var mValidPath = false

    private val mPathValidator: TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable) {
            if (TextUtils.isEmpty(s)) return
            val targetPath = Paths.get(s.toString())
            mValidPath = targetPath.getFile() != null && targetPath.exists()
            if (!mValidPath) {
                mTargetPathLayout!!.error = getText(R.string.invalid_target_path)
            } else {
                mTargetPathLayout!!.error = null
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mDialogView = View.inflate(requireActivity(), R.layout.dialog_new_symlink, null)
        mNameField = mDialogView!!.findViewById(R.id.name)
        mNameField!!.setText("New link")
        mNameField!!.selectAll()
        mTargetPathField = mDialogView!!.findViewById(R.id.target_file)
        mTargetPathLayout = mDialogView!!.findViewById(R.id.target_file_layout)
        mTargetPathField!!.addTextChangedListener(mPathValidator)
        return MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.create_new_symbolic_link)
            .setView(mDialogView)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = mNameField!!.text
                val targetPath = mTargetPathField!!.text
                if (!TextUtils.isEmpty(name) && mValidPath && mOnCreateNewLinkInterface != null) {
                    val newName = name.toString()
                    val prefix = Paths.trimPathExtension(newName)
                    val extension = Paths.getPathExtension(newName, false)
                    mOnCreateNewLinkInterface!!.onCreate(prefix, extension, targetPath.toString())
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
        lifecycle.addObserver(SoftInputLifeCycleObserver(WeakReference(mNameField)))
    }

    fun setOnCreateNewLinkInterface(createNewLinkInterface: OnCreateNewLinkInterface?) {
        mOnCreateNewLinkInterface = createNewLinkInterface
    }

    companion object {
        val TAG: String = NewSymbolicLinkDialogFragment::class.java.simpleName

        @JvmStatic
        fun getInstance(createNewLinkInterface: OnCreateNewLinkInterface?): NewSymbolicLinkDialogFragment {
            val fragment = NewSymbolicLinkDialogFragment()
            fragment.setOnCreateNewLinkInterface(createNewLinkInterface)
            return fragment
        }
    }
}
