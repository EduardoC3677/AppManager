// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PermissionInfo
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.util.SparseArray
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.IntDef
import androidx.annotation.UiThread
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.materialswitch.MaterialSwitch
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat
import io.github.muntashirakon.AppManager.compat.ManifestCompat
import io.github.muntashirakon.AppManager.compat.PermissionCompat
import io.github.muntashirakon.AppManager.details.struct.AppDetailsAppOpItem
import io.github.muntashirakon.AppManager.details.struct.AppDetailsDefinedPermissionItem
import io.github.muntashirakon.AppManager.details.struct.AppDetailsItem
import io.github.muntashirakon.AppManager.details.struct.AppDetailsPermissionItem
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader
import io.github.muntashirakon.AppManager.self.pref.TipsPrefs
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.utils.*
import io.github.muntashirakon.AppManager.utils.PackageUtils.getAppOpModeNames
import io.github.muntashirakon.AppManager.utils.PackageUtils.getAppOpNames
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes
import io.github.muntashirakon.dialog.SearchableItemsDialogBuilder
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder
import io.github.muntashirakon.dialog.TextInputDropdownDialogBuilder
import io.github.muntashirakon.util.AdapterUtils
import io.github.muntashirakon.view.ProgressIndicatorCompat
import io.github.muntashirakon.widget.MaterialAlertView
import io.github.muntashirakon.widget.RecyclerView
import java.util.*

class AppDetailsPermissionsFragment : AppDetailsFragment() {
    @IntDef(value = [APP_OPS, USES_PERMISSIONS, PERMISSIONS])
    @Retention(AnnotationRetention.SOURCE)
    annotation class PermissionProperty

