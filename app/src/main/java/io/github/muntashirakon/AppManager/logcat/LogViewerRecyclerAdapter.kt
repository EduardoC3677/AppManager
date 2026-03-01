// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.collection.SparseArrayCompat
import androidx.core.content.ContextCompat
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.logcat.struct.LogLine
import io.github.muntashirakon.AppManager.logcat.struct.SearchCriteria
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.utils.Utils
import io.github.muntashirakon.util.AccessibilityUtils
import io.github.muntashirakon.util.AdapterUtils
import io.github.muntashirakon.widget.MultiSelectionView
import java.util.*

// Copyright 2012 Nolan Lawson
// Copyright 2021 Muntashir Al-Islam
class LogViewerRecyclerAdapter : MultiSelectionView.Adapter<LogViewerRecyclerAdapter.ViewHolder>(), Filterable {
    private val mLock = Any()
    private var mObjects: MutableList<LogLine> = Collections.synchronizedList(mutableListOf())

    private var mSearchByClickListener: ViewHolder.OnSearchByClickListener? = null
    private var mOriginalValues: ArrayList<LogLine>? = null
    private var mFilter: ArrayFilter? = null
    private var mLogLevelLimit: Int = Prefs.LogViewer.getLogLevel()
    val selectedLogLines: MutableSet<LogLine> = LinkedHashSet()

    var isCollapsedMode: Boolean = false
        set(value) {
            field = value
            synchronized(mLock) {
                val list = mOriginalValues ?: mObjects
                for (logLine in list) {
                    logLine.isExpanded = !value
                }
            }
        }

    init {
        setHasStableIds(true)
    }

    fun setClickListener(listener: ViewHolder.OnSearchByClickListener) {
        mSearchByClickListener = listener
    }

    fun add(logLine: LogLine, notify: Boolean) {
        synchronized(mLock) {
            mOriginalValues?.add(logLine)
            mObjects.add(logLine)
            if (notify) {
                notifyItemInserted(mObjects.size - 1)
            }
        }
    }

    fun addAll(logLines: List<LogLine>, notify: Boolean) {
        synchronized(mLock) {
            mOriginalValues?.addAll(logLines)
            val start = mObjects.size
            mObjects.addAll(logLines)
            if (notify) {
                notifyItemRangeInserted(start, logLines.size)
            }
        }
    }

    fun addWithFilter(logLine: LogLine, searchCriteria: SearchCriteria?, notify: Boolean) {
        if (mOriginalValues != null) {
            val inputList = Collections.singletonList(logLine)
            if (mFilter == null) mFilter = ArrayFilter()
            val filteredObjects = mFilter!!.performFilteringOnList(inputList, searchCriteria)
            synchronized(mLock) {
                mOriginalValues!!.add(logLine)
                mObjects.addAll(filteredObjects)
                if (filteredObjects.isNotEmpty() && notify) {
                    notifyItemRangeInserted(mObjects.size - filteredObjects.size, filteredObjects.size)
                }
            }
        } else {
            synchronized(mLock) {
                mObjects.add(logLine)
                if (notify) {
                    notifyItemInserted(mObjects.size - 1)
                }
            }
        }
    }

    fun removeFirst(n: Int) {
        val stopWatch = Utils.StopWatch("removeFirst()")
        if (mOriginalValues != null) {
            synchronized(mLock) {
                for (i in 0 until n) {
                    val logLine = mOriginalValues!![i]
                    val pos = mObjects.indexOf(logLine)
                    if (pos >= 0) {
                        mObjects.removeAt(pos)
                        notifyItemRemoved(pos)
                    }
                }
                mOriginalValues = ArrayList(mOriginalValues!!.subList(n, mOriginalValues!!.size))
            }
        } else {
            synchronized(mLock) {
                mObjects = ArrayList(mObjects.subList(n, mObjects.size))
                notifyItemRangeRemoved(0, n)
            }
        }
        stopWatch.log()
    }

    fun clear() {
        synchronized(mLock) {
            mOriginalValues?.clear()
            val size = mObjects.size
            mObjects.clear()
            notifyItemRangeRemoved(0, size)
        }
    }

    fun getItem(position: Int): LogLine {
        synchronized(mLock) { return mObjects[position] }
    }

    private fun getItemSafe(position: Int): LogLine? {
        synchronized(mLock) { return if (mObjects.size > position) mObjects[position] else null }
    }

    fun getRealSize(): Int {
        synchronized(mLock) { return (mOriginalValues ?: mObjects).size }
    }

    fun setLogLevelLimit(logLevelLimit: Int) {
        mLogLevelLimit = logLevelLimit
        getFilter().filter(null) // Re-filter to apply new log level limit
    }

    override fun select(position: Int): Boolean {
        synchronized(selectedLogLines) {
            val logLine = getItemSafe(position)
            if (logLine != null) {
                selectedLogLines.add(logLine)
            }
            return logLine != null
        }
    }

