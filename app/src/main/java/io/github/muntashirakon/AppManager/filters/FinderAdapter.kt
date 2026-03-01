// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textview.MaterialTextView
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader
import io.github.muntashirakon.util.AdapterUtils
import java.util.*

class FinderAdapter : RecyclerView.Adapter<FinderAdapter.ViewHolder>() {
    private val mAdapterList = mutableListOf<FilterItem.FilteredItemInfo<FilterableAppInfo>>()

    @UiThread
    fun setDefaultList(list: List<FilterItem.FilteredItemInfo<FilterableAppInfo>>) {
        synchronized(mAdapterList) {
            AdapterUtils.notifyDataSetChanged(this, mAdapterList, list)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_finder, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val itemInfo = synchronized(mAdapterList) { mAdapterList[position] }
        val appInfo = itemInfo.info
        ImageLoader.getInstance().displayImage(appInfo.packageName, appInfo.applicationInfo, holder.icon)
        holder.label.text = appInfo.getAppLabel()
        holder.pkg.text = appInfo.packageName
        holder.item1.visibility = View.GONE
        holder.item2.visibility = View.GONE
        holder.item3.visibility = View.GONE
        holder.toggleBtn.visibility = View.GONE
        holder.itemView.strokeColor = Color.TRANSPARENT
    }

    override fun getItemCount(): Int = synchronized(mAdapterList) { mAdapterList.size }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val itemView: MaterialCardView = itemView as MaterialCardView
        val icon: AppCompatImageView = itemView.findViewById(R.id.icon)
        val label: MaterialTextView = itemView.findViewById(R.id.label)
        val pkg: MaterialTextView = itemView.findViewById(R.id.package_name)
        val item1: MaterialTextView = itemView.findViewById(R.id.item1)
        val item2: MaterialTextView = itemView.findViewById(R.id.item2)
        val item3: MaterialTextView = itemView.findViewById(R.id.item3)
        val toggleBtn: MaterialSwitch = itemView.findViewById(R.id.toggle_button)
    }
}
