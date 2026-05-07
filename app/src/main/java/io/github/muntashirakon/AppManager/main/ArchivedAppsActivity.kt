// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.db.entity.ArchivedApp
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ArchivedAppsActivity : BaseActivity() {

    private val viewModel: ArchivedAppsViewModel by viewModels()
    private var adapter: ArchivedAppsAdapter? = null

    override fun onAuthenticated(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_archived_apps)
        val recyclerView: RecyclerView = findViewById(R.id.archived_apps_list)
        recyclerView.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.archivedApps.collect { archivedApps ->
                    if (adapter == null) {
                        adapter = ArchivedAppsAdapter(archivedApps) { onRestoreClicked(it) }
                        recyclerView.adapter = adapter
                    } else {
                        adapter!!.updateData(archivedApps)
                    }
                }
            }
        }
    }

    private fun onRestoreClicked(archivedApp: ArchivedApp) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${archivedApp.packageName}"))
            startActivity(intent)
        } catch (anfe: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${archivedApp.packageName}")))
        }
    }
}
