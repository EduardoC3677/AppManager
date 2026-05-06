// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sharedpref

import android.app.Dialog
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.annotation.IntDef
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.adapters.SelectedArrayAdapter
import io.github.muntashirakon.widget.MaterialSpinner

class EditPrefItemFragment : DialogFragment() {
    @IntDef(value = [MODE_EDIT, MODE_CREATE, MODE_DELETE])
    @Retention(AnnotationRetention.SOURCE)
    annotation class Mode

    @IntDef(value = [TYPE_BOOLEAN, TYPE_FLOAT, TYPE_INTEGER, TYPE_LONG, TYPE_STRING, TYPE_SET])
    @Retention(AnnotationRetention.SOURCE)
    annotation class Type

    private var mInterfaceCommunicator: InterfaceCommunicator? = null

    interface InterfaceCommunicator {
        fun sendInfo(@Mode mode: Int, prefItem: PrefItem?)
    }

    class PrefItem : Parcelable {
        var keyName: String? = null
        var keyValue: Any? = null

        constructor()
        protected constructor(`in`: Parcel) {
            keyName = `in`.readString()
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeString(keyName)
        }

        override fun describeContents(): Int = 0

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<PrefItem> = object : Parcelable.Creator<PrefItem> {
                override fun createFromParcel(`in`: Parcel): PrefItem = PrefItem(`in`)
                override fun newArray(size: Int): Array<PrefItem?> = arrayOfNulls(size)
            }
        }
    }

    private val mLayoutTypes = arrayOfNulls<ViewGroup>(6)
    private val mValues = arrayOfNulls<TextView>(6)
    @Type
    private var mCurrentType: Int = 0

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity = requireActivity()
        val args = requireArguments()
        val prefItem = BundleCompat.getParcelable(args, ARG_PREF_ITEM, PrefItem::class.java)
        val mode = args.getInt(ARG_MODE)
        val view = View.inflate(activity, R.layout.dialog_edit_pref_item, null)
        val spinner: MaterialSpinner = view.findViewById(R.id.type_selector_spinner)
        val spinnerAdapter: ArrayAdapter<CharSequence> = SelectedArrayAdapter.createFromResource(activity,
            R.array.shared_pref_types, io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item)
        spinner.adapter = spinnerAdapter
        spinner.setOnItemClickListener { _, _, position, _ ->
            for (layout in mLayoutTypes) layout?.visibility = View.GONE
            mLayoutTypes[position]?.visibility = View.VISIBLE
            mCurrentType = position
        }

        mLayoutTypes[TYPE_BOOLEAN] = view.findViewById(R.id.layout_bool)
        mLayoutTypes[TYPE_FLOAT] = view.findViewById(R.id.layout_float)
        mLayoutTypes[TYPE_INTEGER] = view.findViewById(R.id.layout_int)
        mLayoutTypes[TYPE_LONG] = view.findViewById(R.id.layout_long)
        mLayoutTypes[TYPE_STRING] = view.findViewById(R.id.layout_string)
        mLayoutTypes[TYPE_SET] = view.findViewById(R.id.layout_string)

        mValues[TYPE_BOOLEAN] = view.findViewById(R.id.input_bool)
        mValues[TYPE_FLOAT] = view.findViewById(R.id.input_float)
        mValues[TYPE_INTEGER] = view.findViewById(R.id.input_int)
        mValues[TYPE_LONG] = view.findViewById(R.id.input_long)
        mValues[TYPE_STRING] = view.findViewById(R.id.input_string)
        mValues[TYPE_SET] = view.findViewById(R.id.input_string)

        val editKeyName: TextInputEditText = view.findViewById(R.id.key_name)
        if (prefItem != null) {
            val keyName = prefItem.keyName
            val keyValue = prefItem.keyValue
            editKeyName.setText(keyName)
            if (mode == MODE_EDIT) editKeyName.keyListener = null
            when (keyValue) {
                is Boolean -> {
                    mCurrentType = TYPE_BOOLEAN
                    mLayoutTypes[TYPE_BOOLEAN]?.visibility = View.VISIBLE
                    (mValues[TYPE_BOOLEAN] as MaterialSwitch).isChecked = keyValue
                    spinner.setSelection(TYPE_BOOLEAN)
                }
                is Float -> {
                    mCurrentType = TYPE_FLOAT
                    mLayoutTypes[TYPE_FLOAT]?.visibility = View.VISIBLE
                    mValues[TYPE_FLOAT]?.text = keyValue.toString()
                    spinner.setSelection(TYPE_FLOAT)
                }
                is Int -> {
                    mCurrentType = TYPE_INTEGER
                    mLayoutTypes[TYPE_INTEGER]?.visibility = View.VISIBLE
                    mValues[TYPE_INTEGER]?.text = keyValue.toString()
                    spinner.setSelection(TYPE_INTEGER)
                }
                is Long -> {
                    mCurrentType = TYPE_LONG
                    mLayoutTypes[TYPE_LONG]?.visibility = View.VISIBLE
                    mValues[TYPE_LONG]?.text = keyValue.toString()
                    spinner.setSelection(TYPE_LONG)
                }
                is String -> {
                    mCurrentType = TYPE_STRING
                    mLayoutTypes[TYPE_STRING]?.visibility = View.VISIBLE
                    mValues[TYPE_STRING]?.text = keyValue
                    spinner.setSelection(TYPE_STRING)
                }
                is Set<*> -> {
                    mCurrentType = TYPE_SET
                    mLayoutTypes[TYPE_SET]?.visibility = View.VISIBLE
                    @Suppress("UNCHECKED_CAST")
                    mValues[TYPE_SET]?.text = SharedPrefsUtil.flattenToString(keyValue as Set<String>)
                    spinner.setSelection(TYPE_SET)
                }
            }
        }
        mInterfaceCommunicator = activity as InterfaceCommunicator
        val builder = MaterialAlertDialogBuilder(activity)
            .setView(view)
            .setPositiveButton(if (mode == MODE_CREATE) R.string.add else R.string.done) { _, _ ->
                val newPrefItem = prefItem ?: PrefItem().apply { keyName = editKeyName.text.toString() }
                if (newPrefItem.keyName == null) {
                    UIUtils.displayLongToast(R.string.key_name_cannot_be_null)
                    return@setPositiveButton
                }
                try {
                    val valueStr = mValues[mCurrentType]?.text.toString()
                    when (mCurrentType) {
                        TYPE_BOOLEAN -> newPrefItem.keyValue = (mValues[mCurrentType] as MaterialSwitch).isChecked
                        TYPE_FLOAT -> newPrefItem.keyValue = valueStr.toFloat()
                        TYPE_INTEGER -> newPrefItem.keyValue = valueStr.toInt()
                        TYPE_LONG -> newPrefItem.keyValue = valueStr.toLong()
                        TYPE_STRING -> newPrefItem.keyValue = valueStr
                        TYPE_SET -> newPrefItem.keyValue = SharedPrefsUtil.unflattenToSet(valueStr)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    UIUtils.displayLongToast(R.string.error_evaluating_input)
                    return@setPositiveButton
                }
                mInterfaceCommunicator?.sendInfo(mode, newPrefItem)
            }
            .setNegativeButton(R.string.cancel) { _, _ -> dialog?.cancel() }
        if (mode == MODE_EDIT) builder.setNeutralButton(R.string.delete) { _, _ -> mInterfaceCommunicator?.sendInfo(MODE_DELETE, prefItem) }
        return builder.create()
    }

    companion object {
        val TAG: String = EditPrefItemFragment::class.java.simpleName
        const val ARG_PREF_ITEM = "ARG_PREF_ITEM"\nconst val ARG_MODE = "ARG_MODE"
        const val MODE_EDIT = 1
        const val MODE_CREATE = 2
        const val MODE_DELETE = 3
        private const val TYPE_BOOLEAN = 0
        private const val TYPE_FLOAT = 1
        private const val TYPE_INTEGER = 2
        private const val TYPE_LONG = 3
        private const val TYPE_STRING = 4
        private const val TYPE_SET = 5
    }
}
