// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import com.google.android.material.progressindicator.LinearProgressIndicator
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.util.AdapterUtils
import io.github.muntashirakon.widget.RecyclerView
import io.github.muntashirakon.widget.SwipeRefreshLayout
import java.util.*

class AppsFragment : Fragment(), SwipeRefreshLayout.OnRefreshListener {
    class AppsFragmentItem(val packageName: String) {
        var label: CharSequence? = null
        var applicationInfo: ApplicationInfo? = null
        var filterableAppInfo: IFilterableAppInfo? = null

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other is String) {
                return packageName == other
            }
            if (other !is AppsFragmentItem) return false
            return packageName == other.packageName
        }

        override fun hashCode(): Int = Objects.hash(packageName)
    }

    private var mActivity: AppsBaseProfileActivity? = null
    private var mSwipeRefresh: SwipeRefreshLayout? = null
    private var mProgressIndicator: LinearProgressIndicator? = null
    private var mModel: AppsProfileViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mActivity = requireActivity() as AppsBaseProfileActivity
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.pager_app_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mSwipeRefresh = view.findViewById(R.id.swipe_refresh)
        mSwipeRefresh!!.setOnRefreshListener(this)
        val recyclerView: RecyclerView = view.findViewById(R.id.scrollView)
        recyclerView.layoutManager = UIUtils.getGridLayoutAt450Dp(mActivity!!)
        val emptyView: TextView = view.findViewById(android.R.id.empty)
        emptyView.setText(R.string.no_apps)
        recyclerView.setEmptyView(emptyView)
        mProgressIndicator = view.findViewById(R.id.progress_linear)
        mProgressIndicator!!.visibilityAfterHide = View.GONE
        mProgressIndicator!!.show()
        view.findViewById<View>(R.id.alert_text).visibility = View.GONE
        mSwipeRefresh!!.setOnChildScrollUpCallback { _, _ -> recyclerView.canScrollVertically(-1) }
        mModel = mActivity!!.model
        val adapter = AppsRecyclerAdapter()
        recyclerView.adapter = adapter
        mModel!!.getPackages().observe(viewLifecycleOwner) { packages ->
            mProgressIndicator!!.hide()
            adapter.setList(packages)
        }
    }

    override fun onResume() {
        super.onResume()
        mActivity!!.supportActionBar?.subtitle = mModel!!.getPreviewTitleString()
        mActivity!!.fab.show()
    }

    override fun onRefresh() {
        mSwipeRefresh!!.isRefreshing = false
        mModel!!.loadPackages()
    }

    inner class AppsRecyclerAdapter : RecyclerView.Adapter<AppsRecyclerAdapter.ViewHolder>() {
        private val pm: PackageManager = mActivity!!.packageManager
        private val packages = ArrayList<AppsFragmentItem>()
        private val defaultImage: ImageLoader.DefaultImageDrawable = ImageLoader.DefaultImageDrawable("android_default_icon", pm.defaultActivityIcon)

        fun setList(list: List<AppsFragmentItem>) {
            AdapterUtils.notifyDataSetChanged(this, packages, list)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(io.github.muntashirakon.ui.R.layout.m3_preference, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val fragmentItem = packages[position]
            holder.icon.tag = fragmentItem
            if (fragmentItem.applicationInfo != null) {
                ImageLoader.getInstance().displayImage(fragmentItem.packageName, fragmentItem.applicationInfo!!, holder.icon)
            } else {
                ImageLoader.getInstance().displayImage(fragmentItem.packageName, holder.icon) { tag ->
                    ImageLoader.ImageFetcherResult(tag, UIUtils.getBitmapFromDrawable(fragmentItem.filterableAppInfo!!.getAppIcon()), true, true, defaultImage)
                }
            }
            val label = fragmentItem.label
            holder.title.text = label ?: fragmentItem.packageName
            if (label != null) {
                holder.subtitle.visibility = View.VISIBLE
                holder.subtitle.text = fragmentItem.packageName
            } else {
                holder.subtitle.visibility = View.GONE
            }
            if (fragmentItem.applicationInfo != null) {
                holder.itemView.setOnClickListener { }
                holder.itemView.setOnLongClickListener {
                    val popupMenu = PopupMenu(mActivity!!, holder.itemView)
                    popupMenu.setForceShowIcon(true)
                    popupMenu.menu.add(R.string.delete).setIcon(R.drawable.ic_trash_can)
                        .setOnMenuItemClickListener {
                            mModel!!.deletePackage(fragmentItem.packageName)
                            true
                        }
                    popupMenu.show()
                    true
                }
            }
        }

        override fun getItemCount(): Int = packages.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val icon: ImageView = itemView.findViewById(android.R.id.icon)
            val title: TextView = itemView.findViewById(android.R.id.title)
            val subtitle: TextView = itemView.findViewById(android.R.id.summary)

            init {
                icon.contentDescription = itemView.context.getString(R.string.icon)
            }
        }
    }
}
