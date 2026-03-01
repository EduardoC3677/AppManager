// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.util

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.IntRange
import androidx.collection.SimpleArrayMap
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import java.util.*

object AdapterUtils {
    @JvmField
    val STUB = Any()

    private class SimpleListDiffCallback<T>(
        private val mOldList: List<T>,
        private val mNewList: List<T>?,
        private val mStartPosition: Int = 0
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = mOldList.size

        override fun getNewListSize(): Int = (mNewList?.size ?: 0) + mStartPosition

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            if (newItemPosition < mStartPosition) {
                // Both values are null
                return true
            }
            if (mNewList == null) {
                return false
            }
            return mOldList[oldItemPosition] == mNewList[newItemPosition - mStartPosition]
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return newItemPosition < mStartPosition
        }

        override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
            return STUB
        }
    }

    private class SimpleArrayMapDiffCallback<K, V>(
        private val mOldList: SimpleArrayMap<K, V>,
        private val mNewList: SimpleArrayMap<K, V>?
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = mOldList.size()

        override fun getNewListSize(): Int = mNewList?.size() ?: 0

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            if (mNewList == null) {
                return false
            }
            return mOldList.keyAt(oldItemPosition) == mNewList.keyAt(newItemPosition)
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean = false

        override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
            return STUB
        }
    }

    @JvmStatic
    fun <T, V> notifyDataSetChanged(
        adapter: RecyclerView.Adapter<*>,
        baseList: SimpleArrayMap<T, V>,
        newList: SimpleArrayMap<T, V>?
    ) {
        val result = DiffUtil.calculateDiff(SimpleArrayMapDiffCallback(baseList, newList))
        baseList.clear()
        if (newList != null) {
            baseList.putAll(newList)
        }
        result.dispatchUpdatesTo(adapter)
    }

    @JvmStatic
    fun <T> notifyDataSetChanged(
        adapter: RecyclerView.Adapter<*>,
        baseList: MutableList<T?>,
        newList: List<T>?
    ) {
        notifyDataSetChanged(adapter, 0, baseList, newList)
    }

    @JvmStatic
    fun <T> notifyDataSetChanged(
        adapter: RecyclerView.Adapter<*>,
        @IntRange(from = 0) startIndex: Int,
        baseList: MutableList<T?>,
        newList: List<T>?
    ) {
        // base list always has placeholders < startIndex, newList do not. So, it is necessary to
        // offset the placeholders during comparison.
        val result = DiffUtil.calculateDiff(SimpleListDiffCallback(baseList, newList, startIndex))
        baseList.clear()
        // Add |startIndex| no. of null as placeholders
        for (i in 0 until startIndex) {
            baseList.add(null)
        }
        if (newList != null) {
            baseList.addAll(newList)
        }
        // When dispatching updates, null items are never updated in partial update.
        result.dispatchUpdatesTo(adapter)
    }

    @JvmStatic
    fun notifyDataSetChanged(
        adapter: RecyclerView.Adapter<*>, previousCount: Int,
        currentCount: Int
    ) {
        if (Thread.currentThread() !== Looper.getMainLooper().thread) {
            // Main thread is required
            throw RuntimeException("Must be called on the UI thread")
        }
        if (previousCount > currentCount) {
            // Some values are removed
            if (currentCount > 0) {
                adapter.notifyItemRangeChanged(0, currentCount, STUB)
            }
            adapter.notifyItemRangeRemoved(currentCount, previousCount - currentCount)
        } else if (previousCount < currentCount) {
            // Some values are added
            if (previousCount > 0) {
                adapter.notifyItemRangeChanged(0, previousCount, STUB)
            }
            adapter.notifyItemRangeInserted(previousCount, currentCount - previousCount)
        } else if (previousCount > 0) {
            // No values are added or removed
            adapter.notifyItemRangeChanged(0, previousCount, STUB)
        }
    }

    @JvmStatic
    fun setVisible(v: View, visible: Boolean) {
        if (visible && v.visibility != View.VISIBLE) {
            v.visibility = View.VISIBLE
        } else if (!visible && v.visibility != View.GONE) {
            v.visibility = View.GONE
        }
    }

    @JvmStatic
    fun <VH : RecyclerView.ViewHolder> fixTextSelectionInView(holder: VH) {
        fixTextSelectionInView(holder.itemView)
    }

    private fun fixTextSelectionInView(view: View?) {
        if (view is TextView) {
            // Apply the enabled toggle workaround
            view.isEnabled = false
            view.isEnabled = true
        } else if (view is ViewGroup) {
            // If it's a ViewGroup, recurse into children
            for (i in 0 until view.childCount) {
                fixTextSelectionInView(view.getChildAt(i))
            }
        }
    }
}
