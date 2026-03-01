// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBar
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.settings.FeatureController
import io.github.muntashirakon.AppManager.utils.BetterActivityResult
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.adapters.SelectedArrayAdapter
import io.github.muntashirakon.util.AccessibilityUtils
import io.github.muntashirakon.util.UiUtils
import io.github.muntashirakon.view.ProgressIndicatorCompat
import io.github.muntashirakon.widget.MaterialSpinner
import io.github.muntashirakon.widget.RecyclerView
import io.github.muntashirakon.widget.SwipeRefreshLayout

class AppUsageActivity : BaseActivity(), SwipeRefreshLayout.OnRefreshListener {
    private var viewModel: AppUsageViewModel? = null
    private var spinner: MaterialSpinner? = null
    private var recyclerView: RecyclerView? = null
    private var progressIndicator: LinearProgressIndicator? = null
    private var mSwipeRefresh: SwipeRefreshLayout? = null
    private var mAppUsageAdapter: AppUsageAdapter? = null
    private val mRequestPerm = BetterActivityResult.registerForActivityResult(this, ActivityResultContracts.RequestPermission())

    @SuppressLint("WrongConstant")
    override fun onAuthenticated(savedInstanceState: Bundle?) {
        if (!FeatureController.isUsageAccessEnabled()) {
            finish()
            return
        }
        setContentView(R.layout.activity_app_usage)
        setSupportActionBar(findViewById(R.id.toolbar))
        viewModel = ViewModelProvider(this).get(AppUsageViewModel::class.java)
        supportActionBar?.setTitle(R.string.app_usage)

        progressIndicator = findViewById(R.id.progress_linear)
        progressIndicator!!.visibilityAfterHide = View.GONE

        // Interval
        spinner = findViewById(R.id.spinner)
        UiUtils.applyWindowInsetsAsMargin(spinner!!, false, false)
        spinner!!.requestFocus()
        val intervalSpinnerAdapter: ArrayAdapter<CharSequence> = SelectedArrayAdapter.createFromResource(this,
            R.array.usage_interval_dropdown_list, io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item_small)
        spinner!!.adapter = intervalSpinnerAdapter
        spinner!!.setSelection(viewModel!!.currentInterval)
        spinner!!.setOnItemClickListener { _, _, position, _ ->
            ProgressIndicatorCompat.setVisibility(progressIndicator!!, true)
            viewModel!!.currentInterval = position
        }

        // Get usage stats
        mAppUsageAdapter = AppUsageAdapter(this)
        recyclerView = findViewById(R.id.scrollView)
        recyclerView!!.setEmptyView(findViewById(android.R.id.empty))
        recyclerView!!.layoutManager = UIUtils.getGridLayoutAt450Dp(this)
        recyclerView!!.adapter = mAppUsageAdapter

        mSwipeRefresh = findViewById(R.id.swipe_refresh)
        mSwipeRefresh!!.setOnRefreshListener(this)
        mSwipeRefresh!!.setOnChildScrollUpCallback { _, _ -> recyclerView!!.canScrollVertically(-1) }

        viewModel!!.getPackageUsageInfoList().observe(this) { packageUsageInfoList ->
            ProgressIndicatorCompat.setVisibility(progressIndicator!!, false)
            mAppUsageAdapter!!.setDefaultList(packageUsageInfoList)
            recyclerView!!.post {
                if (recyclerView!!.childCount > 0) {
                    val firstChild = recyclerView!!.getChildAt(0)
                    if (firstChild != null) {
                        AccessibilityUtils.requestAccessibilityFocus(firstChild)
                    }
                }
            }
        }
        viewModel!!.getPackageUsageInfo().observe(this) { packageUsageInfo ->
            val fragment = AppUsageDetailsDialog.getInstance(packageUsageInfo,
                viewModel!!.currentInterval, viewModel!!.currentDate)
            fragment.show(supportFragmentManager, AppUsageDetailsDialog.TAG)
        }
        checkPermissions()
    }

    override fun onRefresh() {
        mSwipeRefresh!!.isRefreshing = false
        checkPermissions()
    }

    private fun checkPermissions() {
        if (!SelfPermissions.checkUsageStatsPermission()) {
            promptForUsageStatsPermission()
        } else {
            ProgressIndicatorCompat.setVisibility(progressIndicator!!, true)
            viewModel!!.loadPackageUsageInfoList()
        }
        if (AppUsageStatsManager.requireReadPhoneStatePermission()) {
            mRequestPerm.launch(Manifest.permission.READ_PHONE_STATE) { granted ->
                if (granted) {
                    ActivityCompat.recreate(this)
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_app_usage_actions, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        viewModel?.let {
            menu.findItem(sSortMenuItemIdsMap[it.sortOrder]).isChecked = true
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_sort_by_app_label -> {
                setSortBy(SortOrder.SORT_BY_APP_LABEL)
                item.isChecked = true
                true
            }
            R.id.action_sort_by_last_used -> {
                setSortBy(SortOrder.SORT_BY_LAST_USED)
                item.setChecked(true)
                true
            }
            R.id.action_sort_by_mobile_data -> {
                setSortBy(SortOrder.SORT_BY_MOBILE_DATA)
                item.setChecked(true)
                true
            }
            R.id.action_sort_by_package_name -> {
                setSortBy(SortOrder.SORT_BY_PACKAGE_NAME)
                item.setChecked(true)
                true
            }
            R.id.action_sort_by_screen_time -> {
                setSortBy(SortOrder.SORT_BY_SCREEN_TIME)
                item.setChecked(true)
                true
            }
            R.id.action_sort_by_times_opened -> {
                setSortBy(SortOrder.SORT_BY_TIMES_OPENED)
                item.setChecked(true)
                true
            }
            R.id.action_sort_by_wifi_data -> {
                setSortBy(SortOrder.SORT_BY_WIFI_DATA)
                item.setChecked(true)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setSortBy(@SortOrder sort: Int) {
        viewModel?.sortOrder = sort
    }

    private fun promptForUsageStatsPermission() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.grant_usage_access)
            .setMessage(R.string.grant_usage_acess_message)
            .setPositiveButton(R.string.go) { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                } catch (e: ActivityNotFoundException) {
                    MaterialAlertDialogBuilder(this)
                        .setCancelable(false)
                        .setTitle(R.string.grant_usage_access)
                        .setMessage(R.string.usage_access_not_supported)
                        .setPositiveButton(R.string.go_back) { _, _ ->
                            FeatureController.getInstance().modifyState(FeatureController.FEAT_USAGE_ACCESS, false)
                            finish()
                        }
                        .show()
                }
            }
            .setNegativeButton(R.string.go_back) { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    companion object {
        private val sSortMenuItemIdsMap = intArrayOf(
            R.id.action_sort_by_app_label, R.id.action_sort_by_last_used,
            R.id.action_sort_by_mobile_data, R.id.action_sort_by_package_name,
            R.id.action_sort_by_screen_time, R.id.action_sort_by_times_opened,
            R.id.action_sort_by_wifi_data
        )
    }
}
