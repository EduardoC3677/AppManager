// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.SelfUriManager;
import io.github.muntashirakon.util.UiUtils;

public class SettingsActivity extends BaseActivity implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    public static final String TAG = SettingsActivity.class.getSimpleName();

    private static final String SAVED_KEYS = "saved_keys";

    @NonNull
    public static Intent getSettingsIntent(@NonNull Context context, @Nullable String... paths) {
        Intent intent = new Intent(context, SettingsActivity.class);
        if (paths != null) {
            intent.setData(getSettingUri(paths));
        }
        return intent;
    }

    @NonNull
    private static Uri getSettingUri(@NonNull String... pathSegments) {
        Uri.Builder builder = new Uri.Builder()
                .scheme(SelfUriManager.APP_MANAGER_SCHEME)
                .authority(SelfUriManager.SETTINGS_HOST);
        for (String pathSegment : pathSegments) {
            builder.appendPath(pathSegment);
        }
        return builder.build();
    }

    public LinearProgressIndicator progressIndicator;
    @NonNull
    private List<String> mKeys = Collections.emptyList();
    @NonNull
    private ArrayList<String> mSavedKeys = new ArrayList<>();
    private int mLevel = 0;
    private boolean mDualPaneMode;
    @Nullable
    private MaterialToolbar mSecondaryToolbar;

    @Override
    protected void onAuthenticated(Bundle savedInstanceState) {
        Log.d(TAG, "onAuthenticated: entry");
        int mainPrefSize = UiUtils.dpToPx(this, 450);
        int windowWidth = getResources().getDisplayMetrics().widthPixels;
        mDualPaneMode = windowWidth >= 2 * mainPrefSize;
        Log.d(TAG, "onAuthenticated: mDualPaneMode = " + mDualPaneMode);
        setContentView(mDualPaneMode ? R.layout.activity_settings_dual_pane : R.layout.activity_settings);
        setSupportActionBar(findViewById(R.id.toolbar));
        mSecondaryToolbar = findViewById(R.id.toolbar2);
        FragmentContainerView secondaryContainer = findViewById(R.id.secondary_layout);
        progressIndicator = findViewById(R.id.progress_linear);
        progressIndicator.setVisibilityAfterHide(View.GONE);
        progressIndicator.hide();
        // Apply necessary padding: ignore start
        if (mSecondaryToolbar != null) {
            UiUtils.applyWindowInsetsAsPadding(mSecondaryToolbar, true, false, false, true);
        }
        if (secondaryContainer != null) {
            UiUtils.applyWindowInsetsAsPadding(secondaryContainer, false, true, false, true);
        }

        if (savedInstanceState != null) {
            Log.d(TAG, "onAuthenticated: savedInstanceState is not null");
            clearBackStack();
            ArrayList<String> savedKeys = savedInstanceState.getStringArrayList(SAVED_KEYS);
            if (savedKeys != null) {
                mSavedKeys = savedKeys;
            }
        }
        setKeysFromIntent(getIntent());

        getSupportFragmentManager().addFragmentOnAttachListener((fragmentManager, fragment) -> {
            Log.d(TAG, "onAuthenticated: Fragment attached: " + fragment.getClass().getSimpleName());
            if (!(fragment instanceof MainPreferences)) {
                ++mLevel;
            }
        });
        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            mLevel = getSupportFragmentManager().getBackStackEntryCount();
            Log.d(TAG, "onAuthenticated: Backstack changed. Level: %d", mLevel);
            // Update saved level: Delete everything from mLevel to the last item)
            int size = mSavedKeys.size();
            if (mLevel <= size - 1) {
                mSavedKeys.subList(mLevel, size).clear();
            }
        });

        String defaultPref = getKey(mLevel);
        Log.d(TAG, "onAuthenticated: defaultPref = " + defaultPref);
        if (defaultPref == null && mDualPaneMode) {
            defaultPref = "custom_locale";
            Log.d(TAG, "onAuthenticated: defaultPref (dual pane) = " + defaultPref);
        }
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main_layout, MainPreferences.getInstance(defaultPref, mDualPaneMode))
                .commit();
        Log.d(TAG, "onAuthenticated: exit");
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent: entry, intent = " + intent.getData());
        if (setKeysFromIntent(intent)) {
            // Clear old items
            mSavedKeys.clear();
            clearBackStack();
            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.main_layout);
            if (fragment instanceof MainPreferences) {
                ((MainPreferences) fragment).setPrefKey(getKey(mLevel = 0));
                Log.d(TAG, "onNewIntent: Selected pref (MainPreferences) = %s", fragment.getClass().getName());
            }
        }
        Log.d(TAG, "onNewIntent: exit");
    }

    @Override
    public void setTitle(int titleId) {
        if (mDualPaneMode) {
            Objects.requireNonNull(mSecondaryToolbar).setTitle(titleId);
        } else super.setTitle(titleId);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        Log.d(TAG, "onOptionsItemSelected: item = " + item.getItemId());
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPreferenceStartFragment(@NonNull PreferenceFragmentCompat caller, @NonNull Preference pref) {
        Log.d(TAG, "onPreferenceStartFragment: entry, caller = " + caller.getClass().getSimpleName() + ", pref = " + pref.getKey());
        if (pref.getFragment() == null) {
            Log.d(TAG, "onPreferenceStartFragment: pref.getFragment() is null, returning false");
            return false;
        }
        FragmentManager fragmentManager = getSupportFragmentManager();
        Bundle args = pref.getExtras();
        Fragment fragment = fragmentManager.getFragmentFactory().instantiate(getClassLoader(), pref.getFragment());
        if (fragment instanceof PreferenceFragment) {
            // Inject dual pane mode
            args.putBoolean(PreferenceFragment.PREF_SECONDARY, mDualPaneMode);
            // Inject subKey to the arguments
            String subKey = getKey(mLevel + 1);
            if (subKey != null && Objects.equals(pref.getKey(), getKey(mLevel))) {
                args.putString(PreferenceFragment.PREF_KEY, subKey);
            }
            // Save current key
            saveKey(mLevel, pref.getKey());
            Log.d(TAG, "onPreferenceStartFragment: mLevel = " + mLevel + ", savedKey = " + pref.getKey());
        }
        fragment.setArguments(args);
        // The line below is kept because this is how it is handled in AndroidX library
        fragment.setTargetFragment(caller, 0);
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        if (!mDualPaneMode) {
            transaction.setCustomAnimations(
                    R.animator.enter_from_left,
                    R.animator.enter_from_right,
                    R.animator.exit_from_right,
                    R.animator.exit_from_left
            ).addToBackStack(null);
        }
        transaction
                .replace(mDualPaneMode ? R.id.secondary_layout : R.id.main_layout, fragment)
                .commit();
        Log.d(TAG, "onPreferenceStartFragment: exit");
        return true;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putStringArrayList(SAVED_KEYS, mSavedKeys);
        super.onSaveInstanceState(outState);
    }

    @Nullable
    private String getKey(int level) {
        if (!mSavedKeys.isEmpty() && mSavedKeys.size() > level) {
            String key = mSavedKeys.get(level);
            if (key != null) {
                return key;
            }
        }
        if (mKeys.size() > level) {
            return mKeys.get(level);
        }
        return null;
    }

    private void saveKey(int level, @Nullable String key) {
        Log.d(TAG, "Save level: %d, Key: %s", level, key);
        int size = mSavedKeys.size();
        if (level >= size) {
            // Create levels
            int count = level - size + 1;
            for (int i = 0; i < count; ++i) {
                mSavedKeys.add(null);
            }
        }
        // Add this level
        mSavedKeys.set(level, key);
    }

    private boolean setKeysFromIntent(@NonNull Intent intent) {
        Uri uri = intent.getData();
        if (uri != null && SelfUriManager.APP_MANAGER_SCHEME.equals(uri.getScheme())
                && SelfUriManager.SETTINGS_HOST.equals(uri.getHost()) && uri.getPath() != null) {
            mKeys = Objects.requireNonNull(uri.getPathSegments());
            return true;
        }
        return false;
    }
}