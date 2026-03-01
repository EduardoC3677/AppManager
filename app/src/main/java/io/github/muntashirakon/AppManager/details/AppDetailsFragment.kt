// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.CallSuper
import androidx.annotation.IntDef
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.progressindicator.LinearProgressIndicator
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes
import io.github.muntashirakon.view.ProgressIndicatorCompat
import io.github.muntashirakon.widget.MaterialAlertView
import io.github.muntashirakon.widget.RecyclerView
import io.github.muntashirakon.widget.SwipeRefreshLayout

abstract class AppDetailsFragment : Fragment(), AdvancedSearchView.OnQueryTextListener,
    SwipeRefreshLayout.OnRefreshListener, MenuProvider {

    @IntDef(value = [APP_INFO, ACTIVITIES, SERVICES, RECEIVERS, PROVIDERS, APP_OPS, USES_PERMISSIONS, PERMISSIONS, FEATURES, CONFIGURATIONS, SIGNATURES, SHARED_LIBRARIES, OVERLAYS])
    @Retention(AnnotationRetention.SOURCE)
    annotation class Property

    @IntDef(value = [SORT_BY_NAME, SORT_BY_BLOCKED, SORT_BY_TRACKERS, SORT_BY_APP_OP_VALUES, SORT_BY_DENIED_APP_OPS, SORT_BY_DANGEROUS_PERMS, SORT_BY_DENIED_PERMS, SORT_BY_PRIORITY])
    @Retention(AnnotationRetention.SOURCE)
    annotation class SortOrder

    protected lateinit var packageManager: PackageManager
    protected lateinit var activity: AppDetailsActivity
    protected lateinit var alertView: MaterialAlertView
    protected lateinit var swipeRefresh: SwipeRefreshLayout
    protected lateinit var progressIndicator: LinearProgressIndicator
    protected lateinit var recyclerView: RecyclerView
    protected lateinit var emptyView: TextView
    protected var viewModel: AppDetailsViewModel? = null
    protected var colorQueryStringHighlight: Int = 0

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity = requireActivity() as AppDetailsActivity
        viewModel = ViewModelProvider(activity).get(AppDetailsViewModel::class.java)
        packageManager = activity.packageManager
        colorQueryStringHighlight = ColorCodes.getQueryStringHighlightColor(activity)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.pager_app_details, container, false)
    }

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        swipeRefresh = view.findViewById(R.id.swipe_refresh)
        swipeRefresh.setOnRefreshListener(this)
        recyclerView = view.findViewById(R.id.scrollView)
        recyclerView.layoutManager = UIUtils.getGridLayoutAt450Dp(activity)
        emptyView = view.findViewById(android.R.id.empty)
        recyclerView.setEmptyView(emptyView)
        progressIndicator = view.findViewById(R.id.progress_linear)
        progressIndicator.visibilityAfterHide = View.GONE
        ProgressIndicatorCompat.setVisibility(progressIndicator, true)
        alertView = view.findViewById(R.id.alert_text)
        alertView.setEndIconMode(MaterialAlertView.END_ICON_CUSTOM)
        alertView.setEndIconDrawable(com.google.android.material.R.drawable.mtrl_ic_cancel)
        alertView.setEndIconContentDescription(R.string.close)
        swipeRefresh.setOnChildScrollUpCallback { _, _ -> recyclerView.canScrollVertically(-1) }
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    @CallSuper
    override fun onResume() {
        super.onResume()
        swipeRefresh.isEnabled = true
    }

    @CallSuper
    override fun onPause() {
        super.onPause()
        swipeRefresh.isEnabled = false
    }

    @CallSuper
    override fun onDestroyView() {
        swipeRefresh.isRefreshing = false
        swipeRefresh.clearAnimation()
        super.onDestroyView()
    }

    override fun onQueryTextSubmit(query: String, type: Int): Boolean = false

    companion object {
        const val APP_INFO = 0
        const val ACTIVITIES = 1
        const val SERVICES = 2
        const val RECEIVERS = 3
        const val PROVIDERS = 4
        const val APP_OPS = 5
        const val USES_PERMISSIONS = 6
        const val PERMISSIONS = 7
        const val FEATURES = 8
        const val CONFIGURATIONS = 9
        const val SIGNATURES = 10
        const val SHARED_LIBRARIES = 11
        const val OVERLAYS = 12

        const val SORT_BY_NAME = 0
        const val SORT_BY_BLOCKED = 1
        const val SORT_BY_TRACKERS = 2
        const val SORT_BY_APP_OP_VALUES = 3
        const val SORT_BY_DENIED_APP_OPS = 4
        const val SORT_BY_DANGEROUS_PERMS = 5
        const val SORT_BY_DENIED_PERMS = 6
        const val SORT_BY_PRIORITY = 7

        @JvmField
        val sSortMenuItemIdsMap = intArrayOf(
            R.id.action_sort_by_name, R.id.action_sort_by_blocked_components,
            R.id.action_sort_by_tracker_components, R.id.action_sort_by_app_ops_values,
            R.id.action_sort_by_denied_app_ops, R.id.action_sort_by_dangerous_permissions,
            R.id.action_sort_by_denied_permissions, R.id.action_sort_by_priority
        )

        const val ARG_TYPE = "type"
    }
}
