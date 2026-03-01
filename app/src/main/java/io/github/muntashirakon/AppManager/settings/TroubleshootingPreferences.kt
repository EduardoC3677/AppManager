// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.preference.Preference
import com.google.android.material.transition.MaterialSharedAxis
import io.github.muntashirakon.AppManager.R

class TroubleshootingPreferences : PreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_troubleshooting, rootKey)
        preferenceManager.preferenceDataStore = SettingsDataStore()
        val model = ViewModelProvider(requireActivity())[MainPreferencesViewModel::class.java]
        // Reload apps
        findPreference<Preference>("reload_apps")!!
            .setOnPreferenceClickListener {
                model.reloadApps()
                true
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)
    }

    override fun getTitle(): Int {
        return R.string.troubleshooting
    }
}
