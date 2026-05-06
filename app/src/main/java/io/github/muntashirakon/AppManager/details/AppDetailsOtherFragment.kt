// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details

import android.content.Context
import android.content.Intent
import android.content.pm.ConfigurationInfo
import android.content.pm.FeatureInfo
import android.content.pm.PackageInfo
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.format.Formatter
import android.view.*
import android.widget.TextView
import androidx.annotation.IntDef
import androidx.annotation.UiThread
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.details.struct.AppDetailsFeatureItem
import io.github.muntashirakon.AppManager.details.struct.AppDetailsItem
import io.github.muntashirakon.AppManager.details.struct.AppDetailsLibraryItem
import io.github.muntashirakon.AppManager.utils.PackageUtils
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.AppManager.utils.Utils
import io.github.muntashirakon.io.Paths
import io.github.muntashirakon.util.AdapterUtils
import io.github.muntashirakon.util.LocalizedString
import io.github.muntashirakon.view.ProgressIndicatorCompat
import io.github.muntashirakon.widget.RecyclerView
import java.security.cert.CertificateEncodingException
import java.security.cert.X509Certificate
import java.util.*

class AppDetailsOtherFragment : AppDetailsFragment() {
    @IntDef(value = [FEATURES, CONFIGURATIONS, SIGNATURES, SHARED_LIBRARIES])
    @Retention(AnnotationRetention.SOURCE)
    annotation class OtherProperty

