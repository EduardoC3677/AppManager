// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.preference.Preference
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.self.life.BuildExpiryChecker
import io.github.muntashirakon.AppManager.self.life.FundingCampaignChecker
import io.github.muntashirakon.AppManager.utils.LangUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.preference.InfoAlertPreference
import io.github.muntashirakon.preference.WarningAlertPreference
import java.util.*

class MainPreferences : PreferenceFragment() {
    companion object {
        @JvmStatic
        fun getInstance(key: String?, dualPane: Boolean): MainPreferences {
            val preferences = MainPreferences()
            val args = Bundle()
            args.putString(PREF_KEY, key)
            args.putBoolean(PREF_SECONDARY, dualPane)
            preferences.arguments = args
            return preferences
        }

        private val MODE_NAMES = listOf(
            Ops.MODE_AUTO,
            Ops.MODE_ROOT,
            Ops.MODE_ADB_OVER_TCP,
            Ops.MODE_ADB_WIFI,
            Ops.MODE_NO_ROOT
        )
    }

    private lateinit var mActivity: FragmentActivity
    private var mModePref: Preference? = null
    private var mLocalePref: Preference? = null
    private lateinit var mModes: Array<String>

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_main, rootKey)
        preferenceManager.preferenceDataStore = SettingsDataStore()
        val model = ViewModelProvider(requireActivity())[MainPreferencesViewModel::class.java]
        mActivity = requireActivity()
        // Expiry notice
        val buildExpiringNotice = requirePreference<WarningAlertPreference>("app_manager_expiring_notice")
        buildExpiringNotice.isVisible = BuildExpiryChecker.buildExpired() != false
        // Funding campaign notice
        val fundingCampaignNotice = requirePreference<InfoAlertPreference>("funding_campaign_notice")
        fundingCampaignNotice.isVisible = FundingCampaignChecker.campaignRunning()
        // Custom locale
        mLocalePref = requirePreference("custom_locale")
        // Mode of operation
        mModePref = requirePreference("mode_of_operations")
        mModes = resources.getStringArray(R.array.modes)

        model.operationCompletedLiveData.observe(requireActivity()) { completed ->
            if (requireActivity() is SettingsActivity) {
                (requireActivity() as SettingsActivity).progressIndicator.hide()
            }
            UIUtils.displayShortToast(R.string.the_operation_was_successful)
        }
    }

    override fun onStart() {
        super.onStart()
        // Load mode and locale asynchronously to avoid blocking main thread
        Thread {
            try {
                val mode = Ops.getMode()
                val inferredMode = Ops.getInferredMode(mActivity)
                val modeIndex = MODE_NAMES.indexOf(mode)

                requireActivity().runOnUiThread {
                    if (mModePref != null && isAdded) {
                        mModePref!!.summary = getString(
                            R.string.mode_of_op_with_inferred_mode_of_op,
                            mModes[modeIndex], inferredMode
                        )
                    }
                }
            } catch (e: Exception) {
                // Ignore errors during mode retrieval
            }
        }.start()

        if (mLocalePref != null) {
            mLocalePref!!.summary = languageName
        }
    }

    override fun getTitle(): Int {
        return R.string.settings
    }

    val languageName: CharSequence
        get() {
            val langTag = Prefs.Appearance.getLanguage()
            if (LangUtils.LANG_AUTO == langTag) {
                return getString(R.string.auto)
            }
            val locale = Locale.forLanguageTag(langTag)
            return locale.getDisplayName(locale)
        }
}
