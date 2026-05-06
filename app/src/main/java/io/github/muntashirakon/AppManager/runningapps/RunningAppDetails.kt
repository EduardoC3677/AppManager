// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runningapps

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.os.BundleCompat
import com.google.android.material.button.MaterialButton
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.compat.UserHandleHidden
import io.github.muntashirakon.AppManager.details.AppDetailsActivity
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader
import io.github.muntashirakon.AppManager.utils.DateUtils
import io.github.muntashirakon.dialog.CapsuleBottomSheetDialogFragment
import java.util.*

class RunningAppDetails : CapsuleBottomSheetDialogFragment() {
    override fun initRootView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.dialog_running_app_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val processItem = BundleCompat.getParcelable(requireArguments(), ARG_PS_ITEM, ProcessItem::class.java)
        if (processItem == null) {
            dismiss()
            return
        }
        val appContainer: LinearLayoutCompat = view.findViewById(R.id.app_container)
        val appIcon: ImageView = view.findViewById(R.id.icon)
        val openAppInfoButton: MaterialButton = view.findViewById(R.id.info)
        val appLabel: TextView = view.findViewById(R.id.name)
        val packageName: TextView = view.findViewById(R.id.package_name)
        val processName: TextView = view.findViewById(R.id.process_name)
        val pid: TextView = view.findViewById(R.id.pid)
        val ppid: TextView = view.findViewById(R.id.ppid)
        val rss: TextView = view.findViewById(R.id.rss)
        val vsz: TextView = view.findViewById(R.id.vsz)
        val cpuPercent: TextView = view.findViewById(R.id.cpu_percent)
        val cpuTime: TextView = view.findViewById(R.id.cpu_time)
        val priority: TextView = view.findViewById(R.id.priority)
        val threads: TextView = view.findViewById(R.id.threads)
        val user: TextView = view.findViewById(R.id.user)
        val state: TextView = view.findViewById(R.id.state)
        val seLinuxContext: TextView = view.findViewById(R.id.selinux_context)
        val cliArgs: TextView = view.findViewById(R.id.cli_args)

        processName.text = processItem.name
        pid.text = String.format(Locale.getDefault(), "%d", processItem.pid)
        ppid.text = String.format(Locale.getDefault(), "%d", processItem.ppid)
        rss.text = Formatter.formatFileSize(requireContext(), processItem.memory)
        vsz.text = Formatter.formatFileSize(requireContext(), processItem.virtualMemory)
        cpuPercent.text = String.format(Locale.getDefault(), "%.2f", processItem.cpuTimeInPercent)
        cpuTime.text = DateUtils.getFormattedDuration(requireContext(), processItem.cpuTimeInMillis, false, true)
        priority.text = String.format(Locale.getDefault(), "%d", processItem.priority)
        threads.text = String.format(Locale.getDefault(), "%d", processItem.threadCount)
        user.text = String.format(Locale.getDefault(), "%s (%d)", processItem.user, processItem.uid)
        state.text = if (TextUtils.isEmpty(processItem.state_extra)) processItem.state else "${processItem.state} (${processItem.state_extra})"\nseLinuxContext.text = processItem.context
        cliArgs.text = processItem.commandlineArgsAsString
        if (processItem is AppProcessItem) {
            val packageInfo = processItem.packageInfo
            appContainer.visibility = View.VISIBLE
            ImageLoader.getInstance().displayImage(packageInfo.packageName, packageInfo.applicationInfo, appIcon)
            appLabel.text = packageInfo.applicationInfo.loadLabel(requireContext().packageManager)
            packageName.text = packageInfo.packageName
            openAppInfoButton.setOnClickListener {
                startActivity(AppDetailsActivity.getIntent(requireContext(), packageInfo.packageName, UserHandleHidden.getUserId(processItem.uid)))
                dismiss()
            }
        } else {
            appContainer.visibility = View.GONE
        }
    }

    companion object {
        val TAG: String = RunningAppDetails::class.java.simpleName
        const val ARG_PS_ITEM = "ps_item"

        @JvmStatic
        fun getInstance(processItem: ProcessItem): RunningAppDetails {
            return RunningAppDetails().apply {
                arguments = Bundle().apply { putParcelable(ARG_PS_ITEM, processItem) }
            }
        }
    }
}
