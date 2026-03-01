// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.db.AppsDb
import io.github.muntashirakon.AppManager.db.entity.ArchivedApp

class ArchivedAppsActivity : BaseActivity() {

    private var adapter: ArchivedAppsAdapter? = null

    override fun onAuthenticated(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_archived_apps)
        val recyclerView: RecyclerView = findViewById(R.id.archived_apps_list)
        recyclerView.layoutManager = LinearLayoutManager(this)

        AppsDb.getInstance().archivedAppDao().all.observe(this) { archivedApps ->
            if (adapter == null) {
                adapter = ArchivedAppsAdapter(archivedApps) { onRestoreClicked(it) }
                recyclerView.adapter = adapter
            } else {
                adapter!!.updateData(archivedApps)
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
