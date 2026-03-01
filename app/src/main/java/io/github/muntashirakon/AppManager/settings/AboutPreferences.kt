// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.preference.Preference
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.transition.MaterialSharedAxis
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.changelog.ChangelogRecyclerAdapter
import io.github.muntashirakon.AppManager.misc.HelpActivity
import io.github.muntashirakon.dialog.AlertDialogBuilder
import io.github.muntashirakon.dialog.ScrollableDialogBuilder
import java.util.*

class AboutPreferences : PreferenceFragment() {
    private lateinit var mModel: MainPreferencesViewModel

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_about, rootKey)
        preferenceManager.preferenceDataStore = SettingsDataStore()
        mModel = ViewModelProvider(requireActivity())[MainPreferencesViewModel::class.java]
        val versionPref = findPreference<Preference>("version")!!
        versionPref.summary = String.format(Locale.getDefault(), "%s (%d)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        versionPref.setOnPreferenceClickListener {
            mModel.loadChangeLog()
            true
        }
        // User manual
        findPreference<Preference>("user_manual")!!
            .setOnPreferenceClickListener {
                val helpIntent = Intent(requireContext(), HelpActivity::class.java)
                startActivity(helpIntent)
                true
            }
        // Website
        findPreference<Preference>("website")!!
            .setOnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.website_message)))
                startActivity(intent)
                true
            }
        // Get help
        findPreference<Preference>("get_help")!!
            .setOnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.discussions_site)))
                startActivity(intent)
                true
            }
        // Third-party libraries
        findPreference<Preference>("third_party_libraries")!!
            .setOnPreferenceClickListener {
                ScrollableDialogBuilder(requireActivity())
                    .setTitle(R.string.third_party)
                    .setMessage(R.string.third_party_message)
                    .enableAnchors()
                    .setNegativeButton(R.string.close, null)
                    .show()
                true
            }
        // Credits
        findPreference<Preference>("credits")!!
            .setOnPreferenceClickListener {
                ScrollableDialogBuilder(requireActivity())
                    .setTitle(R.string.credits)
                    .setMessage(R.string.credits_message)
                    .enableAnchors()
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Observe Changelog
        mModel.getChangeLog().observe(viewLifecycleOwner) { changelog ->
            val v = View.inflate(requireContext(), R.layout.dialog_whats_new, null)
            val recyclerView = v.findViewById<RecyclerView>(android.R.id.list)
            recyclerView.layoutManager = LinearLayoutManager(recyclerView.context)
            val adapter = ChangelogRecyclerAdapter()
            recyclerView.adapter = adapter
            adapter.setAdapterList(changelog.changelogItems)
            AlertDialogBuilder(requireActivity(), true)
                .setTitle(R.string.changelog)
                .setView(recyclerView)
                .show()
        }
    }

    override fun getTitle(): Int {
        return R.string.about
    }
}