    private var mAdapter: AppDetailsRecyclerAdapter? = null
    private var mIsExternalApk: Boolean = false
    @OtherProperty
    private var mNeededProperty: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mNeededProperty = requireArguments().getInt(ARG_TYPE)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        emptyView.setText(getNotFoundString(mNeededProperty))
        mAdapter = AppDetailsRecyclerAdapter()
        recyclerView.adapter = mAdapter
        alertView.visibility = View.GONE
        viewModel?.get(mNeededProperty)?.observe(viewLifecycleOwner) { items ->
            if (items != null && mAdapter != null && viewModel!!.isPackageExist) {
                mIsExternalApk = viewModel!!.isExternalApk
                mAdapter!!.setDefaultList(items)
            } else ProgressIndicatorCompat.setVisibility(progressIndicator, false)
        }
    }

    override fun onRefresh() {
        refreshDetails()
        swipeRefresh.isRefreshing = false
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.fragment_app_details_refresh_actions, menu)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_refresh_details) {
            refreshDetails()
            return true
        }
        return false
    }

    override fun onResume() {
        super.onResume()
        activity.searchView?.visibility = View.GONE
    }

    override fun onQueryTextChange(searchQuery: String, type: Int): Boolean {
        viewModel?.setSearchQuery(searchQuery, type, mNeededProperty)
        return true
    }

    private fun refreshDetails() {
        if (viewModel == null || mIsExternalApk) return
        ProgressIndicatorCompat.setVisibility(progressIndicator, true)
        viewModel!!.triggerPackageChange()
    }

    private fun getNotFoundString(@OtherProperty index: Int): Int {
        return when (index) {
            FEATURES -> R.string.no_feature
            CONFIGURATIONS -> R.string.no_configurations
            SIGNATURES -> R.string.app_signing_no_signatures
            SHARED_LIBRARIES -> R.string.no_shared_libs
            else -> 0
        }
    }

    @UiThread
    private inner class AppDetailsRecyclerAdapter : RecyclerView.Adapter<AppDetailsRecyclerAdapter.ViewHolder>() {
        private val mAdapterList = mutableListOf<AppDetailsItem<*>>()
        private var mRequestedProperty: Int = 0

        @UiThread
        fun setDefaultList(list: List<AppDetailsItem<*>>) {
            ThreadUtils.postOnBackgroundThread {
                mRequestedProperty = mNeededProperty
                ThreadUtils.postOnMainThread {
                    if (isDetached) return@postOnMainThread
                    ProgressIndicatorCompat.setVisibility(progressIndicator, false)
                    synchronized(mAdapterList) { AdapterUtils.notifyDataSetChanged(this, mAdapterList, list) }
                }
            }
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val cardView: MaterialCardView = itemView as MaterialCardView
            var textView1: TextView? = null
            var textView2: TextView? = null
            var textView3: TextView? = null
            var textView4: TextView? = null
            var textView5: TextView? = null
            var launchBtn: MaterialButton? = null
            var chipType: Chip? = null

            init {
                when (mRequestedProperty) {
                    FEATURES -> {
                        textView1 = itemView.findViewById(R.id.name)
                        textView3 = itemView.findViewById(R.id.gles_ver)
                    }
                    CONFIGURATIONS -> {
                        textView1 = itemView.findViewById(R.id.reqgles)
                        textView2 = itemView.findViewById(R.id.reqfea)
                        textView3 = itemView.findViewById(R.id.reqkey)
                        textView4 = itemView.findViewById(R.id.reqnav)
                        textView5 = itemView.findViewById(R.id.reqtouch)
                    }
                    SHARED_LIBRARIES -> {
                        textView1 = itemView.findViewById(R.id.item_title)
                        textView2 = itemView.findViewById(R.id.item_subtitle)
                        launchBtn = itemView.findViewById(R.id.item_open)
                        chipType = itemView.findViewById(R.id.lib_type)
                        textView1?.setTextIsSelectable(true)
                        textView2?.setTextIsSelectable(true)
                    }
                    SIGNATURES -> {
                        textView1 = itemView.findViewById(R.id.checksum_description)
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val layout = when (mRequestedProperty) {
                FEATURES -> R.layout.item_app_details_secondary
                CONFIGURATIONS -> R.layout.item_app_details_tertiary
                SIGNATURES -> R.layout.item_app_details_signature
                SHARED_LIBRARIES -> R.layout.item_shared_lib
                else -> R.layout.item_app_details_primary
            }
            return ViewHolder(LayoutInflater.from(parent.context).inflate(layout, parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val context = holder.itemView.context
            when (mRequestedProperty) {
                FEATURES -> getFeaturesView(context, holder, position)
                CONFIGURATIONS -> getConfigurationView(holder, position)
                SIGNATURES -> getSignatureView(context, holder, position)
                SHARED_LIBRARIES -> getSharedLibsView(context, holder, position)
            }
        }

        override fun getItemCount(): Int = synchronized(mAdapterList) { mAdapterList.size }

        private fun getSharedLibsView(context: Context, holder: ViewHolder, index: Int) {
            val item = synchronized(mAdapterList) { mAdapterList[index] as AppDetailsLibraryItem<*> }
            holder.textView1?.text = item.name
            holder.chipType?.text = item.type
            when (item.type) {
                "APK" -> {
                    val pi = item.item as PackageInfo
                    val sb = StringBuilder("${pi.packageName}
")
                    item.path?.let { sb.append(Formatter.formatFileSize(context, item.size)).append(", ") }
                    sb.append(getString(R.string.version_name_with_code, pi.versionName, PackageInfoCompat.getLongVersionCode(pi)))
                    if (item.path != null) {
                        sb.append("\n").append(item.path)
                        holder.launchBtn?.visibility = View.VISIBLE
                        holder.launchBtn?.setIconResource(io.github.muntashirakon.ui.R.drawable.ic_information)
                        holder.launchBtn?.setOnClickListener { startActivity(AppDetailsActivity.getIntent(context, Paths.get(item.path!!), false)) }
                    } else holder.launchBtn?.visibility = View.GONE
                    holder.textView2?.text = sb
                }
                "⚠️", "SHARED", "EXEC", "SO" -> {
                    if (item.path == null) {
                        holder.textView2?.text = (item.item as LocalizedString).toLocalizedString(context)
                        holder.launchBtn?.visibility = View.GONE
                    } else {
                        val sb = StringBuilder(Formatter.formatFileSize(context, item.size)).append("\n").append(item.path)
                        holder.textView2?.text = sb
                        holder.launchBtn?.visibility = View.VISIBLE
                        holder.launchBtn?.setIconResource(R.drawable.ic_open_in_new)
                        holder.launchBtn?.setOnClickListener { Utils.openAsFolderInFM(context, item.path!!.parentFile).onClick(it) }
                    }
                }
                "JAR" -> {
                    val sb = StringBuilder(Formatter.formatFileSize(context, item.size)).append("\n").append(item.path)
                    holder.textView2?.text = sb
                    holder.launchBtn?.visibility = View.VISIBLE
                    holder.launchBtn?.setIconResource(R.drawable.ic_open_in_new)
                    holder.launchBtn?.setOnClickListener { Utils.openAsFolderInFM(context, item.path!!.parentFile).onClick(it) }
                }
            }
            holder.cardView.strokeColor = Color.TRANSPARENT
        }

        private fun getFeaturesView(context: Context, holder: ViewHolder, index: Int) {
            val item = synchronized(mAdapterList) { mAdapterList[index] as AppDetailsFeatureItem }
            val info = item.item
            holder.cardView.strokeColor = when {
                item.required && !item.available -> ContextCompat.getColor(context, io.github.muntashirakon.ui.R.color.red)
                !item.available -> ContextCompat.getColor(context, io.github.muntashirakon.ui.R.color.disabled_user)
                else -> Color.TRANSPARENT
            }
            if (info.name == null) {
                holder.textView1?.text = if (info.reqGlEsVersion == FeatureInfo.GL_ES_VERSION_UNDEFINED) item.name else "${getString(R.string.gles_version)} ${Utils.getGlEsVersion(info.reqGlEsVersion)}"\nholder.textView3?.visibility = View.GONE
            } else {
                holder.textView1?.text = item.name
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && info.version != 0) {
                    holder.textView3?.visibility = View.VISIBLE
                    holder.textView3?.text = getString(R.string.minimum_version, info.version)
                } else holder.textView3?.visibility = View.GONE
            }
        }

        private fun getConfigurationView(holder: ViewHolder, index: Int) {
            val info = synchronized(mAdapterList) { mAdapterList[index].item as ConfigurationInfo }
            holder.cardView.strokeColor = Color.TRANSPARENT
            holder.textView1?.text = "${getString(R.string.gles_version)} ${Utils.getGlEsVersion(info.reqGlEsVersion)}"\nholder.textView2?.text = "${getString(R.string.input_features)}: ${Utils.getInputFeaturesString(info.reqInputFeatures)}"\nholder.textView3?.text = "${getString(R.string.keyboard_type)}: ${getString(Utils.getKeyboardType(info.reqKeyboardType))}"\nholder.textView4?.text = "${getString(R.string.navigation)}: ${getString(Utils.getNavigation(info.reqNavigation))}"\nholder.textView5?.text = "${getString(R.string.touchscreen)}: ${getString(Utils.getTouchScreen(info.reqTouchScreen))}"\n}

        private fun getSignatureView(context: Context, holder: ViewHolder, index: Int) {
            val item = synchronized(mAdapterList) { mAdapterList[index] }
            val sig = item.item as X509Certificate
            val ssb = SpannableStringBuilder()
            if (index == 0) viewModel?.getApkVerifierResult()?.let { ssb.append(PackageUtils.getApkVerifierInfo(it, context)) }
            if (!item.name.isNullOrEmpty()) ssb.append(UIUtils.getTitleText(context, item.name)).append("\n")
            try { ssb.append(PackageUtils.getSigningCertificateInfo(context, sig)) } catch (ignore: CertificateEncodingException) {}
            holder.textView1?.text = ssb
            holder.textView1?.setTextIsSelectable(true)
            holder.cardView.strokeColor = Color.TRANSPARENT
        }
    }
}