    override fun deselect(position: Int): Boolean {
        synchronized(selectedLogLines) {
            val logLine = getItemSafe(position)
            return logLine != null && selectedLogLines.remove(logLine)
        }
    }

    override fun isSelected(position: Int): Boolean {
        synchronized(selectedLogLines) {
            val logLine = getItemSafe(position)
            return logLine != null && selectedLogLines.contains(logLine)
        }
    }

    override fun cancelSelection() {
        super.cancelSelection()
        synchronized(selectedLogLines) { selectedLogLines.clear() }
    }

    override fun getSelectedItemCount(): Int {
        synchronized(selectedLogLines) { return selectedLogLines.size }
    }

    override fun getTotalItemCount(): Int = itemCount

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_logcat, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val context = holder.itemView.context
        val logLine = getItem(position)
        holder.logLine = logLine

        val levelColor = getBackgroundColorForLogLevel(context, logLine.logLevel)
        val t = holder.logLevel
        t.text = logLine.processIdText
        t.setBackgroundColor(levelColor)
        t.setTextColor(getForegroundColorForLogLevel(context, logLine.logLevel))
        t.visibility = if (logLine.logLevel == -1) View.GONE else View.VISIBLE

        holder.itemView.setBackgroundResource(0)
        holder.contentView.setBackgroundResource(if (position % 2 == 0) R.color.logcat_row_even else R.color.logcat_row_odd)

