// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.filters.EditFiltersDialogFragment
import io.github.muntashirakon.AppManager.filters.FilterItem

class AppsFilterProfileActivity : AppsBaseProfileActivity(), EditFiltersDialogFragment.OnSaveDialogButtonInterface {
    override fun getAppsBaseFragment(): Fragment = AppsFragment()

    override fun loadNewProfile(newProfileName: String, intent: Intent) {
        model.loadNewAppsFilterProfile(newProfileName)
    }

    override fun onAuthenticated(savedInstanceState: Bundle?) {
        super.onAuthenticated(savedInstanceState)
        bottomNavigationView.menu.removeItem(R.id.action_apps)
        fab.setImageResource(R.drawable.ic_filter_menu)
        fab.contentDescription = getString(R.string.filters)
        fab.setOnClickListener {
            val dialog = EditFiltersDialogFragment()
            dialog.setOnSaveDialogButtonInterface(this)
            dialog.show(supportFragmentManager, EditFiltersDialogFragment.TAG)
        }
    }

    override fun getFilterItem(): FilterItem = model.getFilterItem()

    override fun onItemAltered(item: FilterItem) {
        model.setModified(true)
        model.loadPackages()
    }

    companion object {
        @JvmStatic
        fun getProfileIntent(context: Context, profileId: String): Intent {
            return Intent(context, AppsFilterProfileActivity::class.java).apply {
                putExtra(EXTRA_PROFILE_ID, profileId)
            }
        }

        @JvmStatic
        fun getNewProfileIntent(context: Context, profileName: String): Intent {
            return Intent(context, AppsFilterProfileActivity::class.java).apply {
                putExtra(EXTRA_NEW_PROFILE_NAME, profileName)
            }
        }

        @JvmStatic
        fun getCloneProfileIntent(context: Context, oldProfileId: String, newProfileName: String): Intent {
            return Intent(context, AppsFilterProfileActivity::class.java).apply {
                putExtra(EXTRA_PROFILE_ID, oldProfileId)
                putExtra(EXTRA_NEW_PROFILE_NAME, newProfileName)
            }
        }
    }
}
