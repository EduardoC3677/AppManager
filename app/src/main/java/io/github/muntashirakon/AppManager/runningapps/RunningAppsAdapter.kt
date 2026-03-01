// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runningapps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Process
import android.text.Spannable
import android.text.TextUtils
import android.text.format.Formatter
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.appcompat.widget.PopupMenu
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.compat.ManifestCompat
import io.github.muntashirakon.AppManager.logcat.LogViewerActivity
import io.github.muntashirakon.AppManager.logcat.struct.SearchCriteria
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader
import io.github.muntashirakon.AppManager.settings.FeatureController
import io.github.muntashirakon.AppManager.settings.Ops
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.utils.LangUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes
import io.github.muntashirakon.io.Paths
import io.github.muntashirakon.proc.ProcMemoryInfo
import io.github.muntashirakon.util.AccessibilityUtils
import io.github.muntashirakon.util.AdapterUtils
import io.github.muntashirakon.widget.MultiSelectionView
import java.util.*

class RunningAppsAdapter(private val mActivity: RunningAppsActivity) : MultiSelectionView.Adapter<MultiSelectionView.ViewHolder>() {
    private val mModel: RunningAppsViewModel = mActivity.model!!
    private val mQueryStringHighlightColor: Int = ColorCodes.getQueryStringHighlightColor(mActivity)
    private val mLock = Any()
    private val mProcessItems = mutableListOf<ProcessItem>()
    private var mProcMemoryInfo: ProcMemoryInfo? = null

    fun setDefaultList(processItems: List<ProcessItem>) {
        synchronized(mLock) { AdapterUtils.notifyDataSetChanged(this, 1, mProcessItems, processItems) }
        notifySelectionChange()
    }

    fun setDeviceMemoryInfo(procMemoryInfo: ProcMemoryInfo) {
        mProcMemoryInfo = procMemoryInfo
        notifyItemChanged(0, AdapterUtils.STUB)
    }

