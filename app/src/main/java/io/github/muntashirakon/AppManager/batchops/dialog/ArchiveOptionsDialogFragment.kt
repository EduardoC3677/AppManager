// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager
import io.github.muntashirakon.AppManager.batchops.struct.BatchArchiveOptions
import io.github.muntashirakon.AppManager.utils.UIUtils

/**
 * Dialog for configuring archive options
 * Allows users to:
 * - Enable cache cleaning before archiving
 * - Enable data cleaning before archiving
 * - View estimated storage savings
 */
class ArchiveOptionsDialogFragment : DialogFragment() {
    
    private var mListener: ArchiveOptionsListener? = null
    private var mEstimatedSavings: Long = 0L
    private var mAppCount: Int = 0
    
    interface ArchiveOptionsListener {
        fun onArchiveConfirmed(options: BatchArchiveOptions)
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val view = View.inflate(context, R.layout.dialog_archive_options, null)
        
        val cacheCleanCheckBox = view.findViewById<MaterialCheckBox>(R.id.cache_clean_checkbox)
        val dataCleanCheckBox = view.findViewById<MaterialCheckBox>(R.id.data_clean_checkbox)
        val storageEstimateText = view.findViewById<TextView>(R.id.storage_estimate_text)
        
        // Update storage estimate display
        if (mEstimatedSavings > 0) {
            storageEstimateText.text = getString(R.string.archive_storage_estimate, 
                formatSize(mEstimatedSavings), mAppCount)
            storageEstimateText.visibility = View.VISIBLE
        } else {
            storageEstimateText.visibility = View.GONE
        }
        
        return MaterialAlertDialogBuilder(context)
            .setTitle(R.string.archive_options)
            .setView(view)
            .setPositiveButton(R.string.archive) { _, _ ->
                var archiveOptions = BatchOpsManager.ARCHIVE_OPTION_NONE
                
                if (cacheCleanCheckBox.isChecked) {
                    archiveOptions = archiveOptions or BatchOpsManager.ARCHIVE_WITH_CACHE_CLEAN
                }
                
                if (dataCleanCheckBox.isChecked) {
                    archiveOptions = archiveOptions or BatchOpsManager.ARCHIVE_WITH_DATA_CLEAN
                }
                
                val options = BatchArchiveOptions(
                    mode = ArchiveHandler.MODE_AUTO,
                    archiveOptions = archiveOptions,
                    includeCacheClean = cacheCleanCheckBox.isChecked,
                    includeDataClean = dataCleanCheckBox.isChecked
                )
                
                mListener?.onArchiveConfirmed(options)
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }
    
    fun setStorageEstimate(savings: Long, appCount: Int) {
        mEstimatedSavings = savings
        mAppCount = appCount
    }
    
    fun setListener(listener: ArchiveOptionsListener) {
        mListener = listener
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
        const val TAG = "ArchiveOptionsDialog"
        
        @JvmStatic
        fun newInstance(): ArchiveOptionsDialogFragment {
            return ArchiveOptionsDialogFragment()
        }
    }
}
