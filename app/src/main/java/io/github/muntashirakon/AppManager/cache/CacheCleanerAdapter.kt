// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.cache

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader

/**
 * Adapter for displaying app cache list
 */
class CacheCleanerAdapter(
    private val mActivity: CacheCleanerActivity
) : RecyclerView.Adapter<CacheCleanerAdapter.ViewHolder>() {
    
    private var mCacheData: List<CacheCleanerViewModel.AppCacheInfo> = emptyList()
    
    fun setCacheData(data: CacheCleanerViewModel.CacheData) {
        mCacheData = data.apps
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cache_cleaner, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = mCacheData[position]
        
        holder.appName.text = app.appName
        holder.packageName.text = app.packageName
        holder.cacheSize.text = formatSize(app.cacheSize)
        
        // Load app icon
        holder.icon.tag = app.packageName
        ImageLoader.getInstance().displayImage(app.packageName, holder.icon)
        
        // Clean button click
        holder.cleanButton.setOnClickListener {
            mActivity.cleanAppCache(app.packageName, app.userId)
        }
        
        // Card click to select (for future multi-select)
        holder.cardView.setOnClickListener {
            // Could add selection logic here
        }
    }
    
    override fun getItemCount(): Int = mCacheData.size
    
    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
    
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView as MaterialCardView
        val icon: AppCompatImageView = itemView.findViewById(R.id.app_icon)
        val appName: TextView = itemView.findViewById(R.id.app_name)
        val packageName: TextView = itemView.findViewById(R.id.package_name)
        val cacheSize: TextView = itemView.findViewById(R.id.cache_size)
        val cleanButton: ImageButton = itemView.findViewById(R.id.clean_button)
    }
}