    override fun getItemViewType(position: Int): Int = if (position == 0) VIEW_TYPE_MEMORY_INFO else VIEW_TYPE_PROCESS_INFO

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MultiSelectionView.ViewHolder {
        return if (viewType == VIEW_TYPE_MEMORY_INFO) {
            HeaderViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.header_running_apps_memory_info, parent, false))
        } else {
            BodyViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_running_app, parent, false))
        }
    }

    override fun onBindViewHolder(holder: MultiSelectionView.ViewHolder, position: Int) {
        if (position == 0) onBindHeaderViewHolder(holder as HeaderViewHolder)
        else {
            onBindBodyViewHolder(holder as BodyViewHolder, position)
            super.onBindViewHolder(holder, position)
        }
    }

    private fun onBindHeaderViewHolder(holder: HeaderViewHolder) {
        val info = mProcMemoryInfo ?: return
        val context = holder.itemView.context
        val cd = StringBuilder()
        val appMem = info.applicationMemory; val cachedMem = info.cachedMemory; val buffers = info.buffers; val freeMem = info.freeMemory
        val total = (appMem + cachedMem + buffers + freeMem).toDouble()
        val totalNonZero = total > 0
        val fUsed = Formatter.formatFileSize(context, info.usedMemory)
        val fTotal = Formatter.formatFileSize(context, info.totalMemory)
        val fAvail = Formatter.formatFileSize(context, info.availableMemory)
        val fApp = Formatter.formatShortFileSize(context, appMem)
        val fCached = Formatter.formatShortFileSize(context, cachedMem)
        val fBuff = Formatter.formatShortFileSize(context, buffers)
        val fFree = Formatter.formatShortFileSize(context, freeMem)
        holder.mMemoryInfoChart.visibility = if (totalNonZero) View.VISIBLE else View.GONE
        holder.mMemoryShortInfoView.visibility = if (totalNonZero) View.VISIBLE else View.GONE
        holder.mMemoryInfoView.visibility = if (totalNonZero) View.VISIBLE else View.GONE
        if (totalNonZero) {
            holder.mMemoryInfoChart.post {
                val w = holder.mMemoryInfoChart.width
                setLayoutWidth(holder.mMemoryInfoChartChildren[0], (w * appMem / total).toInt())
                setLayoutWidth(holder.mMemoryInfoChartChildren[1], (w * cachedMem / total).toInt())
                setLayoutWidth(holder.mMemoryInfoChartChildren[2], (w * buffers / total).toInt())
            }
            cd.append(context.getString(R.string.memory_usage_accessibility_description, fUsed, fTotal, fAvail, fApp, fCached, fBuff))
        } else cd.append(context.getString(R.string.memory_usage_unavailable))
        holder.mMemoryShortInfoView.text = UIUtils.getStyledKeyValue(context, R.string.memory, "$fUsed/$fTotal (${context.getString(R.string.available_memory, fAvail)})")
        val memInfo = UIUtils.charSequenceToSpannable(context.getString(R.string.memory_chart_info, fApp, fCached, fBuff, fFree))
        setColors(holder.itemView, memInfo, intArrayOf(com.google.android.material.R.attr.colorOnSurface, androidx.appcompat.R.attr.colorPrimary, com.google.android.material.R.attr.colorTertiary, com.google.android.material.R.attr.colorSurfaceVariant))
        holder.mMemoryInfoView.text = memInfo

        val usedSwap = info.usedSwap; val totalSwap = info.totalSwap
        val fUsedSwap = Formatter.formatFileSize(context, usedSwap); val fTotalSwap = Formatter.formatFileSize(context, totalSwap)
        val swapNonZero = totalSwap > 0
        holder.mSwapInfoChart.visibility = if (swapNonZero) View.VISIBLE else View.GONE
        holder.mSwapShortInfoView.visibility = if (swapNonZero) View.VISIBLE else View.GONE
        holder.mSwapInfoView.visibility = if (swapNonZero) View.VISIBLE else View.GONE
        if (swapNonZero) {
            holder.mSwapInfoChart.post { setLayoutWidth(holder.mSwapInfoChartChildren[0], (holder.mSwapInfoChart.width * usedSwap / totalSwap).toInt()) }
            cd.append("

").append(context.getString(R.string.swap_usage_accessibility_description, fTotalSwap, fUsedSwap))
        }
        holder.mSwapShortInfoView.text = UIUtils.getStyledKeyValue(context, R.string.swap, "$fUsedSwap/$fTotalSwap")
        val swapInfo = UIUtils.charSequenceToSpannable(context.getString(R.string.swap_chart_info, Formatter.formatShortFileSize(context, usedSwap), Formatter.formatShortFileSize(context, totalSwap - usedSwap)))
        setColors(holder.itemView, swapInfo, intArrayOf(com.google.android.material.R.attr.colorOnSurface, com.google.android.material.R.attr.colorSurfaceVariant))
        holder.mSwapInfoView.text = swapInfo
        holder.itemView.contentDescription = cd.toString()
    }

    private fun onBindBodyViewHolder(holder: BodyViewHolder, position: Int) {
        val item = synchronized(mLock) { mProcessItems[position - 1] }
        val appInfo = (item as? AppProcessItem)?.packageInfo?.applicationInfo
        holder.icon.tag = item.name
        ImageLoader.getInstance().displayImage(item.name!!, appInfo, holder.icon)
        holder.processName.text = UIUtils.getHighlightedText(item.name!!, mModel.query, mQueryStringHighlightColor)
        holder.packageName.visibility = if (appInfo != null) View.VISIBLE else View.GONE
        appInfo?.let { holder.packageName.text = UIUtils.getHighlightedText(it.packageName, mModel.query, mQueryStringHighlightColor) }
        holder.processIds.text = mActivity.getString(R.string.pid_and_ppid, item.pid, item.ppid)
        holder.memoryUsage.text = mActivity.getString(R.string.memory_virtual_memory, Formatter.formatFileSize(mActivity, item.memory), Formatter.formatFileSize(mActivity, item.virtualMemory))
        val stateInfo = if (TextUtils.isEmpty(item.state_extra)) mActivity.getString(R.string.process_state, item.state) else mActivity.getString(R.string.process_state_with_extra, item.state, item.state_extra)
        holder.userAndStateInfo.text = "${mActivity.getString(R.string.user_and_uid, item.user, item.uid)}, $stateInfo"
        holder.selinuxContext.text = "SELinux${LangUtils.getSeparatorString()} ${item.context}"
        holder.more.setOnClickListener {
            val popup = PopupMenu(mActivity, it).apply { inflate(R.menu.activity_running_apps_popup_actions); setForceShowIcon(true) }
            val menu = popup.menu
            val kill = menu.findItem(R.id.action_kill)
            if ((item.uid >= Process.FIRST_APPLICATION_UID || Prefs.RunningApps.enableKillForSystemApps()) && Ops.isWorkingUidRoot()) {
                kill.isVisible = true; kill.setOnMenuItemClickListener { mModel.killProcess(item); true }
            } else kill.isVisible = false
            menu.findItem(R.id.action_view_logs).apply { isVisible = FeatureController.isLogViewerEnabled(); setOnMenuItemClickListener { startActivity(Intent(mActivity.applicationContext, LogViewerActivity::class.java).putExtra(LogViewerActivity.EXTRA_FILTER, SearchCriteria.PID_KEYWORD + item.pid).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } }
            val vt = menu.findItem(R.id.action_scan_vt)
            if (mModel.isVirusTotalAvailable && (appInfo != null || Paths.get(item.commandlineArgs[0]).canRead())) {
                vt.isVisible = true; vt.setOnMenuItemClickListener { mModel.scanWithVt(item); true }
            } else vt.isVisible = false
            val forceStop = menu.findItem(R.id.action_force_stop)
            val bg = menu.findItem(R.id.action_disable_background)
            if (appInfo != null) {
                forceStop.setOnMenuItemClickListener { mModel.forceStop(appInfo); true }.isEnabled = SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.FORCE_STOP_PACKAGES)
                forceStop.setVisible(SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.FORCE_STOP_PACKAGES))
                if (mModel.canRunInBackground(appInfo)) { bg.isVisible = true; bg.setOnMenuItemClickListener { mModel.preventBackgroundRun(appInfo); true } } else bg.isVisible = false
            } else { forceStop.isVisible = false; bg.isVisible = false }
            popup.show()
        }
        holder.icon.setOnClickListener { toggleSelection(position); AccessibilityUtils.requestAccessibilityFocus(holder.itemView) }
        holder.itemView.setOnLongClickListener {
            val last = mModel.lastSelectedItem; val lastPos = if (last == null) -1 else synchronized(mLock) { mProcessItems.indexOf(last) }
            if (lastPos >= 0) selectRange(lastPos + 1, position)
            else { toggleSelection(position); AccessibilityUtils.requestAccessibilityFocus(holder.itemView) }
            true
        }
        holder.itemView.setOnClickListener { if (isInSelectionMode) { toggleSelection(position); AccessibilityUtils.requestAccessibilityFocus(holder.itemView) } else mModel.requestDisplayProcessDetails(item) }
        holder.itemView.strokeColor = Color.TRANSPARENT
    }

    override fun getItemId(position: Int): Long = if (position == 0) mProcMemoryInfo?.hashCode()?.toLong() ?: View.NO_ID.toLong() else synchronized(mLock) { mProcessItems[position - 1].hashCode().toLong() }

    override fun select(position: Int): Boolean { if (position == 0) return false; synchronized(mLock) { mModel.select(mProcessItems[position - 1]); return true } }
    override fun deselect(position: Int): Boolean { if (position == 0) return false; synchronized(mLock) { mModel.deselect(mProcessItems[position - 1]); return true } }
    override fun isSelected(position: Int): Boolean { if (position == 0) return false; synchronized(mLock) { return mModel.isSelected(mProcessItems[position - 1]) } }
    override fun isSelectable(position: Int): Boolean = position > 0
    override fun cancelSelection() { super.cancelSelection(); mModel.clearSelections() }
    val selectedItems: ArrayList<ProcessItem> get() = mModel.selections
    override fun getSelectedItemCount(): Int = mModel.selectionCount
    override fun getTotalItemCount(): Int = mModel.totalCount
    override fun getItemCount(): Int = synchronized(mLock) { mProcessItems.size + 1 }

    private fun setColors(v: View, text: Spannable, colors: IntArray) {
        var idx = 0
        for (color in colors) {
            idx = text.toString().indexOf('●', idx)
            if (idx == -1) break
            text.setSpan(ForegroundColorSpan(MaterialColors.getColor(v, color)), idx, idx + 1, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
            idx++
        }
    }

    private fun setLayoutWidth(view: View, width: Int) { val lp = view.layoutParams; lp.width = width; view.layoutParams = lp }

    private class HeaderViewHolder(itemView: View) : MultiSelectionView.ViewHolder(itemView) {
        val mMemoryShortInfoView: TextView = itemView.findViewById(R.id.memory_usage)
        val mMemoryInfoView: TextView = itemView.findViewById(R.id.memory_usage_info)
        val mMemoryInfoChart: LinearLayoutCompat = itemView.findViewById(R.id.memory_usage_chart)
        val mMemoryInfoChartChildren: Array<View> = Array(mMemoryInfoChart.childCount) { mMemoryInfoChart.getChildAt(it) }
        val mSwapShortInfoView: TextView = itemView.findViewById(R.id.swap_usage)
        val mSwapInfoView: TextView = itemView.findViewById(R.id.swap_usage_info)
        val mSwapInfoChart: LinearLayoutCompat = itemView.findViewById(R.id.swap_usage_chart)
        val mSwapInfoChartChildren: Array<View> = Array(mSwapInfoChart.childCount) { mSwapInfoChart.getChildAt(it) }
    }

    private class BodyViewHolder(itemView: View) : MultiSelectionView.ViewHolder(itemView) {
        val itemView: MaterialCardView = itemView as MaterialCardView
        val icon: ImageView = itemView.findViewById(R.id.icon)
        val more: MaterialButton = itemView.findViewById(R.id.more)
        val processName: TextView = itemView.findViewById(R.id.process_name)
        val packageName: TextView = itemView.findViewById(R.id.package_name)
        val processIds: TextView = itemView.findViewById(R.id.process_ids)
        val memoryUsage: TextView = itemView.findViewById(R.id.memory_usage)
        val userAndStateInfo: TextView = itemView.findViewById(R.id.user_state_info)
        val selinuxContext: TextView = itemView.findViewById(R.id.selinux_context)
    }

    companion object {
        private const val VIEW_TYPE_MEMORY_INFO = 1
        private const val VIEW_TYPE_PROCESS_INFO = 2
    }
}
