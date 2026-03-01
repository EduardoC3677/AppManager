// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops

import android.app.Dialog
import android.os.Bundle
import android.os.PowerManager
import android.view.View
import androidx.annotation.UiThread
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.backup.dialog.BackupRestoreDialogFragment
import io.github.muntashirakon.AppManager.main.ApplicationItem
import io.github.muntashirakon.AppManager.utils.CpuUtils
import io.github.muntashirakon.AppManager.utils.PackageUtils
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder
import java.lang.ref.WeakReference
import java.util.concurrent.Future

class RestoreTasksDialogFragment : DialogFragment() {
    private var mActivity: OneClickOpsActivity? = null
    private var mFuture: Future<*>? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mActivity = requireActivity() as OneClickOpsActivity
        val view = View.inflate(mActivity, R.layout.dialog_restore_tasks, null)
        view.findViewById<View>(R.id.restore_all).setOnClickListener {
            mActivity!!.progressIndicator!!.show()
            mFuture?.cancel(true)
            val wakeLockRef = WeakReference(mActivity!!.wakeLock)
            mFuture = ThreadUtils.postOnBackgroundThread {
                val wakeLock = wakeLockRef.get()
                if (wakeLock != null && !wakeLock.isHeld) wakeLock.acquire()
                try {
                    val items = mutableListOf<ApplicationItem>()
                    val labels = mutableListOf<CharSequence>()
                    PackageUtils.getInstalledOrBackedUpApplicationsFromDb(requireContext(), false, true).forEach { item ->
                        if (ThreadUtils.isInterrupted()) return@postOnBackgroundThread
                        if (item.backup != null) {
                            items.add(item)
                            labels.add(item.label!!)
                        }
                    }
                    if (ThreadUtils.isInterrupted()) return@postOnBackgroundThread
                    ThreadUtils.postOnMainThread { runMultiChoiceDialog(items, labels) }
                } finally {
                    CpuUtils.releaseWakeLock(wakeLock)
                }
            }
        }
        view.findViewById<View>(R.id.restore_not_installed).setOnClickListener {
            mActivity!!.progressIndicator!!.show()
            mFuture?.cancel(true)
            val wakeLockRef = WeakReference(mActivity!!.wakeLock)
            mFuture = ThreadUtils.postOnBackgroundThread {
                val wakeLock = wakeLockRef.get()
                if (wakeLock != null && !wakeLock.isHeld) wakeLock.acquire()
                try {
                    val items = mutableListOf<ApplicationItem>()
                    val labels = mutableListOf<CharSequence>()
                    PackageUtils.getInstalledOrBackedUpApplicationsFromDb(requireContext(), false, true).forEach { item ->
                        if (ThreadUtils.isInterrupted()) return@postOnBackgroundThread
                        if (!item.isInstalled && item.backup != null) {
                            items.add(item)
                            labels.add(item.label!!)
                        }
                    }
                    if (ThreadUtils.isInterrupted()) return@postOnBackgroundThread
                    ThreadUtils.postOnMainThread { runMultiChoiceDialog(items, labels) }
                } finally {
                    CpuUtils.releaseWakeLock(wakeLock)
                }
            }
        }
        view.findViewById<View>(R.id.restore_latest).setOnClickListener {
            mActivity!!.progressIndicator!!.show()
            mFuture?.cancel(true)
            val wakeLockRef = WeakReference(mActivity!!.wakeLock)
            mFuture = ThreadUtils.postOnBackgroundThread {
                val wakeLock = wakeLockRef.get()
                if (wakeLock != null && !wakeLock.isHeld) wakeLock.acquire()
                try {
                    val items = mutableListOf<ApplicationItem>()
                    val labels = mutableListOf<CharSequence>()
                    PackageUtils.getInstalledOrBackedUpApplicationsFromDb(requireContext(), false, true).forEach { item ->
                        if (ThreadUtils.isInterrupted()) return@postOnBackgroundThread
                        if (item.isInstalled && item.backup != null && item.versionCode < item.backup!!.versionCode) {
                            items.add(item)
                            labels.add(item.label!!)
                        }
                    }
                    if (ThreadUtils.isInterrupted()) return@postOnBackgroundThread
                    ThreadUtils.postOnMainThread { runMultiChoiceDialog(items, labels) }
                } finally {
                    CpuUtils.releaseWakeLock(wakeLock)
                }
            }
        }
        return MaterialAlertDialogBuilder(requireActivity())
            .setView(view)
            .setTitle(R.string.restore)
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    override fun onDestroy() {
        mFuture?.cancel(true)
        super.onDestroy()
    }

    @UiThread
    private fun runMultiChoiceDialog(applicationItems: List<ApplicationItem>, applicationLabels: List<CharSequence>) {
        if (isDetached) return
        mActivity!!.progressIndicator!!.hide()
        SearchableMultiChoiceDialogBuilder(mActivity!!, applicationItems, applicationLabels)
            .addSelections(applicationItems)
            .setTitle(R.string.filtered_packages)
            .setPositiveButton(R.string.restore) { _, _, selectedItems ->
                if (isDetached) return@setPositiveButton
                val fragment = BackupRestoreDialogFragment.getInstance(PackageUtils.getUserPackagePairs(selectedItems), BackupRestoreDialogFragment.MODE_RESTORE)
                fragment.setOnActionBeginListener { mActivity!!.progressIndicator!!.show() }
                fragment.setOnActionCompleteListener { _, _ -> mActivity!!.progressIndicator!!.hide() }
                fragment.show(parentFragmentManager, BackupRestoreDialogFragment.TAG)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        const val TAG = "RestoreTasksDialogFragment"
    }
}
