// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.cache

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager
import io.github.muntashirakon.AppManager.batchops.BatchOpsService
import io.github.muntashirakon.AppManager.batchops.BatchQueueItem
import io.github.muntashirakon.AppManager.types.UserPackagePair
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.widget.SwipeRefreshLayout
import java.util.*

/**
 * Cache Cleaner Activity
 * 
 * Standalone feature for cleaning app cache to free up storage space.
 * Shows cache size per app and allows selective or bulk cache cleaning.
 */
class CacheCleanerActivity : BaseActivity(), SwipeRefreshLayout.OnRefreshListener {
    
    private var mViewModel: CacheCleanerViewModel? = null
    private var mAdapter: CacheCleanerAdapter? = null
    private var mSwipeRefresh: SwipeRefreshLayout? = null
    private var mProgressIndicator: LinearProgressIndicator? = null
    private var mTotalCacheText: TextView? = null
    private var mCleanAllButton: View? = null
    
    override fun onAuthenticated(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_cache_cleaner)
        setSupportActionBar(findViewById(R.id.toolbar))
        
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.title = getString(R.string.cache_cleaner)
        }
        
        mProgressIndicator = findViewById(R.id.progress_linear)
        mSwipeRefresh = findViewById(R.id.swipe_refresh)
        mSwipeRefresh!!.setOnRefreshListener(this)
        mTotalCacheText = findViewById(R.id.total_cache_text)
        mCleanAllButton = findViewById(R.id.clean_all_button)
        
        // Initialize ViewModel
        mViewModel = ViewModelProvider(this).get(CacheCleanerViewModel::class.java)
        
        // Setup RecyclerView
        val recyclerView = findViewById<RecyclerView>(R.id.cache_list)
        mAdapter = CacheCleanerAdapter(this)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = mAdapter
        
        // Observe cache data
        mViewModel!!.cacheData.observe(this) { data ->
            mAdapter?.setCacheData(data)
            updateTotalCacheDisplay(data.totalCacheSize)
            mSwipeRefresh?.isRefreshing = false
            mProgressIndicator?.hide()
        }
        
        // Observe cleaning status
        mViewModel!!.cleaningStatus.observe(this) { status ->
            when (status) {
                is CacheCleanerViewModel.CleaningStatus.Idle -> {
                    mProgressIndicator?.hide()
                    mCleanAllButton?.isEnabled = true
                }
                is CacheCleanerViewModel.CleaningStatus.Cleaning -> {
                    mProgressIndicator?.show()
                    mCleanAllButton?.isEnabled = false
                }
                is CacheCleanerViewModel.CleaningStatus.Completed -> {
                    mProgressIndicator?.hide()
                    mCleanAllButton?.isEnabled = true
                    UIUtils.displayShortToast(
                        R.string.cache_cleared_successfully,
                        formatSize(status.spaceFreed)
                    )
                    // Refresh data
                    onRefresh()
                }
            }
        }
        
        // Clean all button click
        mCleanAllButton?.setOnClickListener {
            showCleanAllConfirmation()
        }
        
        // Initial load
        onRefresh()
    }
    
    private fun updateTotalCacheDisplay(totalBytes: Long) {
        mTotalCacheText?.text = getString(R.string.total_cache_size, formatSize(totalBytes))
        
        // Show/hide clean all button based on cache size
        mCleanAllButton?.visibility = if (totalBytes > 0) View.VISIBLE else View.GONE
    }
    
    private fun showCleanAllConfirmation() {
        val totalCache = mViewModel?.cacheData?.value?.totalCacheSize ?: 0L
        
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clean_all_cache)
            .setMessage(getString(R.string.clean_all_cache_message, formatSize(totalCache)))
            .setPositiveButton(R.string.clean) { _, _ ->
                cleanAllCache()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun cleanAllCache() {
        val data = mViewModel?.cacheData?.value ?: return
        
        val packages = data.apps.map { 
            UserPackagePair(it.packageName, it.userId) 
        }
        
        val info = BatchOpsManager.BatchOpsInfo(
            BatchOpsManager.OP_CLEAR_CACHE,
            packages.map { it.packageName },
            packages.map { it.userId },
            null
        )
        
        val item = BatchQueueItem.getOneClickQueue(
            BatchOpsManager.OP_CLEAR_CACHE,
            packages,
            null,
            null
        )
        
        val serviceIntent = BatchOpsService.getIntent(this, item)
        startService(serviceIntent)
        
        mViewModel!!.startCleaning()
    }
    
    fun cleanAppCache(packageName: String, userId: Int) {
        val packages = listOf(UserPackagePair(packageName, userId))
        
        val item = BatchQueueItem.getOneClickQueue(
            BatchOpsManager.OP_CLEAR_CACHE,
            packages,
            null,
            null
        )
        
        val serviceIntent = BatchOpsService.getIntent(this, item)
        startService(serviceIntent)
        
        mViewModel!!.startCleaning()
    }
    
    override fun onRefresh() {
        mProgressIndicator?.show()
        mViewModel!!.loadCacheData()
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_cache_cleaner, menu)
        return super.onCreateOptionsMenu(menu)
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    
    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
    
    companion object {
        const val TAG = "CacheCleanerActivity"
    }
}
