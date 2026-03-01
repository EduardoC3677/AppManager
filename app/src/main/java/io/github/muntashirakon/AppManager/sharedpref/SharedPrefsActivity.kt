// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sharedpref

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBar
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.intercept.IntentCompat
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes
import io.github.muntashirakon.io.Paths
import io.github.muntashirakon.util.AdapterUtils
import io.github.muntashirakon.util.UiUtils
import io.github.muntashirakon.widget.RecyclerView
import java.util.*

class SharedPrefsActivity : BaseActivity(), SearchView.OnQueryTextListener, EditPrefItemFragment.InterfaceCommunicator {
    private var mAdapter: SharedPrefsListingAdapter? = null
    private var mProgressIndicator: LinearProgressIndicator? = null
    private var mViewModel: SharedPrefsViewModel? = null
    private var mWriteAndExit = false
    private val mOnBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (mViewModel!!.isModified) {
                MaterialAlertDialogBuilder(this@SharedPrefsActivity)
                    .setTitle(R.string.exit_confirmation)
                    .setMessage(R.string.file_modified_are_you_sure)
                    .setCancelable(false)
                    .setPositiveButton(R.string.no, null)
                    .setNegativeButton(R.string.yes) { _, _ -> isEnabled = false; onBackPressedDispatcher.onBackPressed() }
                    .setNeutralButton(R.string.save_and_exit) { _, _ -> mWriteAndExit = true; mViewModel!!.writeSharedPrefs(); isEnabled = false }
                    .show()
                return
            }
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onAuthenticated(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_shared_prefs)
        setSupportActionBar(findViewById(R.id.toolbar))
        onBackPressedDispatcher.addCallback(this, mOnBackPressedCallback)
        val sharedPrefUri = IntentCompat.getParcelableExtra(intent, EXTRA_PREF_LOCATION, Uri::class.java)
        val appLabel = intent.getStringExtra(EXTRA_PREF_LABEL)
        if (sharedPrefUri == null) { finish(); return }
        mViewModel = ViewModelProvider(this).get(SharedPrefsViewModel::class.java)
        mViewModel!!.setSharedPrefsFile(Paths.get(sharedPrefUri))
        supportActionBar?.let {
            it.title = appLabel
            it.subtitle = mViewModel!!.sharedPrefFilename
            it.setDisplayShowCustomEnabled(true)
            UIUtils.setupSearchView(it, this)
        }
        mProgressIndicator = findViewById(R.id.progress_linear)
        mProgressIndicator!!.visibilityAfterHide = View.GONE
        mProgressIndicator!!.show()
        val recyclerView: RecyclerView = findViewById(android.R.id.list)
        recyclerView.layoutManager = UIUtils.getGridLayoutAt450Dp(this)
        recyclerView.setEmptyView(findViewById(android.R.id.empty))
        mAdapter = SharedPrefsListingAdapter(this)
        recyclerView.adapter = mAdapter
        val fab: FloatingActionButton = findViewById(R.id.floatingActionButton)
        UiUtils.applyWindowInsetsAsMargin(fab)
        fab.setOnClickListener {
            EditPrefItemFragment().apply {
                arguments = Bundle().apply { putInt(EditPrefItemFragment.ARG_MODE, EditPrefItemFragment.MODE_CREATE) }
                show(supportFragmentManager, EditPrefItemFragment.TAG)
            }
        }
        mViewModel!!.sharedPrefsMapLiveData.observe(this) { mProgressIndicator!!.hide(); mAdapter!!.setDefaultList(it) }
        mViewModel!!.sharedPrefsSavedLiveData.observe(this) {
            if (it) {
                UIUtils.displayShortToast(R.string.saved_successfully)
                if (mWriteAndExit) { onBackPressedDispatcher.onBackPressed(); mWriteAndExit = false }
            } else UIUtils.displayShortToast(R.string.saving_failed)
        }
        mViewModel!!.sharedPrefsDeletedLiveData.observe(this) {
            if (it) { UIUtils.displayShortToast(R.string.deleted_successfully); finish() }
            else UIUtils.displayShortToast(R.string.deletion_failed)
        }
        mViewModel!!.sharedPrefsModifiedLiveData.observe(this) { modified ->
            mOnBackPressedCallback.isEnabled = modified
            supportActionBar?.title = if (modified) "* ${mViewModel!!.sharedPrefFilename}" else mViewModel!!.sharedPrefFilename
        }
        mViewModel!!.loadSharedPrefs()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_shared_prefs_actions, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun sendInfo(mode: Int, prefItem: EditPrefItemFragment.PrefItem?) {
        if (prefItem != null) {
            when (mode) {
                EditPrefItemFragment.MODE_CREATE, EditPrefItemFragment.MODE_EDIT -> mViewModel!!.add(prefItem.keyName!!, prefItem.keyValue!!)
                EditPrefItemFragment.MODE_DELETE -> mViewModel!!.remove(prefItem.keyName!!)
            }
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_separate_window)?.isEnabled = !mViewModel!!.isModified
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { onBackPressedDispatcher.onBackPressed(); true }
            R.id.action_discard -> { finish(); true }
            R.id.action_delete -> { mViewModel!!.deleteSharedPrefFile(); true }
            R.id.action_save -> { mViewModel!!.writeSharedPrefs(); true }
            R.id.action_separate_window -> {
                if (!mViewModel!!.isModified) {
                    val intent = Intent(intent).apply {
                        setClass(this@SharedPrefsActivity, SharedPrefsActivity::class.java)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                    }
                    startActivity(intent)
                    finish()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        mAdapter?.mConstraint?.let { mAdapter!!.filter.filter(it) }
    }

    override fun onQueryTextSubmit(query: String): Boolean = false

    override fun onQueryTextChange(newText: String): Boolean {
        mAdapter?.filter?.filter(newText.lowercase(Locale.ROOT))
        return true
    }

    private fun displayEditor(prefName: String) {
        val prefItem = EditPrefItemFragment.PrefItem().apply { keyName = prefName; keyValue = mViewModel!!.getValue(prefName) }
        EditPrefItemFragment().apply {
            arguments = Bundle().apply {
                putParcelable(EditPrefItemFragment.ARG_PREF_ITEM, prefItem)
                putInt(EditPrefItemFragment.ARG_MODE, EditPrefItemFragment.MODE_EDIT)
            }
            show(supportFragmentManager, EditPrefItemFragment.TAG)
        }
    }

    class SharedPrefsListingAdapter(private val mActivity: SharedPrefsActivity) : RecyclerView.Adapter<SharedPrefsListingAdapter.ViewHolder>(), Filterable {
        private var mFilter: Filter? = null
        var mConstraint: String? = null
        private var mDefaultList: Array<String>? = null
        private var mAdapterList: Array<String>? = null
        private var mAdapterMap: Map<String, Any>? = null
        private val mQueryStringHighlightColor: Int = ColorCodes.getQueryStringHighlightColor(mActivity)

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val itemName: TextView = itemView.findViewById(android.R.id.title)
            val itemValue: TextView = itemView.findViewById(android.R.id.summary)
            init { itemView.findViewById<View>(R.id.icon_frame).visibility = View.GONE }
        }

        fun setDefaultList(list: Map<String, Any>) {
            mDefaultList = list.keys.toTypedArray()
            mAdapterMap = list
            if (!mConstraint.isNullOrEmpty()) filter.filter(mConstraint)
            else {
                val previousCount = mAdapterList?.size ?: 0
                mAdapterList = mDefaultList
                AdapterUtils.notifyDataSetChanged(this, previousCount, mAdapterList!!.size)
            }
        }

        override fun getItemCount(): Int = mAdapterList?.size ?: 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(parent.context).inflate(io.github.muntashirakon.ui.R.layout.m3_preference, parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val prefName = mAdapterList!![position]
            holder.itemName.text = if (mConstraint != null && prefName.lowercase(Locale.ROOT).contains(mConstraint!!)) UIUtils.getHighlightedText(prefName, mConstraint!!, mQueryStringHighlightColor) else prefName
            val value = mAdapterMap?.get(prefName)
            val strValue = value?.toString() ?: ""
            holder.itemValue.text = if (strValue.length > REASONABLE_STR_SIZE) strValue.substring(0, REASONABLE_STR_SIZE) else strValue
            holder.itemView.setOnClickListener { mActivity.displayEditor(prefName) }
        }

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getFilter(): Filter {
            if (mFilter == null) {
                mFilter = object : Filter() {
                    override fun performFiltering(charSequence: CharSequence): FilterResults {
                        val constraint = charSequence.toString().lowercase(Locale.ROOT)
                        mConstraint = constraint
                        val results = FilterResults()
                        if (constraint.isEmpty()) { results.count = 0; results.values = null; return results }
                        val list = mDefaultList!!.filter { it.lowercase(Locale.ROOT).contains(constraint) }
                        results.count = list.size
                        results.values = list.toTypedArray()
                        return results
                    }
                    override fun publishResults(charSequence: CharSequence, filterResults: FilterResults) {
                        val previousCount = mAdapterList?.size ?: 0
                        mAdapterList = if (filterResults.values == null) mDefaultList else filterResults.values as Array<String>
                        AdapterUtils.notifyDataSetChanged(this@SharedPrefsListingAdapter, previousCount, mAdapterList!!.size)
                    }
                }
            }
            return mFilter!!
        }
    }

    companion object {
        const val EXTRA_PREF_LOCATION = "loc"
        const val EXTRA_PREF_LABEL = "label"
        const val REASONABLE_STR_SIZE = 200
    }
}
