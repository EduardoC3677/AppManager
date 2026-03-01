// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.text.SpannableStringBuilder
import android.view.MenuItem
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.apk.dexopt.DexOptDialog
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager
import io.github.muntashirakon.AppManager.batchops.BatchOpsService
import io.github.muntashirakon.AppManager.batchops.BatchQueueItem
import io.github.muntashirakon.AppManager.batchops.struct.BatchAppOpsOptions
import io.github.muntashirakon.AppManager.batchops.struct.BatchComponentOptions
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.utils.*
import io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder
import io.github.muntashirakon.dialog.TextInputDialogBuilder
import io.github.muntashirakon.dialog.TextInputDropdownDialogBuilder
import io.github.muntashirakon.widget.MultiSelectionView
import java.util.*

class OneClickOpsActivity : BaseActivity() {
    private var mViewModel: OneClickOpsViewModel? = null
    private var mItemCreator: ListItemCreator? = null
    private var mProgressIndicator: LinearProgressIndicator? = null
    private var mWakeLock: PowerManager.WakeLock? = null

    private val mBatchOpsBroadCastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            mProgressIndicator?.hide()
        }
    }

    override fun onAuthenticated(savedInstanceState: Bundle?) {
        val op = intent.getIntExtra(EXTRA_OP, BatchOpsManager.OP_NONE)
        if (op == BatchOpsManager.OP_CLEAR_CACHE) {
            val item = BatchQueueItem.getOneClickQueue(BatchOpsManager.OP_CLEAR_CACHE, null, null, null)
            launchService(item)
            finishAndRemoveTask()
            return
        }
        setContentView(R.layout.activity_one_click_ops)
        setSupportActionBar(findViewById(R.id.toolbar))
        mViewModel = ViewModelProvider(this).get(OneClickOpsViewModel::class.java)
        mItemCreator = ListItemCreator(this, R.id.container)
        mProgressIndicator = findViewById(R.id.progress_linear)
        mProgressIndicator!!.visibilityAfterHide = View.GONE
        mWakeLock = CpuUtils.getPartialWakeLock("1-click_ops")
        setItems()

        mViewModel!!.watchTrackerCount().observe(this) { blockTrackers(it) }
        mViewModel!!.watchComponentCount().observe(this) { blockComponents(it.first, it.second) }
        mViewModel!!.watchAppOpsCount().observe(this) { setAppOps(it.first, it.second.first, it.second.second) }
        mViewModel!!.getClearDataCandidates().observe(this) { clearData(it) }
        mViewModel!!.watchTrimCachesResult().observe(this) {
            CpuUtils.releaseWakeLock(mWakeLock)
            mProgressIndicator!!.hide()
            UIUtils.displayShortToast(if (it) R.string.done else R.string.failed)
        }
        mViewModel!!.getAppsInstalledByAmForDexOpt().observe(this) {
            CpuUtils.releaseWakeLock(mWakeLock)
            mProgressIndicator!!.hide()
            DexOptDialog.getInstance(it).show(supportFragmentManager, DexOptDialog.TAG)
        }
    }

    private fun setItems() {
        mItemCreator!!.addItemWithTitleSubtitle(getString(R.string.block_unblock_trackers), getString(R.string.block_unblock_trackers_description))
            .setOnClickListener {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.block_unblock_trackers)
                    .setMessage(R.string.apply_to_system_apps_question)
                    .setPositiveButton(R.string.no) { _, _ -> mProgressIndicator!!.show(); if (!mWakeLock!!.isHeld) mWakeLock!!.acquire(); mViewModel!!.blockTrackers(false) }
                    .setNegativeButton(R.string.yes) { _, _ -> mProgressIndicator!!.show(); if (!mWakeLock!!.isHeld) mWakeLock!!.acquire(); mViewModel!!.blockTrackers(true) }
                    .show()
            }
        mItemCreator!!.addItemWithTitleSubtitle(getString(R.string.block_unblock_components_dots), getString(R.string.block_unblock_components_description))
            .setOnClickListener {
                TextInputDialogBuilder(this, R.string.input_signatures)
                    .setHelperText(R.string.input_signatures_description)
                    .setCheckboxLabel(R.string.apply_to_system_apps)
                    .setTitle(R.string.block_unblock_components_dots)
                    .setPositiveButton(R.string.search) { _, _, signatureNames, systemApps ->
                        if (signatureNames == null) return@setPositiveButton
                        mProgressIndicator!!.show(); if (!mWakeLock!!.isHeld) mWakeLock!!.acquire()
                        val signatures = signatureNames.toString().split("\s+".toRegex()).toTypedArray()
                        mViewModel!!.blockComponents(systemApps, signatures)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        mItemCreator!!.addItemWithTitleSubtitle(getString(R.string.set_mode_for_app_ops_dots), getString(R.string.deny_app_ops_description)).setOnClickListener { showAppOpsSelectionDialog() }
        mItemCreator!!.addItemWithTitleSubtitle(getText(R.string.back_up), getText(R.string.backup_msg)).setOnClickListener { BackupTasksDialogFragment().show(supportFragmentManager, BackupTasksDialogFragment.TAG) }
        mItemCreator!!.addItemWithTitleSubtitle(getText(R.string.restore), getText(R.string.restore_msg)).setOnClickListener { RestoreTasksDialogFragment().show(supportFragmentManager, RestoreTasksDialogFragment.TAG) }
        mItemCreator!!.addItemWithTitleSubtitle(getString(R.string.clear_data_from_uninstalled_apps), getString(R.string.clear_data_from_uninstalled_apps_description)).setOnClickListener { if (!mWakeLock!!.isHeld) mWakeLock!!.acquire(); mViewModel!!.clearData() }
        mItemCreator!!.addItemWithTitleSubtitle(getString(R.string.trim_caches_in_all_apps), getString(R.string.trim_caches_in_all_apps_description)).setOnClickListener {
            if (!SelfPermissions.checkSelfPermission(Manifest.permission.CLEAR_APP_CACHE) && !SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.CLEAR_APP_CACHE)) {
                UIUtils.displayShortToast(R.string.only_works_in_root_or_adb_mode)
                return@setOnClickListener
            }
            MaterialAlertDialogBuilder(this).setTitle(R.string.trim_caches_in_all_apps).setMessage(R.string.are_you_sure).setNegativeButton(R.string.no, null).setPositiveButton(R.string.yes) { _, _ -> mProgressIndicator!!.show(); if (!mWakeLock!!.isHeld) mWakeLock!!.acquire(); mViewModel!!.trimCaches() }.show()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mItemCreator!!.addItemWithTitleSubtitle(getString(R.string.title_perform_runtime_optimization_to_apps), getString(R.string.summary_perform_runtime_optimization_to_apps)).setOnClickListener {
                if (SelfPermissions.isSystemOrRootOrShell()) {
                    DexOptDialog.getInstance(null).show(supportFragmentManager, DexOptDialog.TAG)
                    return@setOnClickListener
                }
                mProgressIndicator!!.show(); if (!mWakeLock!!.isHeld) mWakeLock!!.acquire(); mViewModel!!.listAppsInstalledByAmForDexOpt()
            }
        }
        mProgressIndicator!!.hide()
    }

    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(this, mBatchOpsBroadCastReceiver, IntentFilter(BatchOpsService.ACTION_BATCH_OPS_COMPLETED), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(mBatchOpsBroadCastReceiver)
        mProgressIndicator?.hide()
    }

    private fun blockTrackers(trackerCounts: List<ItemCount>?) {
        CpuUtils.releaseWakeLock(mWakeLock)
        mProgressIndicator!!.hide()
        if (trackerCounts == null) { UIUtils.displayShortToast(R.string.failed_to_fetch_package_info); return }
        if (trackerCounts.isEmpty()) { UIUtils.displayShortToast(R.string.no_tracker_found); return }
        val pkgs = ArrayList<String>()
        val names = mutableListOf<CharSequence>()
        for (t in trackerCounts) {
            pkgs.add(t.packageName!!)
            names.add(SpannableStringBuilder(t.packageLabel).append("
").append(getSmallerText(resources.getQuantityString(R.plurals.no_of_trackers, t.count, t.count))))
        }
        SearchableMultiChoiceDialogBuilder(this, pkgs, names).addSelections(pkgs).setTitle(R.string.filtered_packages).setPositiveButton(R.string.block) { _, _, sel -> mProgressIndicator!!.show(); launchService(BatchQueueItem.getOneClickQueue(BatchOpsManager.OP_BLOCK_TRACKERS, sel, null, null)) }.setNeutralButton(R.string.unblock) { _, _, sel -> mProgressIndicator!!.show(); launchService(BatchQueueItem.getOneClickQueue(BatchOpsManager.OP_UNBLOCK_TRACKERS, sel, null, null)) }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun blockComponents(componentCounts: List<ItemCount>?, signatures: Array<String>) {
        CpuUtils.releaseWakeLock(mWakeLock)
        mProgressIndicator!!.hide()
        if (componentCounts == null) { UIUtils.displayShortToast(R.string.failed_to_fetch_package_info); return }
        if (componentCounts.isEmpty()) { UIUtils.displayShortToast(R.string.no_matching_package_found); return }
        val pkgs = ArrayList<String>()
        val names = mutableListOf<CharSequence>()
        for (c in componentCounts) {
            pkgs.add(c.packageName!!)
            names.add(SpannableStringBuilder(c.packageLabel).append("
").append(getSmallerText(resources.getQuantityString(R.plurals.no_of_components, c.count, c.count))))
        }
        val options = BatchComponentOptions(signatures)
        SearchableMultiChoiceDialogBuilder(this, pkgs, names).addSelections(pkgs).setTitle(R.string.filtered_packages).setPositiveButton(R.string.block) { _, _, sel -> mProgressIndicator!!.show(); launchService(BatchQueueItem.getOneClickQueue(BatchOpsManager.OP_BLOCK_COMPONENTS, sel, null, options)) }.setNeutralButton(R.string.unblock) { _, _, sel -> mProgressIndicator!!.show(); launchService(BatchQueueItem.getOneClickQueue(BatchOpsManager.OP_UNBLOCK_COMPONENTS, sel, null, options)) }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun showAppOpsSelectionDialog() {
        if (!SelfPermissions.canModifyAppOpMode()) { UIUtils.displayShortToast(R.string.only_works_in_root_or_adb_mode); return }
        val modes = AppOpsManagerCompat.getModeConstants()
        val appOps = AppOpsManagerCompat.getAllOps()
        val modeNames = getAppOpModeNames(modes).toList()
        val appOpNames = getAppOpNames(appOps).toList()
        val builder = TextInputDropdownDialogBuilder(this, R.string.input_app_ops)
        builder.setTitle(R.string.set_mode_for_app_ops_dots).setAuxiliaryInput(R.string.mode, null, null, modeNames, true).setCheckboxLabel(R.string.apply_to_system_apps).setHelperText(R.string.input_app_ops_description).setPositiveButton(R.string.search) { _, _, appOpNameList, systemApps ->
            if (appOpNameList == null) return@setPositiveButton
            try {
                val appOpsStr = appOpNameList.toString().split("\s+".toRegex())
                if (appOpsStr.isEmpty()) return@setPositiveButton
                val mode = Utils.getIntegerFromString(builder.auxiliaryInput, modeNames, modes)
                val appOpSet = mutableSetOf<Int>()
                for (op in appOpsStr) appOpSet.add(Utils.getIntegerFromString(op, appOpNames, appOps))
                mProgressIndicator!!.show(); if (!mWakeLock!!.isHeld) mWakeLock!!.acquire(); mViewModel!!.setAppOps(ArrayUtils.convertToIntArray(appOpSet), mode, systemApps)
            } catch (e: Exception) { UIUtils.displayShortToast(R.string.failed_to_parse_some_numbers) }
        }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun setAppOps(appOpCounts: List<AppOpCount>?, appOpList: IntArray, mode: Int) {
        CpuUtils.releaseWakeLock(mWakeLock)
        mProgressIndicator!!.hide()
        if (appOpCounts == null) { UIUtils.displayShortToast(R.string.failed_to_fetch_package_info); return }
        if (appOpCounts.isEmpty()) { UIUtils.displayShortToast(R.string.no_matching_package_found); return }
        val pkgs = ArrayList<String>()
        val names = mutableListOf<CharSequence>()
        for (ao in appOpCounts) {
            pkgs.add(ao.packageName!!)
            names.add(SpannableStringBuilder(ao.packageLabel).append("
").append(getSmallerText("(${ao.count}) ${TextUtilsCompat.joinSpannable(", ", appOpToNames(ao.appOps!!))}")))
        }
        val options = BatchAppOpsOptions(appOpList, mode)
        SearchableMultiChoiceDialogBuilder(this, pkgs, names).addSelections(pkgs).setTitle(R.string.filtered_packages).setPositiveButton(R.string.apply) { _, _, sel -> mProgressIndicator!!.show(); launchService(BatchQueueItem.getOneClickQueue(BatchOpsManager.OP_SET_APP_OPS, sel, null, options)) }.setNegativeButton(R.string.cancel) { _, _, _ -> mProgressIndicator!!.hide() }.show()
    }

    private fun clearData(candidatePackages: List<String>) {
        CpuUtils.releaseWakeLock(mWakeLock)
        if (candidatePackages.isEmpty()) { UIUtils.displayLongToast(R.string.no_matching_package_found); return }
        val pkgs = candidatePackages.toTypedArray()
        SearchableMultiChoiceDialogBuilder(this, pkgs, pkgs).setTitle(R.string.filtered_packages).setPositiveButton(R.string.apply) { _, _, sel -> mProgressIndicator!!.show(); launchService(BatchQueueItem.getOneClickQueue(BatchOpsManager.OP_UNINSTALL, sel, null, null)) }.setNegativeButton(R.string.cancel) { _, _, _ -> mProgressIndicator!!.hide() }.show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        CpuUtils.releaseWakeLock(mWakeLock)
        super.onDestroy()
    }

    private fun launchService(queueItem: BatchQueueItem) {
        ContextCompat.startForegroundService(this, BatchOpsService.getServiceIntent(this, queueItem))
    }

    private fun appOpToNames(appOps: Collection<Int>): List<String> = appOps.map { AppOpsManagerCompat.opToName(it) }

    companion object {
        const val EXTRA_OP = "op"
    }
}
