// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc

import android.annotation.SuppressLint
import android.app.SearchableInfo
import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Parcel
import android.os.Parcelable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import androidx.annotation.IntDef
import androidx.appcompat.widget.PopupMenu
import androidx.customview.view.AbsSavedState
import com.google.android.material.internal.ThemeEnforcement
import com.google.android.material.theme.overlay.MaterialThemeOverlay
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.util.UiUtils
import io.github.muntashirakon.widget.SearchView
import java.util.*
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

class AdvancedSearchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.searchViewStyle
) : SearchView(MaterialThemeOverlay.wrap(context, attrs, defStyleAttr, DEF_STYLE_RES), attrs, defStyleAttr) {

    @IntDef(flag = true, value = [SEARCH_TYPE_CONTAINS, SEARCH_TYPE_PREFIX, SEARCH_TYPE_SUFFIX, SEARCH_TYPE_REGEX])
    @Retention(AnnotationRetention.SOURCE)
    annotation class SearchType

    private var mType: Int = SEARCH_TYPE_CONTAINS
    private var mEnabledTypes: Int = SEARCH_TYPE_CONTAINS or SEARCH_TYPE_PREFIX or SEARCH_TYPE_SUFFIX or SEARCH_TYPE_REGEX
    private var mQueryHint: CharSequence? = null
    private val mSearchTypeSelectionButton: ImageView
    private val mSearchSrcTextView: SearchAutoComplete
    private val mSearchHintIcon: Drawable?
    private var mOnQueryTextListener: OnQueryTextListener? = null
    private var mOnSearchIconClickListener: OnClickListener? = null
    private val mOnSearchIconClickListenerSuper: OnClickListener
    private var mOnQueryTextFocusChangeListener: OnFocusChangeListener? = null
    private val mOnQueryTextFocusChangeListenerSuper: OnFocusChangeListener
    private val mOnQueryTextListenerSuper = object : SearchView.OnQueryTextListener {
        override fun onQueryTextSubmit(query: String): Boolean {
            return mOnQueryTextListener?.onQueryTextSubmit(query, mType) ?: false
        }

        override fun onQueryTextChange(newText: String): Boolean {
            return mOnQueryTextListener?.onQueryTextChange(newText, mType) ?: false
        }
    }
    private val onClickSearchIcon = View.OnClickListener { v ->
        val popupMenu = PopupMenu(getContext(), v)
        popupMenu.inflate(R.menu.view_advanced_search_type_selections)
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_search_type_contains -> mType = SEARCH_TYPE_CONTAINS
                R.id.action_search_type_prefix -> mType = SEARCH_TYPE_PREFIX
                R.id.action_search_type_suffix -> mType = SEARCH_TYPE_SUFFIX
                R.id.action_search_type_regex -> mType = SEARCH_TYPE_REGEX
            }
            mOnQueryTextListener?.onQueryTextChange(query.toString(), mType)
            updateQueryHint()
            true
        }
        val menu = popupMenu.menu
        if (mEnabledTypes and SEARCH_TYPE_CONTAINS == 0) menu.findItem(R.id.action_search_type_contains).isVisible = false
        if (mEnabledTypes and SEARCH_TYPE_PREFIX == 0) menu.findItem(R.id.action_search_type_prefix).isVisible = false
        if (mEnabledTypes and SEARCH_TYPE_SUFFIX == 0) menu.findItem(R.id.action_search_type_suffix).isVisible = false
        if (mEnabledTypes and SEARCH_TYPE_REGEX == 0) menu.findItem(R.id.action_search_type_regex).isVisible = false
        popupMenu.show()
    }

    init {
        val themedContext = getContext()
        mSearchSrcTextView = findViewById(com.google.android.material.R.id.search_src_text)
        mSearchTypeSelectionButton = findViewById(com.google.android.material.R.id.search_mag_icon)
        mSearchTypeSelectionButton.setImageResource(R.drawable.ic_filter_menu)
        mSearchTypeSelectionButton.background = UiUtils.getDrawable(themedContext, android.R.attr.selectableItemBackgroundBorderless)
        mSearchTypeSelectionButton.setOnClickListener(onClickSearchIcon)
        val a = ThemeEnforcement.obtainStyledAttributes(themedContext, attrs, io.github.muntashirakon.ui.R.styleable.SearchView, defStyleAttr, DEF_STYLE_RES)
        mQueryHint = a.getText(io.github.muntashirakon.ui.R.styleable.SearchView_queryHint)
        mSearchHintIcon = a.getDrawable(io.github.muntashirakon.ui.R.styleable.SearchView_searchHintIcon)
        a.recycle()
        isIconified = isIconified
        updateQueryHint()
        mOnQueryTextFocusChangeListenerSuper = OnFocusChangeListener { v, hasFocus ->
            v.postDelayed({
                if (!isIconified) {
                    mSearchTypeSelectionButton.visibility = VISIBLE
                }
            }, 1)
            mOnQueryTextFocusChangeListener?.onFocusChange(v, hasFocus)
        }
        mOnSearchIconClickListenerSuper = OnClickListener { v ->
            mSearchTypeSelectionButton.visibility = VISIBLE
            mOnSearchIconClickListener?.onClick(v)
        }
        mSearchSrcTextView.onFocusChangeListener = mOnQueryTextFocusChangeListenerSuper
        super.setOnSearchClickListener(mOnSearchIconClickListenerSuper)
    }

    protected class SavedState : AbsSavedState {
        var type: Int = 0
        var enabledTypes: Int = 0

        constructor(superState: Parcelable) : super(superState)
        constructor(source: Parcel, loader: ClassLoader?) : super(source, loader) {
            type = source.readInt()
            enabledTypes = source.readInt()
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            super.writeToParcel(dest, flags)
            dest.writeInt(type)
            dest.writeInt(enabledTypes)
        }

        override fun toString(): String {
            return "AdvancedSearchView.SavedState{${Integer.toHexString(System.identityHashCode(this))} type=$type enabledTypes=$enabledTypes}"
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.ClassLoaderCreator<SavedState> {
                override fun createFromParcel(source: Parcel, loader: ClassLoader?): SavedState = SavedState(source, loader)
                override fun createFromParcel(source: Parcel): SavedState = SavedState(source, null)
                override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
            }
        }
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        return SavedState(superState).apply {
            type = mType
            enabledTypes = mEnabledTypes
        }
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        if (state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            mType = state.type
            mEnabledTypes = state.enabledTypes
        } else super.onRestoreInstanceState(state)
        if (!isIconified) mSearchTypeSelectionButton.visibility = VISIBLE
        updateQueryHint()
        requestLayout()
    }

    override fun requestFocus(direction: Int, previouslyFocusedRect: Rect?): Boolean {
        val result = super.requestFocus(direction, previouslyFocusedRect)
        if (result && !isIconified) mSearchTypeSelectionButton.visibility = VISIBLE
        return result
    }

    override fun setOnQueryTextFocusChangeListener(listener: OnFocusChangeListener?) {
        mOnQueryTextFocusChangeListener = listener
    }

    fun setOnQueryTextListener(listener: OnQueryTextListener?) {
        mOnQueryTextListener = listener
        super.setOnQueryTextListener(if (listener == null) null else mOnQueryTextListenerSuper)
    }

    @Deprecated("Use OnQueryTextListener instead", ReplaceWith("setOnQueryTextListener(listener)"))
    override fun setOnQueryTextListener(listener: SearchView.OnQueryTextListener?) {
        throw UnsupportedOperationException("Wrong function. Use the other function by the same name.")
    }

    override fun setOnSearchClickListener(listener: OnClickListener?) {
        mOnSearchIconClickListener = listener
    }

    override fun setIconifiedByDefault(iconified: Boolean) {
        super.setIconifiedByDefault(iconified)
        updateQueryHint()
    }

    override fun setSearchableInfo(searchable: SearchableInfo?) {
        super.setSearchableInfo(searchable)
        if (!isIconified) mSearchTypeSelectionButton.visibility = VISIBLE
    }

    override fun setSubmitButtonEnabled(enabled: Boolean) {
        super.setSubmitButtonEnabled(enabled)
        if (!isIconified) mSearchTypeSelectionButton.visibility = VISIBLE
    }

    override fun setQueryHint(hint: CharSequence?) {
        super.setQueryHint(hint)
        mQueryHint = hint
    }

    fun setEnabledTypes(@SearchType enabledTypes: Int) {
        mEnabledTypes = enabledTypes
        if (mEnabledTypes == 0) mEnabledTypes = SEARCH_TYPE_CONTAINS
    }

    fun addEnabledTypes(@SearchType enabledTypes: Int) {
        mEnabledTypes = mEnabledTypes or enabledTypes
    }

    fun removeEnabledTypes(@SearchType enabledTypes: Int) {
        mEnabledTypes = mEnabledTypes and enabledTypes.inv()
        if (mEnabledTypes == 0) mEnabledTypes = SEARCH_TYPE_CONTAINS
    }

    private fun updateQueryHint() {
        val hintText = "$mQueryHint (${getQueryHint(mType)})"
        if (!isIconfiedByDefault && mSearchHintIcon != null) {
            val textSize = (mSearchSrcTextView.textSize * 1.25).toInt()
            mSearchHintIcon.setBounds(0, 0, textSize, textSize)
            val ssb = SpannableStringBuilder("   ")
            ssb.setSpan(ImageSpan(mSearchHintIcon), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            ssb.append(hintText)
            super.setQueryHint(ssb)
            return
        }
        super.setQueryHint(hintText)
    }

    private fun getQueryHint(@SearchType type: Int): CharSequence {
        return when (type) {
            SEARCH_TYPE_PREFIX -> context.getString(R.string.search_type_prefix)
            SEARCH_TYPE_REGEX -> context.getString(R.string.search_type_regular_expressions)
            SEARCH_TYPE_SUFFIX -> context.getString(R.string.search_type_suffix)
            else -> context.getString(R.string.search_type_contains)
        }
    }

    interface OnQueryTextListener {
        fun onQueryTextChange(newText: String, @SearchType type: Int): Boolean
        fun onQueryTextSubmit(query: String, @SearchType type: Int): Boolean
    }

    interface ChoiceGenerator<T> {
        fun getChoice(obj: T): String?
    }

    interface ChoicesGenerator<T> {
        fun getChoices(obj: T): List<String>
    }

    companion object {
        const val SEARCH_TYPE_CONTAINS = 1
        const val SEARCH_TYPE_PREFIX = 1 shl 1
        const val SEARCH_TYPE_SUFFIX = 1 shl 2
        const val SEARCH_TYPE_REGEX = 1 shl 3

        private val DEF_STYLE_RES = io.github.muntashirakon.ui.R.style.Widget_AppTheme_SearchView

        @JvmStatic
        fun matches(query: String, text: String, @SearchType type: Int): Boolean {
            return when (type) {
                SEARCH_TYPE_CONTAINS -> text.contains(query)
                SEARCH_TYPE_PREFIX -> text.startsWith(query)
                SEARCH_TYPE_SUFFIX -> text.endsWith(query)
                SEARCH_TYPE_REGEX -> text.matches(query.toRegex())
                else -> false
            }
        }

        @JvmStatic
        fun <T> matches(query: String, choices: Collection<T>?, generator: ChoiceGenerator<T>, @SearchType type: Int): List<T>? {
            if (choices == null) return null
            if (choices.isEmpty()) return emptyList()
            val results = mutableListOf<T>()
            if (type == SEARCH_TYPE_REGEX) {
                try {
                    val p = Pattern.compile(query)
                    for (choice in choices) {
                        generator.getChoice(choice)?.let { if (p.matcher(it).find()) results.add(choice) }
                    }
                } catch (ignore: PatternSyntaxException) {}
                return results
            }
            for (choice in choices) {
                generator.getChoice(choice)?.let { if (matches(query, it, type)) results.add(choice) }
            }
            return results
        }

        @JvmStatic
        fun <T> matches(query: String, choices: Collection<T>?, generator: ChoicesGenerator<T>, @SearchType type: Int): List<T>? {
            if (choices == null) return null
            if (choices.isEmpty()) return emptyList()
            val results = mutableListOf<T>()
            if (type == SEARCH_TYPE_REGEX) {
                try {
                    val p = Pattern.compile(query)
                    for (choice in choices) {
                        if (ThreadUtils.isInterrupted()) return emptyList()
                        for (text in generator.getChoices(choice)) {
                            if (p.matcher(text).find()) {
                                results.add(choice)
                                break
                            }
                        }
                    }
                } catch (ignore: PatternSyntaxException) {}
                return results
            }
            for (choice in choices) {
                if (ThreadUtils.isInterrupted()) return emptyList()
                for (text in generator.getChoices(choice)) {
                    if (matches(query, text, type)) {
                        results.add(choice)
                        break
                    }
                }
            }
            return results
        }
    }
}
