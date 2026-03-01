// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details

import android.content.om.IOverlayManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.annotation.UiThread
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.compat.ManifestCompat
import io.github.muntashirakon.AppManager.compat.OverlayManagerCompact
import io.github.muntashirakon.AppManager.details.struct.AppDetailsItem
import io.github.muntashirakon.AppManager.details.struct.AppDetailsOverlayItem
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.self.pref.TipsPrefs
import io.github.muntashirakon.AppManager.utils.LangUtils
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes
import io.github.muntashirakon.util.AdapterUtils
import io.github.muntashirakon.view.ProgressIndicatorCompat
import io.github.muntashirakon.widget.MaterialAlertView
import io.github.muntashirakon.widget.RecyclerView
import java.util.*

class AppDetailsOverlaysFragment : AppDetailsFragment() {
    private var mAdapter: AppDetailsRecyclerAdapter? = null
    private var overlayManager: IOverlayManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.CHANGE_OVERLAY_PACKAGES)) {
            overlayManager = OverlayManagerCompact.getOverlayManager()
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.fragment_app_details_overlay_actions, menu)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        emptyView.text = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O -> getString(R.string.overlay_sdk_version_too_low)
            !SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.CHANGE_OVERLAY_PACKAGES) -> getString(R.string.no_overlay_permission)
            else -> getString(R.string.no_overlays)
        }
        mAdapter = AppDetailsRecyclerAdapter()
        recyclerView.adapter = mAdapter
        alertView.setEndIconOnClickListener {
            alertView.hide()
            TipsPrefs.getInstance().setDisplayInOverlaysTab(false)
        }
        if (TipsPrefs.getInstance().displayInOverlaysTab()) {
            alertView.postDelayed({ alertView.hide() }, 15000)
        } else alertView.visibility = View.GONE
        viewModel?.let { vm ->
            vm.get(OVERLAYS).observe(viewLifecycleOwner) { items ->
                if (items != null && mAdapter != null && vm.isPackageExist) mAdapter!!.setDefaultList(items)
                else ProgressIndicatorCompat.setVisibility(progressIndicator, false)
            }
            vm.getRuleApplicationStatus().observe(viewLifecycleOwner) { status ->
                alertView.setAlertType(MaterialAlertView.ALERT_TYPE_WARN)
                if (status == AppDetailsViewModel.RULE_NOT_APPLIED) alertView.show() else alertView.hide()
            }
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_refresh_details -> { refreshDetails(); true }
            R.id.action_sort_by_name -> { setSortBy(SORT_BY_NAME); menuItem.isChecked = true; true }
            R.id.action_sort_by_priority -> { setSortBy(SORT_BY_PRIORITY); menuItem.isChecked = true; true }
            else -> false
        }
    }

    private fun setSortBy(@SortOrder sortBy: Int) {
        ProgressIndicatorCompat.setVisibility(progressIndicator, true)
        viewModel?.setSortOrder(sortBy, OVERLAYS)
    }

    private fun refreshDetails() {
        if (viewModel == null || overlayManager == null) return
        ProgressIndicatorCompat.setVisibility(progressIndicator, true)
        viewModel!!.triggerPackageChange()
    }

    override fun onRefresh() { swipeRefresh.isRefreshing = false }

    override fun onQueryTextChange(newText: String, type: Int): Boolean {
        viewModel?.setSearchQuery(newText, type, OVERLAYS)
        return true
    }

    private inner class AppDetailsRecyclerAdapter : RecyclerView.Adapter<AppDetailsRecyclerAdapter.ViewHolder>() {
        private val mAdapterList = mutableListOf<AppDetailsItem<*>>()
        private var mConstraint: String? = null

        @UiThread
        fun setDefaultList(list: List<AppDetailsItem<*>>) {
            ThreadUtils.postOnBackgroundThread {
                mConstraint = viewModel?.getSearchQuery()
                ThreadUtils.postOnMainThread {
                    if (isDetached) return@postOnMainThread
                    ProgressIndicatorCompat.setVisibility(progressIndicator, false)
                    synchronized(mAdapterList) { AdapterUtils.notifyDataSetChanged(this, mAdapterList, list) }
                }
            }
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val cardView: MaterialCardView = itemView as MaterialCardView
            val overlayName: TextView = itemView.findViewById(R.id.overlay_name)
            val packageName: TextView = itemView.findViewById(R.id.overlay_package_name)
            val overlayCategory: TextView = itemView.findViewById(R.id.overlay_category)
            val overlayState: TextView = itemView.findViewById(R.id.overlay_state)
            val toggleSwitch: MaterialSwitch = itemView.findViewById(R.id.overlay_toggle_btn)
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.item_app_details_overlay, viewGroup, false))
        }

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onBindViewHolder(holder: ViewHolder, index: Int) {
            val item = synchronized(mAdapterList) { mAdapterList[index] as AppDetailsOverlayItem }
            val name = item.name
            holder.overlayName.text = if (mConstraint != null && name.lowercase(Locale.ROOT).contains(mConstraint!!)) UIUtils.getHighlightedText(name, mConstraint!!, colorQueryStringHighlight) else name
            holder.packageName.text = item.packageName
            item.category?.let {
                holder.overlayCategory.visibility = View.VISIBLE
                holder.overlayCategory.text = "${getString(R.string.overlay_category)}${LangUtils.getSeparatorString()}$it"
            } ?: run { holder.overlayCategory.visibility = View.GONE }
            holder.toggleSwitch.isEnabled = item.isMutable
            holder.toggleSwitch.isClickable = true
            holder.toggleSwitch.isChecked = item.isEnabled
            val sb = StringBuilder("${getString(R.string.state)}${LangUtils.getSeparatorString()}${item.readableState}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) sb.append(" | ${getString(R.string.priority)}${LangUtils.getSeparatorString()}${item.priority}")
            holder.overlayState.text = sb
            holder.itemView.isClickable = false
            if (item.isMutable) {
                holder.toggleSwitch.isClickable = true
                holder.toggleSwitch.setOnClickListener {
                    ThreadUtils.postOnBackgroundThread {
                        try {
                            if (item.setEnabled(overlayManager!!, !item.isEnabled)) ThreadUtils.postOnMainThread { notifyItemChanged(index, AdapterUtils.STUB) }
                            else throw Exception("Error Changing Overlay State $item")
                        } catch (e: Exception) {
                            Log.e(TAG, "Couldn't Change Overlay State", e)
                            ThreadUtils.postOnMainThread { UIUtils.displayShortToast(R.string.failed) }
                        }
                    }
                }
                holder.toggleSwitch.visibility = View.VISIBLE
            } else {
                holder.toggleSwitch.setOnClickListener(null)
                holder.toggleSwitch.isClickable = false
                holder.toggleSwitch.visibility = View.GONE
            }
            if (item.isFabricated) holder.cardView.strokeColor = ColorCodes.getPermissionDangerousIndicatorColor(requireContext())
            holder.cardView.strokeColor = Color.TRANSPARENT
        }

        override fun getItemCount(): Int = synchronized(mAdapterList) { mAdapterList.size }
    }

    companion object {
        private val TAG = AppDetailsOverlaysFragment::class.java.simpleName
    }
}
