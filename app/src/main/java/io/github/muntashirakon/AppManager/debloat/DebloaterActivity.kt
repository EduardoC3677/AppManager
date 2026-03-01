// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBar
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.apk.behavior.FreezeUnfreeze
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager
import io.github.muntashirakon.AppManager.batchops.BatchOpsService
import io.github.muntashirakon.AppManager.batchops.BatchQueueItem
import io.github.muntashirakon.AppManager.batchops.struct.BatchFreezeOptions
import io.github.muntashirakon.AppManager.batchops.struct.IBatchOpOptions
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView
import io.github.muntashirakon.AppManager.profiles.AddToProfileDialogFragment
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.utils.StoragePermission
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.multiselection.MultiSelectionActionsView
import io.github.muntashirakon.widget.MultiSelectionView
import io.github.muntashirakon.widget.RecyclerView
import java.util.*

class DebloaterActivity : BaseActivity(), MultiSelectionView.OnSelectionChangeListener,
    MultiSelectionActionsView.OnItemSelectedListener, AdvancedSearchView.OnQueryTextListener,
    MultiSelectionView.OnSelectionModeChangeListener {
    var viewModel: DebloaterViewModel? = null
    private var mProgressIndicator: LinearProgressIndicator? = null
    private var mMultiSelectionView: MultiSelectionView? = null
    private var mAdapter: DebloaterRecyclerViewAdapter? = null
    private val mStoragePermission = StoragePermission.init(this)
    private val mBatchOpsBroadCastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { mProgressIndicator?.hide() }
    }
    private val mOnBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (mAdapter?.isInSelectionMode == true) { mMultiSelectionView?.cancel(); return }
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onAuthenticated(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_debloater)
        setSupportActionBar(findViewById(R.id.toolbar))
        onBackPressedDispatcher.addCallback(this, mOnBackPressedCallback)
        supportActionBar?.let {
            it.setDisplayHomeAsUpEnabled(true)
            it.setDisplayShowCustomEnabled(true)
            UIUtils.setupAdvancedSearchView(it, this)
        }
        viewModel = ViewModelProvider(this).get(DebloaterViewModel::class.java)
        mProgressIndicator = findViewById(R.id.progress_linear)
        mProgressIndicator!!.show()
        val recyclerView: io.github.muntashirakon.widget.RecyclerView = findViewById(R.id.recycler_view)
        recyclerView.layoutManager = UIUtils.getGridLayoutAt450Dp(this)
        mAdapter = DebloaterRecyclerViewAdapter(this)
        mAdapter!!.setHasStableIds(false)
        recyclerView.adapter = mAdapter
        mMultiSelectionView = findViewById(R.id.selection_view)
        mMultiSelectionView!!.setAdapter(mAdapter)
        mMultiSelectionView!!.hide()
        mMultiSelectionView!!.setOnItemSelectedListener(this)
        mMultiSelectionView!!.setOnSelectionModeChangeListener(this)
        mMultiSelectionView!!.setOnSelectionChangeListener(this)
        viewModel!!.debloatObjectListLiveData.observe(this) { mProgressIndicator!!.hide(); mAdapter!!.setAdapterList(it) }
        viewModel!!.loadPackages()
    }

    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(this, mBatchOpsBroadCastReceiver, IntentFilter(BatchOpsService.ACTION_BATCH_OPS_COMPLETED), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(mBatchOpsBroadCastReceiver)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_debloater_actions, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            R.id.action_list_options -> { DebloaterListOptions().show(supportFragmentManager, DebloaterListOptions.TAG); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSelectionModeEnabled() { mOnBackPressedCallback.isEnabled = true }
    override fun onSelectionModeDisabled() { mOnBackPressedCallback.isEnabled = false }
    override fun onSelectionChange(selectionCount: Int): Boolean = false

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_uninstall -> handleBatchOpWithWarning(BatchOpsManager.OP_UNINSTALL)
            R.id.action_freeze_unfreeze -> showFreezeUnfreezeDialog(Prefs.Blocking.getDefaultFreezingMethod())
            R.id.action_save_apk -> mStoragePermission.request { if (it) handleBatchOp(BatchOpsManager.OP_BACKUP_APK) }
            R.id.action_block_unblock_trackers -> {
                MaterialAlertDialogBuilder(this).setTitle(R.string.block_unblock_trackers).setMessage(R.string.choose_what_to_do).setPositiveButton(R.string.block) { _, _ -> handleBatchOp(BatchOpsManager.OP_BLOCK_TRACKERS) }.setNegativeButton(R.string.cancel, null).setNeutralButton(R.string.unblock) { _, _ -> handleBatchOp(BatchOpsManager.OP_UNBLOCK_TRACKERS) }.show()
            }
            R.id.action_add_to_profile -> {
                AddToProfileDialogFragment.getInstance(viewModel!!.getSelectedPackages().keys.toTypedArray()).show(supportFragmentManager, AddToProfileDialogFragment.TAG)
            }
            else -> return false
        }
        return true
    }

    override fun onQueryTextChange(newText: String, type: Int): Boolean { viewModel!!.setQuery(newText, type); return true }
    override fun onQueryTextSubmit(query: String, type: Int): Boolean = false

    private fun showFreezeUnfreezeDialog(freezeType: Int) {
        val view = View.inflate(this, R.layout.item_checkbox, null)
        val checkBox: MaterialCheckBox = view.findViewById(R.id.checkbox)
        checkBox.setText(R.string.freeze_prefer_per_app_option)
        FreezeUnfreeze.getFreezeDialog(this, freezeType).setIcon(R.drawable.ic_snowflake).setTitle(R.string.freeze_unfreeze).setView(view).setPositiveButton(R.string.freeze) { _, _, sel -> sel?.let { handleBatchOp(BatchOpsManager.OP_ADVANCED_FREEZE, BatchFreezeOptions(it, checkBox.isChecked)) } }.setNegativeButton(R.string.cancel, null).setNeutralButton(R.string.unfreeze) { _, _, _ -> handleBatchOp(BatchOpsManager.OP_UNFREEZE) }.show()
    }

    private fun handleBatchOpWithWarning(@BatchOpsManager.OpType op: Int) {
        MaterialAlertDialogBuilder(this).setTitle(R.string.are_you_sure).setMessage(R.string.this_action_cannot_be_undone).setPositiveButton(R.string.yes) { _, _ -> handleBatchOp(op) }.setNegativeButton(R.string.no, null).show()
    }

    private fun handleBatchOp(@BatchOpsManager.OpType op: Int, options: IBatchOpOptions? = null) {
        mProgressIndicator?.show()
        val result = BatchOpsManager.Result(viewModel!!.getSelectedPackagesWithUsers())
        val item = BatchQueueItem.getBatchOpQueue(op, result.failedPackages, result.associatedUsers, options)
        ContextCompat.startForegroundService(this, BatchOpsService.getServiceIntent(this, item))
        mMultiSelectionView!!.cancel()
    }
}
