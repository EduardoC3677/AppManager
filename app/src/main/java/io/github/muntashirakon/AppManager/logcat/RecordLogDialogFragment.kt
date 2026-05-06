// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.logcat.helper.SaveLogHelper
import io.github.muntashirakon.AppManager.logcat.helper.ServiceHelper
import io.github.muntashirakon.AppManager.logcat.helper.WidgetHelper
import io.github.muntashirakon.AppManager.logcat.reader.LogcatReaderLoader
import io.github.muntashirakon.AppManager.settings.LogViewerPreferences
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.dialog.TextInputDialogBuilder
import io.github.muntashirakon.dialog.TextInputDropdownDialogBuilder
import java.util.*

// Copyright 2012 Nolan Lawson
// Copyright 2021 Muntashir Al-Islam
class RecordLogDialogFragment : DialogFragment() {
    interface OnRecordingServiceStartedListenerInterface {
        fun onServiceStarted()
    }

    private var mActivity: FragmentActivity? = null
    private var mFilterQuery: String? = null
    private var mLogLevel: Int = 0
    private var mListener: OnRecordingServiceStartedListenerInterface? = null
    private var mDismissListener: DialogInterface.OnDismissListener? = null

    fun setOnDismissListener(dismissListener: DialogInterface.OnDismissListener?) {
        mDismissListener = dismissListener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mActivity = requireActivity()
        val suggestions = requireArguments().getStringArray(QUERY_SUGGESTIONS)
        val logFilename = SaveLogHelper.createLogFilename()
        mLogLevel = Prefs.LogViewer.getLogLevel()
        mFilterQuery = ""\nreturn TextInputDialogBuilder(mActivity, R.string.enter_filename)
            .setTitle(R.string.record_log)
            .setInputText(logFilename)
            .setPositiveButton(R.string.record) { _, _, filename, _ ->
                if (SaveLogHelper.isInvalidFilename(filename)) {
                    UIUtils.displayShortToast(R.string.enter_good_filename)
                } else {
                    val loader = LogcatReaderLoader.create(true)
                    LogcatRecordingService.startService(
                        mActivity!!,
                        filename.toString(),
                        loader,
                        mFilterQuery,
                        mLogLevel
                    )
                    mListener?.onServiceStarted()
                    dismiss()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.more_options) { dialog, _ ->
                dialog.dismiss()
                showAdvancedOptions(logFilename, suggestions)
            }
            .create()
    }

    private fun showAdvancedOptions(filename: String, suggestions: Array<String>?) {
        val filterQueryBuilder = TextInputDialogBuilder(mActivity, R.string.logcat_filter)
            .setTitle(R.string.advanced_options)
            .setInputText(mFilterQuery)
            .setInputEditTextOnClickListener {
                if (suggestions == null || suggestions.isEmpty()) return@setInputEditTextOnClickListener
                TextInputDropdownDialogBuilder(
                    mActivity,
                    Arrays.asList(*suggestions),
                    { s ->
                        filterQueryBuilder.setInputText(s)
                    })
                    .show()
            }
            .setNeutralButton(R.string.log_level) { _, _, currentFilter, _ ->
                showLogLevelDialog(currentFilter.toString(), filename)
            }
            .setPositiveButton(R.string.record) { _, _, currentFilter, _ ->
                mFilterQuery = currentFilter.toString()
                val loader = LogcatReaderLoader.create(true)
                LogcatRecordingService.startService(mActivity!!, filename, loader, mFilterQuery, mLogLevel)
                mListener?.onServiceStarted()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showLogLevelDialog(filterQuery: String, filename: String) {
        val logLevels = mActivity!!.resources.getStringArray(R.array.log_levels)
        val selectedLogLevel = LogLine.convertLogLevelToChar(mLogLevel).code - 'A'.code
        MaterialAlertDialogBuilder(mActivity!!)
            .setTitle(R.string.log_level)
            .setSingleChoiceItems(logLevels, selectedLogLevel) { _, which ->
                mLogLevel = LogLine.convertCharToLogLevel(logLevels[which][0])
            }
            .setPositiveButton(R.string.ok) { _, _ ->
                val loader = LogcatReaderLoader.create(true)
                LogcatRecordingService.startService(mActivity!!, filename, loader, filterQuery, mLogLevel)
                mListener?.onServiceStarted()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        mDismissListener?.onDismiss(dialog)
    }

    override fun onResume() {
        super.onResume()
        // If service is running, show stop recording dialog
        if (ServiceHelper.isServiceRunning(mActivity!!)) {
            val alertDialog = MaterialAlertDialogBuilder(mActivity!!)
                .setTitle(R.string.logcat_recorder_already_running)
                .setMessage(R.string.logcat_recorder_already_running_message)
                .setNeutralButton(R.string.cancel) { _, _ -> dismiss() }
                .setPositiveButton(R.string.stop_recording) { _, _ ->
                    stopRecordingService()
                    dismiss()
                }
                .create()
            alertDialog.show()
            alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                alertDialog.dismiss()
                dismiss()
            }
        }
    }

    private fun stopRecordingService() {
        if (!ServiceHelper.isServiceRunning(mActivity!!)) return
        val serviceIntent = LogcatRecordingService.getServiceIntent(mActivity!!)
        mActivity!!.stopService(serviceIntent)
        ThreadUtils.postOnBackgroundThread { WidgetHelper.updateWidgets(mActivity!!) }
    }

    companion object {
        val TAG: String = RecordLogDialogFragment::class.java.simpleName
        private const val QUERY_SUGGESTIONS = "suggestions"

        @JvmStatic
        fun getInstance(
            suggestions: Array<String>?,
            listener: OnRecordingServiceStartedListenerInterface?
        ): RecordLogDialogFragment {
            val dialogFragment = RecordLogDialogFragment()
            val args = Bundle()
            args.putStringArray(QUERY_SUGGESTIONS, suggestions)
            dialogFragment.arguments = args
            dialogFragment.mListener = listener
            return dialogFragment
        }
    }
}
