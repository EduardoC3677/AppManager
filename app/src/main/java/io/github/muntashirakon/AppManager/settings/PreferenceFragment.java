// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.util.UiUtils;

public abstract class PreferenceFragment extends PreferenceFragmentCompat {
    public static final String PREF_KEY = "key";
    public static final String PREF_SECONDARY = "secondary";

    @Nullable
    private String mPrefKey;

    @CallSuper
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ENHANCEMENT: Add haptic feedback to all preferences
        setupHaptics(getPreferenceScreen());

        boolean secondary = false;
        if (getArguments() != null) {
            mPrefKey = requireArguments().getString(PREF_KEY);
            secondary = requireArguments().getBoolean(PREF_SECONDARY);
            requireArguments().remove(PREF_KEY);
            requireArguments().remove(PREF_SECONDARY);
        }
        // https://github.com/androidx/androidx/blob/androidx-main/preference/preference/res/layout/preference_recyclerview.xml
        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setFitsSystemWindows(true);
        recyclerView.setClipToPadding(false);
        if (secondary) {
            if (this instanceof MainPreferences) {
                UiUtils.applyWindowInsetsAsPadding(recyclerView, false, true, true, false);
            } else {
                UiUtils.applyWindowInsetsAsPadding(recyclerView, false, true, false, true);
            }
        } else UiUtils.applyWindowInsetsAsPaddingNoTop(recyclerView);
    }

    @CallSuper
    @Override
    public void onStart() {
        requireActivity().setTitle(getTitle());
        super.onStart();
        updateUi();
    }

    @StringRes
    public abstract int getTitle();

    public void setPrefKey(@Nullable String prefKey) {
        mPrefKey = prefKey;
        updateUi();
    }

    private void setupHaptics(androidx.preference.PreferenceGroup group) {
        if (group == null) return;
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            androidx.preference.Preference pref = group.getPreference(i);
            if (pref instanceof androidx.preference.PreferenceGroup) {
                setupHaptics((androidx.preference.PreferenceGroup) pref);
            } else {
                // Haptics for change
                androidx.preference.Preference.OnPreferenceChangeListener originalChangeListener = pref.getOnPreferenceChangeListener();
                pref.setOnPreferenceChangeListener((preference, newValue) -> {
                    triggerHaptics();
                    return originalChangeListener == null || originalChangeListener.onPreferenceChange(preference, newValue);
                });

                // Haptics for click
                androidx.preference.Preference.OnPreferenceClickListener originalClickListener = pref.getOnPreferenceClickListener();
                pref.setOnPreferenceClickListener(preference -> {
                    triggerHaptics();
                    return originalClickListener != null && originalClickListener.onPreferenceClick(preference);
                });
            }
        }
    }

    private void triggerHaptics() {
        if (getView() != null) {
            int feedbackConstant = android.view.HapticFeedbackConstants.KEYBOARD_TAP;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                feedbackConstant = android.view.HapticFeedbackConstants.CONFIRM;
            }
            getView().performHapticFeedback(feedbackConstant);
        }
    }

    public <T extends androidx.preference.Preference> T requirePreference(CharSequence key) {
        return Objects.requireNonNull(findPreference(key));
    }

    protected void enablePrefs(boolean enable, Preference ...prefs) {
        if (prefs == null) {
            return;
        }
        for (Preference pref : prefs) {
            pref.setEnabled(enable);
        }
    }

    @SuppressLint("RestrictedApi")
    private void updateUi() {
        if (mPrefKey != null) {
            Preference prefToNavigate = findPreference(mPrefKey);
            if (prefToNavigate != null) {
                scrollToPreference(prefToNavigate);
                if (prefToNavigate.getFragment() != null) {
                    prefToNavigate.performClick();
                }
            }
        }
    }
}
