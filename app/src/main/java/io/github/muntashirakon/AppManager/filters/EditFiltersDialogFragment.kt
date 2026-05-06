// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.Spanned
import android.text.TextUtils
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.filters.options.FilterOption
import io.github.muntashirakon.dialog.DialogTitleBuilder
import io.github.muntashirakon.view.TextInputLayoutCompat
import io.github.muntashirakon.widget.RecyclerView

class EditFiltersDialogFragment : DialogFragment(), EditFilterOptionFragment.OnClickDialogButtonInterface {
    interface OnSaveDialogButtonInterface {
        fun getFilterItem(): FilterItem
        fun onItemAltered(item: FilterItem)
    }

    private class ExprTester(private val mFilterItem: FilterItem) : AbsExpressionEvaluator() {
        override fun evalId(id: String): Boolean {
            if (TextUtils.isEmpty(id)) return false
            val idx = id.lastIndexOf('_')
            val intId = if (idx >= 0 && id.length > idx + 1) {
                val part2 = id.substring(idx + 1)
                if (TextUtils.isDigitsOnly(part2)) part2.toInt() else 0
            } else 0
            val option = mFilterItem.getFilterOptionForId(intId)
            if (option == null) lastError = "Invalid ID '$id'"\nreturn option != null
        }
    }

    private var mFinderFilterAdapter: FinderFilterAdapter? = null
    private var mFinderFilterEditorLayout: TextInputLayout? = null
    private var mFinderFilterEditor: TextInputEditText? = null
    private var mFilterItem: FilterItem? = null
    private var mOnSaveDialogButtonInterface: OnSaveDialogButtonInterface? = null
    private var mFilterEditorModified = false
    private var mExprTester: ExprTester? = null
    private val mFinderFilterEditorWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable) {
            updateEditorColors(s)
            mFilterEditorModified = true
        }
    }

    fun setOnSaveDialogButtonInterface(onSaveDialogButtonInterface: OnSaveDialogButtonInterface?) {
        mOnSaveDialogButtonInterface = onSaveDialogButtonInterface
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity = requireActivity()
        mFilterItem = mOnSaveDialogButtonInterface!!.getFilterItem()
        mFinderFilterAdapter = FinderFilterAdapter(mFilterItem!!)
        val view = View.inflate(activity, R.layout.dialog_edit_filter_item, null)
        val recyclerView: RecyclerView = view.findViewById(android.R.id.list)
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = mFinderFilterAdapter
        mFinderFilterEditor = view.findViewById(R.id.editor)
        mFinderFilterEditor!!.setText(mFilterItem!!.expr)
        mFinderFilterEditor!!.addTextChangedListener(mFinderFilterEditorWatcher)
        mFinderFilterEditorLayout = TextInputLayoutCompat.fromTextInputEditText(mFinderFilterEditor!!)
        val builder = DialogTitleBuilder(activity)
            .setTitle(R.string.filters)
            .setEndIcon(R.drawable.ic_add) {
                val dialogFragment = EditFilterOptionFragment()
                dialogFragment.setOnClickDialogButtonInterface(this)
                dialogFragment.show(childFragmentManager, EditFilterOptionFragment.TAG)
            }
            .setEndIconContentDescription(R.string.add_filter_ellipsis)
        mFinderFilterAdapter!!.setOnItemClickListener(object : FinderFilterAdapter.OnClickListener {
            override fun onEdit(view: View, position: Int, filterOption: FilterOption) {
                displayEditor(position, filterOption)
            }

            override fun onRemove(view: View, position: Int, filterOption: FilterOption) {
                onDeleteItem(position, filterOption.id)
            }
        })
        return MaterialAlertDialogBuilder(activity)
            .setCustomTitle(builder.build())
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.apply) { _, _ ->
                if (mFilterEditorModified && mFinderFilterEditorLayout!!.error == null) {
                    mFilterItem!!.expr = mFinderFilterEditor!!.text.toString()
                }
                mOnSaveDialogButtonInterface!!.onItemAltered(mFilterItem!!)
            }
            .create()
    }

    private fun displayEditor(position: Int, filterOption: FilterOption) {
        val dialogFragment = EditFilterOptionFragment()
        val args = Bundle()
        args.putParcelable(EditFilterOptionFragment.ARG_OPTION, filterOption)
        args.putInt(EditFilterOptionFragment.ARG_POSITION, position)
        dialogFragment.arguments = args
        dialogFragment.setOnClickDialogButtonInterface(this)
        dialogFragment.show(childFragmentManager, EditFilterOptionFragment.TAG)
    }

    override fun onAddItem(item: FilterOption) {
        mFinderFilterAdapter!!.add(item)
        mFinderFilterEditor!!.removeTextChangedListener(mFinderFilterEditorWatcher)
        mFinderFilterEditor!!.setText(mFilterItem!!.expr)
        updateEditorColors(mFinderFilterEditor!!.text)
        mFinderFilterEditor!!.addTextChangedListener(mFinderFilterEditorWatcher)
    }

    override fun onUpdateItem(position: Int, item: FilterOption) {
        mFinderFilterAdapter!!.update(position, item)
        mFinderFilterEditor!!.removeTextChangedListener(mFinderFilterEditorWatcher)
        mFinderFilterEditor!!.setText(mFilterItem!!.expr)
        updateEditorColors(mFinderFilterEditor!!.text)
        mFinderFilterEditor!!.addTextChangedListener(mFinderFilterEditorWatcher)
    }

    override fun onDeleteItem(position: Int, id: Int) {
        mFinderFilterAdapter!!.remove(position, id)
        mFinderFilterEditor!!.removeTextChangedListener(mFinderFilterEditorWatcher)
        mFinderFilterEditor!!.setText(mFilterItem!!.expr)
        updateEditorColors(mFinderFilterEditor!!.text)
        mFinderFilterEditor!!.addTextChangedListener(mFinderFilterEditorWatcher)
    }

    private fun updateEditorColors(s: Editable?) {
        if (mExprTester == null) {
            mExprTester = ExprTester(mFilterItem!!)
        }
        if (s == null) return
        val text = s.toString()
        for ((keyword, color) in HIGHLIGHT_MAP) {
            var index = text.indexOf(keyword)
            while (index >= 0) {
                s.setSpan(ForegroundColorSpan(color), index, index + keyword.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                index = text.indexOf(keyword, index + keyword.length)
            }
        }
        if (!mExprTester!!.evaluate(s.toString())) {
            mFinderFilterEditorLayout!!.error = mExprTester!!.lastError
        } else {
            mFinderFilterEditorLayout!!.error = null
        }
    }

    companion object {
        val TAG: String = EditFiltersDialogFragment::class.java.simpleName
        private val HIGHLIGHT_MAP = mapOf("&" to Color.RED, "|" to Color.RED, "(" to Color.RED, ")" to Color.RED, "true" to Color.BLUE, "false" to Color.BLUE)
    }
}