    private var mPackageName: String? = null
    private var mAdapter: AppDetailsRecyclerAdapter? = null
    private var mIsExternalApk: Boolean = false
    @PermissionProperty
    private var mNeededProperty: Int = 0
    private var mSortOrder: Int = 0
    private var mSearchQuery: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mNeededProperty = requireArguments().getInt(ARG_TYPE)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        emptyView.setText(getNotFoundString(mNeededProperty))
        mAdapter = AppDetailsRecyclerAdapter()
        recyclerView.adapter = mAdapter
        alertView.setEndIconOnClickListener {
            alertView.hide()
            when (mNeededProperty) {
                APP_OPS -> TipsPrefs.getInstance().setDisplayInAppOpsTab(false)
                USES_PERMISSIONS -> TipsPrefs.getInstance().setDisplayInUsesPermissionsTab(false)
                PERMISSIONS -> TipsPrefs.getInstance().setDisplayInPermissionsTab(false)
            }
        }
        val helpStringRes = getHelpString(mNeededProperty)
        if (helpStringRes != 0) {
            alertView.setText(helpStringRes)
            alertView.postDelayed({ alertView.hide() }, 15000)
        } else alertView.visibility = View.GONE
        viewModel?.let { vm ->
            mSortOrder = vm.getSortOrder(mNeededProperty)
            mSearchQuery = vm.getSearchQuery()
            mPackageName = vm.getPackageName()
            vm.get(mNeededProperty).observe(viewLifecycleOwner) { items ->
                if (items != null && mAdapter != null && vm.isPackageExist) {
                    mPackageName = vm.getPackageName()
                    mIsExternalApk = vm.isExternalApk
                    mAdapter!!.setDefaultList(items)
                } else ProgressIndicatorCompat.setVisibility(progressIndicator, false)
            }
            vm.getRuleApplicationStatus().observe(viewLifecycleOwner) { status ->
                alertView.setAlertType(MaterialAlertView.ALERT_TYPE_WARN)
                if (status == AppDetailsViewModel.RULE_NOT_APPLIED) alertView.show() else alertView.hide()
            }
        }
    }

    override fun onRefresh() {
        refreshDetails()
        swipeRefresh.isRefreshing = false
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        when (mNeededProperty) {
            APP_OPS -> inflater.inflate(R.menu.fragment_app_details_app_ops_actions, menu)
            USES_PERMISSIONS -> {
                if (viewModel?.isExternalApk == false) inflater.inflate(R.menu.fragment_app_details_permissions_actions, menu)
                else inflater.inflate(R.menu.fragment_app_details_refresh_actions, menu)
            }
            else -> inflater.inflate(R.menu.fragment_app_details_refresh_actions, menu)
        }
    }

    override fun onPrepareMenu(menu: Menu) {
        if (viewModel == null || viewModel!!.isExternalApk) return
        menu.findItem(sSortMenuItemIdsMap[viewModel!!.getSortOrder(mNeededProperty)])?.isChecked = true
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh_details -> { refreshDetails(); true }
            R.id.action_reset_to_default -> {
                ProgressIndicatorCompat.setVisibility(progressIndicator, true)
                ThreadUtils.postOnBackgroundThread {
                    if (viewModel?.resetAppOps() == false) ThreadUtils.postOnMainThread { UIUtils.displayShortToast(R.string.failed_to_reset_app_ops) }
                    else ThreadUtils.postOnMainThread { if (!isDetached) refreshDetails() }
                }
                true
            }
            R.id.action_deny_dangerous_app_ops -> {
                ProgressIndicatorCompat.setVisibility(progressIndicator, true)
                ThreadUtils.postOnBackgroundThread {
                    if (viewModel?.ignoreDangerousAppOps() == true) ThreadUtils.postOnMainThread { if (!isDetached) refreshDetails() }
                    else ThreadUtils.postOnMainThread { UIUtils.displayShortToast(R.string.failed_to_deny_dangerous_app_ops) }
                }
                true
            }
            R.id.action_toggle_default_app_ops -> {
                ProgressIndicatorCompat.setVisibility(progressIndicator, true)
                Prefs.AppDetailsPage.setDisplayDefaultAppOps(!Prefs.AppDetailsPage.displayDefaultAppOps())
                refreshDetails()
                true
            }
            R.id.action_custom_app_op -> {
                val modes = AppOpsManagerCompat.getModeConstants()
                val appOps = AppOpsManagerCompat.getAllOps()
                val modeNames = getAppOpModeNames(modes).toList()
                val appOpNames = getAppOpNames(appOps).toList()
                val builder = TextInputDropdownDialogBuilder(activity, R.string.set_custom_app_op)
                builder.setTitle(R.string.set_custom_app_op)
                    .setDropdownItems(appOpNames, -1, true)
                    .setAuxiliaryInput(R.string.mode, null, null, modeNames, true)
                    .setPositiveButton(R.string.apply) { _, _, inputText, _ ->
                        val mode = try { Utils.getIntegerFromString(builder.auxiliaryInput, modeNames, modes) } catch (e: Exception) { return@setPositiveButton }
                        val op = try { Utils.getIntegerFromString(inputText, appOpNames, appOps) } catch (e: Exception) { return@setPositiveButton }
                        ThreadUtils.postOnBackgroundThread {
                            if (viewModel?.setAppOp(op, mode) == true) ThreadUtils.postOnMainThread { if (!isDetached) refreshDetails() }
                            else ThreadUtils.postOnMainThread { UIUtils.displayShortToast(R.string.failed_to_enable_op) }
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                true
            }
            R.id.action_deny_dangerous_permissions -> {
                ProgressIndicatorCompat.setVisibility(progressIndicator, true)
                ThreadUtils.postOnBackgroundThread {
                    if (viewModel?.revokeDangerousPermissions() == false) ThreadUtils.postOnMainThread { UIUtils.displayShortToast(R.string.failed_to_deny_dangerous_perms) }
                    ThreadUtils.postOnMainThread { if (!isDetached) refreshDetails() }
                }
                true
            }
            R.id.action_sort_by_name -> { setSortBy(SORT_BY_NAME); item.isChecked = true; true }
            R.id.action_sort_by_app_ops_values -> { setSortBy(SORT_BY_APP_OP_VALUES); item.isChecked = true; true }
            R.id.action_sort_by_denied_app_ops -> { setSortBy(SORT_BY_DENIED_APP_OPS); item.isChecked = true; true }
            R.id.action_sort_by_dangerous_permissions -> { setSortBy(SORT_BY_DANGEROUS_PERMS); item.isChecked = true; true }
            R.id.action_sort_by_denied_permissions -> { setSortBy(SORT_BY_DENIED_PERMS); item.isChecked = true; true }
            else -> false
        }
    }

    override fun onResume() {
        super.onResume()
        activity.searchView?.let {
            if (!it.isShown) it.visibility = View.VISIBLE
            it.setOnQueryTextListener(this)
            viewModel?.let { vm ->
                if (vm.getSortOrder(mNeededProperty) != mSortOrder || vm.getSearchQuery() != mSearchQuery) vm.filterAndSortItems(mNeededProperty)
            }
        }
    }

    override fun onQueryTextChange(searchQuery: String, type: Int): Boolean {
        viewModel?.setSearchQuery(searchQuery, type, mNeededProperty)
        return true
    }

    private fun getNotFoundString(@PermissionProperty index: Int): Int {
        return when (index) {
            APP_OPS -> if (mIsExternalApk) R.string.external_apk_no_app_op else if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.GET_APP_OPS_STATS)) R.string.no_app_ops else R.string.no_app_ops_permission
            else -> R.string.require_no_permission
        }
    }

    private fun getHelpString(@PermissionProperty index: Int): Int {
        return when (index) {
            APP_OPS -> if (TipsPrefs.getInstance().displayInAppOpsTab() && SelfPermissions.canModifyAppOpMode()) R.string.help_app_ops_tab else 0
            USES_PERMISSIONS -> if (TipsPrefs.getInstance().displayInUsesPermissionsTab() && SelfPermissions.canModifyPermissions()) R.string.help_uses_permissions_tab else 0
            PERMISSIONS -> if (TipsPrefs.getInstance().displayInPermissionsTab()) R.string.help_permissions_tab else 0
            else -> 0
        }
    }

    private fun setSortBy(@SortOrder sortBy: Int) {
        ProgressIndicatorCompat.setVisibility(progressIndicator, true)
        viewModel?.setSortOrder(sortBy, mNeededProperty)
    }

    private fun refreshDetails() {
        if (viewModel == null || mIsExternalApk) return
        ProgressIndicatorCompat.setVisibility(progressIndicator, true)
        viewModel!!.triggerPackageChange()
    }

    @UiThread
    private inner class AppDetailsRecyclerAdapter : RecyclerView.Adapter<AppDetailsRecyclerAdapter.ViewHolder>() {
        private val mAdapterList = mutableListOf<AppDetailsItem<*>>()
        private var mRequestedProperty: Int = 0
        private var mConstraint: String? = null
        private var mCanModifyAppOpMode: Boolean = false

        @UiThread
        fun setDefaultList(list: List<AppDetailsItem<*>>) {
            ThreadUtils.postOnBackgroundThread {
                mRequestedProperty = mNeededProperty
                mConstraint = viewModel?.getSearchQuery()
                mCanModifyAppOpMode = SelfPermissions.canModifyAppOpMode()
                ThreadUtils.postOnMainThread {
                    if (isDetached) return@postOnMainThread
                    ProgressIndicatorCompat.setVisibility(progressIndicator, false)
                    synchronized(mAdapterList) { AdapterUtils.notifyDataSetChanged(this, mAdapterList, list) }
                }
            }
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val cardView: MaterialCardView = itemView as MaterialCardView
            var textView1: TextView? = null
            var textView2: TextView? = null
            var textView3: TextView? = null
            var textView4: TextView? = null
            var textView5: TextView? = null
            var textView6: TextView? = null
            var textView7: TextView? = null
            var textView8: TextView? = null
            var imageView: ImageView? = null
            var toggleSwitch: MaterialSwitch? = null
            var settingButton: MaterialButton? = null
            var chipType: Chip? = null

            init {
                when (mRequestedProperty) {
                    PERMISSIONS -> {
                        imageView = itemView.findViewById(R.id.icon)
                        textView1 = itemView.findViewById(R.id.label)
                        textView2 = itemView.findViewById(R.id.name)
                        textView3 = itemView.findViewById(R.id.taskAffinity)
                        textView4 = itemView.findViewById(R.id.orientation)
                        textView5 = itemView.findViewById(R.id.launchMode)
                        chipType = itemView.findViewById(R.id.type)
                        itemView.findViewById<View>(R.id.softInput).visibility = View.GONE
                        itemView.findViewById<View>(R.id.launch).visibility = View.GONE
                        itemView.findViewById<View>(R.id.edit_shortcut_btn).visibility = View.GONE
                        itemView.findViewById<View>(R.id.toggle_button).visibility = View.GONE
                    }
                    APP_OPS -> {
                        textView1 = itemView.findViewById(R.id.op_name)
                        textView2 = itemView.findViewById(R.id.perm_description)
                        textView3 = itemView.findViewById(R.id.perm_protection_level)
                        textView4 = itemView.findViewById(R.id.perm_package_name)
                        textView5 = itemView.findViewById(R.id.perm_group)
                        textView6 = itemView.findViewById(R.id.perm_name)
                        textView7 = itemView.findViewById(R.id.op_mode_running_duration)
                        textView8 = itemView.findViewById(R.id.op_accept_reject_time)
                        toggleSwitch = itemView.findViewById(R.id.perm_toggle_btn)
                    }
                    USES_PERMISSIONS -> {
                        textView1 = itemView.findViewById(R.id.perm_name)
                        textView2 = itemView.findViewById(R.id.perm_description)
                        textView3 = itemView.findViewById(R.id.perm_protection_level)
                        textView4 = itemView.findViewById(R.id.perm_package_name)
                        textView5 = itemView.findViewById(R.id.perm_group)
                        toggleSwitch = itemView.findViewById(R.id.perm_toggle_btn)
                        settingButton = itemView.findViewById(R.id.action_settings)
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val layout = when (mRequestedProperty) {
                APP_OPS -> R.layout.item_app_details_appop
                USES_PERMISSIONS -> R.layout.item_app_details_perm
                else -> R.layout.item_app_details_primary
            }
            return ViewHolder(LayoutInflater.from(parent.context).inflate(layout, parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val context = holder.itemView.context
            when (mRequestedProperty) {
                APP_OPS -> getAppOpsView(context, holder, position)
                USES_PERMISSIONS -> getUsesPermissionsView(context, holder, position)
                PERMISSIONS -> getPermissionsView(context, holder, position)
            }
        }

        override fun getItemCount(): Int = synchronized(mAdapterList) { mAdapterList.size }

        private fun getAppOpsView(context: Context, holder: ViewHolder, index: Int) {
            val item = synchronized(mAdapterList) { mAdapterList[index] as AppDetailsAppOpItem }
            val opNameSsb = SpannableStringBuilder("${item.op} - ")
            if (item.name == item.op.toString()) opNameSsb.append(getString(R.string.unknown_op))
            else if (mConstraint != null && item.name.lowercase(Locale.ROOT).contains(mConstraint!!)) opNameSsb.append(UIUtils.getHighlightedText(item.name, mConstraint!!, colorQueryStringHighlight))
            else opNameSsb.append(item.name)
            holder.textView1?.text = opNameSsb
            val opInfo = StringBuilder("${context.getString(R.string.mode)}${LangUtils.getSeparatorString()}${AppOpsManagerCompat.modeToName(item.mode)}")
            if (item.isRunning) opInfo.append(", ${context.getString(R.string.running)}")
            if (item.duration != 0L) opInfo.append(", ${context.getString(R.string.duration)}${LangUtils.getSeparatorString()}${DateUtils.getFormattedDuration(context, item.duration, true)}")
            holder.textView7?.text = opInfo
            val now = System.currentTimeMillis()
            val hasAcc = item.time != 0L && item.time != -1L
            val hasRej = item.rejectTime != 0L && item.rejectTime != -1L
            if (hasAcc || hasRej) {
                val sb = StringBuilder()
                if (hasAcc) sb.append("${context.getString(R.string.accept_time)}${LangUtils.getSeparatorString()}${DateUtils.getFormattedDuration(context, now - item.time)} ${context.getString(R.string.ago)}")
                if (hasRej) {
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append("${context.getString(R.string.reject_time)}${LangUtils.getSeparatorString()}${DateUtils.getFormattedDuration(context, now - item.rejectTime)} ${context.getString(R.string.ago)}")
                }
                holder.textView8?.visibility = View.VISIBLE
                holder.textView8?.text = sb
            } else holder.textView8?.visibility = View.GONE
            item.permissionInfo?.let { pi ->
                holder.textView6?.visibility = View.VISIBLE
                holder.textView6?.text = "${context.getString(R.string.permission_name)}${LangUtils.getSeparatorString()}${pi.name}"\npi.loadDescription(packageManager)?.let { holder.textView2?.visibility = View.VISIBLE; holder.textView2?.text = it } ?: run { holder.textView2?.visibility = View.GONE }
                holder.textView3?.visibility = View.VISIBLE
                holder.textView3?.text = "⚑ ${Utils.getProtectionLevelString(pi)}|${if (item.permission!!.isGranted()) "granted" else "revoked"}"\npi.packageName?.let { holder.textView4?.visibility = View.VISIBLE; holder.textView4?.text = "${context.getString(R.string.package_name)}${LangUtils.getSeparatorString()}$it" } ?: run { holder.textView4?.visibility = View.GONE }
                pi.group?.let { holder.textView5?.visibility = View.VISIBLE; holder.textView5?.text = "${context.getString(R.string.group)}${LangUtils.getSeparatorString()}$it" } ?: run { holder.textView5?.visibility = View.GONE }
            } ?: run { holder.textView2?.visibility = View.GONE; holder.textView3?.visibility = View.GONE; holder.textView4?.visibility = View.GONE; holder.textView5?.visibility = View.GONE; holder.textView6?.visibility = View.GONE }
            holder.cardView.strokeColor = if (item.isDangerous) ColorCodes.getPermissionDangerousIndicatorColor(context) else Color.TRANSPARENT
            holder.toggleSwitch?.visibility = if (mCanModifyAppOpMode) View.VISIBLE else View.GONE
            holder.toggleSwitch?.isChecked = item.isAllowed
            holder.itemView.setOnClickListener {
                ThreadUtils.postOnBackgroundThread {
                    if (viewModel?.setAppOpMode(item) == true) ThreadUtils.postOnMainThread { notifyItemChanged(index, AdapterUtils.STUB) }
                    else ThreadUtils.postOnMainThread { UIUtils.displayShortToast(if (!item.isAllowed) R.string.failed_to_enable_op else R.string.failed_to_disable_op) }
                }
            }
            holder.itemView.setOnLongClickListener {
                val modes = AppOpsManagerCompat.getModeConstants()
                SearchableSingleChoiceDialogBuilder(activity, modes, getAppOpModeNames(modes)).setTitle(R.string.set_app_op_mode).setSelection(item.mode).setOnSingleChoiceClickListener { dialog, which, _, _ ->
                    val opMode = modes[which]
                    ThreadUtils.postOnBackgroundThread {
                        if (viewModel?.setAppOpMode(item, opMode) == true) ThreadUtils.postOnMainThread { notifyItemChanged(index, AdapterUtils.STUB) }
                        else ThreadUtils.postOnMainThread { UIUtils.displayShortToast(R.string.failed_to_change_app_op_mode) }
                    }
                    dialog.dismiss()
                }.show()
                true
            }
        }

        private fun getUsesPermissionsView(context: Context, holder: ViewHolder, index: Int) {
            val item = synchronized(mAdapterList) { mAdapterList[index] as AppDetailsPermissionItem }
            val info = item.item
            holder.textView1?.text = if (mConstraint != null && info.name.lowercase(Locale.ROOT).contains(mConstraint!!)) UIUtils.getHighlightedText(info.name, mConstraint!!, colorQueryStringHighlight) else info.name
            info.loadDescription(packageManager)?.let { holder.textView2?.visibility = View.VISIBLE; holder.textView2?.text = it } ?: run { holder.textView2?.visibility = View.GONE }
            holder.textView3?.text = "⚑ ${Utils.getProtectionLevelString(info)}|${if (item.permission.isGranted()) "granted" else "revoked"}"\nholder.cardView.strokeColor = if (item.isDangerous) ColorCodes.getPermissionDangerousIndicatorColor(context) else Color.TRANSPARENT
            info.packageName?.let { holder.textView4?.visibility = View.VISIBLE; holder.textView4?.text = "${context.getString(R.string.package_name)}${LangUtils.getSeparatorString()}$it" } ?: run { holder.textView4?.visibility = View.GONE }
            info.group?.let { holder.textView5?.visibility = View.VISIBLE; holder.textView5?.text = "${context.getString(R.string.group)}${LangUtils.getSeparatorString()}$it" } ?: run { holder.textView5?.visibility = View.GONE }
            val canMod = item.modifiable && !mIsExternalApk
            if (canMod) {
                holder.toggleSwitch?.visibility = View.VISIBLE
                holder.toggleSwitch?.isChecked = item.permission.isGranted()
                holder.itemView.setOnClickListener {
                    ThreadUtils.postOnBackgroundThread {
                        try {
                            if (viewModel?.togglePermission(item) == true) ThreadUtils.postOnMainThread { notifyItemChanged(index, AdapterUtils.STUB) }
                            else throw Exception()
                        } catch (e: Exception) {
                            ThreadUtils.postOnMainThread { UIUtils.displayShortToast(if (item.permission.isGranted()) R.string.failed_to_grant_permission else R.string.failed_to_revoke_permission) }
                        }
                    }
                }
            } else {
                holder.toggleSwitch?.visibility = View.GONE
                holder.itemView.setOnClickListener(null)
                holder.itemView.isClickable = false
                item.settingItem?.let { si ->
                    holder.settingButton?.visibility = View.VISIBLE
                    holder.settingButton?.setOnClickListener { try { startActivity(si.toIntent(viewModel!!.getPackageNameNonNull())) } catch (th: Throwable) { th.localizedMessage?.let { UIUtils.displayLongToast(it) } } }
                } ?: run { holder.settingButton?.visibility = View.GONE }
            }
            val flags = item.permission.getFlags()
            holder.itemView.setOnLongClickListener(if (flags == 0) null else View.OnLongClickListener {
                val pf = PermissionCompat.getPermissionFlagsWithString(flags)
                val strings = Array(pf.size()) { i -> pf.valueAt(i) }
                SearchableItemsDialogBuilder(activity, strings).setTitle(R.string.permission_flags).setNegativeButton(R.string.close, null).show()
                true
            })
            holder.itemView.isLongClickable = flags != 0
        }

        private fun getPermissionsView(context: Context, holder: ViewHolder, index: Int) {
            val item = synchronized(mAdapterList) { mAdapterList[index] as AppDetailsDefinedPermissionItem }
            val info = item.item
            holder.chipType?.setText(if (item.isExternal) R.string.external else R.string.internal)
            holder.textView1?.text = info.loadLabel(packageManager)
            holder.textView2?.text = if (mConstraint != null && info.name.lowercase(Locale.ROOT).contains(mConstraint!!)) UIUtils.getHighlightedText(info.name, mConstraint!!, colorQueryStringHighlight)
            else if (info.name.startsWith(mPackageName!!)) info.name.replaceFirst(mPackageName!!, "") else info.name
            val tag = "${mPackageName}_${info.name}"\nholder.imageView?.tag = tag
            ImageLoader.getInstance().displayImage(tag, info, holder.imageView!!)
            info.loadDescription(packageManager)?.let { holder.textView3?.visibility = View.VISIBLE; holder.textView3?.text = it } ?: run { holder.textView3?.visibility = View.GONE }
            holder.textView4?.text = "${getString(R.string.group)}: ${info.group}${permAppOp(info.name)}"\nval prot = Utils.getProtectionLevelString(info)
            holder.textView5?.text = "⚑ $prot"\nholder.cardView.strokeColor = if (prot.contains("dangerous")) ColorCodes.getPermissionDangerousIndicatorColor(context) else Color.TRANSPARENT
        }

        private fun permAppOp(s: String): String = AppOpsManagerCompat.permissionToOp(s)?.let { "\nAppOp: $it" } ?: ""
    }
}
