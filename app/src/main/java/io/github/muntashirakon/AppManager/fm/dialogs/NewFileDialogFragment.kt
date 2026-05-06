// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.dialogs

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
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.adapters.SelectedArrayAdapter
import io.github.muntashirakon.io.Paths
import io.github.muntashirakon.lifecycle.SoftInputLifeCycleObserver
import io.github.muntashirakon.widget.MaterialSpinner
import java.lang.ref.WeakReference

class NewFileDialogFragment : DialogFragment() {
    interface OnCreateNewFileInterface {
        fun onCreate(prefix: String, extension: String?, template: String)
    }

    private var mOnCreateNewFileInterface: OnCreateNewFileInterface? = null
    private var mDialogView: View? = null
    private var mEditText: TextInputEditText? = null
    private var mType: Int = 0

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mDialogView = View.inflate(requireActivity(), R.layout.dialog_new_file, null)
        mEditText = mDialogView!!.findViewById(R.id.name)
        val name = "New file.txt"\nmEditText!!.setText(name)
        handleFilename(name, null)
        val spinner: MaterialSpinner = mDialogView!!.findViewById(R.id.type_selector_spinner)
        val spinnerAdapter: ArrayAdapter<CharSequence> = SelectedArrayAdapter(
            requireContext(),
            io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item, TYPE_LABELS
        )
        spinner.adapter = spinnerAdapter
        spinner.setSelection(TYPE_TEXT)
        spinner.setOnItemClickListener { _, _, position, _ ->
            if (mType != position) {
                mType = position
                handleFilename(mEditText!!.text, TYPE_DEFAULT_EXTENSIONS[mType])
            }
        }
        return MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.create_new_file)
            .setView(mDialogView)
            .setPositiveButton(R.string.ok) { _, _ ->
                val editable = mEditText!!.text
                if (!TextUtils.isEmpty(editable) && mOnCreateNewFileInterface != null) {
                    val newName = editable.toString()
                    val prefix = Paths.trimPathExtension(newName)
                    val extension = Paths.getPathExtension(newName, false)
                    val template = getFileTemplateFromTypeAndExtension(mType, extension)
                    mOnCreateNewFileInterface!!.onCreate(prefix, extension, template)
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

    fun setOnCreateNewFileInterface(createNewFileInterface: OnCreateNewFileInterface?) {
        mOnCreateNewFileInterface = createNewFileInterface
    }

    private fun handleFilename(charSequence: CharSequence?, newExtension: String?) {
        if (charSequence == null) return
        var name = charSequence.toString()
        var lastIndex = name.lastIndexOf('.')
        if (newExtension != null && lastIndex != -1) {
            // Change extension before setting selection
            name = name.substring(0, lastIndex) + "." + newExtension
            mEditText!!.setText(name)
            lastIndex = name.lastIndexOf('.')
        }
        if (lastIndex != -1 || lastIndex == name.length - 1) {
            mEditText!!.setSelection(0, lastIndex)
        } else {
            mEditText!!.selectAll()
        }
    }

    companion object {
        val TAG: String = NewFileDialogFragment::class.java.simpleName

        @JvmStatic
        fun getInstance(createNewFileInterface: OnCreateNewFileInterface?): NewFileDialogFragment {
            val fragment = NewFileDialogFragment()
            fragment.setOnCreateNewFileInterface(createNewFileInterface)
            return fragment
        }

        private const val TYPE_TEXT = 0
        private const val TYPE_PDF = 1
        private const val TYPE_DOCS = 2
        private const val TYPE_SHEET = 3
        private const val TYPE_PRESENTATION = 4
        private const val TYPE_DB = 5

        private val TYPE_LABELS = arrayOf(
            "Text", // TYPE_TEXT
            "PDF", // TYPE_PDF
            "Document (.docx, .odt)", // TYPE_DOCS
            "Sheet (.xlsx, .ods)", // TYPE_SHEET
            "Presentation (.ppt, .odp)", // TYPE_PRESENTATION
            "Database" // TYPE_DB
        )

        private val TYPE_DEFAULT_EXTENSIONS = arrayOf(
            "txt", // TYPE_TEXT
            "pdf", // TYPE_PDF
            "docx", // TYPE_DOCS
            "xlsx", // TYPE_SHEET
            "pptx", // TYPE_PRESENTATION
            "db" // TYPE_DB
        )

        @JvmStatic
        private fun getFileTemplateFromTypeAndExtension(type: Int, extension: String?): String {
            val prefix = "blank"\nreturn when (type) {
                TYPE_TEXT -> "$prefix.txt"\nTYPE_PDF -> "$prefix.pdf"\nTYPE_DOCS -> prefix + if ("odt" == extension) ".odt" else ".docx"\nTYPE_SHEET -> prefix + if ("ods" == extension) ".ods" else ".xlsx"\nTYPE_PRESENTATION -> prefix + if ("odp" == extension) ".odp" else ".pptx"\nTYPE_DB -> "$prefix.db"\nelse -> "$prefix.txt"
            }
        }
    }
}
