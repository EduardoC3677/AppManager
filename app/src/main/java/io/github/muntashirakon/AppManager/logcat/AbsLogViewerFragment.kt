// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat

import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.Filter
import androidx.activity.OnBackPressedCallback
import androidx.annotation.CallSuper
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.logcat.helper.PreferenceHelper
import io.github.muntashirakon.AppManager.logcat.helper.SaveLogHelper
import io.github.muntashirakon.AppManager.logcat.struct.LogLine
import io.github.muntashirakon.AppManager.logcat.struct.SearchCriteria
import io.github.muntashirakon.AppManager.settings.LogViewerPreferences
import io.github.muntashirakon.AppManager.utils.StoragePermission
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.dialog.SearchableItemsDialogBuilder
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder
import io.github.muntashirakon.dialog.TextInputDialogBuilder
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.multiselection.MultiSelectionActionsView
import io.github.muntashirakon.util.AdapterUtils
import io.github.muntashirakon.widget.MultiSelectionView
import java.util.*

abstract class AbsLogViewerFragment : Fragment(), MenuProvider,
    LogViewerViewModel.LogLinesAvailableInterface,
    MultiSelectionActionsView.OnItemSelectedListener,
    MultiSelectionView.OnSelectionModeChangeListener,
    LogViewerActivity.SearchingInterface, Filter.FilterListener {

    protected var mRecyclerView: RecyclerView? = null
    protected var mMultiSelectionView: MultiSelectionView? = null
    protected var mViewModel: LogViewerViewModel? = null
    protected var mActivity: LogViewerActivity? = null
    protected var mLogListAdapter: LogViewerRecyclerAdapter? = null

    protected var mAutoscrollToBottom = true
    @Volatile
    protected var mSearchCriteria: SearchCriteria? = null

    protected val mStoragePermission = StoragePermission.init(this)
    protected val mRecyclerViewScrollListener: RecyclerView.OnScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)
        }

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            val layoutManager = recyclerView.layoutManager as LinearLayoutManager?
            if (layoutManager != null) {
                mAutoscrollToBottom = layoutManager.findLastCompletelyVisibleItemPosition() == (mLogListAdapter!!.itemCount - 1)
            }
        }
    }
    private val mOnBackPressedCallback: OnBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (mLogListAdapter!!.isInSelectionMode) {
                mMultiSelectionView!!.cancel()
            } else {
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_logcat, container, false)
    }

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mViewModel = ViewModelProvider(requireActivity()).get(LogViewerViewModel::class.java)
        mActivity = requireActivity() as LogViewerActivity
        mActivity!!.setSearchingInterface(this)
        mRecyclerView = view.findViewById(R.id.list)
        mRecyclerView!!.layoutManager = LinearLayoutManager(mActivity)
        mRecyclerView!!.itemAnimator = null
        mSearchCriteria = mActivity!!.getSearchQuery()
        if (mSearchCriteria != null) {
            mRecyclerView!!.postDelayed({ mActivity!!.search(mSearchCriteria!!) }, 1000)
        }
        mLogListAdapter = LogViewerRecyclerAdapter()
        mLogListAdapter!!.setClickListener(mActivity)
        mMultiSelectionView = view.findViewById(R.id.selection_view)
        mMultiSelectionView!!.adapter = mLogListAdapter
        mMultiSelectionView!!.onItemSelectedListener = this
        mMultiSelectionView!!.onSelectionModeChangeListener = this
        mMultiSelectionView!!.hide()
        mRecyclerView!!.adapter = mLogListAdapter
        mRecyclerView!!.addOnScrollListener(mRecyclerViewScrollListener)
        mActivity!!.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        mViewModel!!.getExpandLogsLiveData().observe(viewLifecycleOwner) { expanded ->
            val oldFirstVisibleItem = (mRecyclerView!!.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
            mLogListAdapter!!.setCollapseMode(!expanded)
            mLogListAdapter!!.notifyItemRangeChanged(0, mLogListAdapter!!.itemCount, AdapterUtils.STUB)
            if (mAutoscrollToBottom) {
                mRecyclerView!!.scrollToPosition(mLogListAdapter!!.itemCount - 1)
            } else if (oldFirstVisibleItem != -1) {
                mRecyclerView!!.scrollToPosition(oldFirstVisibleItem)
            }
            mActivity!!.supportInvalidateOptionsMenu()
        }
        mViewModel!!.observeLogLevelLiveData().observe(viewLifecycleOwner) { logLevel ->
            mLogListAdapter!!.setLogLevelLimit(logLevel)
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        requireActivity().onBackPressedDispatcher.addCallback(this, mOnBackPressedCallback)
    }

    @CallSuper
    override fun onDestroy() {
        mRecyclerView?.removeOnScrollListener(mRecyclerViewScrollListener)
        super.onDestroy()
    }

    abstract override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater)

    @CallSuper
    override fun onPrepareMenu(menu: Menu) {
        val expandMenu = menu.findItem(R.id.action_expand_collapse)
        if (expandMenu != null) {
            if (mViewModel!!.isCollapsedMode) {
                expandMenu.setIcon(R.drawable.ic_expand_more)
                expandMenu.setTitle(R.string.expand_all)
            } else {
                expandMenu.setIcon(R.drawable.ic_expand_less)
                expandMenu.setTitle(R.string.collapse_all)
            }
        }
    }

    @CallSuper
    override fun onMenuItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.action_expand_collapse) {
            mViewModel!!.setCollapsedMode(!mViewModel!!.isCollapsedMode)
        } else if (id == R.id.action_open) {
            mStoragePermission.request { granted ->
                if (granted) displayOpenLogFileDialog()
            }
        } else if (id == R.id.action_save) {
            mStoragePermission.request { granted ->
                if (granted) displaySaveLogDialog(false)
            }
        } else if (id == R.id.action_delete) {
            displayDeleteSavedLogsDialog()
        } else if (id == R.id.action_change_log_level) {
            val logLevelsLocalised = resources.getStringArray(R.array.log_levels)
            SearchableSingleChoiceDialogBuilder(mActivity, LogViewerPreferences.LOG_LEVEL_VALUES, logLevelsLocalised)
                .setTitle(R.string.log_level)
                .setSelection(mViewModel!!.logLevel)
                .setOnSingleChoiceClickListener { _, _, selectedLogLevel, _ ->
                    mViewModel!!.setLogLevel(selectedLogLevel)
                    mActivity!!.search(mSearchCriteria)
                }
                .setNegativeButton(R.string.close, null)
                .show()
        } else if (id == R.id.action_settings) {
            mActivity!!.displayLogViewerSettings()
        } else if (id == R.id.action_show_saved_filters) {
            mViewModel!!.loadFilters()
        } else if (id == R.id.action_share) {
            displaySaveDebugLogsDialog(true, false)
        } else if (id == R.id.action_export) {
            displaySaveDebugLogsDialog(false, false)
            return true
        } else return false
        return true
    }

    override fun onSelectionModeEnabled() {
        mOnBackPressedCallback.isEnabled = true
    }

    override fun onSelectionModeDisabled() {
        mOnBackPressedCallback.isEnabled = false
    }

    @CallSuper
    override fun onQuery(searchCriteria: SearchCriteria?) {
        mSearchCriteria = searchCriteria
        val filter = mLogListAdapter!!.filter
        filter.filter(if (searchCriteria != null) searchCriteria.query else null, this)
    }

    override fun onFilterComplete(count: Int) {
        mRecyclerView!!.scrollToPosition(count - 1)
    }

    protected fun getCurrentLogsAsListOfStrings(): List<String> {
        val result: MutableList<String> = ArrayList(mLogListAdapter!!.itemCount)
        for (i in 0 until mLogListAdapter!!.itemCount) {
            result.add(mLogListAdapter!!.getItem(i).originalLine)
        }
        return result
    }

    protected fun getSelectedLogsAsStrings(): List<String> {
        val result: MutableList<String> = ArrayList()
        for (logLine in mLogListAdapter!!.selectedLogLines) {
            result.add(logLine.originalLine)
        }
        return result
    }

    protected fun displaySaveLogDialog(onlySelected: Boolean) {
        TextInputDialogBuilder(mActivity, R.string.filename)
            .setTitle(R.string.save_log)
            .setInputText(SaveLogHelper.createLogFilename())
            .setPositiveButton(R.string.ok) { _, _, inputText, _ ->
                if (SaveLogHelper.isInvalidFilename(inputText)) {
                    UIUtils.displayShortToast(R.string.enter_good_filename)
                } else {
                    val filename = inputText.toString()
                    mViewModel!!.saveLogs(filename, if (onlySelected) selectedLogsAsStrings else currentLogsAsListOfStrings)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    protected fun displaySaveDebugLogsDialog(share: Boolean, onlySelected: Boolean) {
        val view = LayoutInflater.from(mActivity).inflate(R.layout.checkbox_with_title, null)
        val dmesg = view.findViewById<CheckBox>(R.id.checkbox)
        dmesg.setText(R.string.pref_include_dmesg_title)
        dmesg.isChecked = PreferenceHelper.getIncludeDmesgPreference(mActivity!!)
        val deviceInfo = view.findViewById<CheckBox>(R.id.checkbox2)
        deviceInfo.setText(R.string.pref_include_device_info_title)
        deviceInfo.isChecked = PreferenceHelper.getIncludeDeviceInfoPreference(mActivity!!)
        MaterialAlertDialogBuilder(mActivity!!)
            .setTitle(if (share) R.string.share_logs else R.string.export_logs)
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _ ->
                PreferenceHelper.setIncludeDmesgPreference(mActivity!!, dmesg.isChecked)
                PreferenceHelper.setIncludeDeviceInfoPreference(mActivity!!, deviceInfo.isChecked)
                mViewModel!!.saveDebugLogs(
                    share,
                    dmesg.isChecked,
                    deviceInfo.isChecked,
                    if (onlySelected) selectedLogsAsStrings else currentLogsAsListOfStrings
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    protected fun displayOpenLogFileDialog() {
        val files = SaveLogHelper.getLogFiles()
        if (files.isEmpty()) {
            UIUtils.displayShortToast(R.string.no_logs_found)
            return
        }
        val fileNames = SaveLogHelper.getFormattedFilenames(mActivity!!, files)
        SearchableItemsDialogBuilder(mActivity, files, fileNames)
            .setTitle(R.string.open_log)
            .hideSearchBar(true)
            .setPositiveButton(R.string.ok) { _, _, filename ->
                mViewModel!!.openLogFile(filename.name)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    protected fun displayDeleteSavedLogsDialog() {
        val files = SaveLogHelper.getLogFiles()
        if (files.isEmpty()) {
            UIUtils.displayShortToast(R.string.no_logs_found)
            return
        }
        val fileNames = SaveLogHelper.getFormattedFilenames(mActivity!!, files)
        SearchableMultiChoiceDialogBuilder(mActivity, files, fileNames)
            .setTitle(R.string.delete_logs)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _, selectedItems ->
                if (selectedItems.isEmpty()) return@setPositiveButton
                MaterialAlertDialogBuilder(mActivity!!)
                    .setTitle(R.string.delete_selected_logs)
                    .setMessage(R.string.are_you_sure)
                    .setNegativeButton(R.string.no, null)
                    .setPositiveButton(R.string.yes) { _, _ ->
                        mViewModel!!.deleteLogs(selectedItems.map { it.getName() })
                        UIUtils.displayShortToast(R.string.deleted_successfully)
                    }
                    .show()
            }
            .show()
    }

    companion object {
        val TAG: String = AbsLogViewerFragment::class.java.simpleName
    }
}
