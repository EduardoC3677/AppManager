// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings

import android.content.ComponentName
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.inputmethod.EditorInfo
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.transition.MaterialSharedAxis
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.fm.FmActivity
import io.github.muntashirakon.AppManager.fm.FmUtils
import io.github.muntashirakon.dialog.TextInputDialogBuilder
import java.io.File

class FileManagerPreferences : PreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_file_manager, rootKey)
        preferenceManager.preferenceDataStore = SettingsDataStore()
        // Display in launcher
        val displayInLauncherPref = findPreference<SwitchPreferenceCompat>("fm_display_in_launcher")!!
        displayInLauncherPref.isChecked = Prefs.FileManager.displayInLauncher()
        displayInLauncherPref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
            val isChecked = newValue as Boolean
            val newState = if (isChecked) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            val componentName = ComponentName(BuildConfig.APPLICATION_ID, FmActivity.LAUNCHER_ALIAS)
            requireContext().packageManager.setComponentEnabledSetting(componentName, newState, PackageManager.DONT_KILL_APP)
            true
        }
        // Remember last opened path
        val filesRememberLastPathPref = findPreference<SwitchPreferenceCompat>("fm_remember_last_path")!!
        filesRememberLastPathPref.isChecked = Prefs.FileManager.isRememberLastOpenedPath()
        // Set home
        val setHomePrefs = findPreference<Preference>("fm_home")!!
        setHomePrefs.summary = FmUtils.getDisplayablePath(Prefs.FileManager.getHome())
        setHomePrefs.setOnPreferenceClickListener {
            TextInputDialogBuilder(requireContext(), null)
                .setTitle(R.string.pref_set_home)
                .setInputText(FmUtils.getDisplayablePath(Prefs.FileManager.getHome()))
                .setInputInputType(InputType.TYPE_CLASS_TEXT)
                .setInputImeOptions(EditorInfo.IME_ACTION_DONE or EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok) { dialog, which, inputText, isChecked ->
                    if (TextUtils.isEmpty(inputText)) {
                        return@setPositiveButton
                    }
                    val newHome = inputText.toString()
                    val uri: Uri = if (newHome.startsWith(File.separator)) {
                        Uri.Builder().scheme(ContentResolver.SCHEME_FILE).path(newHome).build()
                    } else {
                        Uri.parse(newHome)
                    }
                    Prefs.FileManager.setHome(uri)
                    setHomePrefs.summary = FmUtils.getDisplayablePath(uri)
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
        return R.string.files
    }
}
