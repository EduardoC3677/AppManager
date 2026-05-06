// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sysconfig

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.details.AppDetailsActivity
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader
import io.github.muntashirakon.AppManager.utils.LangUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes
import io.github.muntashirakon.adapters.SelectedArrayAdapter
import io.github.muntashirakon.util.AdapterUtils
import io.github.muntashirakon.util.UiUtils
import io.github.muntashirakon.widget.MaterialSpinner
import io.github.muntashirakon.widget.RecyclerView
import java.util.*

class SysConfigActivity : BaseActivity() {
    private var mAdapter: SysConfigRecyclerAdapter? = null
    private var mProgressIndicator: LinearProgressIndicator? = null
    @SysConfigType
    private var mType: String = SysConfigType.TYPE_GROUP
    private var mViewModel: SysConfigViewModel? = null

    override fun onAuthenticated(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_sys_config)
        setSupportActionBar(findViewById(R.id.toolbar))
        mViewModel = ViewModelProvider(this).get(SysConfigViewModel::class.java)
        val spinner: MaterialSpinner = findViewById(R.id.spinner)
        UiUtils.applyWindowInsetsAsMargin(spinner, false, false)
        spinner.requestFocus()
        val recyclerView: RecyclerView = findViewById(R.id.recycler_view)
        recyclerView.setEmptyView(findViewById(android.R.id.empty))
        mProgressIndicator = findViewById(R.id.progress_linear)
        mProgressIndicator!!.visibilityAfterHide = View.GONE

        val sysConfigTypes = resources.getStringArray(R.array.sys_config_names)
        val adapter = SelectedArrayAdapter(this, io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item_small, android.R.id.text1, sysConfigTypes)
        spinner.adapter = adapter
        spinner.setOnItemClickListener { _, _, position, _ ->
            mProgressIndicator!!.show()
            mType = sysConfigTypes[position]
            mViewModel!!.loadSysConfigInfo(mType)
        }

