// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops

import android.app.Dialog
import android.os.Bundle
import android.os.PowerManager
import android.text.SpannableStringBuilder
import android.view.View
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.backup.BackupManager
import io.github.muntashirakon.AppManager.backup.dialog.BackupRestoreDialogFragment
import io.github.muntashirakon.AppManager.db.entity.Backup
import io.github.muntashirakon.AppManager.main.ApplicationItem
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.settings.FeatureController
import io.github.muntashirakon.AppManager.usage.AppUsageStatsManager
import io.github.muntashirakon.AppManager.usage.TimeInterval
import io.github.muntashirakon.AppManager.utils.*
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder
import io.github.muntashirakon.io.DirectoryUtils
import io.github.muntashirakon.io.Paths
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.concurrent.Future

class BackupTasksDialogFragment : DialogFragment() {
    private var mActivity: OneClickOpsActivity? = null
    private var mFuture: Future<*>? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mActivity = requireActivity() as OneClickOpsActivity
        val view = View.inflate(requireContext(), R.layout.dialog_backup_tasks, null)
        view.findViewById<View>(R.id.backup_all).setOnClickListener {
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
                        if (item.isInstalled) {
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
        view.findViewById<View>(R.id.redo_existing_backups).setOnClickListener {
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
                        if (item.isInstalled && item.backup != null) {
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
        view.findViewById<View>(R.id.backup_apps_without_backup).setOnClickListener {
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
                        if (item.isInstalled && item.backup == null) {
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
        view.findViewById<View>(R.id.verify_and_redo_backups).setOnClickListener {
            mActivity!!.progressIndicator!!.show()
            mFuture?.cancel(true)
            val wakeLockRef = WeakReference(mActivity!!.wakeLock)
            mFuture = ThreadUtils.postOnBackgroundThread {
                val wakeLock = wakeLockRef.get()
                if (wakeLock != null && !wakeLock.isHeld) wakeLock.acquire()
                try {
                    val items = mutableListOf<ApplicationItem>()
                    val labels = mutableListOf<CharSequence>()
                    val bm = BackupManager()
                    PackageUtils.getInstalledOrBackedUpApplicationsFromDb(requireContext(), false, true).forEach { item ->
                        if (ThreadUtils.isInterrupted()) return@postOnBackgroundThread
                        val backup = item.backup
                        if (backup != null && item.isInstalled) {
                            try { bm.verify(backup.uuid) }
                            catch (e: Throwable) {
                                items.add(item)
                                labels.add(SpannableStringBuilder(backup.label).append(LangUtils.getSeparatorString()).append(backup.backupName).append('
').append(UIUtils.getSmallerText(UIUtils.getSecondaryText(mActivity!!, SpannableStringBuilder(backup.packageName).append('
').append(e.message)))))
                            }
                        }
                    }
                    if (ThreadUtils.isInterrupted()) return@postOnBackgroundThread
                    ThreadUtils.postOnMainThread { runMultiChoiceDialog(items, labels) }
                } finally {
                    CpuUtils.releaseWakeLock(wakeLock)
                }
            }
        }
        view.findViewById<View>(R.id.backup_apps_with_changes).setOnClickListener {
            mActivity!!.progressIndicator!!.show()
            mFuture?.cancel(true)
            val wakeLockRef = WeakReference(mActivity!!.wakeLock)
            mFuture = ThreadUtils.postOnBackgroundThread {
                val wakeLock = wakeLockRef.get()
                if (wakeLock != null && !wakeLock.isHeld) wakeLock.acquire()
                try {
                    val items = mutableListOf<ApplicationItem>()
                    val labels = mutableListOf<CharSequence>()
                    val ignored = setOf("cache", "code_cache", "no_backup")
                    val hasUsage = FeatureController.isUsageAccessEnabled() && SelfPermissions.checkUsageStatsPermission()
                    val bm = BackupManager()
                    PackageUtils.getInstalledOrBackedUpApplicationsFromDb(requireContext(), false, true).forEach { item ->
                        if (ThreadUtils.isInterrupted()) return@postOnBackgroundThread
                        val backup = item.backup ?: return@forEach
                        if (!item.isInstalled) return@forEach
                        val needSourceUpdate = item.versionCode > backup.versionCode || item.lastUpdateTime > backup.backupTime
                        if (needSourceUpdate || (hasUsage && AppUsageStatsManager.getLastActivityTime(item.packageName!!, TimeInterval(backup.backupTime, System.currentTimeMillis())) > backup.backupTime) || isDataDirectoryChanged(backup, ignored) || !isVerified(bm, backup)) {
                            items.add(item)
                            labels.add(SpannableStringBuilder().append(backup.label).append(LangUtils.getSeparatorString()).append(backup.backupName).append('
').append(UIUtils.getSmallerText(UIUtils.getSecondaryText(mActivity!!, backup.packageName))))
                        }
                    }
                    if (ThreadUtils.isInterrupted()) return@postOnBackgroundThread
                    ThreadUtils.postOnMainThread { runMultiChoiceDialog(items, labels) }
                } finally {
                    CpuUtils.releaseWakeLock(wakeLock)
                }
            }
        }
        return MaterialAlertDialogBuilder(mActivity!!)
            .setView(view)
            .setTitle(R.string.back_up)
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    @UiThread
    private fun runMultiChoiceDialog(applicationItems: List<ApplicationItem>, applicationLabels: List<CharSequence>) {
        if (isDetached) return
        mActivity!!.progressIndicator!!.hide()
        SearchableMultiChoiceDialogBuilder(mActivity!!, applicationItems, applicationLabels)
            .addSelections(applicationItems)
            .setTitle(R.string.filtered_packages)
            .setPositiveButton(R.string.back_up) { _, _, selectedItems ->
                if (isDetached) return@setPositiveButton
                val fragment = BackupRestoreDialogFragment.getInstance(PackageUtils.getUserPackagePairs(selectedItems), BackupRestoreDialogFragment.MODE_BACKUP)
                fragment.setOnActionBeginListener { mActivity!!.progressIndicator!!.show() }
                fragment.setOnActionCompleteListener { _, _ -> mActivity!!.progressIndicator!!.hide() }
                fragment.show(parentFragmentManager, BackupRestoreDialogFragment.TAG)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroy() {
        mFuture?.cancel(true)
        super.onDestroy()
    }

    private fun isVerified(backupManager: BackupManager, backup: Backup): Boolean {
        return try { backupManager.verify(backup.uuid); true } catch (ignore: Throwable) { false }
    }

    private fun isDataDirectoryChanged(backup: Backup, ignoredDirs: Set<String>): Boolean {
        return try {
            backup.item.metadata.metadata.dataDirs.any { DirectoryUtils.isDirectoryChanged(Paths.get(it), backup.backupTime, 3, ignoredDirs) }
        } catch (ignore: IOException) { false }
    }

    companion object {
        const val TAG = "BackupTasksDialogFragment"
    }
}