        if (isInSelectionMode && isSelected(position)) {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.selected_background))
        }

        holder.itemView.setOnClickListener {
            if (isInSelectionMode) {
                toggleSelection(position)
                AccessibilityUtils.requestAccessibilityFocus(holder.itemView)
                return@setOnClickListener
            }
            if (logLine.isExpanded) {
                logLine.isExpanded = false
                notifyItemChanged(position)
            } else {
                logLine.isExpanded = true
                notifyItemChanged(position)
            }
        }
        holder.itemView.setOnLongClickListener {
            toggleSelection(position)
            AccessibilityUtils.requestAccessibilityFocus(holder.itemView)
            true
        }

        holder.message.text = logLine.logOutput

        if (logLine.isExpanded) {
            holder.expandedView.visibility = View.VISIBLE
            val owner = logLine.uidOwner ?: ""
            val pkgName = logLine.packageName ?: ""
            holder.expandedText.text = context.getString(
                R.string.logcat_expanded_item_format,
                logLine.timestamp,
                logLine.tagName,
                LogLine.convertLogLevelToChar(logLine.logLevel),
                logLine.pid,
                logLine.tid,
                if (logLine.uid != -1) logLine.uid else "",
                if (owner.isNotEmpty()) owner else "",
                if (pkgName.isNotEmpty()) pkgName else ""
            )
            holder.menuButton.setOnClickListener { v ->
                val popup = PopupMenu(v.context, v)
                popup.menuInflater.inflate(R.menu.menu_logcat_item_expanded, popup.menu)
                popup.setOnMenuItemClickListener { item ->
                    mSearchByClickListener?.onSearchByClick(item.itemId, logLine)
                    true
                }
                if (logLine.uid == -1) popup.menu.findItem(R.id.action_search_by_uid).isVisible = false
                if (logLine.packageName == null) popup.menu.findItem(R.id.action_search_by_package).isVisible = false
                popup.show()
            }
        } else {
            holder.expandedView.visibility = View.GONE
        }
        val textColor = if (Prefs.LogViewer.getHighlightColors()) {
            getOrCreateTagColor(context, logLine.tagName)
        } else {
            ContextCompat.getColor(context, io.github.muntashirakon.ui.R.color.brian_wrinkle_white)
        }
        holder.tag.setTextColor(textColor)
        holder.tag.text = logLine.tagName
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).hashCode().toLong()
    }

    override fun getItemCount(): Int {
        synchronized(mLock) { return mObjects.size }
    }

    override fun getFilter(): Filter {
        if (mFilter == null) {
            mFilter = ArrayFilter()
        }
        return mFilter!!
    }

    private inner class ArrayFilter : Filter() {
        private var mFilterListener: Filter.FilterListener? = null
        private var mSearchCriteria: SearchCriteria? = null

        fun setFilterListener(filterListener: Filter.FilterListener) {
            mFilterListener = filterListener
        }

        fun setSearchCriteria(searchCriteria: SearchCriteria?) {
            mSearchCriteria = searchCriteria
        }

        fun performFilteringOnList(list: List<LogLine>, searchCriteria: SearchCriteria?): List<LogLine> {
            val results = mutableListOf<LogLine>()
            if (searchCriteria == null || searchCriteria.isEmpty) {
                // If the filter is empty, then return everything
                list.filterTo(results) { it.logLevel <= mLogLevelLimit }
            } else {
                list.filterTo(results) { it.logLevel <= mLogLevelLimit && searchCriteria.matches(it) }
            }
            return results
        }

        override fun performFiltering(prefix: CharSequence?): FilterResults {
            val results = FilterResults()
            var values: List<LogLine>
            synchronized(mLock) {
                if (mOriginalValues == null) {
                    mOriginalValues = ArrayList(mObjects)
                }
                values = mOriginalValues!!
            }
            results.values = performFilteringOnList(values, mSearchCriteria)
            results.count = results.values.let { it as List<LogLine> }.size
            return results
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults) {
            synchronized(mLock) {
                mObjects = results.values as MutableList<LogLine>
            }
            notifyDataSetChanged()
            mFilterListener?.onFilterComplete(mObjects.size)
        }
    }

    class ViewHolder(itemView: View) : MultiSelectionView.ViewHolder(itemView) {
        val contentView: View = itemView.findViewById(R.id.content_view)
        val logLevel: TextView = itemView.findViewById(R.id.log_level)
        val tag: TextView = itemView.findViewById(R.id.tag)
        val message: TextView = itemView.findViewById(R.id.message)
        val expandedView: View = itemView.findViewById(R.id.expanded_view)
        val expandedText: TextView = itemView.findViewById(R.id.expanded_text)
        val menuButton: MaterialButton = itemView.findViewById(R.id.menu_button)
        var logLine: LogLine? = null

        init {
            itemView.setOnClickListener {
                if (logLine != null) {
                    logLine!!.isExpanded = !logLine!!.isExpanded
                    if (logLine!!.isExpanded) expandedView.visibility = View.VISIBLE
                    else expandedView.visibility = View.GONE
                }
            }
        }

        interface OnSearchByClickListener {
            fun onSearchByClick(itemId: Int, logLine: LogLine)
        }
    }

    companion object {
        val TAG: String = LogViewerRecyclerAdapter::class.java.simpleName

        private val BACKGROUND_COLORS = SparseArrayCompat<Int>(7).apply {
            put(android.util.Log.VERBOSE, io.github.muntashirakon.ui.R.color.the_brown_shirts)
            put(android.util.Log.DEBUG, io.github.muntashirakon.ui.R.color.night_blue_shadow)
            put(android.util.Log.INFO, io.github.muntashirakon.ui.R.color.blue_popsicle)
            put(android.util.Log.WARN, io.github.muntashirakon.ui.R.color.red_orange)
            put(android.util.Log.ERROR, io.github.muntashirakon.ui.R.color.pure_red)
            put(android.util.Log.ASSERT, io.github.muntashirakon.ui.R.color.pure_red)
            put(LogLine.LOG_FATAL, io.github.muntashirakon.ui.R.color.electric_red)
        }

        private val FOREGROUND_COLORS = SparseArrayCompat<Int>(7).apply {
            put(android.util.Log.VERBOSE, io.github.muntashirakon.ui.R.color.brian_wrinkle_white)
            put(android.util.Log.DEBUG, io.github.muntashirakon.ui.R.color.brian_wrinkle_white)
            put(android.util.Log.INFO, io.github.muntashirakon.ui.R.color.brian_wrinkle_white)
            put(android.util.Log.WARN, io.github.muntashirakon.ui.R.color.brian_wrinkle_white)
            put(android.util.Log.ERROR, io.github.muntashirakon.ui.R.color.brian_wrinkle_white)
            put(android.util.Log.ASSERT, io.github.muntashirakon.ui.R.color.brian_wrinkle_white)
            put(LogLine.LOG_FATAL, io.github.muntashirakon.ui.R.color.brian_wrinkle_white)
        }

        private var sTagColors: IntArray? = null

        @JvmStatic
        @ColorInt
        private fun getBackgroundColorForLogLevel(context: Context, logLevel: Int): Int {
            val result = BACKGROUND_COLORS[logLevel]
                ?: throw IllegalArgumentException("Invalid log level: $logLevel")
            return ContextCompat.getColor(context, result)
        }

        @JvmStatic
        @ColorInt
        private fun getForegroundColorForLogLevel(context: Context, logLevel: Int): Int {
            val result = FOREGROUND_COLORS[logLevel]
                ?: throw IllegalArgumentException("Invalid log level: $logLevel")
            return ContextCompat.getColor(context, result)
        }

        @JvmStatic
        @Synchronized
        private fun getOrCreateTagColor(context: Context, tag: String?): Int {
            if (sTagColors == null) {
                sTagColors = context.resources.getIntArray(R.array.random_colors)
            }
            val hashCode = tag?.hashCode() ?: 0
            val smear = Math.abs(hashCode) % sTagColors!!.size
            return sTagColors!![smear]
        }
    }
}
