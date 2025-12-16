// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.self.life.BuildExpiryChecker;
import io.github.muntashirakon.AppManager.self.life.FundingCampaignChecker;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.preference.InfoAlertPreference;
import io.github.muntashirakon.preference.WarningAlertPreference;
import io.github.muntashirakon.AppManager.logs.Log; // Add import for Log

public class MainPreferences extends PreferenceFragment {
    public static final String TAG = MainPreferences.class.getSimpleName(); // Add TAG constant
    @NonNull
    public static MainPreferences getInstance(@Nullable String key, boolean dualPane) {
        MainPreferences preferences = new MainPreferences();
        Bundle args = new Bundle();
        args.putString(PREF_KEY, key);
        args.putBoolean(PREF_SECONDARY, dualPane);
        preferences.setArguments(args);
        return preferences;
    }

    private static final List<String> MODE_NAMES = Arrays.asList(
            Ops.MODE_AUTO,
            Ops.MODE_ROOT,
            Ops.MODE_ADB_OVER_TCP,
            Ops.MODE_ADB_WIFI,
            Ops.MODE_NO_ROOT);

    private FragmentActivity mActivity;
    private Preference mModePref;
    private Preference mLocalePref;
    private String[] mModes;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Log.d(TAG, "onCreatePreferences: entry");
        setPreferencesFromResource(R.xml.preferences_main, rootKey);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());
        MainPreferencesViewModel model = new ViewModelProvider(requireActivity()).get(MainPreferencesViewModel.class);
        mActivity = requireActivity();
        // Expiry notice
        WarningAlertPreference buildExpiringNotice = requirePreference("app_manager_expiring_notice");
        buildExpiringNotice.setVisible(!Boolean.FALSE.equals(BuildExpiryChecker.buildExpired()));
        // Funding campaign notice
        InfoAlertPreference fundingCampaignNotice = requirePreference("funding_campaign_notice");
        fundingCampaignNotice.setVisible(FundingCampaignChecker.campaignRunning());
        // Custom locale
        mLocalePref = requirePreference("custom_locale");
        // Mode of operation
        mModePref = requirePreference("mode_of_operations");
        mModes = getResources().getStringArray(R.array.modes);

        model.getOperationCompletedLiveData().observe(requireActivity(), completed -> {
            if (requireActivity() instanceof SettingsActivity) {
                ((SettingsActivity) requireActivity()).progressIndicator.hide();
            }
            UIUtils.displayShortToast(R.string.the_operation_was_successful);
        });
        Log.d(TAG, "onCreatePreferences: exit");
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart: entry");
        // Load mode and locale asynchronously to avoid blocking main thread
        new Thread(() -> {
            Log.d(TAG, "onStart: new thread started");
            try {
                String mode = Ops.getMode();
                CharSequence inferredMode = Ops.getInferredMode(mActivity);
                int modeIndex = MODE_NAMES.indexOf(mode);

                requireActivity().runOnUiThread(() -> {
                    Log.d(TAG, "onStart: updating UI on main thread");
                    if (mModePref != null && isAdded()) {
                        mModePref.setSummary(getString(R.string.mode_of_op_with_inferred_mode_of_op,
                                mModes[modeIndex], inferredMode));
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "onStart: error in new thread", e);
                // Ignore errors during mode retrieval
            }
            Log.d(TAG, "onStart: new thread finished");
        }).start();

        if (mLocalePref != null) {
            mLocalePref.setSummary(getLanguageName());
        }
        Log.d(TAG, "onStart: exit");
    }

    @Override
    public int getTitle() {
        return R.string.settings;
    }

    public CharSequence getLanguageName() {
        String langTag = Prefs.Appearance.getLanguage();
        if (LangUtils.LANG_AUTO.equals(langTag)) {
            return getString(R.string.auto);
        }
        Locale locale = Locale.forLanguageTag(langTag);
        return locale.getDisplayName(locale);
    }
}
