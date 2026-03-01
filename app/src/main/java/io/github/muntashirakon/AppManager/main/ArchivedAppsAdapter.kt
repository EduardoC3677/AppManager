// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.db.entity.ArchivedApp

class ArchivedAppsAdapter(
    private var archivedApps: List<ArchivedApp>,
    private val listener: (ArchivedApp) -> Unit
) : RecyclerView.Adapter<ArchivedAppsAdapter.ViewHolder>() {

    fun interface OnRestoreClickListener {
        fun onRestoreClicked(archivedApp: ArchivedApp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_archived_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val archivedApp = archivedApps[position]
        holder.appName.text = archivedApp.appName
        holder.packageName.text = archivedApp.packageName
        holder.restoreButton.setOnClickListener { listener(archivedApp) }
    }

    override fun getItemCount(): Int = archivedApps.size

    fun updateData(newArchivedApps: List<ArchivedApp>) {
        this.archivedApps = newArchivedApps
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appName: TextView = view.findViewById(R.id.app_name)
        val packageName: TextView = view.findViewById(R.id.package_name)
        val restoreButton: Button = view.findViewById(R.id.restore_button)
    }
}
