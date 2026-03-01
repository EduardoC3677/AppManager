// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.db.entity.LogFilter
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.util.AdapterUtils
import java.util.*

class LogFilterAdapter(items: List<LogFilter>) : RecyclerView.Adapter<LogFilterAdapter.ViewHolder>() {

    private var mListener: OnClickListener? = null
    private val mItems: MutableList<LogFilter> = Collections.synchronizedList(ArrayList(items))

    fun setOnItemClickListener(listener: OnClickListener) {
        mListener = listener
    }

    fun add(filter: LogFilter) {
        val previousSize = mItems.size
        mItems.add(filter)
        mItems.sortWith(LogFilter.COMPARATOR)
        val currentSize = mItems.size
        AdapterUtils.notifyDataSetChanged(this, previousSize, currentSize)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_title_action, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val logFilter = mItems[position]
        holder.textView.text = logFilter.name
        holder.itemView.setOnClickListener {
            mListener?.onClick(holder.itemView, position, logFilter)
        }
        holder.actionButton.setOnClickListener {
            ThreadUtils.postOnBackgroundThread { LogFilterManager.deleteFilter(logFilter) }
            mItems.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    override fun getItemCount(): Int = mItems.size

    interface OnClickListener {
        fun onClick(view: View, position: Int, logFilter: LogFilter)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.findViewById(R.id.item_title)
        val actionButton: MaterialButton = itemView.findViewById(R.id.item_action)

        init {
            actionButton.contentDescription = itemView.context.getString(R.string.item_remove)
        }
    }
}
