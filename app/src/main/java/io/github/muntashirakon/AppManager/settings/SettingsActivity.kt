// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import androidx.annotation.NonNull
import androidx.core.util.Pair
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.progressindicator.LinearProgressIndicator
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.self.SelfUriManager
import io.github.muntashirakon.util.UiUtils

class SettingsActivity : BaseActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    companion object {
        @JvmField
        val TAG: String = SettingsActivity::class.java.simpleName
        private const val SAVED_KEYS = "saved_keys"\n@JvmStatic
        fun getSettingsIntent(context: Context, vararg paths: String?): Intent {
            val intent = Intent(context, SettingsActivity::class.java)
            if (paths.isNotEmpty()) {
                intent.data = getSettingUri(*paths as Array<out String>)
            }
            return intent
        }

        private fun getSettingUri(vararg pathSegments: String): Uri {
            val builder = Uri.Builder()
                .scheme(SelfUriManager.APP_MANAGER_SCHEME)
                .authority(SelfUriManager.SETTINGS_HOST)
            for (pathSegment in pathSegments) {
                builder.appendPath(pathSegment)
            }
            return builder.build()
        }
    }

    lateinit var progressIndicator: LinearProgressIndicator
    private var mKeys: List<String> = emptyList()
    private var mSavedKeys = ArrayList<String?>()
    private var mLevel = 0
    private var mDualPaneMode = false
    private var mSecondaryToolbar: MaterialToolbar? = null

    override fun onAuthenticated(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            mSavedKeys = savedInstanceState.getStringArrayList(SAVED_KEYS) as ArrayList<String?>
        }
        setKeysFromIntent(intent)
        val mainPrefSize = UiUtils.dpToPx(this, 450)
        val windowWidth = resources.displayMetrics.widthPixels
        mDualPaneMode = windowWidth >= 2 * mainPrefSize
        setContentView(if (mDualPaneMode) R.layout.activity_settings_dual_pane else R.layout.activity_settings)
        setSupportActionBar(findViewById(R.id.toolbar))
        mSecondaryToolbar = findViewById(R.id.toolbar2)
        val secondaryContainer: FragmentContainerView? = findViewById(R.id.secondary_layout)
        progressIndicator = findViewById(R.id.progress_linear)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        getSupportFragmentManager().addFragmentOnAttachListener { _, fragment ->
            if (fragment is PreferenceFragment) {
                mLevel = getSupportFragmentManager().backStackEntryCount
                if (!mDualPaneMode) {
                    ++mLevel
                }
            }
        }
        getSupportFragmentManager().addOnBackStackChangedListener {
            mLevel = getSupportFragmentManager().backStackEntryCount
            Log.d(TAG, "Backstack changed. Level: %d", mLevel)
            // Update saved level: Delete everything from mLevel to the last item)
            val size = mSavedKeys.size
            if (mLevel <= size - 1) {
                mSavedKeys.subList(mLevel, size).clear()
            }
        }

        var defaultPref = getKey(mLevel)
        if (defaultPref == null && mDualPaneMode) {
            defaultPref = "custom_locale"\n}
        getSupportFragmentManager()
            .beginTransaction()
            .setCustomAnimations(
                R.animator.enter_from_left,
                R.animator.enter_from_right,
                R.animator.exit_from_right,
                R.animator.exit_from_left
            )
            .replace(R.id.main_layout, MainPreferences.getInstance(defaultPref, mDualPaneMode))
            .commit()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (setKeysFromIntent(intent)) {
            // Clear old items
            mSavedKeys.clear()
            clearBackStack()
            val fragment = getSupportFragmentManager().findFragmentById(R.id.main_layout)
            if (fragment is MainPreferences) {
                mLevel = 0
                fragment.setPrefKey(getKey(mLevel))
                Log.d(TAG, "Selected pref: %s", fragment.javaClass.name)
            }
        }
    }

    override fun setTitle(titleId: Int) {
        if (mDualPaneMode) {
            mSecondaryToolbar!!.setTitle(titleId)
        } else {
            super.setTitle(titleId)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        if (pref.fragment == null) {
            return false
        }
        val fragmentManager = supportFragmentManager
        val args = pref.extras
        val fragment = fragmentManager.fragmentFactory.instantiate(classLoader, pref.fragment!!)
        if (fragment is PreferenceFragment) {
            // Inject dual pane mode
            args.putBoolean(PreferenceFragment.PREF_SECONDARY, mDualPaneMode)
            // Inject subKey to the arguments
            val subKey = getKey(mLevel + 1)
            if (subKey != null && pref.key == getKey(mLevel)) {
                args.putString(PreferenceFragment.PREF_KEY, subKey)
            }
            // Save current key
            saveKey(mLevel, pref.key)
        }
        fragment.arguments = args
        // The line below is kept because this is how it is handled in AndroidX library
        @Suppress("DEPRECATION")
        fragment.setTargetFragment(caller, 0)
        val transaction = fragmentManager.beginTransaction()
        if (!mDualPaneMode) {
            transaction.setCustomAnimations(
                R.animator.enter_from_left,
                R.animator.enter_from_right,
                R.animator.exit_from_right,
                R.animator.exit_from_left
            ).addToBackStack(null)
        }
        transaction
            .replace(if (mDualPaneMode) R.id.secondary_layout else R.id.main_layout, fragment)
            .commit()
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList(SAVED_KEYS, mSavedKeys)
        super.onSaveInstanceState(outState)
    }

    private fun getKey(level: Int): String? {
        if (mSavedKeys.isNotEmpty() && mSavedKeys.size > level) {
            val key = mSavedKeys[level]
            if (key != null) {
                return key
            }
        }
        if (mKeys.size > level) {
            return mKeys[level]
        }
        return null
    }

    private fun saveKey(level: Int, key: String?) {
        Log.d(TAG, "Save level: %d, Key: %s", level, key)
        val size = mSavedKeys.size
        if (level >= size) {
            // Create levels
            val count = level - size + 1
            for (i in 0 until count) {
                mSavedKeys.add(null)
            }
        }
        // Add this level
        mSavedKeys[level] = key
    }

    private fun setKeysFromIntent(intent: Intent): Boolean {
        val uri = intent.data
        if (uri != null && SelfUriManager.APP_MANAGER_SCHEME == uri.scheme &&
            SelfUriManager.SETTINGS_HOST == uri.host && uri.path != null
        ) {
            mKeys = uri.pathSegments!!
            return true
        }
        return false
    }
}
