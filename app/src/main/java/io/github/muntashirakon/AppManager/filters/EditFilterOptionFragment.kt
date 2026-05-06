// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckedTextView
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AlertDialog
import androidx.core.os.BundleCompat
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.resources.MaterialAttributes
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.filters.options.FilterOption
import io.github.muntashirakon.AppManager.filters.options.FilterOption.*
import io.github.muntashirakon.AppManager.filters.options.FilterOptions
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.AppManager.utils.DateUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.adapters.SelectedArrayAdapter
import io.github.muntashirakon.util.AdapterUtils
import io.github.muntashirakon.view.TextInputLayoutCompat
import io.github.muntashirakon.widget.MaterialSpinner
import mobi.upod.timedurationpicker.TimeDurationPickerDialog
import java.util.*
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

class EditFilterOptionFragment : DialogFragment() {
    interface OnClickDialogButtonInterface {
        fun onDeleteItem(position: Int, id: Int)
        fun onUpdateItem(position: Int, item: FilterOption)
        fun onAddItem(item: FilterOption)
    }

    private var mKeySpinner: MaterialSpinner? = null
    private var mGenericTextInputLayout: TextInputLayout? = null
    private var mGenericEditText: TextInputEditText? = null
    private var mDateTextInputLayout: TextInputLayout? = null
    private var mDateEditText: TextInputEditText? = null
    private var mFlagsRecyclerView: RecyclerView? = null
    private var mFilterOption: FilterOption? = null
    private var mCurrentFilterOption: FilterOption? = null
    private var mCurrentKey: String? = null
    @FilterOption.KeyType
    private var mCurrentKeyType: Int = TYPE_NONE
    private var mKeyAdapter: ArrayAdapter<String>? = null
    private var mFilterOptionFlagsAdapter: FilterOptionFlagsAdapter? = null
    private var mOnClickDialogButtonInterface: OnClickDialogButtonInterface? = null
    private var mPosition: Int = 0
    private var mDate: Long = 0
    private val mGenericEditTextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable) {
            if (mCurrentKeyType != TYPE_NONE && TextUtils.isEmpty(s)) {
                mGenericTextInputLayout!!.isErrorEnabled = true
                mGenericTextInputLayout!!.error = getString(R.string.value_cannot_be_empty)
                return
            }
            when (mCurrentKeyType) {
                TYPE_REGEX -> {
                    try {
                        Pattern.compile(s.toString())
                    } catch (e: PatternSyntaxException) {
                        mGenericTextInputLayout!!.isErrorEnabled = true
                        mGenericTextInputLayout!!.error = getString(R.string.invalid_regex)
                        return
                    }
                }
                TYPE_DURATION_MILLIS -> {
                    try {
                        mDate = s.toString().toLong()
                        mDateEditText!!.setText(DateUtils.getFormattedDuration(ContextUtils.getContext(), mDate))
                    } catch (ignore: NumberFormatException) {}
                }
                TYPE_TIME_MILLIS -> {
                    try {
                        mDate = s.toString().toLong()
                        mDateEditText!!.setText(DateUtils.formatDate(ContextUtils.getContext(), mDate))
                    } catch (ignore: NumberFormatException) {}
                }
                TYPE_INT_FLAGS -> {
                    try {
                        mFilterOptionFlagsAdapter!!.setFlag(s.toString().toInt())
                    } catch (ignore: NumberFormatException) {}
                }
            }
            mGenericTextInputLayout!!.isErrorEnabled = false
        }
    }

    fun setOnClickDialogButtonInterface(onClickDialogButtonInterface: OnClickDialogButtonInterface?) {
        mOnClickDialogButtonInterface = onClickDialogButtonInterface
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity = requireActivity()
        val args = requireArguments()
        mFilterOption = BundleCompat.getParcelable(args, ARG_OPTION, FilterOption::class.java)
        mPosition = args.getInt(ARG_POSITION, -1)
        val editMode = mFilterOption != null
        val view = View.inflate(activity, R.layout.dialog_edit_filter_option, null)
        val filterSpinner: MaterialSpinner = view.findViewById(R.id.filter_selector_spinner)
        val filters: ArrayAdapter<CharSequence> = SelectedArrayAdapter.createFromResource(activity, R.array.finder_filters, io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item)
        filterSpinner.adapter = filters
        mKeySpinner = view.findViewById(R.id.type_selector_spinner)
        mKeyAdapter = SelectedArrayAdapter(requireActivity(), io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item)
        mKeySpinner!!.adapter = mKeyAdapter
        mGenericEditText = view.findViewById(R.id.input_string)
        mGenericEditText!!.addTextChangedListener(mGenericEditTextWatcher)
        mGenericTextInputLayout = TextInputLayoutCompat.fromTextInputEditText(mGenericEditText!!)
        mDateEditText = view.findViewById(android.R.id.input)
        mDateEditText!!.keyListener = null
        mDateTextInputLayout = TextInputLayoutCompat.fromTextInputEditText(mDateEditText!!)
        mFlagsRecyclerView = view.findViewById(R.id.recycler_view)
        mFlagsRecyclerView!!.layoutManager = LinearLayoutManager(requireContext())
        @SuppressLint("RestrictedApi", "PrivateResource")
        val layoutId = MaterialAttributes.resolveInteger(requireContext(), androidx.appcompat.R.attr.multiChoiceItemLayout,
            com.google.android.material.R.layout.mtrl_alert_select_dialog_multichoice)
        mFilterOptionFlagsAdapter = FilterOptionFlagsAdapter(layoutId) {
            mGenericEditText!!.setText(mFilterOptionFlagsAdapter!!.flag.toString())
        }
        mFlagsRecyclerView!!.adapter = mFilterOptionFlagsAdapter
        if (mFilterOption != null) {
            mCurrentFilterOption = mFilterOption
            filterSpinner.setSelection(filters.getPosition(mCurrentFilterOption!!.type))
            updateUiForFilter(mCurrentFilterOption!!)
        } else {
            filterSpinner.setSelection(-1)
        }
        filterSpinner.setOnItemClickListener { _, _, position, _ ->
            mCurrentFilterOption = FilterOptions.create(filters.getItem(position).toString())
            updateUiForFilter(mCurrentFilterOption!!)
        }
        mKeySpinner!!.setOnItemClickListener { _, _, position, _ ->
            if (mKeyAdapter == null || mCurrentFilterOption == null) return@setOnItemClickListener
            mCurrentKey = mKeyAdapter!!.getItem(position)
            val lastKeyType = mCurrentKeyType
            mCurrentKeyType = mCurrentFilterOption!!.keysWithType[mCurrentKey]!!
            if (lastKeyType != mCurrentKeyType) mGenericEditText!!.setText("")
            updateUiForType(mCurrentKeyType)
        }
        val builder = MaterialAlertDialogBuilder(activity)
            .setView(view)
            .setPositiveButton(if (editMode) R.string.update else R.string.add) { _, which ->
                if (mCurrentFilterOption == null) {
                    UIUtils.displayLongToast(R.string.key_name_cannot_be_null)
                    return@setPositiveButton
                }
                val editable = mGenericEditText!!.text
                try {
                    mCurrentFilterOption!!.setKeyValue(mCurrentKey!!, if (TextUtils.isEmpty(editable)) null else editable.toString())
                } catch (e: Exception) {
                    e.printStackTrace()
                    UIUtils.displayLongToast(R.string.error_evaluating_input)
                    return@setPositiveButton
                }
                if (editMode) mOnClickDialogButtonInterface!!.onUpdateItem(mPosition, mCurrentFilterOption!!)
                else mOnClickDialogButtonInterface!!.onAddItem(mCurrentFilterOption!!)
            }
            .setNegativeButton(R.string.cancel) { _, _ -> dialog?.cancel() }
        if (editMode) {
            builder.setNeutralButton(R.string.delete) { _, _ -> mOnClickDialogButtonInterface!!.onDeleteItem(mPosition, mFilterOption!!.id) }
        }
        return builder.create()
    }

    private fun updateUiForFilter(filterOption: FilterOption) {
        if (mKeyAdapter == null) return
        mKeyAdapter!!.clear()
        mKeyAdapter!!.addAll(filterOption.keysWithType.keys)
        mCurrentKey = filterOption.key
        mCurrentKeyType = filterOption.keyType
        mKeySpinner!!.setSelection(mKeyAdapter!!.getPosition(mCurrentKey))
        mGenericEditText!!.setText(filterOption.value)
        updateUiForType(mCurrentKeyType)
    }

    private fun updateUiForType(@FilterOption.KeyType type: Int) {
        when (type) {
            TYPE_NONE, TYPE_INT_FLAGS -> {
                mGenericTextInputLayout!!.visibility = View.GONE
                mDateTextInputLayout!!.visibility = View.GONE
            }
            TYPE_DURATION_MILLIS, TYPE_TIME_MILLIS -> {
                mGenericTextInputLayout!!.visibility = View.GONE
                mDateTextInputLayout!!.visibility = View.VISIBLE
            }
            else -> {
                mGenericTextInputLayout!!.visibility = View.VISIBLE
                mDateTextInputLayout!!.visibility = View.GONE
            }
        }
        mFlagsRecyclerView!!.visibility = if (type == TYPE_INT_FLAGS) View.VISIBLE else View.GONE
        mGenericEditText!!.isSingleLine = type != TYPE_STR_MULTIPLE
        when (type) {
            TYPE_INT_FLAGS -> {
                mGenericEditText!!.inputType = InputType.TYPE_CLASS_NUMBER
                mFilterOptionFlagsAdapter!!.setFlagMap(mCurrentFilterOption!!.getFlags(mCurrentKey!!))
            }
            TYPE_DURATION_MILLIS -> {
                mDateTextInputLayout!!.hint = getString(R.string.duration)
                mGenericEditText!!.inputType = InputType.TYPE_CLASS_NUMBER
                mDateEditText!!.setOnClickListener { openDurationPicker() }
            }
            TYPE_INT -> {
                mGenericTextInputLayout!!.hint = getString(R.string.integer_value)
                mGenericEditText!!.inputType = InputType.TYPE_CLASS_NUMBER
            }
            TYPE_LONG -> {
                mGenericTextInputLayout!!.hint = getString(R.string.long_integer_value)
                mGenericEditText!!.inputType = InputType.TYPE_CLASS_NUMBER
            }
            TYPE_REGEX -> {
                mGenericTextInputLayout!!.hint = getString(R.string.search_option_regex)
                mGenericEditText!!.inputType = InputType.TYPE_CLASS_TEXT
            }
            TYPE_SIZE_BYTES -> {
                mGenericTextInputLayout!!.hint = getString(R.string.size_in_bytes)
                mGenericEditText!!.inputType = InputType.TYPE_CLASS_NUMBER
            }
            TYPE_STR_MULTIPLE -> {
                mGenericTextInputLayout!!.hint = getString(R.string.string_value)
                mGenericEditText!!.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            }
            TYPE_STR_SINGLE -> {
                mGenericTextInputLayout!!.hint = getString(R.string.string_value)
                mGenericEditText!!.inputType = InputType.TYPE_CLASS_TEXT
            }
            TYPE_TIME_MILLIS -> {
                mDateTextInputLayout!!.hint = getString(R.string.date)
                mGenericEditText!!.inputType = InputType.TYPE_CLASS_NUMBER
                mDateEditText!!.setOnClickListener { openDatePicker() }
            }
            else -> {}
        }
    }

    fun openDatePicker() {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.date)
            .setSelection(if (mDate <= 0) MaterialDatePicker.todayInUtcMilliseconds() else mDate)
            .build()
        datePicker.addOnPositiveButtonClickListener { selection -> mGenericEditText!!.setText(selection.toString()) }
        datePicker.show(childFragmentManager, "DatePicker")
    }

    fun openDurationPicker() {
        TimeDurationPickerDialog(requireContext(), { _, duration -> mGenericEditText!!.setText(duration.toString()) }, mDate).show()
    }

    private class FilterOptionFlagsAdapter(@LayoutRes private val mLayoutId: Int, private val mItemClickListener: View.OnClickListener) : RecyclerView.Adapter<FilterOptionFlagsAdapter.ViewHolder>() {
        private val mFlags = Collections.synchronizedList(ArrayList<Int>())
        private var mFlagMap: Map<Int, CharSequence> = emptyMap()
        var flag: Int = 0
            private set

        fun setFlagMap(flagMap: Map<Int, CharSequence>) {
            mFlagMap = flagMap
            AdapterUtils.notifyDataSetChanged(this, mFlags, ArrayList(flagMap.keys))
        }

        fun setFlag(flag: Int) {
            this.flag = flag
            notifyItemRangeChanged(0, mFlags.size, AdapterUtils.STUB)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(mLayoutId, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val f = mFlags[position]
            holder.item.text = mFlagMap[f]
            holder.item.isChecked = (flag and f) != 0
            holder.item.setOnClickListener {
                if ((flag and f) != 0) {
                    flag = flag and f.inv()
                    holder.item.isChecked = false
                } else {
                    flag = flag or f
                    holder.item.isChecked = true
                }
                mItemClickListener.onClick(it)
            }
        }

        override fun getItemCount(): Int = mFlags.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val item: CheckedTextView = itemView.findViewById(android.R.id.text1)

            init {
                @SuppressLint("RestrictedApi")
                val textAppearanceBodyLarge = MaterialAttributes.resolveInteger(item.context, com.google.android.material.R.attr.textAppearanceBodyLarge, 0)
                TextViewCompat.setTextAppearance(item, textAppearanceBodyLarge)
                item.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                item.setTextColor(MaterialColors.getColor(item.context, com.google.android.material.R.attr.colorOnSurfaceVariant, -1))
            }
        }
    }

    companion object {
        val TAG: String = EditFilterOptionFragment::class.java.simpleName
        const val ARG_OPTION = "opt"\nconst val ARG_POSITION = "pos"
    }
}
