// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ComponentInfo
import android.content.pm.ProviderInfo
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.IntDef
import androidx.annotation.MainThread
import androidx.annotation.UiThread
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.compat.ActivityManagerCompat
import io.github.muntashirakon.AppManager.compat.ManifestCompat
import io.github.muntashirakon.AppManager.compat.UserHandleHidden
import io.github.muntashirakon.AppManager.details.struct.AppDetailsActivityItem
import io.github.muntashirakon.AppManager.details.struct.AppDetailsComponentItem
import io.github.muntashirakon.AppManager.details.struct.AppDetailsItem
import io.github.muntashirakon.AppManager.details.struct.AppDetailsServiceItem
import io.github.muntashirakon.AppManager.intercept.ActivityInterceptor
import io.github.muntashirakon.AppManager.rules.RuleType
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils
import io.github.muntashirakon.AppManager.rules.struct.ComponentRule
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader
import io.github.muntashirakon.AppManager.settings.FeatureController
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.shortcut.CreateShortcutDialogFragment
import io.github.muntashirakon.AppManager.types.UserPackagePair
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.AppManager.utils.Utils
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes
import io.github.muntashirakon.util.AdapterUtils
import io.github.muntashirakon.view.ProgressIndicatorCompat
import io.github.muntashirakon.widget.MaterialAlertView
import io.github.muntashirakon.widget.RecyclerView
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AppDetailsComponentsFragment : AppDetailsFragment() {
    @IntDef(value = [ACTIVITIES, SERVICES, RECEIVERS, PROVIDERS])
    @Retention(AnnotationRetention.SOURCE)
    annotation class ComponentProperty

    private var mPackageName: String? = null
    private var mAdapter: AppDetailsRecyclerAdapter? = null
    private var mBlockingToggler: MenuItem? = null
    private var mIsExternalApk: Boolean = false
    @ComponentProperty
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
        alertView.setEndIconOnClickListener { alertView.hide() }
        alertView.setText(R.string.rules_not_applied)
        alertView.visibility = View.GONE
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
                updateBlockMenuItem(status)
            }
        }
    }

    override fun onRefresh() {
        refreshDetails()
        swipeRefresh.isRefreshing = false
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        if (viewModel != null && !viewModel!!.isExternalApk && SelfPermissions.canModifyAppComponentStates(viewModel!!.getUserId(), viewModel!!.getPackageNameNonNull(), viewModel!!.isTestOnlyApp())) {
            menuInflater.inflate(R.menu.fragment_app_details_components_actions, menu)
            mBlockingToggler = menu.findItem(R.id.action_toggle_blocking)
        } else menuInflater.inflate(R.menu.fragment_app_details_refresh_actions, menu)
    }

    override fun onPrepareMenu(menu: Menu) {
        if (viewModel == null || viewModel!!.isExternalApk) return
        menu.findItem(sSortMenuItemIdsMap[viewModel!!.getSortOrder(mNeededProperty)])?.isChecked = true
        viewModel!!.getRuleApplicationStatus().value?.let { updateBlockMenuItem(it) }
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh_details -> { refreshDetails(); true }
            R.id.action_toggle_blocking -> { viewModel?.applyRulesToggle(); true }
            R.id.action_block_unblock_trackers -> {
                MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.block_unblock_trackers)
                    .setMessage(R.string.choose_what_to_do)
                    .setPositiveButton(R.string.block) { _, _ -> blockUnblockTrackers(true) }
                    .setNegativeButton(R.string.cancel, null)
                    .setNeutralButton(R.string.unblock) { _, _ -> blockUnblockTrackers(false) }
                    .show()
                true
            }
            R.id.action_sort_by_name -> { setSortBy(SORT_BY_NAME); item.isChecked = true; true }
            R.id.action_sort_by_blocked_components -> { setSortBy(SORT_BY_BLOCKED); item.isChecked = true; true }
            R.id.action_sort_by_tracker_components -> { setSortBy(SORT_BY_TRACKERS); item.isChecked = true; true }
            else -> false
        }
    }

    override fun onResume() {
        super.onResume()
        activity.searchView?.let {
            if (!it.isShown) it.visibility = View.VISIBLE
            it.setOnQueryTextListener(this)
            viewModel?.let { vm ->
                val sort = vm.getSortOrder(mNeededProperty)
                val search = vm.getSearchQuery()
                if (sort != mSortOrder || search != mSearchQuery) vm.filterAndSortItems(mNeededProperty)
            }
        }
    }

    override fun onQueryTextChange(searchQuery: String, type: Int): Boolean {
        viewModel?.setSearchQuery(searchQuery, type, mNeededProperty)
        return true
    }

    private fun updateBlockMenuItem(status: Int) {
        mBlockingToggler?.let {
            when (status) {
                AppDetailsViewModel.RULE_APPLIED -> {
                    it.isVisible = !Prefs.Blocking.globalBlockingEnabled()
                    it.setTitle(R.string.menu_remove_rules)
                }
                AppDetailsViewModel.RULE_NOT_APPLIED -> {
                    it.isVisible = !Prefs.Blocking.globalBlockingEnabled()
                    it.setTitle(R.string.menu_apply_rules)
                }
                AppDetailsViewModel.RULE_NO_RULE -> it.isVisible = false
            }
        }
    }

    private fun blockUnblockTrackers(block: Boolean) {
        val vm = viewModel ?: return
        val userPackagePairs = listOf(UserPackagePair(mPackageName!!, UserHandleHidden.myUserId()))
        ThreadUtils.postOnBackgroundThread {
            val failed = if (block) ComponentUtils.blockTrackingComponents(userPackagePairs) else ComponentUtils.unblockTrackingComponents(userPackagePairs)
            ThreadUtils.postOnMainThread {
                if (failed.isNotEmpty()) UIUtils.displayShortToast(if (block) R.string.failed_to_block_trackers else R.string.failed_to_unblock_trackers)
                else {
                    UIUtils.displayShortToast(if (block) R.string.trackers_blocked_successfully else R.string.trackers_unblocked_successfully)
                    if (!isDetached) refreshDetails()
                }
            }
            vm.setRuleApplicationStatusInternal()
        }
    }

    private fun getNotFoundString(@ComponentProperty index: Int): Int {
        return when (index) {
            SERVICES -> R.string.no_service
            RECEIVERS -> R.string.no_receivers
            PROVIDERS -> R.string.no_providers
            else -> R.string.no_activities
        }
    }

    private fun setSortBy(@SortOrder sortBy: Int) {
        ProgressIndicatorCompat.setVisibility(progressIndicator, true)
        viewModel?.setSortOrder(sortBy, mNeededProperty)
    }

    @MainThread
    private fun refreshDetails() {
        if (viewModel == null || mIsExternalApk) return
        ProgressIndicatorCompat.setVisibility(progressIndicator, true)
        viewModel!!.triggerPackageChange()
    }

    private inner class AppDetailsRecyclerAdapter : RecyclerView.Adapter<AppDetailsRecyclerAdapter.ViewHolder>() {
        private val mAdapterList = mutableListOf<AppDetailsItem<*>>()
        private var mRequestedProperty: Int = 0
        private var mConstraint: String? = null
        private var mUserId: Int = UserHandleHidden.myUserId()
        private var mCanModifyComponentStates: Boolean = false
        private var mCanStartAnyActivity: Boolean = false
        private val mBlockedIndicatorColor = ColorCodes.getComponentBlockedIndicatorColor(activity)
        private val mBlockedExternallyIndicatorColor = ColorCodes.getComponentExternallyBlockedIndicatorColor(activity)
        private val mTrackerIndicatorColor = ColorCodes.getComponentTrackerIndicatorColor(activity)
        private val mRunningIndicatorColor = ColorCodes.getComponentRunningIndicatorColor(activity)

        @UiThread
        fun setDefaultList(list: List<AppDetailsItem<*>>) {
            ThreadUtils.postOnBackgroundThread {
                mRequestedProperty = mNeededProperty
                mCanStartAnyActivity = SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.START_ANY_ACTIVITY)
                viewModel?.let {
                    mCanModifyComponentStates = !mIsExternalApk && SelfPermissions.canModifyAppComponentStates(it.getUserId(), it.getPackageNameNonNull(), it.isTestOnlyApp())
                    mConstraint = it.getSearchQuery()
                    mUserId = it.getUserId()
                } ?: run {
                    mCanModifyComponentStates = false
                    mConstraint = null
                    mUserId = UserHandleHidden.myUserId()
                }
                ThreadUtils.postOnMainThread {
                    if (isDetached) return@postOnMainThread
                    ProgressIndicatorCompat.setVisibility(progressIndicator, false)
                    synchronized(mAdapterList) { AdapterUtils.notifyDataSetChanged(this, mAdapterList, list) }
                }
            }
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val cardView: MaterialCardView = itemView as MaterialCardView
            val labelView: TextView = itemView.findViewById(R.id.label)
            val nameView: TextView = itemView.findViewById(R.id.name)
            val textView1: TextView = itemView.findViewById(R.id.taskAffinity)
            val textView2: TextView = itemView.findViewById(R.id.launchMode)
            val textView3: TextView = itemView.findViewById(R.id.orientation)
            val textView4: TextView = itemView.findViewById(R.id.softInput)
            val processNameView: TextView = itemView.findViewById(R.id.process_name)
            val imageView: ImageView = itemView.findViewById(R.id.icon)
            val shortcutBtn: Button = itemView.findViewById(R.id.edit_shortcut_btn)
            val launchBtn: MaterialButton = itemView.findViewById(R.id.launch)
            val toggleSwitch: MaterialSwitch = itemView.findViewById(R.id.toggle_button)
            val blockingMethod: TextView = itemView.findViewById(R.id.method)
            val chipType: Chip = itemView.findViewById(R.id.type)

            init {
                imageView.contentDescription = itemView.context.getString(R.string.icon)
                when (mRequestedProperty) {
                    SERVICES -> {
                        itemView.findViewById<View>(R.id.taskAffinity).visibility = View.GONE
                        itemView.findViewById<View>(R.id.launchMode).visibility = View.GONE
                        itemView.findViewById<View>(R.id.softInput).visibility = View.GONE
                        shortcutBtn.visibility = View.GONE
                    }
                    RECEIVERS, PROVIDERS -> {
                        launchBtn.visibility = View.GONE
                        shortcutBtn.visibility = View.GONE
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_details_primary, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            when (mRequestedProperty) {
                SERVICES -> getServicesView(holder, position)
                RECEIVERS -> getReceiverView(holder, position)
                PROVIDERS -> getProviderView(holder, position)
                ACTIVITIES -> getActivityView(holder, position)
            }
        }

        override fun getItemCount(): Int = synchronized(mAdapterList) { mAdapterList.size }

        private fun handleBlock(holder: ViewHolder, item: AppDetailsComponentItem, ruleType: RuleType) {
            val rule = item.rule
            val isBlocked = item.isBlocked
            if (isBlocked) {
                holder.blockingMethod.visibility = View.VISIBLE
                holder.blockingMethod.text = if (rule!!.isIfw) (if (item.isDisabled) "IFW+Dis" else "IFW") else "Dis"
            } else holder.blockingMethod.visibility = View.GONE
            holder.toggleSwitch.isChecked = !isBlocked
            holder.toggleSwitch.visibility = View.VISIBLE
            holder.toggleSwitch.setOnClickListener {
                val status = if (item.isBlocked) ComponentRule.COMPONENT_TO_BE_DEFAULTED else Prefs.Blocking.getDefaultBlockingMethod()
                viewModel?.updateRulesForComponent(item, ruleType, status)
            }
            holder.toggleSwitch.setOnLongClickListener {
                val popup = PopupMenu(activity, holder.toggleSwitch)
                val canIfw = item.item !is ProviderInfo && SelfPermissions.canBlockByIFW()
                popup.inflate(R.menu.fragment_app_details_components_selection_actions)
                popup.menu.findItem(R.id.action_ifw_and_disable).isEnabled = canIfw
                popup.menu.findItem(R.id.action_ifw).isEnabled = canIfw
                popup.setOnMenuItemClickListener { mi ->
                    val status = when (mi.itemId) {
                        R.id.action_ifw_and_disable -> ComponentRule.COMPONENT_TO_BE_BLOCKED_IFW_DISABLE
                        R.id.action_ifw -> ComponentRule.COMPONENT_TO_BE_BLOCKED_IFW
                        R.id.action_disable -> ComponentRule.COMPONENT_TO_BE_DISABLED
                        R.id.action_enable -> ComponentRule.COMPONENT_TO_BE_ENABLED
                        R.id.action_default -> ComponentRule.COMPONENT_TO_BE_DEFAULTED
                        else -> ComponentRule.COMPONENT_TO_BE_BLOCKED_IFW_DISABLE
                    }
                    viewModel?.updateRulesForComponent(item, ruleType, status)
                    true
                }
                popup.show()
                true
            }
        }

        private fun getActivityView(holder: ViewHolder, index: Int) {
            val item = synchronized(mAdapterList) { mAdapterList[index] as AppDetailsActivityItem }
            val info = item.item as ActivityInfo
            val name = item.name
            val disabled = !mIsExternalApk && item.isDisabled
            holder.cardView.strokeColor = when {
                !mIsExternalApk && item.isBlocked -> mBlockedIndicatorColor
                disabled -> mBlockedExternallyIndicatorColor
                item.isTracker -> mTrackerIndicatorColor
                else -> Color.TRANSPARENT
            }
            holder.chipType.visibility = if (item.isTracker) { holder.chipType.setText(R.string.tracker); View.VISIBLE } else View.GONE
            holder.nameView.text = if (mConstraint != null && name.lowercase(Locale.ROOT).contains(mConstraint!!)) UIUtils.getHighlightedText(name, mConstraint!!, colorQueryStringHighlight)
            else if (name.startsWith(mPackageName!!)) name.replaceFirst(mPackageName!!, "") else name
            val tag = "${mPackageName}_$name"
            holder.imageView.tag = tag
            ImageLoader.getInstance().displayImage(tag, info, holder.imageView)
            holder.textView1.text = "${getString(R.string.task_affinity)}: ${info.taskAffinity}"
            holder.textView2.text = "${getString(R.string.launch_mode)}: ${getString(Utils.getLaunchMode(info.launchMode))} | ${getString(R.string.orientation)}: ${getString(Utils.getOrientationString(info.screenOrientation))}"
            holder.textView3.text = Utils.getActivitiesFlagsString(info.flags)
            holder.textView4.text = "${getString(R.string.soft_input)}: ${Utils.getSoftInputString(info.softInputMode)} | ${info.permission ?: getString(R.string.require_no_permission)}"
            holder.labelView.text = item.label
            if (info.processName != null && info.processName != mPackageName) {
                holder.processNameView.visibility = View.VISIBLE
                holder.processNameView.text = "${getString(R.string.process_name)}: ${info.processName}"
            } else holder.processNameView.visibility = View.GONE
            if (item.canLaunch || item.canLaunchAssist) {
                holder.launchBtn.setOnClickListener {
                    val cn = ComponentName(mPackageName!!, name)
                    if (item.canLaunch) {
                        try { ActivityManagerCompat.startActivity(Intent().setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), mUserId) }
                        catch (e: Throwable) { UIUtils.displayLongToast(e.localizedMessage) }
                    } else {
                        ActivityManagerCompat.startActivityViaAssist(ContextUtils.getContext(), cn) {
                            val latch = CountDownLatch(1)
                            ThreadUtils.postOnMainThread {
                                MaterialAlertDialogBuilder(holder.itemView.context).setTitle(R.string.launch_activity_dialog_title).setMessage(R.string.launch_activity_dialog_message).setCancelable(false).setOnDismissListener { latch.countDown() }.setNegativeButton(R.string.close, null).show()
                            }
                            try { latch.await(10, TimeUnit.MINUTES) } catch (ignore: Exception) {}
                        }
                    }
                }
                if (FeatureController.isInterceptorEnabled()) {
                    holder.launchBtn.setOnLongClickListener {
                        val needRoot = mCanStartAnyActivity && (!info.exported || !SelfPermissions.checkSelfOrRemotePermission(info.permission))
                        startActivity(Intent(activity, ActivityInterceptor::class.java).apply { putExtra(ActivityInterceptor.EXTRA_PACKAGE_NAME, mPackageName); putExtra(ActivityInterceptor.EXTRA_CLASS_NAME, name); putExtra(ActivityInterceptor.EXTRA_USER_HANDLE, mUserId); putExtra(ActivityInterceptor.EXTRA_ROOT, needRoot) })
                        true
                    }
                }
                holder.shortcutBtn.setOnClickListener {
                    val si = PackageItemShortcutInfo(info, ActivityInfo::class.java, mUserId, item.canLaunchAssist).apply { name = item.label; icon = UIUtils.getBitmapFromDrawable(info.loadIcon(packageManager)) }
                    CreateShortcutDialogFragment.getInstance(si).show(parentFragmentManager, CreateShortcutDialogFragment.TAG)
                }
                holder.shortcutBtn.visibility = View.VISIBLE
                holder.launchBtn.visibility = View.VISIBLE
            } else {
                holder.shortcutBtn.visibility = View.GONE
                holder.launchBtn.visibility = View.GONE
            }
            if (mCanModifyComponentStates) handleBlock(holder, item, RuleType.ACTIVITY)
            else { holder.toggleSwitch.visibility = View.GONE; holder.blockingMethod.visibility = View.GONE }
        }

        private fun getServicesView(holder: ViewHolder, index: Int) {
            val item = synchronized(mAdapterList) { mAdapterList[index] as AppDetailsServiceItem }
            val info = item.item as ServiceInfo
            val disabled = !mIsExternalApk && item.isDisabled
            holder.cardView.strokeColor = when {
                item.isRunning -> mRunningIndicatorColor
                !mIsExternalApk && item.isBlocked -> mBlockedIndicatorColor
                disabled -> mBlockedExternallyIndicatorColor
                item.isTracker -> mTrackerIndicatorColor
                else -> Color.TRANSPARENT
            }
            holder.chipType.visibility = if (item.isTracker) { holder.chipType.setText(R.string.tracker); View.VISIBLE } else View.GONE
            holder.labelView.text = item.label
            holder.nameView.text = if (mConstraint != null && info.name.lowercase(Locale.ROOT).contains(mConstraint!!)) UIUtils.getHighlightedText(info.name, mConstraint!!, colorQueryStringHighlight)
            else if (info.name.startsWith(mPackageName!!)) info.name.replaceFirst(mPackageName!!, "") else info.name
            val tag = "${mPackageName}_${info.name}"
            holder.imageView.tag = tag
            ImageLoader.getInstance().displayImage(tag, info, holder.imageView)
            val sb = StringBuilder(Utils.getServiceFlagsString(info.flags))
            if (sb.isNotEmpty()) sb.append("
")
            sb.append(info.permission ?: getString(R.string.require_no_permission))
            holder.textView1.text = sb
            if (info.processName != null && info.processName != mPackageName) {
                holder.processNameView.visibility = View.VISIBLE
                holder.processNameView.text = "${getString(R.string.process_name)}: ${info.processName}"
            } else holder.processNameView.visibility = View.GONE
            if (item.canLaunch) {
                holder.launchBtn.setOnClickListener { try { ActivityManagerCompat.startService(Intent().setClassName(mPackageName!!, info.name), mUserId, true) } catch (th: Throwable) { UIUtils.displayShortToast(th.toString()) } }
                holder.launchBtn.visibility = View.VISIBLE
            } else holder.launchBtn.visibility = View.GONE
            if (mCanModifyComponentStates) handleBlock(holder, item, RuleType.SERVICE)
            else { holder.toggleSwitch.visibility = View.GONE; holder.blockingMethod.visibility = View.GONE }
        }

        private fun getReceiverView(holder: ViewHolder, index: Int) {
            val item = synchronized(mAdapterList) { mAdapterList[index] as AppDetailsComponentItem }
            val info = item.item as ActivityInfo
            holder.cardView.strokeColor = when {
                !mIsExternalApk && item.isBlocked -> mBlockedIndicatorColor
                !mIsExternalApk && item.isDisabled -> mBlockedExternallyIndicatorColor
                item.isTracker -> mTrackerIndicatorColor
                else -> Color.TRANSPARENT
            }
            holder.chipType.visibility = if (item.isTracker) { holder.chipType.setText(R.string.tracker); View.VISIBLE } else View.GONE
            holder.labelView.text = item.label
            holder.nameView.text = if (mConstraint != null && info.name.lowercase(Locale.ROOT).contains(mConstraint!!)) UIUtils.getHighlightedText(info.name, mConstraint!!, colorQueryStringHighlight)
            else if (info.name.startsWith(mPackageName!!)) info.name.replaceFirst(mPackageName!!, "") else info.name
            val tag = "${mPackageName}_${info.name}"
            holder.imageView.tag = tag
            ImageLoader.getInstance().displayImage(tag, info, holder.imageView)
            holder.textView1.text = "${getString(R.string.task_affinity)}: ${info.taskAffinity}"
            holder.textView2.text = "${getString(R.string.launch_mode)}: ${getString(Utils.getLaunchMode(info.launchMode))} | ${getString(R.string.orientation)}: ${getString(Utils.getOrientationString(info.screenOrientation))}"
            holder.textView3.text = info.permission ?: getString(R.string.require_no_permission)
            holder.textView4.text = "${getString(R.string.soft_input)}: ${Utils.getSoftInputString(info.softInputMode)}"
            if (info.processName != null && info.processName != mPackageName) {
                holder.processNameView.visibility = View.VISIBLE
                holder.processNameView.text = "${getString(R.string.process_name)}: ${info.processName}"
            } else holder.processNameView.visibility = View.GONE
            if (mCanModifyComponentStates) handleBlock(holder, item, RuleType.RECEIVER)
            else { holder.toggleSwitch.visibility = View.GONE; holder.blockingMethod.visibility = View.GONE }
        }

        private fun getProviderView(holder: ViewHolder, index: Int) {
            val item = synchronized(mAdapterList) { mAdapterList[index] as AppDetailsComponentItem }
            val info = item.item as ProviderInfo
            holder.cardView.strokeColor = when {
                !mIsExternalApk && item.isBlocked -> mBlockedIndicatorColor
                !mIsExternalApk && item.isDisabled -> mBlockedExternallyIndicatorColor
                item.isTracker -> mTrackerIndicatorColor
                else -> Color.TRANSPARENT
            }
            holder.chipType.visibility = if (item.isTracker) { holder.chipType.setText(R.string.tracker); View.VISIBLE } else View.GONE
            holder.labelView.text = item.label
            val tag = "${mPackageName}_${info.name}"
            holder.imageView.tag = tag
            ImageLoader.getInstance().displayImage(tag, info, holder.imageView)
            holder.textView1.text = "${getString(R.string.grant_uri_permission)}: ${info.grantUriPermissions}"
            val pp = info.pathPermissions
            holder.textView2.text = "${getString(R.string.path_permissions)}: ${pp?.let { val sb = StringBuilder(); val r = getString(R.string.read); val w = getString(R.string.write); it.forEach { p -> sb.append("$r: ${p.readPermission}/$w: ${p.writePermission}, ") }; Utils.checkStringBuilderEnd(sb); sb.toString() } ?: "null"}"
            val up = info.uriPermissionPatterns
            holder.textView3.text = "${getString(R.string.patterns_allowed)}: ${up?.let { val sb = StringBuilder(); it.forEach { p -> sb.append("${p}, ") }; Utils.checkStringBuilderEnd(sb); sb.toString() } ?: "null"}"
            holder.textView4.text = "${getString(R.string.authority)}: ${info.authority}"
            holder.nameView.text = if (mConstraint != null && info.name.lowercase(Locale.ROOT).contains(mConstraint!!)) UIUtils.getHighlightedText(info.name, mConstraint!!, colorQueryStringHighlight)
            else if (info.name.startsWith(mPackageName!!)) info.name.replaceFirst(mPackageName!!, "") else info.name
            if (info.processName != null && info.processName != mPackageName) {
                holder.processNameView.visibility = View.VISIBLE
                holder.processNameView.text = "${getString(R.string.process_name)}: ${info.processName}"
            } else holder.processNameView.visibility = View.GONE
            if (mCanModifyComponentStates) handleBlock(holder, item, RuleType.PROVIDER)
            else { holder.toggleSwitch.visibility = View.GONE; holder.blockingMethod.visibility = View.GONE }
        }
    }
}
