// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.divider.MaterialDivider
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.details.info.ListItem.Companion.LIST_ITEM_GROUP_BEGIN
import io.github.muntashirakon.AppManager.details.info.ListItem.Companion.LIST_ITEM_INLINE
import io.github.muntashirakon.AppManager.details.info.ListItem.Companion.LIST_ITEM_REGULAR
import io.github.muntashirakon.AppManager.details.info.ListItem.Companion.LIST_ITEM_REGULAR_ACTION
import io.github.muntashirakon.util.AdapterUtils

class AppInfoRecyclerAdapter(private val mContext: Context) : RecyclerView.Adapter<AppInfoRecyclerAdapter.ViewHolder>() {
    private val mAdapterList = mutableListOf<ListItem>()

    fun setAdapterList(list: List<ListItem>) {
        AdapterUtils.notifyDataSetChanged(this, mAdapterList, list)
    }

    @ListItem.ListItemType
    override fun getItemViewType(position: Int): Int = mAdapterList[position].type

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = when (viewType) {
            LIST_ITEM_GROUP_BEGIN -> LayoutInflater.from(parent.context).inflate(io.github.muntashirakon.ui.R.layout.m3_preference_category, parent, false)
            else -> {
                val v = LayoutInflater.from(parent.context).inflate(io.github.muntashirakon.ui.R.layout.m3_preference, parent, false)
                val frame = v.findViewById<LinearLayoutCompat>(android.R.id.widget_frame)
                if (viewType == LIST_ITEM_REGULAR_ACTION) {
                    frame.addView(LayoutInflater.from(parent.context).inflate(R.layout.item_right_standalone_action, parent, false))
                } else if (viewType == LIST_ITEM_INLINE) {
                    frame.addView(LayoutInflater.from(parent.context).inflate(R.layout.item_right_summary, parent, false))
                }
                v
            }
        }
        return ViewHolder(view, viewType)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = mAdapterList[position]
        holder.title.text = item.title
        if (item.type == LIST_ITEM_GROUP_BEGIN) return
        holder.subtitle.text = item.subtitle
        holder.subtitle.setTextIsSelectable(item.isSelectable)
        holder.subtitle.typeface = if (item.isMonospace) Typeface.MONOSPACE else Typeface.DEFAULT
        if (item.type == LIST_ITEM_INLINE) return
        if (item.type == LIST_ITEM_REGULAR_ACTION) {
            holder.actionDivider?.visibility = if (item.onActionClickListener != null) View.VISIBLE else View.GONE
            if (item.actionIconRes != 0) holder.actionIcon?.setIconResource(item.actionIconRes)
            item.actionContentDescription?.let { holder.actionIcon?.contentDescription = it }
                ?: run { if (item.actionContentDescriptionRes != 0) holder.actionIcon?.contentDescription = mContext.getString(item.actionContentDescriptionRes) }
            if (item.onActionClickListener != null) {
                holder.actionIcon?.visibility = View.VISIBLE
                holder.actionIcon?.setOnClickListener(item.onActionClickListener)
            } else holder.actionIcon?.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = mAdapterList.size

    class ViewHolder(itemView: View, @ListItem.ListItemType viewType: Int) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(android.R.id.title)
        lateinit var subtitle: TextView
        var actionIcon: MaterialButton? = null
        var actionDivider: MaterialDivider? = null

        init {
            itemView.findViewById<View>(R.id.icon_frame).visibility = View.GONE
            when (viewType) {
                LIST_ITEM_GROUP_BEGIN -> itemView.findViewById<View>(android.R.id.summary).visibility = View.GONE
                LIST_ITEM_REGULAR, LIST_ITEM_REGULAR_ACTION -> {
                    subtitle = itemView.findViewById(android.R.id.summary)
                    actionDivider = itemView.findViewById(R.id.divider)
                    actionIcon = itemView.findViewById(android.R.id.button1)
                }
                LIST_ITEM_INLINE -> {
                    subtitle = itemView.findViewById(android.R.id.text1)
                    itemView.findViewById<View>(android.R.id.summary).visibility = View.GONE
                }
            }
        }
    }
}
