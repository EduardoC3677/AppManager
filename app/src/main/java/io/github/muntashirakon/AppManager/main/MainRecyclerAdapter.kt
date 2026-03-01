// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.RemoteException
import android.os.UserHandle
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.RelativeSizeSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SectionIndexer
import android.widget.TextView
import androidx.annotation.GuardedBy
import androidx.annotation.UiThread
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerActivity
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat
import io.github.muntashirakon.AppManager.backup.dialog.BackupRestoreDialogFragment
import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat
import io.github.muntashirakon.AppManager.details.AppDetailsActivity
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader
import io.github.muntashirakon.AppManager.settings.FeatureController
import io.github.muntashirakon.AppManager.types.UserPackagePair
import io.github.muntashirakon.AppManager.users.UserInfo
import io.github.muntashirakon.AppManager.users.Users
import io.github.muntashirakon.AppManager.utils.ArrayUtils
import io.github.muntashirakon.AppManager.utils.DateUtils
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes
import io.github.muntashirakon.AppManager.utils.appearance.ExpressiveHaptics
import io.github.muntashirakon.AppManager.utils.appearance.ExpressiveMotion
import io.github.muntashirakon.dialog.SearchableItemsDialogBuilder
import io.github.muntashirakon.io.Paths
import io.github.muntashirakon.util.AccessibilityUtils
import io.github.muntashirakon.util.AdapterUtils
import io.github.muntashirakon.widget.MultiSelectionView
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit

class MainRecyclerAdapter(activity: MainActivity) : MultiSelectionView.Adapter<MainRecyclerAdapter.ViewHolder>(),
    SectionIndexer {
    private val mActivity: MainActivity = activity
    private var mSearchQuery: String? = null
    @GuardedBy("mAdapterList")
    private val mAdapterList: MutableList<ApplicationItem> = ArrayList()

    private val mColorGreen: Int = ContextCompat.getColor(activity, io.github.muntashirakon.ui.R.color.stopped)
    private val mColorOrange: Int = ContextCompat.getColor(activity, io.github.muntashirakon.ui.R.color.orange)
    private val mColorPrimary: Int = ContextCompat.getColor(activity, io.github.muntashirakon.ui.R.color.textColorPrimary)
    private val mColorSecondary: Int = ContextCompat.getColor(activity, io.github.muntashirakon.ui.R.color.textColorSecondary)
    private val mQueryStringHighlight: Int = ColorCodes.getQueryStringHighlightColor(activity)
    private val mColorUninstalled: Int = ColorCodes.getAppUninstalledIndicatorColor(activity)
    private val mColorDisabled: Int = ColorCodes.getAppDisabledIndicatorColor(activity)
    private val mColorForceStopped: Int = ColorCodes.getAppForceStoppedIndicatorColor(activity)
    private var mIsInitialLoad = true
    private val mDensity: Float = activity.resources.displayMetrics.density
    private val mCornerRadiusPx: Float = Prefs.Appearance.getEffectiveCornerRadius() * mDensity
    private val mAnimatedPositions: MutableSet<Int> = Collections.synchronizedSet(HashSet())

    @GuardedBy("mAdapterList")
    @UiThread
    fun setDefaultList(list: List<ApplicationItem>) {
        if (mActivity.viewModel == null) return
        synchronized(mAdapterList) {
            mSearchQuery = mActivity.viewModel!!.getSearchQuery()
            mAnimatedPositions.clear()
            AdapterUtils.notifyDataSetChanged(this, mAdapterList, list)
            notifySelectionChange()
            if (mIsInitialLoad && list.isNotEmpty()) {
                mIsInitialLoad = false
            }
        }
    }

    @GuardedBy("mAdapterList")
    override fun cancelSelection() {
        super.cancelSelection()
        mActivity.viewModel!!.cancelSelection()
    }

    override fun getSelectedItemCount(): Int {
        return mActivity.viewModel?.getSelectedPackages()?.size ?: 0
    }

    override fun getTotalItemCount(): Int {
        return mActivity.viewModel?.applicationItemCount ?: 0
    }

    @GuardedBy("mAdapterList")
    override fun isSelected(position: Int): Boolean {
        synchronized(mAdapterList) {
            return mAdapterList[position].isSelected
        }
    }

    @GuardedBy("mAdapterList")
    override fun select(position: Int): Boolean {
        synchronized(mAdapterList) {
            mAdapterList[position] = mActivity.viewModel!!.select(mAdapterList[position])
            return true
        }
    }

    @GuardedBy("mAdapterList")
    override fun deselect(position: Int): Boolean {
        synchronized(mAdapterList) {
            mAdapterList[position] = mActivity.viewModel!!.deselect(mAdapterList[position])
            return true
        }
    }

    @GuardedBy("mAdapterList")
    override fun toggleSelection(position: Int) {
        synchronized(mAdapterList) {
            super.toggleSelection(position)
        }
    }

    @GuardedBy("mAdapterList")
    override fun selectAll() {
        synchronized(mAdapterList) {
            super.selectAll()
        }
    }

    @GuardedBy("mAdapterList")
    override fun selectRange(firstPosition: Int, secondPosition: Int) {
        synchronized(mAdapterList) {
            super.selectRange(firstPosition, secondPosition)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_main, parent, false)
        return ViewHolder(view)
    }

    @GuardedBy("mAdapterList")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = mAdapterList[position]
        val cardView = holder.itemView
        val context = cardView.context

        cardView.radius = mCornerRadiusPx

        // Material You 2026 Expressive entrance animation with spring physics
        if (!mIsInitialLoad && !mAnimatedPositions.contains(position)) {
            mAnimatedPositions.add(position)
            ExpressiveMotion.animateSlideInBottom(
                cardView,
                duration = 400,
                delay = (position % 10) * 20L
            )
        }

        cardView.setOnClickListener { v ->
            // Material You 2026 expressive haptic feedback
            ExpressiveHaptics.performButtonPressHaptic(true)
            
            // Add spring press animation
            ExpressiveMotion.animateCardPress(cardView, true)

            if (isInSelectionMode) {
                toggleSelection(position)
                AccessibilityUtils.requestAccessibilityFocus(holder.itemView)
                return@setOnClickListener
            }
            handleClick(item)
        }
        
        // Reset scale on touch end
        cardView.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP ||
                event.action == android.view.MotionEvent.ACTION_CANCEL) {
                ExpressiveMotion.animateCardPress(cardView, false)
            }
            false
        }
        
        cardView.setOnLongClickListener { v ->
            // Heavy haptic for long press (gesture start)
            ExpressiveHaptics.performHapticFeedback(ExpressiveHaptics.HAPTIC_HEAVY)

            synchronized(mAdapterList) {
                val lastSelectedItem = mActivity.viewModel!!.getLastSelectedPackage()
                val lastSelectedItemPosition = if (lastSelectedItem == null) -1 else mAdapterList.indexOf(lastSelectedItem)
                if (lastSelectedItemPosition >= 0) {
                    selectRange(lastSelectedItemPosition, position)
                } else {
                    toggleSelection(position)
                    AccessibilityUtils.requestAccessibilityFocus(holder.itemView)
                }
            }
            true
        }
        holder.icon.setOnClickListener { v ->
            // Light haptic for icon selection
            ExpressiveHaptics.performListItemHaptic()

            toggleSelection(position)
            AccessibilityUtils.requestAccessibilityFocus(holder.itemView)
        }

        if (!item.isInstalled) {
            cardView.strokeColor = mColorUninstalled
        } else if (item.isDisabled) {
            cardView.strokeColor = mColorDisabled
        } else if (item.isStopped) {
            cardView.strokeColor = mColorForceStopped
        } else {
            cardView.strokeColor = Color.TRANSPARENT
        }
        holder.debugIcon.visibility = if (item.isDebuggable) View.VISIBLE else View.INVISIBLE
        val lastUpdateDate = DateUtils.formatDate(context, item.lastUpdateTime)
        if (item.firstInstallTime == item.lastUpdateTime) {
            holder.date.text = lastUpdateDate
        } else {
            val days = TimeUnit.DAYS.convert(item.lastUpdateTime - item.firstInstallTime, TimeUnit.MILLISECONDS)
            val ssDate = SpannableString(context.resources.getQuantityString(R.plurals.main_list_date_days, days.toInt(), lastUpdateDate, days))
            ssDate.setSpan(RelativeSizeSpan(.8f), lastUpdateDate.length, ssDate.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            holder.date.text = ssDate
        }
        holder.date.setTextColor(if (item.canReadLogs) mColorOrange else mColorSecondary)
        if (item.isInstalled) {
            if (item.uidOrAppIds.isNotEmpty()) holder.userId.text = item.uidOrAppIds
            holder.userId.setTextColor(if (item.sharedUserId != null) mColorOrange else mColorSecondary)
        } else holder.userId.text = ""
        if (item.sha != null) {
            holder.issuer.visibility = View.VISIBLE
            holder.issuer.text = item.issuerShortName
            holder.sha.visibility = View.VISIBLE
            holder.sha.text = item.sha!!.second
        } else {
            holder.issuer.visibility = View.GONE
            holder.sha.visibility = View.GONE
        }
        holder.icon.tag = item.packageName
        ImageLoader.getInstance().displayImage(item.packageName, item, holder.icon)
        val currentSearchQuery = mSearchQuery
        if (!TextUtils.isEmpty(currentSearchQuery)) {
            item.ensureLowerCaseFields()
            if (item.labelLowerCase.contains(currentSearchQuery!!)) {
                holder.label.text = UIUtils.getHighlightedText(item.label, currentSearchQuery, mQueryStringHighlight)
            } else {
                holder.label.text = item.label
            }
        } else {
            holder.label.text = item.label
        }
        if (item.isInstalled && !item.allowClearingUserData) {
            holder.label.setTextColor(Color.RED)
        } else holder.label.setTextColor(mColorPrimary)
        if (!TextUtils.isEmpty(currentSearchQuery)) {
            if (item.packageNameLowerCase.contains(currentSearchQuery!!)) {
                holder.packageName.text = UIUtils.getHighlightedText(item.packageName, currentSearchQuery, mQueryStringHighlight)
            } else {
                holder.packageName.text = item.packageName
            }
        } else {
            holder.packageName.text = item.packageName
        }
        if (item.trackerCount > 0) {
            holder.packageName.setTextColor(ColorCodes.getComponentTrackerIndicatorColor(context))
        } else holder.packageName.setTextColor(mColorSecondary)
        holder.version.text = item.versionTag
        holder.version.setTextColor(if (item.isAppInactive) mColorGreen else mColorSecondary)
        if (item.isInstalled) {
            val isSystemApp = context.getString(if (item.isSystem) R.string.system else R.string.user) + item.appTypePostfix
            holder.isSystemApp.text = isSystemApp
        } else {
            holder.isSystemApp.text = "-"
        }
        holder.isSystemApp.setTextColor(if (item.isPersistent) Color.MAGENTA else mColorSecondary)
        if (item.sdkString != null) {
            holder.size.text = item.sdkString
        } else holder.size.text = "-"
        holder.size.setTextColor(if (item.usesCleartextTraffic) mColorOrange else mColorSecondary)
        if (item.backup != null) {
            holder.backupIndicator.visibility = View.VISIBLE
            holder.backupInfo.visibility = View.VISIBLE
            holder.backupInfoExt.visibility = View.VISIBLE
            holder.backupIndicator.setText(R.string.backup)
            val indicatorColor = if (item.isInstalled) {
                if (item.backup!!.versionCode >= item.versionCode) ColorCodes.getBackupLatestIndicatorColor(context)
                else ColorCodes.getBackupOutdatedIndicatorColor(context)
            } else {
                ColorCodes.getBackupUninstalledIndicatorColor(context)
            }
            holder.backupIndicator.setTextColor(indicatorColor)
            val backup = item.backup!!
            val days = item.lastBackupDays
            holder.backupInfo.text = String.format(
                "%s: %s, %s %s",
                context.getString(R.string.latest_backup), context.resources
                    .getQuantityString(R.plurals.usage_days, days.toInt(), days),
                context.getString(R.string.version), backup.versionName
            )
            holder.backupInfoExt.text = item.backupFlagsStr
        } else {
            holder.backupIndicator.visibility = View.GONE
            holder.backupInfo.visibility = View.GONE
            holder.backupInfoExt.visibility = View.GONE
        }
        super.onBindViewHolder(holder, position)
    }

    @GuardedBy("mAdapterList")
    override fun getItemId(position: Int): Long {
        synchronized(mAdapterList) {
            return mAdapterList[position].hashCode().toLong()
        }
    }

    @GuardedBy("mAdapterList")
    override fun getItemCount(): Int {
        synchronized(mAdapterList) {
            return mAdapterList.size
        }
    }

    @GuardedBy("mAdapterList")
    override fun getPositionForSection(section: Int): Int {
        synchronized(mAdapterList) {
            for (i in 0 until itemCount) {
                val item = mAdapterList[i].label
                if (item.isNotEmpty()) {
                    if (item[0] == sSections[section]) return i
                }
            }
            return 0
        }
    }

    override fun getSectionForPosition(i: Int): Int = 0

    override fun getSections(): Array<Any> {
        val sectionsArr = arrayOfNulls<String>(sSections.length)
        for (i in 0 until sSections.length) sectionsArr[i] = sSections[i].toString()
        return sectionsArr.requireNoNulls()
    }

    private fun handleClick(item: ApplicationItem) {
        if (!item.isInstalled || item.userIds.isEmpty()) {
            val info: ApplicationInfo? = try {
                PackageManagerCompat.getApplicationInfo(
                    item.packageName, PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES
                            or PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES,
                    UserHandle.myUserId()
                )
            } catch (e: RemoteException) {
                showBackupRestoreDialogOrAppNotInstalled(item)
                return
            } catch (e: PackageManager.NameNotFoundException) {
                showBackupRestoreDialogOrAppNotInstalled(item)
                return
            }
            if (info != null && ApplicationInfoCompat.isInstalled(info)) {
                item.isInstalled = true
                item.isOnlyDataInstalled = false
                item.userIds = intArrayOf(UserHandle.myUserId())
                val intent = AppDetailsActivity.getIntent(mActivity, item.packageName, UserHandle.myUserId())
                mActivity.startActivity(intent)
                return
            }
            if (info != null && FeatureController.isInstallerEnabled()) {
                if (ApplicationInfoCompat.isSystemApp(info) && SelfPermissions.canInstallExistingPackages()) {
                    mActivity.startActivity(PackageInstallerActivity.getLaunchableInstance(mActivity, item.packageName))
                    return
                }
                if (Paths.exists(info.publicSourceDir)) {
                    mActivity.startActivity(
                        PackageInstallerActivity.getLaunchableInstance(
                            mActivity,
                            Uri.fromFile(File(info.publicSourceDir))
                        )
                    )
                    return
                }
            }
            if (info != null && ApplicationInfoCompat.isSystemApp(info)) {
                showBackupRestoreDialogOrAppNotInstalled(item)
                return
            }
            MaterialAlertDialogBuilder(mActivity)
                .setTitle(mActivity.getString(R.string.uninstall_app, item.label))
                .setMessage(R.string.uninstall_app_again_message)
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes) { _, _ ->
                    ThreadUtils.postOnBackgroundThread {
                        val installer = PackageInstallerCompat.getNewInstance()
                        installer.setAppLabel(item.label)
                        val uninstalled = installer.uninstall(item.packageName, UserHandle.myUserId(), false)
                        ThreadUtils.postOnMainThread {
                            if (uninstalled) {
                                UIUtils.displayLongToast(R.string.uninstalled_successfully, item.label)
                            } else {
                                UIUtils.displayLongToast(R.string.failed_to_uninstall, item.label)
                            }
                        }
                    }
                }
                .show()
            return
        }
        if (item.userIds.size == 1) {
            val userHandles = Users.getUsersIds()
            if (ArrayUtils.contains(userHandles, item.userIds[0])) {
                val intent = AppDetailsActivity.getIntent(mActivity, item.packageName, item.userIds[0])
                mActivity.startActivity(intent)
                return
            }
            showBackupRestoreDialogOrAppNotInstalled(item)
            return
        }
        val userNames = arrayOfNulls<CharSequence>(item.userIds.size)
        val users = Users.getUsers()
        for (userInfo in users) {
            for (i in item.userIds.indices) {
                if (userInfo.id == item.userIds[i]) {
                    userNames[i] = userInfo.toLocalizedString(mActivity)
                }
            }
        }
        SearchableItemsDialogBuilder(mActivity, userNames)
            .setTitle(R.string.select_user)
            .setOnItemClickListener { dialog, which, _ ->
                val intent = AppDetailsActivity.getIntent(mActivity, item.packageName, item.userIds[which])
                mActivity.startActivity(intent)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showBackupRestoreDialogOrAppNotInstalled(item: ApplicationItem) {
        if (item.backup == null) {
            UIUtils.displayShortToast(R.string.app_not_installed)
            return
        }
        val fragment = BackupRestoreDialogFragment.getInstance(
            Collections.singletonList(
                UserPackagePair(
                    item.packageName,
                    UserHandle.myUserId()
                )
            )
        )
        fragment.setOnActionBeginListener { mActivity.showProgressIndicator(true) }
        fragment.setOnActionCompleteListener { _, _ -> mActivity.showProgressIndicator(false) }
        fragment.show(mActivity.supportFragmentManager, BackupRestoreDialogFragment.TAG)
    }

    class ViewHolder(itemView: View) : MultiSelectionView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView as MaterialCardView
        val icon: AppCompatImageView = itemView.findViewById(R.id.icon)
        val debugIcon: AppCompatImageView = itemView.findViewById(R.id.favorite_icon)
        val label: TextView = itemView.findViewById(R.id.label)
        val packageName: TextView = itemView.findViewById(R.id.packageName)
        val version: TextView = itemView.findViewById(R.id.version)
        val isSystemApp: TextView = itemView.findViewById(R.id.isSystem)
        val date: TextView = itemView.findViewById(R.id.date)
        val size: TextView = itemView.findViewById(R.id.size)
        val userId: TextView = itemView.findViewById(R.id.shareid)
        val issuer: TextView = itemView.findViewById(R.id.issuer)
        val sha: TextView = itemView.findViewById(R.id.sha)
        val backupIndicator: TextView = itemView.findViewById(R.id.backup_indicator)
        val backupInfo: TextView = itemView.findViewById(R.id.backup_info)
        val backupInfoExt: TextView = itemView.findViewById(R.id.backup_info_ext)
    }

    companion object {
        private const val sSections = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    }
}
