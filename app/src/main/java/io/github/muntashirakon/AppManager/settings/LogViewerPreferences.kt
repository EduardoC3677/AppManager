// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.fragment.app.FragmentActivity
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.transition.MaterialSharedAxis
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.logcat.helper.LogcatHelper
import io.github.muntashirakon.AppManager.logcat.helper.PreferenceHelper
import io.github.muntashirakon.AppManager.logcat.struct.LogLine
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder
import io.github.muntashirakon.dialog.TextInputDialogBuilder
import io.github.muntashirakon.preference.TopSwitchPreference

class LogViewerPreferences : PreferenceFragment() {
    companion object {
        @JvmField
        val LOG_LEVEL_VALUES = listOf(
            Log.VERBOSE, Log.DEBUG, Log.INFO, Log.WARN,
            Log.ERROR, LogLine.LOG_FATAL
        )

        @JvmField
        val LOG_BUFFER_NAMES = listOf<CharSequence>(
            LogcatHelper.BUFFER_MAIN, LogcatHelper.BUFFER_RADIO,
            LogcatHelper.BUFFER_EVENTS, LogcatHelper.BUFFER_SYSTEM, LogcatHelper.BUFFER_CRASH
        )

        @JvmField
        val LOG_BUFFERS = listOf(
            LogcatHelper.LOG_ID_MAIN, LogcatHelper.LOG_ID_RADIO,
            LogcatHelper.LOG_ID_EVENTS, LogcatHelper.LOG_ID_SYSTEM, LogcatHelper.LOG_ID_CRASH
        )

        private const val MAX_LOG_WRITE_PERIOD = 1000
        private const val MIN_LOG_WRITE_PERIOD = 1
        private const val MAX_DISPLAY_LIMIT = 100000
        private const val MIN_DISPLAY_LIMIT = 1000

        @JvmStatic
        fun sendBufferChanged(activity: FragmentActivity) {
            val intent = Intent().putExtra("bufferChanged", true)
            activity.setResult(Activity.RESULT_FIRST_USER, intent)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_log_viewer)
        preferenceManager.preferenceDataStore = SettingsDataStore()

        val activity = requireActivity()
        val isLogViewerEnabled = FeatureController.isLogViewerEnabled()
        val catAppearance = requirePreference<PreferenceCategory>("cat_appearance")
        val catConf = requirePreference<PreferenceCategory>("cat_conf")
        val catAdvanced = requirePreference<PreferenceCategory>("cat_advanced")
        val useLogViewer = requirePreference<TopSwitchPreference>("use_log_viewer")
        useLogViewer.isChecked = isLogViewerEnabled
        useLogViewer.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
            val isEnabled = newValue as Boolean
            enablePrefs(isEnabled, catAppearance, catAdvanced, catConf)
            FeatureController.getInstance().modifyState(FeatureController.FEAT_LOG_VIEWER, isEnabled)
            true
        }
        enablePrefs(isLogViewerEnabled, catAppearance, catAdvanced, catConf)

        val expandByDefault = requirePreference<SwitchPreferenceCompat>("log_viewer_expand_by_default")
        expandByDefault.isChecked = Prefs.LogViewer.expandByDefault()

        val showPidTidTimestamp = requirePreference<SwitchPreferenceCompat>("log_viewer_show_pid_tid_timestamp")
        showPidTidTimestamp.isChecked = Prefs.LogViewer.showPidTidTimestamp()

