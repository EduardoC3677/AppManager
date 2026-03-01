// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat

import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.annotation.CallSuper
import androidx.core.os.BundleCompat
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.logcat.struct.LogLine
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.utils.UIUtils
import java.lang.ref.WeakReference
import java.util.*

class SavedLogViewerFragment : AbsLogViewerFragment(), LogViewerViewModel.LogLinesAvailableInterface {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val filename = requireArguments().getString(ARG_FILENAME)
        val uri = BundleCompat.getParcelable(requireArguments(), ARG_URI, Uri::class.java)

        val logLinesAvailableInterface = WeakReference(this)
        if (filename != null) mViewModel!!.openLogFile(filename, logLinesAvailableInterface)
        else if (uri != null) mViewModel!!.openLogsFromFile(uri, logLinesAvailableInterface)

        mViewModel!!.observeLoadingProgress().observe(viewLifecycleOwner) { progress ->
            mActivity!!.setSubtitle("$progress%")
        }
    }

    override fun onResume() {
        if (mViewModel!!.isCollapsedMode) {
            val oldFirstVisibleItem = (mRecyclerView!!.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
            mLogListAdapter!!.isCollapsedMode = true
            mLogListAdapter!!.notifyItemRangeChanged(0, mLogListAdapter!!.itemCount)
            mRecyclerView!!.scrollToPosition(oldFirstVisibleItem)
        }
        super.onResume()
    }

    @CallSuper
    override fun onDestroy() {
        mActivity!!.setSubtitle("")
        super.onDestroy()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.fragment_saved_log_viewer_actions, menu)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.action_save_selection) {
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
            val sb = StringBuilder()
            for (line in mLogListAdapter!!.selectedLogLines) {
                sb.append(line.originalLine).append("
")
            }
            UIUtils.copyToClipboard(mActivity, "", sb.toString())
            UIUtils.displayShortToast(R.string.copied_to_clipboard)
        } else return super.onMenuItemSelected(item)
        return true
    }

    override fun onNewLogsAvailable(logLines: List<LogLine>) {
        if (!isAdded) return
        val stopWatch = io.github.muntashirakon.AppManager.utils.Utils.StopWatch("onNewLogsAvailable")
        mLogListAdapter!!.addAll(logLines, true)
        mRecyclerView!!.scrollToPosition(mLogListAdapter!!.itemCount - 1)
        Log.d(TAG, "Logs: " + mLogListAdapter!!.realSize)
        stopWatch.log()
        mActivity!!.setSubtitle(getString(R.string.log_count_single, mLogListAdapter!!.realSize))
    }

    companion object {
        val TAG: String = SavedLogViewerFragment::class.java.simpleName
        private const val ARG_FILENAME = "filename"
        private const val ARG_URI = "uri"

        @JvmStatic
        fun getInstance(filename: String?): SavedLogViewerFragment {
            val fragment = SavedLogViewerFragment()
            val args = Bundle()
            args.putString(ARG_FILENAME, filename)
            fragment.arguments = args
            return fragment
        }

        @JvmStatic
        fun getInstance(uri: Uri?): SavedLogViewerFragment {
            val fragment = SavedLogViewerFragment()
            val args = Bundle()
            args.putParcelable(ARG_URI, uri)
            fragment.arguments = args
            return fragment
        }
    }
}
