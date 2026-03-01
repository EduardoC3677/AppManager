// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.transition.MaterialSharedAxis
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.crypto.auth.AuthManagerActivity
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.session.SessionMonitoringService

class PrivacyPreferences : PreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_privacy, rootKey)
        preferenceManager.preferenceDataStore = SettingsDataStore()
        val isScreenLockEnabled = Prefs.Privacy.isScreenLockEnabled()
        val isPersistentSessionEnabled = Prefs.Privacy.isPersistentSessionAllowed()
        // Auto lock
        val autoLock = requirePreference<SwitchPreferenceCompat>("enable_auto_lock")
        autoLock.isVisible = isScreenLockEnabled && isPersistentSessionEnabled
        autoLock.isChecked = Prefs.Privacy.isAutoLockEnabled()
        autoLock.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
            val enabled = newValue as Boolean
            restartServiceIfNeeded(null, enabled, null)
            true
        }
        // Screen lock
        val screenLock = requirePreference<SwitchPreferenceCompat>("enable_screen_lock")
        screenLock.isChecked = isScreenLockEnabled
        screenLock.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
            val enabled = newValue as Boolean
            // Auto lock pref has to be updated depending on this
            if (enabled) {
                autoLock.isVisible = Prefs.Privacy.isPersistentSessionAllowed()
            } else {
                autoLock.isVisible = false
            }
            restartServiceIfNeeded(enabled, null, null)
            true
        }
        // Persistent session
        val persistentSession = requirePreference<SwitchPreferenceCompat>("enable_persistent_session")
        persistentSession.isChecked = isPersistentSessionEnabled
        persistentSession.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
            val enabled = newValue as Boolean
            // Auto lock pref has to be updated depending on this
            if (enabled) {
                autoLock.isVisible = Prefs.Privacy.isScreenLockEnabled()
            } else {
                autoLock.isVisible = false
            }
            restartServiceIfNeeded(null, null, enabled)
            true
        }
        // Toggle Internet
        val toggleInternet = requirePreference<SwitchPreferenceCompat>("toggle_internet")
        toggleInternet.isEnabled = SelfPermissions.checkSelfPermission(Manifest.permission.INTERNET)
        toggleInternet.isChecked = FeatureController.isInternetEnabled()
        toggleInternet.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
            val isEnabled = newValue as Boolean
            FeatureController.getInstance().modifyState(FeatureController.FEAT_INTERNET, isEnabled)
            true
        }
        // Authorization Management
        requirePreference<Preference>("auth_manager").setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), AuthManagerActivity::class.java))
            true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)
    }

    override fun getTitle(): Int {
        return R.string.pref_privacy
    }

    fun restartServiceIfNeeded(
        screenLockEnabled: Boolean?, autoLockEnabled: Boolean?,
        persistentSessionEnabled: Boolean?
    ) {
        if (screenLockEnabled == null && autoLockEnabled == null && persistentSessionEnabled == null) {
            // Nothing is set
            return
        }
        val service = Intent(requireContext(), SessionMonitoringService::class.java)
        if (persistentSessionEnabled == false) {
            // Stop background session
            requireContext().stopService(service)
            return
        }
        if (persistentSessionEnabled == true) {
            // Start background session
            ContextCompat.startForegroundService(requireContext(), service)
            return
        }
        val isPersistentSessionAllowed = Prefs.Privacy.isPersistentSessionAllowed()
        if (!isPersistentSessionAllowed) {
            // Session not enabled and not running
            return
        }
        // Session enabled
        if (autoLockEnabled != null || screenLockEnabled != null) {
            // Auto lock preference has changed, restart service
            requireContext().stopService(service)
            ContextCompat.startForegroundService(requireContext(), service)
        }
    }
}