        val omitSensitiveInfo = requirePreference<SwitchPreferenceCompat>("log_viewer_omit_sensitive_info")
        omitSensitiveInfo.isChecked = Prefs.LogViewer.omitSensitiveInfo()
        omitSensitiveInfo.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
            LogLine.omitSensitiveInfo = newValue as Boolean
            true
        }

        val filterPattern = requirePreference<Preference>("log_viewer_filter_pattern")
        filterPattern.setOnPreferenceClickListener {
            TextInputDialogBuilder(activity, null)
                .setTitle(R.string.pref_filter_pattern_title)
                .setInputText(Prefs.LogViewer.getFilterPattern())
                .setInputTypeface(Typeface.MONOSPACE)
                .setInputImeOptions(EditorInfo.IME_ACTION_DONE or EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING)
                .setPositiveButton(R.string.save) { dialog, which, inputText, isChecked ->
                    if (inputText == null) return@setPositiveButton
                    Prefs.LogViewer.setFilterPattern(inputText.toString().trim())
                    UIUtils.displayLongToast(R.string.restart_log_viewer_to_see_changes)
                }
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.reset_to_default) { dialog, which, inputText, isChecked ->
                    Prefs.LogViewer.setFilterPattern(activity.getString(R.string.pref_filter_pattern_default))
                    UIUtils.displayLongToast(R.string.restart_log_viewer_to_see_changes)
                }
                .show()
            true
        }

        val displayLimit = requirePreference<Preference>("log_viewer_display_limit")
        displayLimit.summary = getString(R.string.pref_display_limit_summary, Prefs.LogViewer.getDisplayLimit())
        displayLimit.setOnPreferenceClickListener {
            TextInputDialogBuilder(activity, null)
                .setTitle(R.string.pref_display_limit_title)
                .setHelperText(getString(R.string.pref_display_limit_hint, MIN_DISPLAY_LIMIT, MAX_DISPLAY_LIMIT))
                .setInputText(Prefs.LogViewer.getDisplayLimit().toString())
                .setInputInputType(InputType.TYPE_CLASS_NUMBER)
                .setInputImeOptions(EditorInfo.IME_ACTION_DONE or EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING)
                .setPositiveButton(R.string.save) { dialog, which, inputText, isChecked ->
                    if (inputText == null) return@setPositiveButton
                    try {
                        val displayLimitInt = inputText.toString().trim().toInt()
                        if (displayLimitInt in MIN_DISPLAY_LIMIT..MAX_DISPLAY_LIMIT) {
                            Prefs.LogViewer.setDisplayLimit(displayLimitInt)
                            UIUtils.displayLongToast(R.string.restart_log_viewer_to_see_changes)
                            displayLimit.summary = getString(R.string.pref_display_limit_summary, displayLimitInt)
                        }
                    } catch (ignore: NumberFormatException) {
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.reset_to_default) { dialog, which, inputText, isChecked ->
                    Prefs.LogViewer.setDisplayLimit(LogcatHelper.DEFAULT_DISPLAY_LIMIT)
                    UIUtils.displayLongToast(R.string.restart_log_viewer_to_see_changes)
                    displayLimit.summary = getString(
                        R.string.pref_display_limit_summary,
                        Prefs.LogViewer.getDisplayLimit()
                    )
                }
                .show()
            true
        }

        val writePeriod = requirePreference<Preference>("log_viewer_write_period")
        writePeriod.summary = getString(R.string.pref_log_write_period_summary, Prefs.LogViewer.getLogWritingInterval())
        writePeriod.setOnPreferenceClickListener {
            TextInputDialogBuilder(activity, null)
                .setTitle(R.string.pref_log_write_period_title)
                .setHelperText(getString(R.string.pref_log_line_period_error))
                .setInputText(Prefs.LogViewer.getLogWritingInterval().toString())
                .setInputInputType(InputType.TYPE_CLASS_NUMBER)
                .setInputImeOptions(EditorInfo.IME_ACTION_DONE or EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING)
                .setPositiveButton(R.string.save) { dialog, which, inputText, isChecked ->
                    if (inputText == null) return@setPositiveButton
                    try {
                        val writePeriodInt = inputText.toString().trim().toInt()
                        if (writePeriodInt in MIN_LOG_WRITE_PERIOD..MAX_LOG_WRITE_PERIOD) {
                            Prefs.LogViewer.setLogWritingInterval(writePeriodInt)
                            UIUtils.displayLongToast(R.string.restart_log_viewer_to_see_changes)
                            writePeriod.summary = getString(R.string.pref_log_write_period_summary, writePeriodInt)
                        }
                    } catch (ignore: NumberFormatException) {
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.reset_to_default) { dialog, which, inputText, isChecked ->
                    Prefs.LogViewer.setLogWritingInterval(LogcatHelper.DEFAULT_LOG_WRITE_INTERVAL)
                    UIUtils.displayLongToast(R.string.restart_log_viewer_to_see_changes)
                    writePeriod.summary = getString(
                        R.string.pref_log_write_period_summary,
                        Prefs.LogViewer.getLogWritingInterval()
                    )
                }
                .show()
            true
        }

        val logLevel = requirePreference<Preference>("log_viewer_default_log_level")
        logLevel.setOnPreferenceClickListener {
            val logLevelsLocalised = resources.getStringArray(R.array.log_levels)
            SearchableSingleChoiceDialogBuilder(activity, LOG_LEVEL_VALUES, logLevelsLocalised)
                .setTitle(R.string.pref_default_log_level_title)
                .setSelection(Prefs.LogViewer.getLogLevel())
                .setPositiveButton(R.string.save) { dialog, which, newLogLevel ->
                    if (newLogLevel != null) {
                        Prefs.LogViewer.setLogLevel(newLogLevel)
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.reset_to_default) { dialog, which, newLogLevel -> Prefs.LogViewer.setLogLevel(Log.VERBOSE) }
                .show()
            true
        }

        val logBuffers = requirePreference<Preference>("log_viewer_buffer")
        logBuffers.setOnPreferenceClickListener {
            SearchableMultiChoiceDialogBuilder(activity, LOG_BUFFERS, LOG_BUFFER_NAMES)
                .setTitle(R.string.pref_buffer_title)
                .addSelections(PreferenceHelper.getBuffers())
                .setPositiveButton(R.string.save) { dialog, which, selectedItems ->
                    if (selectedItems.isEmpty()) return@setPositiveButton
                    var bufferFlags = 0
                    for (flag in selectedItems) {
                        bufferFlags = bufferFlags or flag
                    }
                    val previousFlags = Prefs.LogViewer.getBuffers()
                    Prefs.LogViewer.setBuffers(bufferFlags)
                    if (previousFlags != bufferFlags) {
                        sendBufferChanged(activity)
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.reset_to_default) { dialog, which, selectedItems ->
                    val previousFlags = Prefs.LogViewer.getBuffers()
                    Prefs.LogViewer.setBuffers(LogcatHelper.LOG_ID_DEFAULT)
                    if (previousFlags != LogcatHelper.LOG_ID_DEFAULT) {
                        sendBufferChanged(activity)
                    }
                }
                .show()
            true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)
    }

    override fun getTitle(): Int {
        return R.string.log_viewer
    }
}
