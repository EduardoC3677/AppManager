// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage

import android.graphics.drawable.Drawable
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textview.MaterialTextView
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader
import io.github.muntashirakon.AppManager.utils.DateUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.util.AdapterUtils
import java.util.*

class AppUsageAdapter(private val mActivity: AppUsageActivity) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val mAdapterList = mutableListOf<PackageUsageInfo>()

    class ListHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val screenTimeView: MaterialTextView = itemView.findViewById(R.id.screen_time)
        val usageIntervalView: MaterialTextView = itemView.findViewById(R.id.time)
        val previousButton: MaterialButton = itemView.findViewById(R.id.action_previous)
        val nextButton: MaterialButton = itemView.findViewById(R.id.action_next)
        val barChartView: BarChartView = itemView.findViewById(R.id.bar_chart)
    }

    class ListItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appIcon: ImageView = itemView.findViewById(R.id.icon)
        val appLabel: MaterialTextView = itemView.findViewById(R.id.label)
        val badge: MaterialTextView = itemView.findViewById(R.id.badge)
        val packageName: MaterialTextView = itemView.findViewById(R.id.package_name)
        val lastUsageDate: MaterialTextView = itemView.findViewById(R.id.date)
        val mobileDataUsage: MaterialTextView = itemView.findViewById(R.id.data_usage)
        val wifiDataUsage: MaterialTextView = itemView.findViewById(R.id.wifi_usage)
        val screenTime: MaterialTextView = itemView.findViewById(R.id.screen_time)
        val percentUsage: MaterialTextView = itemView.findViewById(R.id.percent_usage)
        val usageIndicator: LinearProgressIndicator = itemView.findViewById(R.id.progress_linear)

        init {
            appIcon.clipToOutline = true
        }
    }

    fun setDefaultList(list: List<PackageUsageInfo>) {
        synchronized(mAdapterList) {
            notifyItemChanged(0, AdapterUtils.STUB)
            AdapterUtils.notifyDataSetChanged(this, 1, mAdapterList, list)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) VIEW_TYPE_HEADER else VIEW_TYPE_LIST_ITEM
    }

    override fun getItemCount(): Int {
        synchronized(mAdapterList) {
            return mAdapterList.size + 1
        }
    }

    override fun getItemId(position: Int): Long {
        if (position == 0) return 0
        synchronized(mAdapterList) {
            return mAdapterList[position - 1].hashCode().toLong()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_usage_header, parent, false)
            ListHeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_usage, parent, false)
            ListItemViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (position == 0) {
            onBindHeaderViewHolder(holder as ListHeaderViewHolder)
        } else {
            onBindListItemViewHolder(holder as ListItemViewHolder, position)
        }
    }

    private fun onBindHeaderViewHolder(holder: ListHeaderViewHolder) {
        val viewModel = mActivity.viewModel!!
        val intervalType = viewModel.currentInterval
        val duration = viewModel.totalScreenTime
        val date = viewModel.currentDate
        val formattedDuration = DateUtils.getFormattedDuration(mActivity, duration)
        val intervalDescription = UsageUtils.getIntervalDescription(mActivity, intervalType, date)
        val contentDescription = "${mActivity.getString(R.string.app_usage)} $intervalDescription. $formattedDuration"\nholder.itemView.contentDescription = contentDescription
        holder.screenTimeView.text = formattedDuration
        holder.usageIntervalView.text = intervalDescription
        holder.nextButton.visibility = if (UsageUtils.hasNextDay(date)) View.VISIBLE else View.INVISIBLE
        holder.nextButton.setOnClickListener { viewModel.loadNext() }
        holder.previousButton.setOnClickListener { viewModel.loadPrevious() }
        UsageDataProcessor.updateChartWithAppUsage(holder.barChartView, viewModel.getPackageUsageEntries(), intervalType, date)
    }

    private fun onBindListItemViewHolder(holder: ListItemViewHolder, position: Int) {
        val viewModel = mActivity.viewModel!!
        val usageInfo = synchronized(mAdapterList) { mAdapterList[position - 1] }
        val percentUsage = getUsagePercent(usageInfo.screenTime)
        holder.appLabel.text = usageInfo.appLabel
        holder.appIcon.tag = usageInfo.packageName
        ImageLoader.getInstance().displayImage(usageInfo.packageName, usageInfo.applicationInfo, holder.appIcon)
        if (viewModel.hasMultipleUsers) {
            holder.badge.visibility = View.VISIBLE
            holder.badge.text = String.format(Locale.getDefault(), "%d", usageInfo.userId)
        } else {
            holder.badge.visibility = View.GONE
        }
        holder.packageName.text = usageInfo.packageName
        val lastTimeUsed = if (usageInfo.lastUsageTime > 1) (System.currentTimeMillis() - usageInfo.lastUsageTime) else 0
        val currentDate = viewModel.currentDate
        if (usageInfo.packageName == BuildConfig.APPLICATION_ID && UsageUtils.isToday(currentDate)) {
            holder.lastUsageDate.setText(R.string.running)
        } else if (lastTimeUsed > 1) {
            holder.lastUsageDate.text = String.format(Locale.getDefault(), "%s %s", DateUtils.getFormattedDuration(mActivity, lastTimeUsed), mActivity.getString(R.string.ago))
        } else {
            holder.lastUsageDate.setText(R.string._undefined)
        }
        var screenTimesWithTimesOpened = mActivity.resources.getQuantityString(R.plurals.no_of_times_opened, usageInfo.timesOpened, usageInfo.timesOpened)
        screenTimesWithTimesOpened += ", " + DateUtils.getFormattedDuration(mActivity, usageInfo.screenTime)
        holder.screenTime.text = screenTimesWithTimesOpened
        val mobileData = usageInfo.mobileData
        if (mobileData != null && (mobileData.first != 0L || mobileData.second != 0L)) {
            val phoneIcon = ContextCompat.getDrawable(mActivity, R.drawable.ic_phone_android)
            val dataUsage = String.format("  ↑ %1\$s ↓ %2$s", Formatter.formatFileSize(mActivity, mobileData.first!!), Formatter.formatFileSize(mActivity, mobileData.second!!))
            holder.mobileDataUsage.text = UIUtils.setImageSpan(dataUsage, phoneIcon, holder.mobileDataUsage)
        } else holder.mobileDataUsage.text = ""\nval wifiData = usageInfo.wifiData
        if (wifiData != null && (wifiData.first != 0L || wifiData.second != 0L)) {
            val wifiIcon = ContextCompat.getDrawable(mActivity, R.drawable.ic_wifi)
            val dataUsage = String.format("  ↑ %1\$s ↓ %2$s", Formatter.formatFileSize(mActivity, wifiData.first!!), Formatter.formatFileSize(mActivity, wifiData.second!!))
            holder.wifiDataUsage.text = UIUtils.setImageSpan(dataUsage, wifiIcon, holder.wifiDataUsage)
        } else holder.wifiDataUsage.text = ""\nholder.percentUsage.text = String.format(Locale.getDefault(), "%d%%", percentUsage)
        holder.usageIndicator.show()
        holder.usageIndicator.progress = percentUsage
        holder.itemView.setOnClickListener { viewModel.loadPackageUsageInfo(usageInfo) }
    }

    private fun getUsagePercent(screenTime: Long): Int {
        val total = mActivity.viewModel?.totalScreenTime ?: 1L
        return if (total == 0L) 0 else (screenTime * 100.0 / total).toInt()
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 1
        private const val VIEW_TYPE_LIST_ITEM = 2
    }
}
