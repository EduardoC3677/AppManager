// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.CallSuper
import androidx.annotation.StringRes
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.recyclerview.widget.RecyclerView
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.util.UiUtils

abstract class PreferenceFragment : PreferenceFragmentCompat() {
    companion object {
        const val PREF_KEY = "key"
        const val PREF_SECONDARY = "secondary"
    }

    private var mPrefKey: String? = null

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ENHANCEMENT: Add haptic feedback to all preferences
        setupHaptics(preferenceScreen)

        var secondary = false
        val args = arguments
        if (args != null) {
            mPrefKey = args.getString(PREF_KEY)
            secondary = args.getBoolean(PREF_SECONDARY)
            args.remove(PREF_KEY)
            args.remove(PREF_SECONDARY)
        }
        // https://github.com/androidx/androidx/blob/androidx-main/preference/preference/res/layout/preference_recyclerview.xml
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view)
        recyclerView.isFitsSystemWindows = true
        recyclerView.clipToPadding = false
        if (secondary) {
            if (this is MainPreferences) {
                UiUtils.applyWindowInsetsAsPadding(recyclerView, false, true, true, false)
            } else {
                UiUtils.applyWindowInsetsAsPadding(recyclerView, false, true, false, true)
            }
        } else {
            UiUtils.applyWindowInsetsAsPaddingNoTop(recyclerView)
        }
    }

    @CallSuper
    override fun onStart() {
        requireActivity().setTitle(getTitle())
        super.onStart()
        updateUi()
    }

    @StringRes
    abstract fun getTitle(): Int

    fun setPrefKey(prefKey: String?) {
        mPrefKey = prefKey
        updateUi()
    }

    private fun setupHaptics(group: PreferenceGroup?) {
        if (group == null) return
        for (i in 0 until group.preferenceCount) {
            val pref = group.getPreference(i)
            if (pref is PreferenceGroup) {
                setupHaptics(pref)
            } else {
                // Haptics for change
                val originalChangeListener = pref.onPreferenceChangeListener
                pref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
                    triggerHaptics()
                    originalChangeListener == null || originalChangeListener.onPreferenceChange(preference, newValue)
                }

                // Haptics for click
                val originalClickListener = pref.onPreferenceClickListener
                pref.onPreferenceClickListener = Preference.OnPreferenceClickListener { preference ->
                    triggerHaptics()
                    originalClickListener != null && originalClickListener.onPreferenceClick(preference)
                }
            }
        }
    }

    private fun triggerHaptics() {
        if (view != null) {
            var feedbackConstant = HapticFeedbackConstants.KEYBOARD_TAP
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                feedbackConstant = HapticFeedbackConstants.CONFIRM
            }
            view!!.performHapticFeedback(feedbackConstant)
        }
    }

    fun <T : Preference> requirePreference(key: CharSequence): T {
        return findPreference<T>(key)!!
    }

    protected fun enablePrefs(enable: Boolean, vararg prefs: Preference?) {
        for (pref in prefs) {
            pref?.isEnabled = enable
        }
    }

    @SuppressLint("RestrictedApi")
    private fun updateUi() {
        if (mPrefKey != null) {
            val prefToNavigate = findPreference<Preference>(mPrefKey!!)
            if (prefToNavigate != null) {
                scrollToPreference(prefToNavigate)
                if (prefToNavigate.fragment != null) {
                    prefToNavigate.performClick()
                }
            }
        }
    }
}
