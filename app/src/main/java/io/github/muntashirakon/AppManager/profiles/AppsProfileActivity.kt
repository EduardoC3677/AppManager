// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.text.SpannableStringBuilder
import androidx.core.util.Pair
import androidx.fragment.app.Fragment
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder
import java.util.*

class AppsProfileActivity : AppsBaseProfileActivity() {
    override fun getAppsBaseFragment(): Fragment = AppsFragment()

    override fun loadNewProfile(newProfileName: String, intent: Intent) {
        val initialPackages = intent.getStringArrayExtra(EXTRA_NEW_PROFILE_PACKAGES)
        model.loadNewAppsProfile(newProfileName, initialPackages)
    }

    override fun onAuthenticated(savedInstanceState: Bundle?) {
        super.onAuthenticated(savedInstanceState)
        bottomNavigationView.menu.removeItem(R.id.action_preview)
        if (intent.hasExtra(EXTRA_SHORTCUT_TYPE)) {
            val shortcutType = intent.getStringExtra(EXTRA_SHORTCUT_TYPE)
            val profileState = intent.getStringExtra(EXTRA_STATE)
            if (shortcutType != null && profileId != null) {
                ProfileApplierActivity.getShortcutIntent(this, profileId!!, shortcutType, profileState)
            }
            finish()
            return
        }
        fab.setImageResource(R.drawable.ic_add)
        fab.contentDescription = getString(R.string.add_item)
        fab.setOnClickListener {
            progressIndicator.show()
            model.loadInstalledApps()
        }
        model.observeInstalledApps().observe(this) { itemPairs ->
            val items = ArrayList<String>(itemPairs.size)
            val itemNames = ArrayList<CharSequence>(itemPairs.size)
            for (itemPair in itemPairs) {
                items.add(itemPair.second.packageName)
                val isSystem = (itemPair.second.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                itemNames.add(SpannableStringBuilder(itemPair.first)
                    .append("\n")
                    .append(getSmallerText(getString(if (isSystem) R.string.system else R.string.user))))
            }
            progressIndicator.hide()
            SearchableMultiChoiceDialogBuilder(this, items, itemNames)
                .addSelections(model.getCurrentPackages())
                .setTitle(R.string.apps)
                .setPositiveButton(R.string.ok) { _, _, selectedItems -> model.setPackages(selectedItems) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    companion object {
        private const val EXTRA_NEW_PROFILE_PACKAGES = "new_prof_pkgs"\nprivate const val EXTRA_SHORTCUT_TYPE = "shortcut"

        @JvmStatic
        fun getProfileIntent(context: Context, profileId: String): Intent {
            return Intent(context, AppsProfileActivity::class.java).apply {
                putExtra(EXTRA_PROFILE_ID, profileId)
            }
        }

        @JvmStatic
        fun getNewProfileIntent(context: Context, profileName: String): Intent {
            return getNewProfileIntent(context, profileName, null)
        }

        @JvmStatic
        fun getNewProfileIntent(context: Context, profileName: String, initialPackages: Array<String>?): Intent {
            return Intent(context, AppsProfileActivity::class.java).apply {
                putExtra(EXTRA_NEW_PROFILE_NAME, profileName)
                if (initialPackages != null) {
                    putExtra(EXTRA_NEW_PROFILE_PACKAGES, initialPackages)
                }
            }
        }

        @JvmStatic
        fun getCloneProfileIntent(context: Context, oldProfileId: String, newProfileName: String): Intent {
            return Intent(context, AppsProfileActivity::class.java).apply {
                putExtra(EXTRA_PROFILE_ID, oldProfileId)
                putExtra(EXTRA_NEW_PROFILE_NAME, newProfileName)
            }
        }
    }
}
