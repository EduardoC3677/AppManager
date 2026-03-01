// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles

import android.app.Dialog
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.profiles.struct.BaseProfile
import io.github.muntashirakon.adapters.SelectedArrayAdapter
import io.github.muntashirakon.lifecycle.SoftInputLifeCycleObserver
import io.github.muntashirakon.view.TextInputLayoutCompat
import io.github.muntashirakon.widget.MaterialSpinner
import java.lang.ref.WeakReference

class NewProfileDialogFragment : DialogFragment() {
    fun interface OnCreateNewProfileInterface {
        fun onCreateNewProfile(newProfileName: String, @BaseProfile.ProfileType type: Int)
    }

    private var mOnCreateNewProfileInterface: OnCreateNewProfileInterface? = null
    private var mDialogView: View? = null
    private var mEditText: TextInputEditText? = null
    private var mType: Int = BaseProfile.PROFILE_TYPE_APPS

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mDialogView = View.inflate(requireActivity(), R.layout.dialog_new_file, null)
        mEditText = mDialogView!!.findViewById(R.id.name)
        val name = "Untitled profile"
        mEditText!!.setText(name)
        mEditText!!.selectAll()
        val editTextLayout: TextInputLayout = TextInputLayoutCompat.fromTextInputEditText(mEditText!!)
        editTextLayout.helperText = requireContext().getText(R.string.input_profile_name_description)
        val spinner: MaterialSpinner = mDialogView!!.findViewById(R.id.type_selector_spinner)
        val spinnerAdapter: ArrayAdapter<CharSequence> = SelectedArrayAdapter.createFromResource(requireContext(),
            R.array.profile_types, io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item)
        spinner.adapter = spinnerAdapter
        spinner.setSelection(BaseProfile.PROFILE_TYPE_APPS)
        spinner.setOnItemClickListener { _, _, position, _ ->
            if (mType != position) {
                mType = position
            }
        }
        return MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.new_profile)
            .setView(mDialogView)
            .setPositiveButton(R.string.go) { _, _ ->
                val editable = mEditText!!.text
                if (!TextUtils.isEmpty(editable) && mOnCreateNewProfileInterface != null) {
                    mOnCreateNewProfileInterface!!.onCreateNewProfile(editable.toString(), mType)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return mDialogView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        lifecycle.addObserver(SoftInputLifeCycleObserver(WeakReference(mEditText)))
    }

    fun setOnCreateNewProfileInterface(createNewProfileInterface: OnCreateNewProfileInterface?) {
        mOnCreateNewProfileInterface = createNewProfileInterface
    }

    companion object {
        val TAG: String = NewProfileDialogFragment::class.java.simpleName

        @JvmStatic
        fun getInstance(createNewProfileInterface: OnCreateNewProfileInterface?): NewProfileDialogFragment {
            val fragment = NewProfileDialogFragment()
            fragment.setOnCreateNewProfileInterface(createNewProfileInterface)
            return fragment
        }
    }
}
