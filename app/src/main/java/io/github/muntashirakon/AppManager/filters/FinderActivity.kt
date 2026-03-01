// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters

import android.os.Bundle
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.utils.DateUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.util.UiUtils
import io.github.muntashirakon.view.ProgressIndicatorCompat
import io.github.muntashirakon.widget.MultiSelectionView
import io.github.muntashirakon.widget.RecyclerView
import androidx.lifecycle.ViewModelProvider

class FinderActivity : BaseActivity(), EditFiltersDialogFragment.OnSaveDialogButtonInterface {
    private var mViewModel: FinderViewModel? = null
    private var mProgress: LinearProgressIndicator? = null
    private var mRecyclerView: RecyclerView? = null
    private var mAdapter: FinderAdapter? = null
    private var mFilterBtn: FloatingActionButton? = null
    private var mMultiSelectionView: MultiSelectionView? = null

    override fun onAuthenticated(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_finder)
        setSupportActionBar(findViewById(R.id.toolbar))
        mViewModel = ViewModelProvider(this).get(FinderViewModel::class.java)
        mProgress = findViewById(R.id.progress_linear)
        mRecyclerView = findViewById(R.id.item_list)
        mFilterBtn = findViewById(R.id.floatingActionButton)
        mMultiSelectionView = findViewById(R.id.selection_view)
        UiUtils.applyWindowInsetsAsMargin(mFilterBtn!!)
        mAdapter = FinderAdapter()
        mRecyclerView!!.layoutManager = UIUtils.getGridLayoutAt450Dp(this)
        mRecyclerView!!.adapter = mAdapter
        mMultiSelectionView!!.hide()
        mFilterBtn!!.setOnClickListener { showFiltersDialog() }
        mViewModel!!.filteredAppListLiveData.observe(this) { list ->
            ProgressIndicatorCompat.setVisibility(mProgress!!, false)
            mAdapter!!.setDefaultList(list)
        }
        mViewModel!!.lastUpdateTimeLiveData.observe(this) { time ->
            val subtitle = if (time < 0) getString(R.string.loading)
            else "Loaded at: ${DateUtils.formatDateTime(this, time)}"
            supportActionBar?.subtitle = subtitle
        }
        mViewModel!!.loadFilteredAppList(true)
    }

    private fun showFiltersDialog() {
        val dialog = EditFiltersDialogFragment()
        dialog.setOnSaveDialogButtonInterface(this)
        dialog.show(supportFragmentManager, EditFiltersDialogFragment.TAG)
    }

    override fun getFilterItem(): FilterItem = mViewModel!!.filterItem

    override fun onItemAltered(item: FilterItem) {
        mViewModel!!.loadFilteredAppList(false)
    }
}
