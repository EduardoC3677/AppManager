// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Filter
import androidx.annotation.CallSuper
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.logcat.helper.ServiceHelper
import io.github.muntashirakon.AppManager.logcat.struct.LogLine
import io.github.muntashirakon.AppManager.logcat.struct.SearchCriteria
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.multiselection.MultiSelectionActionsView
import java.lang.ref.WeakReference
import java.util.*

// Copyright 2022 Muntashir Al-Islam
class LiveLogViewerFragment : AbsLogViewerFragment(), LogViewerViewModel.LogLinesAvailableInterface {
    private var mLogCounter = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mMultiSelectionView!!.setOnSelectionChangeListener { selectionCount ->
            if (selectionCount == 1) mViewModel!!.pauseLogcat()
            else if (selectionCount == 0) mViewModel!!.resumeLogcat()
            false
        }
        mViewModel!!.startLogcat(WeakReference(this))
    }

    override fun onResume() {
        if (mLogListAdapter != null && mLogListAdapter!!.itemCount > 0) {
            mRecyclerView!!.scrollToPosition(mLogListAdapter!!.itemCount - 1)
        }
        mActivity!!.supportActionBar?.subtitle = ""
        super.onResume()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.fragment_live_log_viewer_actions, menu)
    }

    override fun onPrepareMenu(menu: Menu) {
        super.onPrepareMenu(menu)

        val recordingInProgress = ServiceHelper.checkIfServiceIsRunning(
            requireContext().applicationContext,
            LogcatRecordingService::class.java
        )
        val recordMenuItem = menu.findItem(R.id.action_record)
        recordMenuItem.isEnabled = !recordingInProgress
        recordMenuItem.isVisible = !recordingInProgress

        val crazyLoggerMenuItem = menu.findItem(R.id.action_crazy_logger_service)
        crazyLoggerMenuItem.isEnabled = BuildConfig.DEBUG
        crazyLoggerMenuItem.isVisible = BuildConfig.DEBUG
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.action_play_pause) {
            if (mViewModel!!.isLogcatPaused()) {
                mViewModel!!.resumeLogcat()
                item.setIcon(R.drawable.ic_pause)
            } else {
                mViewModel!!.pauseLogcat()
                item.setIcon(R.drawable.ic_play_arrow)
            }
        } else if (id == R.id.action_clear) {
            mLogListAdapter?.clear()
            UIUtils.displayShortToast(R.string.logs_cleared)
        } else if (id == R.id.action_record) {
            startRecordDialog(null)
        } else if (id == R.id.action_crazy_logger_service) {
            if (ServiceHelper.checkIfServiceIsRunning(ContextUtils.getContext(), CrazyLoggerService::class.java)) {
                item.title = getString(R.string.crazy_logger_start)
                ServiceHelper.stopBackgroundService(ContextUtils.getContext(), CrazyLoggerService::class.java)
            } else {
                item.title = getString(R.string.crazy_logger_stop)
                ServiceHelper.startBackgroundService(ContextUtils.getContext(), CrazyLoggerService::class.java)
            }
        } else if (id == R.id.action_save_selection) {
            if (mLogListAdapter!!.selectedLogLines.isEmpty()) {
                UIUtils.displayShortToast(R.string.nothing_selected)
                return true
            }
            displaySaveLogDialog(true)
        } else if (id == R.id.action_share_selection) {
            if (mLogListAdapter!!.selectedLogLines.isEmpty()) {
                UIUtils.displayShortToast(R.string.nothing_selected)
                return true
            }
            displaySaveDebugLogsDialog(true, true)
        } else if (id == R.id.action_copy_selection) {
            if (mLogListAdapter!!.selectedLogLines.isEmpty()) {
                UIUtils.displayShortToast(R.string.nothing_selected)
                return true
            }
            Utils.copyToClipboard(mActivity, "", TextUtils.join("
", selectedLogsAsStrings))
            UIUtils.displayShortToast(R.string.copied_to_clipboard)
        } else return super.onMenuItemSelected(item)
        return true
    }

    override fun onNewLogsAvailable(logLines: List<LogLine>) {
        if (!isAdded) return
        mLogListAdapter!!.addWithFilter(logLines[0], mSearchCriteria, true)
        if (mLogListAdapter!!.realSize > Prefs.LogViewer.getDisplayLimit()) {
            mLogListAdapter!!.removeFirst(mLogListAdapter!!.realSize - Prefs.LogViewer.getDisplayLimit())
        }
        if (mAutoscrollToBottom) {
            mRecyclerView!!.scrollToPosition(mLogListAdapter!!.itemCount - 1)
        }
        mLogCounter++
        if (mLogCounter % UPDATE_CHECK_INTERVAL == 0) {
            Log.d(TAG, "Logs: " + mLogListAdapter!!.realSize)
            mLogCounter = 0
        }
    }

    private fun startRecordDialog(suggestions: Array<String>?) {
        val dialog = RecordLogDialogFragment.getInstance(suggestions, object : RecordLogDialogFragment.OnRecordingServiceStartedListenerInterface {
            override fun onServiceStarted() {
                val menuItem = mActivity!!.findViewById<View>(R.id.action_record) as MenuItem
                if (menuItem != null) {
                    menuItem.setEnabled(false)
                    menuItem.setVisible(false)
                }
            }
        })
        dialog.show(mActivity!!.supportFragmentManager, RecordLogDialogFragment.TAG)
    }

    companion object {
        val TAG: String = LiveLogViewerFragment::class.java.simpleName
        const val UPDATE_CHECK_INTERVAL = 200
        private const val ARG_RESTART = "restart"

        @JvmStatic
        fun getInstance(restart: Boolean): LiveLogViewerFragment {
            val fragment = LiveLogViewerFragment()
            val args = Bundle()
            args.putBoolean(ARG_RESTART, restart)
            fragment.arguments = args
            return fragment
        }
    }
}
