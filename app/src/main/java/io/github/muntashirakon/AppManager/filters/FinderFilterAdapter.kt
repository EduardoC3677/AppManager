// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.filters.options.FilterOption
import io.github.muntashirakon.util.AdapterUtils

class FinderFilterAdapter(private val mFilterItem: FilterItem) : RecyclerView.Adapter<FinderFilterAdapter.ViewHolder>() {
    private var mClickListener: OnClickListener? = null

    fun setOnItemClickListener(listener: OnClickListener?) {
        mClickListener = listener
    }

    fun add(filter: FilterOption) {
        val position = mFilterItem.addFilterOption(filter)
        if (position >= 0) {
            notifyItemInserted(position)
        }
    }

    fun update(position: Int, filter: FilterOption) {
        mFilterItem.updateFilterOptionAt(position, filter)
        notifyItemChanged(position, AdapterUtils.STUB)
    }

    fun remove(position: Int, id: Int) {
        val filterOption = mFilterItem.getFilterOptionAt(position)
        if (filterOption.id == id && mFilterItem.removeFilterOptionAt(position)) {
            notifyItemRemoved(position)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_icon_title_subtitle, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val filterOption = mFilterItem.getFilterOptionAt(position)
        holder.titleView.text = filterOption.getFullId()
        holder.subtitleView.text = filterOption.toLocalizedString(holder.itemView.context)
        holder.itemView.setOnClickListener {
            mClickListener?.onEdit(holder.itemView, holder.absoluteAdapterPosition, filterOption)
        }
        holder.actionButton.setOnClickListener {
            mClickListener?.onRemove(holder.itemView, holder.absoluteAdapterPosition, filterOption)
        }
    }

    override fun getItemCount(): Int = mFilterItem.size

    interface OnClickListener {
        fun onEdit(view: View, position: Int, filterOption: FilterOption)
        fun onRemove(view: View, position: Int, filterOption: FilterOption)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleView: TextView = itemView.findViewById(R.id.item_title)
        val subtitleView: TextView = itemView.findViewById(R.id.item_subtitle)
        val actionButton: MaterialButton = itemView.findViewById(R.id.item_open)

        init {
            itemView.findViewById<View>(R.id.item_icon).visibility = View.GONE
            actionButton.icon = ContextCompat.getDrawable(itemView.context, io.github.muntashirakon.ui.R.drawable.ic_clear)
            actionButton.contentDescription = itemView.context.getString(R.string.item_remove)
        }
    }
}
