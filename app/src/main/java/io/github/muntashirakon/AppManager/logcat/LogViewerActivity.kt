// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.BaseColumns
import android.text.TextUtils
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.cursoradapter.widget.CursorAdapter
import androidx.cursoradapter.widget.SimpleCursorAdapter
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputLayout
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.db.AppsDb
import io.github.muntashirakon.AppManager.db.entity.LogFilter
import io.github.muntashirakon.AppManager.fm.FmProvider
import io.github.muntashirakon.AppManager.intercept.IntentCompat
import io.github.muntashirakon.AppManager.logcat.helper.SaveLogHelper
import io.github.muntashirakon.AppManager.logcat.helper.ServiceHelper
import io.github.muntashirakon.AppManager.logcat.struct.LogLine
import io.github.muntashirakon.AppManager.logcat.struct.SearchCriteria
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.settings.SettingsActivity
import io.github.muntashirakon.AppManager.utils.AppExecutor
import io.github.muntashirakon.AppManager.utils.BetterActivityResult
import io.github.muntashirakon.AppManager.utils.CpuUtils
import io.github.muntashirakon.AppManager.utils.StoragePermission
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.dialog.DialogTitleBuilder
import io.github.muntashirakon.dialog.TextInputDropdownDialogBuilder
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import io.github.muntashirakon.util.UiUtils
import io.github.muntashirakon.widget.RecyclerView
import io.github.muntashirakon.widget.SearchView
import java.io.IOException
import java.util.*
import java.util.concurrent.ExecutorService

