// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.os.BundleCompat
import com.google.android.material.textfield.TextInputLayout
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.details.AppDetailsActivity
import io.github.muntashirakon.AppManager.utils.DateUtils
import io.github.muntashirakon.dialog.CapsuleBottomSheetDialogFragment
import io.github.muntashirakon.dialog.DialogTitleBuilder
import io.github.muntashirakon.view.TextInputLayoutCompat
import io.github.muntashirakon.widget.TextInputTextView
import java.util.*

class AppUsageDetailsDialog : CapsuleBottomSheetDialogFragment() {
    override fun initRootView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.dialog_app_usage_details, container, false)
    }

    override fun displayLoaderByDefault(): Boolean = true

    override fun onBodyInitialized(view: View, savedInstanceState: Bundle?) {
        val activity = requireActivity()
        val usageInfo = BundleCompat.getParcelable(requireArguments(), ARG_PACKAGE_USAGE_INFO, PackageUsageInfo::class.java)
        if (usageInfo == null) {
            finishLoading()
            return
        }
        val intervalType = requireArguments().getInt(ARG_INTERVAL_TYPE)
        val date = requireArguments().getLong(ARG_DATE)
        val titleBuilder = DialogTitleBuilder(activity)
            .setTitle(usageInfo.appLabel)
            .setTitleSelectable(true)
            .setSubtitle(usageInfo.packageName)
            .setSubtitleSelectable(true)
            .setEndIcon(io.github.muntashirakon.ui.R.drawable.ic_information) {
                val appDetailsIntent = AppDetailsActivity.getIntent(activity, usageInfo.packageName, usageInfo.userId)
                startActivity(appDetailsIntent)
            }
            .setEndIconContentDescription(R.string.app_info)
        usageInfo.applicationInfo?.let { titleBuilder.setStartIcon(it.loadIcon(activity.packageManager)) }
        setHeader(titleBuilder.build())

        val barChartView: BarChartView = view.findViewById(R.id.bar_chart)
        val screenTime: TextInputTextView = view.findViewById(R.id.screen_time)
        val timesOpened: TextInputTextView = view.findViewById(R.id.times_opened)
        val lastUsed: TextInputTextView = view.findViewById(R.id.last_used)
        val userId: TextInputTextView = view.findViewById(R.id.user_id)
        val mobileDataUsage: TextInputTextView = view.findViewById(R.id.data_usage)
        val mobileDataUsageLayout: TextInputLayout = TextInputLayoutCompat.fromTextInputEditText(mobileDataUsage)
        val wifiDataUsage: TextInputTextView = view.findViewById(R.id.wifi_usage)
        val wifiDataUsageLayout: TextInputLayout = TextInputLayoutCompat.fromTextInputEditText(wifiDataUsage)
        val dataUsageLayout: LinearLayoutCompat = view.findViewById(R.id.data_usage_layout)

        val mobileData = usageInfo.mobileData
        val wifiData = usageInfo.wifiData

        screenTime.text = DateUtils.getFormattedDuration(requireContext(), usageInfo.screenTime)
        timesOpened.text = resources.getQuantityString(R.plurals.no_of_times_opened, usageInfo.timesOpened, usageInfo.timesOpened)
        val lastRun = if (usageInfo.lastUsageTime > 1) (System.currentTimeMillis() - usageInfo.lastUsageTime) else 0
        if (usageInfo.packageName == BuildConfig.APPLICATION_ID) {
            lastUsed.setText(R.string.running)
        } else if (lastRun > 1) {
            lastUsed.text = String.format(Locale.getDefault(), "%s %s", DateUtils.getFormattedDuration(requireContext(), lastRun), getString(R.string.ago))
        } else {
            lastUsed.setText(R.string._undefined)
        }
        userId.text = String.format(Locale.getDefault(), "%d", usageInfo.userId)
        if ((mobileData == null && wifiData == null) || (mobileData != null && wifiData != null && (mobileData.total + wifiData.total == 0L))) {
            dataUsageLayout.visibility = View.GONE
        } else {
            dataUsageLayout.visibility = View.VISIBLE
            if (mobileData != null && mobileData.total != 0L) {
                val dataUsage = String.format("  ↑ %1\$s ↓ %2$s", Formatter.formatFileSize(requireContext(), mobileData.first!!), Formatter.formatFileSize(requireContext(), mobileData.second!!))
                mobileDataUsageLayout.visibility = View.VISIBLE
                mobileDataUsage.text = dataUsage
            } else mobileDataUsageLayout.visibility = View.GONE
            if (wifiData != null && wifiData.total != 0L) {
                val dataUsage = String.format("  ↑ %1\$s ↓ %2$s", Formatter.formatFileSize(requireContext(), wifiData.first!!), Formatter.formatFileSize(requireContext(), wifiData.second!!))
                wifiDataUsageLayout.visibility = View.VISIBLE
                wifiDataUsage.text = dataUsage
            } else wifiDataUsageLayout.visibility = View.GONE
        }
        usageInfo.entries?.let { UsageDataProcessor.updateChartWithAppUsage(barChartView, it, intervalType, date) }

        requireView().postDelayed({ finishLoading() }, 300)
    }

    companion object {
        val TAG: String = AppUsageDetailsDialog::class.java.simpleName
        private const val ARG_PACKAGE_USAGE_INFO = "pkg_usg_info"\nprivate const val ARG_INTERVAL_TYPE = "interval"\nprivate const val ARG_DATE = "date"

        @JvmStatic
        fun getInstance(usageInfo: PackageUsageInfo?, @IntervalType interval: Int, date: Long): AppUsageDetailsDialog {
            return AppUsageDetailsDialog().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PACKAGE_USAGE_INFO, usageInfo)
                    putInt(ARG_INTERVAL_TYPE, interval)
                    putLong(ARG_DATE, date)
                }
            }
        }
    }
}
