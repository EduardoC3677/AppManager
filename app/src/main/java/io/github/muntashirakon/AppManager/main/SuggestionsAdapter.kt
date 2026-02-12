// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader

class SuggestionsAdapter(
    private var items: List<ApplicationItem>,
    private val listener: (ApplicationItem) -> Unit
) : RecyclerView.Adapter<SuggestionsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.app_icon)
        val label: TextView = view.findViewById(R.id.app_label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_suggestion, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.label.text = item.label
        ImageLoader.getInstance().displayImage(item.packageName, item, holder.icon)
        holder.itemView.setOnClickListener { listener(item) }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<ApplicationItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
