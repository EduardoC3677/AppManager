// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings

import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.inputmethod.EditorInfo
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.lifecycle.ViewModelProvider
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.dialog.TextInputDialogBuilder
import io.github.muntashirakon.preference.DefaultAlertPreference
import io.github.muntashirakon.preference.TopSwitchPreference

class VirusTotalPreferences : PreferenceFragment() {
    private lateinit var mModel: MainPreferencesViewModel

    override fun getTitle(): Int {
        return R.string.virus_total
    }

    override fun onCreatePreferences(bundle: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_virus_total, rootKey)
        preferenceManager.preferenceDataStore = SettingsDataStore()
        mModel = ViewModelProvider(requireActivity())[MainPreferencesViewModel::class.java]
        val hasInternet = FeatureController.isInternetEnabled()
        val isVtEnabled = FeatureController.isVirusTotalEnabled()
        val apiKey = Prefs.VirusTotal.getApiKey()
        val useVtPref = requirePreference<TopSwitchPreference>("use_vt")
        val infoNoInternetPref = requirePreference<DefaultAlertPreference>("info_no_internet")
        val vtApiKeyPref = requirePreference<Preference>("virus_total_api_key")
        val promptBeforeUploadPref = requirePreference<SwitchPreferenceCompat>("virus_total_prompt_before_uploading")
        val infoPref = requirePreference<DefaultAlertPreference>("info")
        // Set values
        useVtPref.isEnabled = hasInternet
        useVtPref.isChecked = isVtEnabled
        useVtPref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
            val isEnabled = newValue as Boolean
            enablePrefs(isEnabled, vtApiKeyPref, promptBeforeUploadPref)
            FeatureController.getInstance().modifyState(FeatureController.FEAT_VIRUS_TOTAL, isEnabled)
            true
        }
        infoNoInternetPref.isVisible = !hasInternet
        enablePrefs(isVtEnabled, vtApiKeyPref, promptBeforeUploadPref)
        if (apiKey != null) {
            vtApiKeyPref.summary = apiKey
        } else {
            vtApiKeyPref.setSummary(R.string.key_not_set)
        }
        vtApiKeyPref.setOnPreferenceClickListener {
            TextInputDialogBuilder(requireContext(), null)
                .setTitle(R.string.pref_vt_apikey)
                .setInputText(Prefs.VirusTotal.getApiKey())
                .setInputTypeface(Typeface.MONOSPACE)
                .setInputInputType(InputType.TYPE_CLASS_TEXT)
                .setInputImeOptions(EditorInfo.IME_ACTION_DONE or EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save) { dialog, which, inputText, isChecked ->
                    val newApiKey = if (!TextUtils.isEmpty(inputText)) inputText.toString() else null
                    Prefs.VirusTotal.setApiKey(newApiKey)
                    if (newApiKey != null) {
                        vtApiKeyPref.summary = newApiKey
                    } else {
                        vtApiKeyPref.setSummary(R.string.key_not_set)
                    }
                }
                .show()
            true
        }
        promptBeforeUploadPref.isChecked = Prefs.VirusTotal.promptBeforeUpload()
        infoPref.summary = getString(R.string.pref_vt_apikey_description) + "

" + getString(R.string.vt_disclaimer)
    }
}
