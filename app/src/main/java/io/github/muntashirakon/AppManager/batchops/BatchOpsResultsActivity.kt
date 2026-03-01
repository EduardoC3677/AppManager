// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.*
import android.widget.TextView
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.intercept.IntentCompat
import io.github.muntashirakon.AppManager.utils.PackageUtils
import io.github.muntashirakon.AppManager.utils.RestartUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.util.AccessibilityUtils
import java.util.*

class BatchOpsResultsActivity : BaseActivity() {
    private var mRecyclerView: RecyclerView? = null
    private var mLogViewer: AppCompatEditText? = null
    private var mBatchQueueItem: BatchQueueItem? = null

    override fun onAuthenticated(savedInstanceState: Bundle?) {
        val intent = intent ?: run {
            finish()
            return
        }
        if (restartIfNeeded(intent)) return
        setContentView(R.layout.activity_batch_ops_results)
        setSupportActionBar(findViewById(R.id.toolbar))
        findViewById<View>(R.id.progress_linear).visibility = View.GONE
        mRecyclerView = findViewById(R.id.list)
        mRecyclerView!!.layoutManager = UIUtils.getGridLayoutAt450Dp(this)
        val logToggler = findViewById<MaterialButton>(R.id.action_view_logs)
        mLogViewer = findViewById(R.id.text)
        mLogViewer!!.keyListener = null
        logToggler.setOnClickListener {
            mLogViewer!!.visibility = View.VISIBLE
            AccessibilityUtils.requestAccessibilityFocus(mLogViewer!!)
        }
        handleIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.clear()
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (restartIfNeeded(intent)) return
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        mBatchQueueItem = IntentCompat.getUnwrappedParcelableExtra(intent, BatchOpsService.EXTRA_QUEUE_ITEM, BatchQueueItem::class.java)
        if (mBatchQueueItem == null) {
            finish()
            return
        }
        title = intent.getStringExtra(BatchOpsService.EXTRA_FAILURE_MESSAGE)
        val packageLabels = PackageUtils.packagesToAppLabels(
            packageManager,
            mBatchQueueItem!!.packages, mBatchQueueItem!!.users
        )
        val adapter = RecyclerAdapter(packageLabels ?: emptyList())
        mRecyclerView!!.adapter = adapter
        packageLabels?.let {
            adapter.notifyItemRangeInserted(0, it.size)
        }
        mLogViewer!!.setText(getFormattedLogs(BatchOpsLogger.getAllLogs()))
        intent.removeExtra(BatchOpsService.EXTRA_QUEUE_ITEM)
    }

    private fun restartIfNeeded(intent: Intent): Boolean {
        if (intent.getBooleanExtra(BatchOpsService.EXTRA_REQUIRES_RESTART, false)) {
            RestartUtils.restart(RestartUtils.RESTART_NORMAL)
            return true
        }
        return false
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_batch_ops_results, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_done) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun getFormattedLogs(logs: List<String>): SpannableString {
        val sb = StringBuilder()
        for (log in logs) {
            sb.append(log).append("
")
        }
        val ss = SpannableString(sb.toString())
        var start = 0
        for (log in logs) {
            if (log.startsWith("====")) {
                ss.setSpan(StyleSpan(Typeface.BOLD), start, start + log.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            start += log.length + 1
        }
        return ss
    }

    private inner class RecyclerAdapter(private val mPackageLabels: List<CharSequence>) : RecyclerView.Adapter<RecyclerAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_batch_ops_result, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.text.text = mPackageLabels[position]
        }

        override fun getItemCount(): Int = mPackageLabels.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val text: TextView = itemView.findViewById(R.id.text)
        }
    }
}
