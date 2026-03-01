// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.CallSuper
import androidx.annotation.StringRes
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.dialog.CapsuleBottomSheetDialogFragment
import io.github.muntashirakon.widget.MaterialSpinner
import java.util.*

abstract class ListOptions : CapsuleBottomSheetDialogFragment() {
    interface ListOptionActions {
        fun setReverseSort(reverseSort: Boolean) {}
        fun isReverseSort(): Boolean = false
        fun setSortBy(sortBy: Int) {}
        fun getSortBy(): Int = 0
        fun hasFilterFlag(flag: Int): Boolean = false
        fun addFilterFlag(flag: Int) {}
        fun removeFilterFlag(flag: Int) {}
        fun isOptionSelected(option: Int): Boolean = false
        fun onOptionSelected(option: Int, selected: Boolean) {}
    }

    private lateinit var mSortText: TextView
    private lateinit var mSortGroup: ChipGroup
    private lateinit var mReverseSort: MaterialCheckBox
    private lateinit var mFilterText: TextView
    private lateinit var mFilterOptions: ChipGroup
    private lateinit var mOptionsText: TextView
    private lateinit var mOptionsView: LinearLayoutCompat
    private var mListOptionActions: ListOptionActions? = null
    private var mListOptionsViewModel: ListOptionsViewModel? = null

    protected lateinit var profileNameSpinner: MaterialSpinner
    protected lateinit var selectUserView: MaterialButton

    fun setListOptionActions(listOptionActions: ListOptionActions?) {
        mListOptionsViewModel?.setListOptionActions(listOptionActions) ?: run { mListOptionActions = listOptionActions }
    }

    @CallSuper
    override fun initRootView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.dialog_list_options, container, false)
    }

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mListOptionsViewModel = ViewModelProvider(this).get(ListOptionsViewModel::class.java)
        mSortText = view.findViewById(R.id.sort_text)
        mSortGroup = view.findViewById(R.id.sort_options)
        mReverseSort = view.findViewById(R.id.reverse_sort)
        mFilterText = view.findViewById(R.id.filter_text)
        mFilterOptions = view.findViewById(R.id.filter_options)
        mOptionsText = view.findViewById(R.id.options_text)
        mOptionsView = view.findViewById(R.id.options)
        profileNameSpinner = view.findViewById(R.id.spinner)
        selectUserView = view.findViewById(R.id.user)
        init(false)
    }

    abstract fun getSortIdLocaleMap(): LinkedHashMap<Int, Int>?
    abstract fun getFilterFlagLocaleMap(): LinkedHashMap<Int, Int>?
    abstract fun getOptionIdLocaleMap(): LinkedHashMap<Int, Int>?

    open fun enableProfileNameInput(): Boolean = false
    open fun enableSelectUser(): Boolean = false

    fun reloadUi() {
        init(true)
    }

    private fun requireListOptionActions(): ListOptionActions {
        val viewModel = mListOptionsViewModel ?: throw NullPointerException("ViewModel is not initialized.")
        mListOptionActions?.let {
            viewModel.setListOptionActions(it)
            mListOptionActions = null
        }
        return viewModel.getListOptionActions() ?: throw NullPointerException("ListOptionsActions must be set before calling init.")
    }

    private fun init(reinit: Boolean) {
        val actions = requireListOptionActions()
        val sortMap = getSortIdLocaleMap()
        val sortingEnabled = sortMap != null
        mSortText.visibility = if (sortingEnabled) View.VISIBLE else View.GONE
        mSortGroup.visibility = if (sortingEnabled) View.VISIBLE else View.GONE
        mReverseSort.visibility = if (sortingEnabled) View.VISIBLE else View.GONE
        if (sortingEnabled) {
            sortMap!!.forEach { (id, strRes) -> mSortGroup.addView(getRadioChip(id, strRes)) }
            mSortGroup.check(actions.getSortBy())
            mSortGroup.setOnCheckedStateChangeListener { _, _ -> actions.setSortBy(mSortGroup.checkedChipId) }
            mReverseSort.isChecked = actions.isReverseSort()
            mReverseSort.setOnCheckedChangeListener { _, isChecked -> actions.setReverseSort(isChecked) }
        }

        val filterMap = getFilterFlagLocaleMap()
        val filteringEnabled = filterMap != null
        mFilterText.visibility = if (filteringEnabled) View.VISIBLE else View.GONE
        mFilterOptions.visibility = if (filteringEnabled) View.VISIBLE else View.GONE
        if (filteringEnabled) {
            filterMap!!.forEach { (flag, strRes) -> mFilterOptions.addView(getFilterChip(flag, strRes)) }
        }

        val optionMap = getOptionIdLocaleMap()
        val optionsEnabled = optionMap != null
        mOptionsText.visibility = if (optionsEnabled) View.VISIBLE else View.GONE
        mOptionsView.visibility = if (optionsEnabled) View.VISIBLE else View.GONE
        if (optionsEnabled) {
            optionMap!!.forEach { (option, strRes) -> mOptionsView.addView(getOption(option, strRes)) }
        }

        profileNameSpinner.visibility = if (enableProfileNameInput()) View.VISIBLE else View.GONE
        selectUserView.visibility = if (enableSelectUser()) View.VISIBLE else View.GONE

        if (!reinit) {
            when {
                sortingEnabled && mSortGroup.childCount > 0 -> mSortGroup.getChildAt(0).requestFocus()
                filteringEnabled && mFilterOptions.childCount > 0 -> mFilterOptions.getChildAt(0).requestFocus()
                optionsEnabled && mOptionsView.childCount > 0 -> mOptionsView.getChildAt(0).requestFocus()
            }
        }
    }

    private fun getOption(option: Int, @StringRes strRes: Int): MaterialSwitch {
        val actions = requireListOptionActions()
        return (View.inflate(mOptionsView.context, R.layout.item_switch, null) as MaterialSwitch).apply {
            isFocusable = true
            id = option
            setText(strRes)
            isChecked = actions.isOptionSelected(option)
            setOnCheckedChangeListener { _, isChecked -> actions.onOptionSelected(option, isChecked) }
        }
    }

    private fun getFilterChip(flag: Int, @StringRes strRes: Int): Chip {
        val actions = requireListOptionActions()
        return Chip(mFilterOptions.context).apply {
            isFocusable = true
            isCloseIconVisible = false
            id = flag
            setText(strRes)
            isChecked = actions.hasFilterFlag(flag)
            setOnCheckedChangeListener { _, isChecked -> if (isChecked) actions.addFilterFlag(flag) else actions.removeFilterFlag(flag) }
        }
    }

    private fun getRadioChip(sortOrder: Int, @StringRes strRes: Int): Chip {
        return Chip(mSortGroup.context).apply {
            isFocusable = true
            isCloseIconVisible = false
            id = sortOrder
            setText(strRes)
        }
    }

    class ListOptionsViewModel(application: Application) : AndroidViewModel(application) {
        private var mListOptionActions: ListOptionActions? = null
        fun setListOptionActions(actions: ListOptionActions?) { mListOptionActions = actions }
        fun getListOptionActions(): ListOptionActions? = mListOptionActions
    }

    companion object {
        val TAG: String = ListOptions::class.java.simpleName
    }
}
