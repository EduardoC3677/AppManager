// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import androidx.collection.ArraySet
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.google.android.material.resources.MaterialAttributes
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.utils.LangUtils
import io.github.muntashirakon.AppManager.utils.appearance.AppearanceUtils
import io.github.muntashirakon.util.AdapterUtils
import io.github.muntashirakon.util.UiUtils
import io.github.muntashirakon.widget.SearchView
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class ChangeLanguageFragment : Fragment() {
    private lateinit var mSearchView: SearchView
    private lateinit var mRecyclerView: io.github.muntashirakon.widget.RecyclerView
    private lateinit var mViewContainer: FrameLayout
    private var mAdapter: SearchableRecyclerViewAdapter<String>? = null
    private var mCurrentLang: String? = null
    private val mIsTextSelectable = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(io.github.muntashirakon.ui.R.layout.dialog_searchable_single_choice, container, false)
        mRecyclerView = view.findViewById(android.R.id.list)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mRecyclerView.scrollIndicators = 0
        }
        mRecyclerView.layoutManager = LinearLayoutManager(view.context, LinearLayoutManager.VERTICAL, false)
        mViewContainer = view.findViewById(io.github.muntashirakon.ui.R.id.container)
        mSearchView = view.findViewById(io.github.muntashirakon.ui.R.id.action_search)
        mSearchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                mAdapter?.setFilteredItems(newText)
                return true
            }
        })
        view.isFitsSystemWindows = true
        var secondary = false
        val args = arguments
        if (args != null) {
            secondary = args.getBoolean(PreferenceFragment.PREF_SECONDARY)
            args.remove(PreferenceFragment.PREF_KEY)
            args.remove(PreferenceFragment.PREF_SECONDARY)
        }
        if (secondary) {
            UiUtils.applyWindowInsetsAsPadding(view, false, true, false, true)
        } else {
            UiUtils.applyWindowInsetsAsPaddingNoTop(view)
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mCurrentLang = Prefs.Appearance.getLanguage()
        val locales = LangUtils.getAppLanguages(requireActivity())
        val languageNames = getLanguagesL(locales)
        val languages = arrayOfNulls<String>(languageNames.size)
        var i = 0
        var localeIndex = 0
        for (localeEntry in locales.entries) {
            languages[i] = localeEntry.key
            if (languages[i] == mCurrentLang) {
                localeIndex = i
            }
            ++i
        }
        @SuppressLint("RestrictedApi", "PrivateResource")
        val layoutId = MaterialAttributes.resolveInteger(
            requireContext(), androidx.appcompat.R.attr.singleChoiceItemLayout,
            com.google.android.material.R.layout.mtrl_alert_select_dialog_singlechoice
        )
        mAdapter = SearchableRecyclerViewAdapter(languageNames.toList(), languages.toList() as List<String>, layoutId)
        mAdapter!!.setSelectedIndex(localeIndex, false)
        mRecyclerView.adapter = mAdapter
    }

    override fun onStart() {
        super.onStart()
        requireActivity().setTitle(R.string.choose_language)
    }

    private fun getLanguagesL(locales: Map<String, Locale>): Array<CharSequence?> {
        val localesL = arrayOfNulls<CharSequence>(locales.size)
        var i = 0
        for (localeEntry in locales.entries) {
            val locale = localeEntry.value
            if (LangUtils.LANG_AUTO == localeEntry.key) {
                localesL[i] = getString(R.string.auto)
            } else {
                localesL[i] = locale.getDisplayName(locale)
            }
            ++i
        }
        return localesL
    }

    private fun triggerSingleChoiceClickListener(index: Int, isChecked: Boolean) {
        if (mAdapter == null) {
            return
        }
        val selectedItem = mAdapter!!.mItems[index]
        if (selectedItem != null && isChecked) {
            mCurrentLang = selectedItem
            Prefs.Appearance.setLanguage(mCurrentLang!!)
            AppearanceUtils.applyConfigurationChangesToActivities()
        }
    }

    internal inner class SearchableRecyclerViewAdapter<T>(
        private val mItemNames: List<CharSequence>,
        val mItems: List<T>,
        @LayoutRes private val mLayoutId: Int
    ) : RecyclerView.Adapter<SearchableRecyclerViewAdapter<T>.ViewHolder>() {
        private val mFilteredItems = ArrayList<Int>()
        private var mSelectedItem = -1
        private val mDisabledItems: MutableSet<Int> = ArraySet()

        init {
            synchronized(mFilteredItems) {
                for (i in mItems.indices) {
                    mFilteredItems.add(i)
                }
            }
        }

        fun setFilteredItems(constraint: String?) {
            var mConstraint = if (TextUtils.isEmpty(constraint)) null else constraint!!.lowercase(Locale.ROOT)
            val locale = Locale.getDefault()
            synchronized(mFilteredItems) {
                val previousCount = mFilteredItems.size
                mFilteredItems.clear()
                for (i in mItems.indices) {
                    if (mConstraint == null
                        || mItemNames[i].toString().lowercase(locale).contains(mConstraint)
                        || mItems[i].toString().lowercase(Locale.ROOT).contains(mConstraint)
                    ) {
                        mFilteredItems.add(i)
                    }
                }
                AdapterUtils.notifyDataSetChanged(this, previousCount, mFilteredItems.size)
            }
        }

        fun getSelection(): T? {
            return if (mSelectedItem >= 0) {
                mItems[mSelectedItem]
            } else null
        }

        fun setSelection(selectedItem: T?, triggerListener: Boolean) {
            if (selectedItem != null) {
                val index = mItems.indexOf(selectedItem)
                if (index != -1) {
                    setSelectedIndex(index, triggerListener)
                }
            }
        }

        fun setSelectedIndex(selectedIndex: Int, triggerListener: Boolean) {
            if (selectedIndex == mSelectedItem) {
                // Do nothing
                return
            }
            updateSelection(false, triggerListener)
            mSelectedItem = selectedIndex
            updateSelection(true, triggerListener)
            mRecyclerView.setSelection(selectedIndex)
        }

        fun addDisabledItems(disabledItems: List<T>?) {
            if (disabledItems != null) {
                for (item in disabledItems) {
                    val index = mItems.indexOf(item)
                    if (index != -1) {
                        synchronized(mDisabledItems) {
                            mDisabledItems.add(index)
                        }
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(mLayoutId, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val index = synchronized(mFilteredItems) {
                mFilteredItems[position]
            }
            val selected = AtomicBoolean(mSelectedItem == index)
            holder.item.text = mItemNames[index]
            holder.item.setTextIsSelectable(mIsTextSelectable)
            synchronized(mDisabledItems) {
                holder.item.isEnabled = !mDisabledItems.contains(index)
            }
            holder.item.isChecked = selected.get()
            holder.item.setOnClickListener { v: View? ->
                if (selected.get()) {
                    // Already selected, do nothing
                    return@setOnClickListener
                }
                // Unselect the previous and select this one
                updateSelection(false, true)
                mSelectedItem = index
                // Update selection manually
                selected.set(!selected.get())
                holder.item.isChecked = selected.get()
                triggerSingleChoiceClickListener(index, selected.get())
            }
        }

        override fun getItemCount(): Int {
            return synchronized(mFilteredItems) {
                mFilteredItems.size
            }
        }

        private fun updateSelection(selected: Boolean, triggerListener: Boolean) {
            if (mSelectedItem < 0) {
                return
            }
            val position = synchronized(mFilteredItems) {
                mFilteredItems.indexOf(mSelectedItem)
            }
            if (position >= 0) {
                notifyItemChanged(position, AdapterUtils.STUB)
            }
            if (triggerListener) {
                triggerSingleChoiceClickListener(mSelectedItem, selected)
            }
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            var item: CheckedTextView = itemView.findViewById(android.R.id.text1)

            init {
                @SuppressLint("RestrictedApi")
                val textAppearanceBodyLarge = MaterialAttributes.resolveInteger(
                    item.context,
                    com.google.android.material.R.attr.textAppearanceBodyLarge,
                    0
                )
                TextViewCompat.setTextAppearance(item, textAppearanceBodyLarge)
                item.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                item.setTextColor(
                    MaterialColors.getColor(
                        item.context,
                        com.google.android.material.R.attr.colorOnSurfaceVariant,
                        -1
                    )
                )
            }
        }
    }
}