// Copyright 2012 Nolan Lawson
// Copyright 2021 Muntashir Al-Islam
class LogViewerActivity : BaseActivity(), SearchView.OnQueryTextListener,
    LogViewerRecyclerAdapter.ViewHolder.OnSearchByClickListener, SearchView.OnSuggestionListener {

    interface SearchingInterface {
        fun onQuery(searchCriteria: SearchCriteria?)
    }

    private var mProgressIndicator: LinearProgressIndicator? = null
    private var mStopRecordingFab: ExtendedFloatingActionButton? = null
    private var mLoadingDialog: AlertDialog? = null
    private var mSearchView: SearchView? = null
    private var mSearchCriteria: SearchCriteria? = null
    private var mDynamicallyEnteringSearchQuery: Boolean = false
    private val mSearchSuggestionsSet = HashSet<String>()
    private var mSearchSuggestionsAdapter: CursorAdapter? = null
    private var mLogsToBeShared: Boolean = false
    private var mSearchingInterface: SearchingInterface? = null
    private var mViewModel: LogViewerViewModel? = null
    private var mWakeLock: PowerManager.WakeLock? = null

    private val mExecutor: ExecutorService = AppExecutor.getExecutor()
    private val mActivityLauncher: BetterActivityResult<Intent, ActivityResult> =
        BetterActivityResult.registerActivityForResult(this)
    private val mStoragePermission = StoragePermission.init(this)
    private val mSaveLauncher: BetterActivityResult<String, Uri> =
        BetterActivityResult.registerForActivityResult(this, ActivityResultContracts.CreateDocument("*/*"))

    override fun onAuthenticated(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_logcat)
        setSupportActionBar(findViewById(R.id.toolbar))
        mViewModel = ViewModelProvider(this).get(LogViewerViewModel::class.java)
        mProgressIndicator = findViewById(R.id.progress_linear)
        mProgressIndicator!!.visibilityAfterHide = View.GONE
        mStopRecordingFab = findViewById(R.id.fab)
        UiUtils.applyWindowInsetsAsMargin(mStopRecordingFab!!)

        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setDisplayShowCustomEnabled(true)
            mSearchView = UIUtils.setupSearchView(actionBar, this)
            mSearchView!!.setOnSuggestionListener(this)
        }

        mSearchSuggestionsAdapter = SimpleCursorAdapter(
            this, io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item, null,
            arrayOf("suggestion"), intArrayOf(android.R.id.text1),
            CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER
        )
        mSearchView!!.setSuggestionsAdapter(mSearchSuggestionsAdapter)

        LogLine.omitSensitiveInfo = Prefs.LogViewer.omitSensitiveInfo()

        if ("record" == intent.getStringExtra("shortcut_action")) {
            mStoragePermission.request { granted ->
                if (granted) startLogRecorder()
            }
            return
        }

        mStopRecordingFab!!.setOnClickListener {
            ServiceHelper.stopBackgroundServiceIfRunning(this@LogViewerActivity)
        }

        mViewModel!!.setCollapsedMode(!Prefs.LogViewer.expandByDefault())

        mViewModel!!.observeLoggingFinished().observe(this) { finished ->
            if (finished) {
                mProgressIndicator!!.hide()
                if (mViewModel!!.isLogcatPaused()) {
                    mViewModel!!.resumeLogcat()
                }
            }
        }
        mViewModel!!.observeLoadingProgress().observe(this) { percentage ->
            mProgressIndicator!!.setProgressCompat(percentage, true)
        }
        mViewModel!!.observeTruncatedLines().observe(this) { maxDisplayedLines ->
            UIUtils.displayLongToast(
                resources.getQuantityString(R.plurals.toast_log_truncated, maxDisplayedLines, maxDisplayedLines)
            )
        }
        mViewModel!!.getLogFilters().observe(this) { this.showFiltersDialog(it) }
        mViewModel!!.observeLogSaved().observe(this) { path ->
            if (path != null) {
                UIUtils.displayShortToast(R.string.log_saved)
                if (!path.getName().endsWith(".zip")) openLogFile(path.getName())
            } else {
                UIUtils.displayLongToast(R.string.unable_to_save_log)
            }
        }
        mViewModel!!.getLogsToBeSent().observe(this) { sendLogDetails ->
            mLoadingDialog?.dismiss()
            if (sendLogDetails == null || sendLogDetails.attachmentType == null || sendLogDetails.attachment == null) {
                UIUtils.displayLongToast(R.string.failed)
                return@observe
            }
            if (mLogsToBeShared) {
                startChooser(
                    this, sendLogDetails.subject, sendLogDetails.attachmentType,
                    sendLogDetails.attachment
                )
            } else {
                mSaveLauncher.launch(sendLogDetails.attachment.getName()) { uri ->
                    if (uri == null) return@launch
                    mViewModel!!.saveLogs(Paths.get(uri), sendLogDetails)
                }
            }
        }

        startLogging()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            true
        } else super.onOptionsItemSelected(item)
    }

    override fun onSearchByClick(itemId: Int, logLine: LogLine) {
        val oldSearchCriteria = mSearchCriteria
        val newSearchCriteria: String? = when (itemId) {
            R.id.action_search_by_pid -> SearchCriteria.PID_KEYWORD + logLine.pid
            R.id.action_search_by_uid -> SearchCriteria.UID_KEYWORD + logLine.uidOwner
            R.id.action_search_by_package -> SearchCriteria.PKG_KEYWORD + logLine.packageName
            R.id.action_search_by_tag -> SearchCriteria.TAG_KEYWORD + logLine.tagName
            R.id.action_search_by_message -> logLine.logOutput
            else -> null
        }
        if (newSearchCriteria == null) return

        if (oldSearchCriteria != null) {
            val oldSearchQuery = oldSearchCriteria.query
            mSearchView!!.setQuery(if (TextUtils.isEmpty(oldSearchQuery)) newSearchCriteria else "$oldSearchQuery $newSearchCriteria", true)
        } else mSearchView!!.setQuery(newSearchCriteria, true)
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        mSearchView!!.clearFocus()
        mSearchCriteria = SearchCriteria(query)
        mSearchingInterface?.onQuery(mSearchCriteria)
        addToAutocompleteSuggestions(query)
        return true
    }

    override fun onQueryTextChange(newText: String): Boolean {
        if (mDynamicallyEnteringSearchQuery) {
            mSearchCriteria = SearchCriteria(newText)
            mSearchingInterface?.onQuery(mSearchCriteria)
        }
        populateSearchSuggestions(newText)
        return false
    }

    fun getSearchQuery(): SearchCriteria? = mSearchCriteria
    fun setSearchQuery(query: String?) {
        mDynamicallyEnteringSearchQuery = true
        mSearchView!!.setQuery(query, true)
        mDynamicallyEnteringSearchQuery = false
    }

    fun loadNewFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.main_layout, fragment).addToBackStack(null).commit()
    }

    private fun startLogRecorder() {
        val logFilename = SaveLogHelper.createLogFilename()
        mExecutor.submit {
            val intent = ServiceHelper.getLogcatRecorderServiceIfNotAlreadyRunning(
                this, logFilename,
                "", Prefs.LogViewer.getLogLevel()
            )
            runOnUiThread {
                if (intent != null) ContextCompat.startForegroundService(this, intent)
                finish()
            }
        }
    }

    @WorkerThread
    private fun addFiltersToSuggestions() {
        for (logFilter in AppsDb().logFilterDao().all) {
            addToAutocompleteSuggestions(logFilter.name)
        }
    }

    private fun startLogging() {
        applyFiltersFromIntent(intent)
        val dataUri = IntentCompat.getDataUri(intent)
        val filename = intent.getStringExtra(EXTRA_FILENAME)
        if (dataUri != null) {
            openLogFile(dataUri)
        } else if (filename != null) {
            openLogFile(filename)
        } else {
            mWakeLock = CpuUtils.getPartialWakeLock("logcat_activity")
            mWakeLock!!.acquire()
            startLiveLogViewer(false)
        }
    }

    private fun applyFiltersFromIntent(intent: Intent?) {
        if (intent == null) return
        val filter = intent.getStringExtra(EXTRA_FILTER)
        val level = intent.getStringExtra(EXTRA_LEVEL)
        if (!TextUtils.isEmpty(filter)) setSearchQuery(filter)
        if (!TextUtils.isEmpty(level)) mViewModel!!.setLogLevel(LogLine.convertCharToLogLevel(level!![0]))
    }

    private fun stopLogging() {
        mWakeLock?.release()
        mWakeLock = null
        if (mViewModel!!.isLogcatKilled()) return
        mViewModel!!.killLogcatReader()
        startLiveLogViewer(true)
    }

    private fun showLoadingDialog() {
        mProgressIndicator!!.show()
        mLoadingDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.loading)
            .setMessage(R.string.loading_logs)
            .setCancelable(false)
            .create()
        mLoadingDialog!!.show()
    }

    private fun openLogFile(filename: String?) {
        showLoadingDialog()
        val fragment = SavedLogViewerFragment.getInstance(filename)
        loadNewFragment(fragment)
    }

    private fun openLogFile(uri: Uri?) {
        showLoadingDialog()
        val fragment = SavedLogViewerFragment.getInstance(uri)
        loadNewFragment(fragment)
    }

    private fun startLiveLogViewer(restart: Boolean) {
        val fragment = LiveLogViewerFragment.getInstance(restart)
        loadNewFragment(fragment)
    }

    fun displayLogViewerSettings() {
        val intent = SettingsActivity.getSettingsIntent(this, "log_viewer_prefs")
        mActivityLauncher.launch(intent) { }
    }

    fun setSearchSuggestions(suggestions: Set<String>) {
        mSearchSuggestionsSet.clear()
        mSearchSuggestionsSet.addAll(suggestions)
        populateSearchSuggestions("")
    }

    private fun populateSearchSuggestions(query: String) {
        val cursor = MatrixCursor(arrayOf(BaseColumns._ID, "suggestion"))
        var id = 0
        for (suggestion in mSearchSuggestionsSet) {
            if (suggestion.contains(query, true)) {
                cursor.addRow(arrayOf(id, suggestion))
                id++
            }
        }
        mSearchSuggestionsAdapter!!.changeCursor(cursor)
    }

    override fun onSuggestionSelect(position: Int): Boolean = false
    override fun onSuggestionClick(position: Int): Boolean {
        val cursor = mSearchSuggestionsAdapter!!.getItem(position) as Cursor
        val selection = cursor.getString(cursor.getColumnIndexOrThrow("suggestion"))
        mSearchView!!.setQuery(selection, true)
        return true
    }

    override fun onDestroy() {
        stopLogging()
        super.onDestroy()
    }

    private fun showFiltersDialog(filters: List<LogFilter>) {
        if (filters.isEmpty()) {
            UIUtils.displayShortToast(R.string.no_filters_found)
            return
        }
        val names = filters.map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.saved_filters)
            .setItems(names) { _, which ->
                val logFilter = filters[which]
                mSearchView!!.setQuery(logFilter.query, true)
                mViewModel!!.setLogLevel(logFilter.logLevel)
            }
            .setNegativeButton(R.string.close, null)
            .create().apply {
                getListView()?.apply {
                    layoutManager = LinearLayoutManager(this@LogViewerActivity, LinearLayoutManager.VERTICAL, false)
                    adapter = LogFilterAdapter(filters).apply {
                        setOnItemClickListener { _, _, logFilter ->
                            mSearchView!!.setQuery(logFilter.query, true)
                            mViewModel!!.setLogLevel(logFilter.logLevel)
                            dismiss()
                        }
                    }
                }
            }.show()
    }

    fun setSearchingInterface(searchingInterface: SearchingInterface?) {
        mSearchingInterface = searchingInterface
    }

    companion object {
        val TAG: String = LogViewerActivity::class.java.simpleName
        const val EXTRA_FILTER = "filter"\nconst val EXTRA_LEVEL = "level"\nconst val UPDATE_CHECK_INTERVAL = 200
        const val EXTRA_FILENAME = "filename"

        private const val MAX_NUM_SUGGESTIONS = 1000

        @JvmStatic
        fun startChooser(
            context: Context, subject: String?,
            attachmentType: String, attachment: Path
        ) {
            val actionSendIntent = Intent(Intent.ACTION_SEND)
                .setType(attachmentType)
                .putExtra(Intent.EXTRA_SUBJECT, subject)
                .putExtra(Intent.EXTRA_STREAM, FmProvider.getContentUri(attachment))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try {
                context.startActivity(
                    Intent.createChooser(actionSendIntent, context.resources.getText(R.string.send_log_title))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                UIUtils.displayLongToast(e.message)
            }
        }
    }
}