        mAdapter = SysConfigRecyclerAdapter(this)
        recyclerView.layoutManager = UIUtils.getGridLayoutAt450Dp(this)
        recyclerView.adapter = mAdapter
        mViewModel!!.sysConfigInfoListLiveData.observe(this) { list ->
            supportActionBar?.subtitle = mType
            mAdapter!!.setList(list)
            mProgressIndicator!!.hide()
        }
        mViewModel!!.loadSysConfigInfo(mType)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    class SysConfigRecyclerAdapter(private val mActivity: SysConfigActivity) : RecyclerView.Adapter<SysConfigRecyclerAdapter.ViewHolder>() {
        private val mList = mutableListOf<SysConfigInfo>()
        private val mPm: PackageManager = mActivity.packageManager
        private val mCardColor0 = ColorCodes.getListItemColor0(mActivity)
        private val mCardColor1 = ColorCodes.getListItemColor1(mActivity)

        fun setList(list: List<SysConfigInfo>) {
            AdapterUtils.notifyDataSetChanged(this, mList, list)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_sys_config, parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.icon.setImageDrawable(null)
            holder.cardView.setCardBackgroundColor(if (position % 2 == 0) mCardColor1 else mCardColor0)
            val info = mList[position]
            if (info.isPackage) {
                holder.icon.visibility = View.VISIBLE
                try {
                    val ai = mPm.getApplicationInfo(info.name, 0)
                    holder.title.text = ai.loadLabel(mPm)
                    holder.packageName.visibility = View.VISIBLE
                    holder.packageName.text = info.name
                    holder.icon.tag = ai.packageName
                    ImageLoader.getInstance().displayImage(ai.packageName, ai, holder.icon)
                } catch (e: PackageManager.NameNotFoundException) {
                    holder.title.text = info.name
                    holder.packageName.visibility = View.GONE
                    holder.icon.tag = info.name
                    ImageLoader.getInstance().displayImage(info.name, null, holder.icon)
                }
                holder.icon.setOnClickListener { mActivity.startActivity(AppDetailsActivity.getIntent(mActivity, info.name, 0)) }
            } else {
                holder.icon.visibility = View.GONE
                holder.title.text = info.name
                holder.packageName.visibility = View.GONE
            }
            setSubtitle(holder, info)
        }

        override fun getItemCount(): Int = mList.size

        private fun setSubtitle(holder: ViewHolder, info: SysConfigInfo) {
            val context = holder.itemView.context
            val sb = SpannableStringBuilder()
            when (info.type) {
                SysConfigType.TYPE_PERMISSION -> {
                    sb.append(UIUtils.getStyledKeyValue(context, "GID", Arrays.toString(info.gids))).append("\n")
                    sb.append(UIUtils.getStyledKeyValue(context, "Per user", info.perUser.toString()))
                }
                SysConfigType.TYPE_ASSIGN_PERMISSION -> {
                    sb.append(UIUtils.getStyledKeyValue(context, "Permissions", ""))
                    if (info.permissions?.isEmpty() == true) sb.append(" None")
                    info.permissions?.forEach { sb.append("\n- $it") }
                }
                SysConfigType.TYPE_SPLIT_PERMISSION -> {
                    sb.append(UIUtils.getStyledKeyValue(context, "Target SDK", info.targetSdk.toString())).append("\n")
                    sb.append(UIUtils.getStyledKeyValue(context, "Permissions", ""))
                    if (info.permissions?.isEmpty() == true) sb.append(" None")
                    info.permissions?.forEach { sb.append("\n- $it") }
                }
                SysConfigType.TYPE_LIBRARY -> {
                    sb.append(UIUtils.getStyledKeyValue(context, "Filename", info.filename)).append("\n")
                    sb.append(UIUtils.getStyledKeyValue(context, "Dependencies", ""))
                    if (info.dependencies?.isEmpty() == true) sb.append(" None")
                    info.dependencies?.forEach { sb.append("\n- $it") }
                }
                SysConfigType.TYPE_FEATURE -> if (info.version > 0) sb.append(UIUtils.getStyledKeyValue(context, "Version", info.version.toString()))
                SysConfigType.TYPE_DEFAULT_ENABLED_VR_APP -> {
                    sb.append(UIUtils.getStyledKeyValue(context, "Components", Arrays.toString(info.classNames)))
                    if (info.classNames?.isEmpty() == true) sb.append(" None")
                    info.classNames?.forEach { sb.append("\n- $it") }
                }
                SysConfigType.TYPE_COMPONENT_OVERRIDE -> {
                    sb.append(UIUtils.getStyledKeyValue(context, "Components", ""))
                    if (info.classNames?.isEmpty() == true) sb.append(" None")
                    info.classNames?.indices?.forEach { sb.append("\n- ${info.classNames!![it]} = ${if (info.whitelist!![it]) "Enabled" else "Disabled"}") }
                }
                SysConfigType.TYPE_BACKUP_TRANSPORT_WHITELISTED_SERVICE -> {
                    sb.append(UIUtils.getStyledKeyValue(context, "Services", ""))
                    if (info.classNames?.isEmpty() == true) sb.append(" None")
                    info.classNames?.forEach { sb.append("\n- $it") }
                }
                SysConfigType.TYPE_DISABLED_UNTIL_USED_PREINSTALLED_CARRIER_ASSOCIATED_APP -> {
                    sb.append(UIUtils.getStyledKeyValue(context, "Associated packages", ""))
                    if (info.packages?.isEmpty() == true) sb.append(" None")
                    info.packages?.indices?.forEach { sb.append("\n- Package${LangUtils.getSeparatorString()}${info.packages!![it]}, Target SDK${LangUtils.getSeparatorString()}${info.targetSdks!![it]}") }
                }
                SysConfigType.TYPE_PRIVAPP_PERMISSIONS, SysConfigType.TYPE_OEM_PERMISSIONS -> {
                    sb.append(UIUtils.getStyledKeyValue(context, "Permissions", ""))
                    if (info.permissions?.isEmpty() == true) sb.append(" None")
                    info.permissions?.indices?.forEach { sb.append("\n- ${info.permissions!![it]} = ${if (info.whitelist!![it]) "Granted" else "Revoked"}") }
                }
                SysConfigType.TYPE_ALLOW_ASSOCIATION -> {
                    sb.append(UIUtils.getStyledKeyValue(context, "Associated packages", ""))
                    if (info.packages?.isEmpty() == true) sb.append(" None")
                    info.packages?.forEach { sb.append("\n- $it") }
                }
                SysConfigType.TYPE_INSTALL_IN_USER_TYPE -> {
                    sb.append(UIUtils.getStyledKeyValue(context, "User types", ""))
                    if (info.userTypes?.isEmpty() == true) sb.append(" None")
                    info.userTypes?.indices?.forEach { sb.append("\n- ${info.userTypes!![it]} = ${if (info.whitelist!![it]) "Whitelisted" else "Blacklisted"}") }
                }
                SysConfigType.TYPE_NAMED_ACTOR -> info.actors?.indices?.forEach { sb.append("Actor${LangUtils.getSeparatorString()}${info.actors!![it]}, Package${LangUtils.getSeparatorString()}${info.packages!![it]}
") }
            }
            if (sb.isNotEmpty()) { holder.subtitle.visibility = View.VISIBLE; holder.subtitle.text = sb }
            else holder.subtitle.visibility = View.GONE
        }

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val cardView: MaterialCardView = itemView as MaterialCardView
            val title: TextView = itemView.findViewById(android.R.id.title)
            val packageName: TextView = itemView.findViewById(R.id.package_name)
            val subtitle: TextView = itemView.findViewById(android.R.id.summary)
            val icon: ImageView = itemView.findViewById(android.R.id.icon)
        }
    }
}
