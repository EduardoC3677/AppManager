// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main

import android.Manifest
import android.os.Build
import android.view.Menu
import android.view.MenuItem
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.compat.ManifestCompat
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.widget.MultiSelectionView

class MainBatchOpsHandler(multiSelectionView: MultiSelectionView, private val mViewModel: MainViewModel) :
    MultiSelectionView.OnSelectionChangeListener {

    private val mUninstallMenu: MenuItem = selectionMenu.findItem(R.id.action_uninstall)
    private val mFreezeUnfreezeMenu: MenuItem = selectionMenu.findItem(R.id.action_freeze_unfreeze)
    private val mForceStopMenu: MenuItem = selectionMenu.findItem(R.id.action_force_stop)
    private val mClearDataCacheMenu: MenuItem = selectionMenu.findItem(R.id.action_clear_data_cache)
    private val mSaveApkMenu: MenuItem = selectionMenu.findItem(R.id.action_save_apk)
    private val mBackupRestoreMenu: MenuItem = selectionMenu.findItem(R.id.action_backup)
    private val mPreventBackgroundMenu: MenuItem = selectionMenu.findItem(R.id.action_disable_background)
    private val mBlockUnblockTrackersMenu: MenuItem = selectionMenu.findItem(R.id.action_block_unblock_trackers)
    private val mNetPolicyMenu: MenuItem = selectionMenu.findItem(R.id.action_net_policy)
    private val mExportRulesMenu: MenuItem = selectionMenu.findItem(R.id.action_export_blocking_rules)
    private val mExportAppListMenu: MenuItem = selectionMenu.findItem(R.id.action_export_app_list)
    private val mOptimizeMenu: MenuItem = selectionMenu.findItem(R.id.action_optimize)
    private val mAddToProfileMenu: MenuItem = selectionMenu.findItem(R.id.action_add_to_profile)
    private val mArchiveMenu: MenuItem = selectionMenu.findItem(R.id.action_archive)
    private val mEditTagsMenu: MenuItem = selectionMenu.findItem(R.id.action_edit_tags)

    private var mCanFreezeUnfreezePackages: Boolean = false
    private var mCanForceStopPackages: Boolean = false
    private var mCanClearData: Boolean = false
    private var mCanClearCache: Boolean = false
    private var mCanModifyAppOpMode: Boolean = false
    private var mCanModifyNetPolicy: Boolean = false
    private var mCanModifyComponentState: Boolean = false

    init {
        updateConstraints()
    }

    fun updateConstraints() {
        mCanFreezeUnfreezePackages = SelfPermissions.canFreezeUnfreezePackages()
        mCanForceStopPackages = SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.FORCE_STOP_PACKAGES)
        mCanClearData = SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.CLEAR_APP_USER_DATA)
        mCanClearCache = SelfPermissions.canClearAppCache()
        mCanModifyAppOpMode = SelfPermissions.canModifyAppOpMode()
        mCanModifyNetPolicy = SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_NETWORK_POLICY)
        mCanModifyComponentState = SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE)
    }

    override fun onSelectionChange(selectionCount: Int): Boolean {
        val selectedItems = mViewModel.getSelectedApplicationItems()
        val nonZeroSelection = selectedItems.isNotEmpty()

        var areAllInstalled = true
        var areAllUninstalledWithoutData = true
        var areAllUninstalledSystem = true
        var doAllUninstalledhaveBackup = true

        for (item in selectedItems) {
            if (item.isInstalled) {
                // Keep the installed state, this check is for uninstalled apps
            } else {
                areAllInstalled = false
                if (areAllUninstalledWithoutData) {
                    areAllUninstalledWithoutData = item.isOnlyDataInstalled
                }
                if (!doAllUninstalledhaveBackup && !areAllUninstalledSystem) {
                    break
                }
                if (areAllUninstalledSystem && item.isUser) {
                    areAllUninstalledSystem = false
                }
                if (doAllUninstalledhaveBackup && item.backup == null) {
                    doAllUninstalledhaveBackup = false
                }
            }
        }
        /* === Enable/Disable === */
        mUninstallMenu.isEnabled = nonZeroSelection && (areAllInstalled || areAllUninstalledWithoutData)
        mArchiveMenu.isEnabled = nonZeroSelection && areAllInstalled
        mFreezeUnfreezeMenu.isEnabled = nonZeroSelection && areAllInstalled
        mForceStopMenu.isEnabled = nonZeroSelection && areAllInstalled
        mClearDataCacheMenu.isEnabled = nonZeroSelection && areAllInstalled
        mPreventBackgroundMenu.isEnabled = nonZeroSelection && areAllInstalled
        mBlockUnblockTrackersMenu.isEnabled = nonZeroSelection && areAllInstalled
        mNetPolicyMenu.isEnabled = nonZeroSelection && areAllInstalled

        mSaveApkMenu.isEnabled = nonZeroSelection && (areAllInstalled || areAllUninstalledSystem)
        mBackupRestoreMenu.isEnabled = nonZeroSelection && (areAllInstalled || doAllUninstalledhaveBackup)

        mExportRulesMenu.isEnabled = nonZeroSelection
        mExportAppListMenu.isEnabled = nonZeroSelection
        mOptimizeMenu.isEnabled = nonZeroSelection
        mAddToProfileMenu.isEnabled = nonZeroSelection
        mEditTagsMenu.isEnabled = nonZeroSelection && areAllInstalled

        /* === Visible/Invisible === */
        mFreezeUnfreezeMenu.isVisible = mCanFreezeUnfreezePackages
        mForceStopMenu.isVisible = mCanForceStopPackages
        mClearDataCacheMenu.isVisible = mCanClearData || mCanClearCache
        mPreventBackgroundMenu.isVisible = mCanModifyAppOpMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
        mNetPolicyMenu.isVisible = mCanModifyNetPolicy
        mBlockUnblockTrackersMenu.isVisible = mCanModifyComponentState
        mOptimizeMenu.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
        return true
    }
}
