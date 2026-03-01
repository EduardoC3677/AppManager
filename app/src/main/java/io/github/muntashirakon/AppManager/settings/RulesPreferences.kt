// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings

import android.os.Bundle
import android.text.SpannableStringBuilder
import androidx.lifecycle.ViewModelProvider
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialSharedAxis
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.rules.struct.ComponentRule
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.utils.ArrayUtils
import io.github.muntashirakon.AppManager.utils.FreezeUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.dialog.DialogTitleBuilder
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder
import java.util.concurrent.atomic.AtomicInteger

class RulesPreferences : PreferenceFragment() {
    companion object {
        private val BLOCKING_METHODS = arrayOf(
            ComponentRule.COMPONENT_TO_BE_BLOCKED_IFW_DISABLE,
            ComponentRule.COMPONENT_TO_BE_BLOCKED_IFW,
            ComponentRule.COMPONENT_TO_BE_DISABLED
        )

        private val BLOCKING_METHOD_TITLES = arrayOf(
            R.string.intent_firewall_and_disable,
            R.string.intent_firewall,
            R.string.disable
        )

        private val BLOCKING_METHOD_DESCRIPTIONS = arrayOf(
            R.string.pref_intent_firewall_and_disable_description,
            R.string.pref_intent_firewall_description,
            R.string.pref_disable_description
        )

        private val FREEZING_METHODS = arrayOf(
            FreezeUtils.FREEZE_SUSPEND,
            FreezeUtils.FREEZE_ADV_SUSPEND,
            FreezeUtils.FREEZE_DISABLE,
            FreezeUtils.FREEZE_HIDE
        )

        private val FREEZING_METHOD_TITLES = arrayOf(
            R.string.suspend_app,
            R.string.advanced_suspend_app,
            R.string.disable,
            R.string.hide_app
        )

        private val FREEZING_METHOD_DESCRIPTIONS = arrayOf(
            R.string.suspend_app_description,
            R.string.advanced_suspend_app_description,
            R.string.disable_app_description,
            R.string.hide_app_description
        )
    }

    private lateinit var mActivity: SettingsActivity

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_rules)
        preferenceManager.preferenceDataStore = SettingsDataStore()
        val model = ViewModelProvider(requireActivity())[MainPreferencesViewModel::class.java]
        mActivity = requireActivity() as SettingsActivity
        // Default freezing method
        val defaultFreezingMethod = findPreference<Preference>("freeze_type")!!
        val freezeTypeIdx = AtomicInteger(
            ArrayUtils.indexOf(
                FREEZING_METHODS,
                Prefs.Blocking.getDefaultFreezingMethod()
            )
        )
        if (freezeTypeIdx.get() != -1) {
            defaultFreezingMethod.summary = getString(FREEZING_METHOD_TITLES[freezeTypeIdx.get()])
        }
        defaultFreezingMethod.isEnabled = SelfPermissions.canFreezeUnfreezePackages()
        defaultFreezingMethod.setOnPreferenceClickListener {
            val itemDescription = Array<CharSequence>(FREEZING_METHODS.size) { i ->
                SpannableStringBuilder(getString(FREEZING_METHOD_TITLES[i])).append("
")
                    .append(UIUtils.getSmallerText(getString(FREEZING_METHOD_DESCRIPTIONS[i])))
            }
            SearchableSingleChoiceDialogBuilder(mActivity, FREEZING_METHODS, itemDescription)
                .setTitle(
                    DialogTitleBuilder(mActivity)
                        .setTitle(R.string.pref_default_freezing_method)
                        .setSubtitle(R.string.pref_default_freezing_method_description)
                        .build()
                )
                .setSelection(Prefs.Blocking.getDefaultFreezingMethod())
                .setOnSingleChoiceClickListener { dialog, which, selectedFreezingMethod, isChecked ->
                    if (!isChecked) {
                        return@setOnSingleChoiceClickListener
                    }
                    Prefs.Blocking.setDefaultFreezingMethod(selectedFreezingMethod!!)
                    defaultFreezingMethod.summary = getString(FREEZING_METHOD_TITLES[which])
                    freezeTypeIdx.set(which)
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.close, null)
                .show()
            true
        }
        // Default component blocking method
        val defaultBlockingMethod = findPreference<Preference>("default_blocking_method")!!
        // Disable this option if IFW folder can't be accessed
        defaultBlockingMethod.isEnabled = SelfPermissions.canBlockByIFW()
        val csIdx = ArrayUtils.indexOf(BLOCKING_METHODS, Prefs.Blocking.getDefaultBlockingMethod())
        if (csIdx != -1) {
            defaultBlockingMethod.summary = getString(BLOCKING_METHOD_TITLES[csIdx])
        }
        defaultBlockingMethod.setOnPreferenceClickListener {
            val itemDescription = Array<CharSequence>(BLOCKING_METHODS.size) { i ->
                SpannableStringBuilder(getString(BLOCKING_METHOD_TITLES[i])).append("
")
                    .append(UIUtils.getSmallerText(getString(BLOCKING_METHOD_DESCRIPTIONS[i])))
            }
            SearchableSingleChoiceDialogBuilder(mActivity, BLOCKING_METHODS, itemDescription)
                .setTitle(
                    DialogTitleBuilder(mActivity)
                        .setTitle(R.string.pref_default_blocking_method)
                        .setSubtitle(R.string.pref_default_blocking_method_description)
                        .build()
                )
                .setSelection(Prefs.Blocking.getDefaultBlockingMethod())
                .setOnSingleChoiceClickListener { dialog, which, selectedBlockingMethod, isChecked ->
                    if (!isChecked) {
                        return@setOnSingleChoiceClickListener
                    }
                    Prefs.Blocking.setDefaultBlockingMethod(selectedBlockingMethod!!)
                    defaultBlockingMethod.summary = getString(BLOCKING_METHOD_TITLES[which])
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.close, null)
                .show()
            true
        }
        // Global blocking enabled
        val gcb = findPreference<SwitchPreferenceCompat>("global_blocking_enabled")!!
        gcb.isChecked = Prefs.Blocking.globalBlockingEnabled()
        gcb.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, isEnabled ->
            if (isEnabled as Boolean) {
                model.applyAllRules()
            }
            true
        }
        // Remove all rules
        findPreference<Preference>("remove_all_rules")!!.setOnPreferenceClickListener {
            MaterialAlertDialogBuilder(mActivity)
                .setTitle(R.string.pref_remove_all_rules)
                .setMessage(getString(R.string.are_you_sure) + " " + getString(R.string.pref_remove_all_rules_msg))
                .setPositiveButton(R.string.yes) { dialog, which ->
                    mActivity.progressIndicator.show()
                    model.removeAllRules()
                }
                .setNegativeButton(R.string.no, null)
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
        return R.string.rules
    }
}
