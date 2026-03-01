// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.transition.MaterialSharedAxis
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.utils.appearance.AppearanceUtils
import io.github.muntashirakon.AppManager.utils.appearance.TypefaceUtil
import io.github.muntashirakon.dialog.SearchableFlagsDialogBuilder
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder

class AppearancePreferences : PreferenceFragment() {
    companion object {
        private val THEME_CONST = listOf(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES
        )
        private val LAYOUT_ORIENTATION_CONST = listOf(
            View.LAYOUT_DIRECTION_LOCALE,
            View.LAYOUT_DIRECTION_LTR,
            View.LAYOUT_DIRECTION_RTL
        )
    }

    private var mCurrentTheme = 0
    private var mCurrentLayoutDirection = 0

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_appearance, rootKey)
        preferenceManager.preferenceDataStore = SettingsDataStore()
        // App theme
        val themes = resources.getStringArray(R.array.themes)
        mCurrentTheme = Prefs.Appearance.getNightMode()
        val appTheme = findPreference<Preference>("app_theme")!!
        appTheme.summary = themes[THEME_CONST.indexOf(mCurrentTheme)]
        appTheme.setOnPreferenceClickListener {
            SearchableSingleChoiceDialogBuilder(requireActivity(), THEME_CONST, themes)
                .setTitle(R.string.select_theme)
                .setSelection(mCurrentTheme)
                .setPositiveButton(R.string.apply) { dialog, which, selectedTheme ->
                    if (selectedTheme != null && selectedTheme != mCurrentTheme) {
                        mCurrentTheme = selectedTheme
                        Prefs.Appearance.setNightMode(mCurrentTheme)
                        AppCompatDelegate.setDefaultNightMode(mCurrentTheme)
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            true
        }
        // Black theme/custom theme
        val fullBlackTheme = findPreference<SwitchPreferenceCompat>("app_theme_pure_black")!!
        fullBlackTheme.isChecked = Prefs.Appearance.isPureBlackTheme()
        fullBlackTheme.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
            val enabled = newValue as Boolean
            Prefs.Appearance.setPureBlackTheme(enabled)
            AppearanceUtils.applyConfigurationChangesToActivities()
            true
        }
        // Black theme/custom theme
        val useSystemFontPref = findPreference<SwitchPreferenceCompat>("use_system_font")!!
        useSystemFontPref.isChecked = Prefs.Appearance.useSystemFont()
        useSystemFontPref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
            if (newValue as Boolean) {
                // Enable system font
                TypefaceUtil.replaceFontsWithSystem(requireContext())
            } else {
                // Disable system font
                TypefaceUtil.restoreFonts()
            }
            AppearanceUtils.applyConfigurationChangesToActivities()
            true
        }
        // Layout orientation
        val layoutOrientations = resources.getStringArray(R.array.layout_orientations)
        mCurrentLayoutDirection = Prefs.Appearance.getLayoutDirection()
        val layoutOrientation = findPreference<Preference>("layout_orientation")!!
        layoutOrientation.summary = layoutOrientations[LAYOUT_ORIENTATION_CONST.indexOf(mCurrentLayoutDirection)]
        layoutOrientation.setOnPreferenceClickListener {
            SearchableSingleChoiceDialogBuilder(requireActivity(), LAYOUT_ORIENTATION_CONST, layoutOrientations)
                .setTitle(R.string.pref_layout_direction)
                .setSelection(mCurrentLayoutDirection)
                .setPositiveButton(R.string.apply) { dialog, which, selectedLayoutOrientation ->
                    mCurrentLayoutDirection = selectedLayoutOrientation!!
                    Prefs.Appearance.setLayoutDirection(mCurrentLayoutDirection)
                    AppearanceUtils.applyConfigurationChangesToActivities()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            true
        }
        // Enable/disable features
        val fc = FeatureController.getInstance()
        findPreference<Preference>("enabled_features")!!
            .setOnPreferenceClickListener {
                SearchableFlagsDialogBuilder(requireActivity(), FeatureController.featureFlags, FeatureController.getFormattedFlagNames(requireActivity()), fc.flags)
                    .setTitle(R.string.enable_disable_features)
                    .setOnMultiChoiceClickListener { dialog, which, item, isChecked ->
                        fc.modifyState(FeatureController.featureFlags[which], isChecked)
                    }
                    .setNegativeButton(R.string.close, null)
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
        return R.string.pref_cat_appearance
    }
}
