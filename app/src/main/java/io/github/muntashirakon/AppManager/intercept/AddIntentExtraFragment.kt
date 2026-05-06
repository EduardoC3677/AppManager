// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.intercept

import android.app.Dialog
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.annotation.IntDef
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.adapters.SelectedArrayAdapter
import io.github.muntashirakon.widget.MaterialSpinner
import java.io.Serializable

class AddIntentExtraFragment : DialogFragment() {
    @IntDef(value = [MODE_EDIT, MODE_CREATE, MODE_DELETE])
    @Retention(AnnotationRetention.SOURCE)
    annotation class Mode

    @IntDef(value = [TYPE_BOOLEAN, TYPE_COMPONENT_NAME, TYPE_FLOAT, TYPE_FLOAT_ARR, TYPE_FLOAT_AL, TYPE_INTEGER, TYPE_INT_ARR, TYPE_INT_AL, TYPE_LONG, TYPE_LONG_ARR, TYPE_LONG_AL, TYPE_NULL, TYPE_STRING, TYPE_STRING_ARR, TYPE_STRING_AL, TYPE_URI, TYPE_URI_ARR, TYPE_URI_AL])
    @Retention(AnnotationRetention.SOURCE)
    annotation class Type

    private var mOnSaveListener: OnSaveListener? = null

    interface OnSaveListener {
        fun onSave(@Mode mode: Int, extraItem: ExtraItem)
    }

    class ExtraItem : Serializable {
        @Type var type: Int = 0
        var keyName: String? = null
        var keyValue: Any? = null

        override fun toString(): String = "PrefItem{type=$type, keyName='$keyName', keyValue=$keyValue}"\ncompanion object {
            private const val serialVersionUID = 4815162342L
        }
    }

    private val mLayoutTypes = arrayOfNulls<ViewGroup>(TYPE_COUNT)
    private val mValues = arrayOfNulls<TextView>(TYPE_COUNT)
    @Type private var mCurrentType: Int = TYPE_STRING

    fun setOnSaveListener(onSaveListener: OnSaveListener?) {
        mOnSaveListener = onSaveListener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity = requireActivity()
        val args = requireArguments()
        val extraItem = args.getSerializable(ARG_PREF_ITEM) as? ExtraItem
        val mode = args.getInt(ARG_MODE, MODE_CREATE)
        val view = View.inflate(activity, R.layout.dialog_edit_pref_item, null)
        val spinner: MaterialSpinner = view.findViewById(R.id.type_selector_spinner)
        val spinnerAdapter: ArrayAdapter<CharSequence> = SelectedArrayAdapter.createFromResource(activity, R.array.extras_types, io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item)
        spinner.adapter = spinnerAdapter
        spinner.setOnItemClickListener { _, _, position, _ ->
            mLayoutTypes.forEach { it?.visibility = View.GONE }
            if (position != TYPE_NULL) {
                mLayoutTypes[position]?.apply {
                    visibility = View.VISIBLE
                    if (this is TextInputLayout) hint = spinnerAdapter.getItem(position)
                }
            }
            mCurrentType = position
        }

        val layoutIds = intArrayOf(R.id.layout_bool, R.id.layout_string, R.id.layout_float, R.id.layout_string, R.id.layout_string, R.id.layout_int, R.id.layout_string, R.id.layout_string, R.id.layout_long, R.id.layout_string, R.id.layout_string, R.id.layout_string, R.id.layout_string, R.id.layout_string, R.id.layout_string, R.id.layout_string, R.id.layout_string, R.id.layout_string)
        val valueIds = intArrayOf(R.id.input_bool, R.id.input_string, R.id.input_float, R.id.input_string, R.id.input_string, R.id.input_int, R.id.input_string, R.id.input_string, R.id.input_long, R.id.input_string, R.id.input_string, R.id.input_string, R.id.input_string, R.id.input_string, R.id.input_string, R.id.input_string, R.id.input_string, R.id.input_string)
        for (i in 0 until TYPE_COUNT) {
            mLayoutTypes[i] = view.findViewById(layoutIds[i])
            mValues[i] = view.findViewById(valueIds[i])
        }

        val editKeyName: TextInputEditText = view.findViewById(R.id.key_name)
        extraItem?.let { item ->
            mCurrentType = item.type
            editKeyName.setText(item.keyName)
            if (mode == MODE_EDIT) editKeyName.isEnabled = false
            mLayoutTypes.forEach { it?.visibility = View.GONE }
            if (mCurrentType != TYPE_NULL) {
                mLayoutTypes[mCurrentType]?.apply {
                    visibility = View.VISIBLE
                    if (this is TextInputLayout) hint = spinnerAdapter.getItem(mCurrentType)
                }
                item.keyValue?.let { val tv = mValues[mCurrentType]; if (tv is MaterialSwitch) tv.isChecked = it as Boolean else tv?.text = it.toString() }
            }
            spinner.setSelection(mCurrentType)
        }

        return MaterialAlertDialogBuilder(activity)
            .setView(view)
            .setPositiveButton(if (mode == MODE_CREATE) R.string.add else R.string.done) { _, _ ->
                val key = editKeyName.text?.toString()?.trim()
                if (key.isNullOrEmpty()) { UIUtils.displayLongToast(R.string.key_name_cannot_be_null); return@setPositiveButton }
                val newItem = extraItem ?: ExtraItem().apply { keyName = key }
                newItem.type = mCurrentType
                try {
                    newItem.keyValue = if (mCurrentType == TYPE_BOOLEAN) (mValues[mCurrentType] as MaterialSwitch).isChecked
                    else IntentCompat.parseExtraValue(mCurrentType, mValues[mCurrentType]?.text?.toString()?.trim() ?: "")
                } catch (e: Exception) {
                    e.printStackTrace(); UIUtils.displayLongToast(R.string.error_evaluating_input); return@setPositiveButton
                }
                mOnSaveListener?.onSave(mode, newItem)
            }
            .setNegativeButton(R.string.cancel) { _, _ -> dialog?.cancel() }
            .apply { if (mode == MODE_EDIT) setNeutralButton(R.string.delete) { _, _ -> mOnSaveListener?.onSave(MODE_DELETE, extraItem!!) } }
            .create()
    }

    companion object {
        const val TAG = "AddIntentExtraFragment"\nconst val ARG_PREF_ITEM = "ARG_PREF_ITEM"\nconst val ARG_MODE = "ARG_MODE"
        const val MODE_EDIT = 1
        const val MODE_CREATE = 2
        const val MODE_DELETE = 3
        const val TYPE_BOOLEAN = 0
        const val TYPE_COMPONENT_NAME = 1
        const val TYPE_FLOAT = 2
        const val TYPE_FLOAT_ARR = 3
        const val TYPE_FLOAT_AL = 4
        const val TYPE_INTEGER = 5
        const val TYPE_INT_ARR = 6
        const val TYPE_INT_AL = 7
        const val TYPE_LONG = 8
        const val TYPE_LONG_ARR = 9
        const val TYPE_LONG_AL = 10
        const val TYPE_NULL = 11
        const val TYPE_STRING = 12
        const val TYPE_STRING_ARR = 13
        const val TYPE_STRING_AL = 14
        const val TYPE_URI = 15
        const val TYPE_URI_ARR = 16
        const val TYPE_URI_AL = 17
        private const val TYPE_COUNT = 18
    }
}
