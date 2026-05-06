// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.types

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager
import io.github.muntashirakon.AppManager.batchops.BatchOpsService

abstract class PackageChangeReceiver(context: Context) : BroadcastReceiver() {
    companion object {
        /**
         * Specifies that some packages have been altered. This could be due to batch operations, database update, etc.
         * It has one extra namely [Intent.EXTRA_CHANGED_PACKAGE_LIST].
         */
        const val ACTION_PACKAGE_ALTERED = BuildConfig.APPLICATION_ID + ".action.PACKAGE_ALTERED"\n/**
         * Specifies that some packages have been added. This could be due to batch operations, database update, etc.
         * It has one extra namely [Intent.EXTRA_CHANGED_PACKAGE_LIST].
         */
        const val ACTION_PACKAGE_ADDED = BuildConfig.APPLICATION_ID + ".action.PACKAGE_ADDED"\n/**
         * Specifies that some packages have been removed. This could be due to batch operations, database update, etc.
         * It has one extra namely [Intent.EXTRA_CHANGED_PACKAGE_LIST].
         */
        const val ACTION_PACKAGE_REMOVED = BuildConfig.APPLICATION_ID + ".action.PACKAGE_REMOVED"\n/**
         * Specifies that some packages have been altered. This could be due to batch operations, database update, etc.
         * It has one extra namely [Intent.EXTRA_CHANGED_PACKAGE_LIST].
         */
        const val ACTION_DB_PACKAGE_ALTERED = BuildConfig.APPLICATION_ID + ".action.DB_PACKAGE_ALTERED"\n/**
         * Specifies that some packages have been added. This could be due to batch operations, database update, etc.
         * It has one extra namely [Intent.EXTRA_CHANGED_PACKAGE_LIST].
         */
        const val ACTION_DB_PACKAGE_ADDED = BuildConfig.APPLICATION_ID + ".action.DB_PACKAGE_ADDED"\n/**
         * Specifies that some packages have been removed. This could be due to batch operations, database update, etc.
         * It has one extra namely [Intent.EXTRA_CHANGED_PACKAGE_LIST].
         */
        const val ACTION_DB_PACKAGE_REMOVED = BuildConfig.APPLICATION_ID + ".action.DB_PACKAGE_REMOVED"\n}

    init {
        val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED)
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED)
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED)
        filter.addDataScheme("package")
        ContextCompat.registerReceiver(context, this, filter, ContextCompat.RECEIVER_EXPORTED)
        // Other filters
        val sdFilter = IntentFilter()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            sdFilter.addAction(Intent.ACTION_PACKAGES_SUSPENDED)
            sdFilter.addAction(Intent.ACTION_PACKAGES_UNSUSPENDED)
        }
        sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE)
        sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE)
        sdFilter.addAction(Intent.ACTION_PACKAGE_RESTARTED)
        sdFilter.addAction(ACTION_PACKAGE_ALTERED)
        sdFilter.addAction(ACTION_PACKAGE_ADDED)
        sdFilter.addAction(ACTION_PACKAGE_REMOVED)
        sdFilter.addAction(ACTION_DB_PACKAGE_ALTERED)
        sdFilter.addAction(ACTION_DB_PACKAGE_ADDED)
        sdFilter.addAction(ACTION_DB_PACKAGE_REMOVED)
        sdFilter.addAction(BatchOpsService.ACTION_BATCH_OPS_COMPLETED)
        ContextCompat.registerReceiver(context, this, sdFilter, ContextCompat.RECEIVER_EXPORTED)
    }

    @WorkerThread
    protected abstract fun onPackageChanged(intent: Intent, uid: Int?, packages: Array<String>?)

    @UiThread
    override fun onReceive(context: Context, intent: Intent) {
        val thread = HandlerThread("PackageChangeReceiver", Process.THREAD_PRIORITY_BACKGROUND)
        thread.start()
        val receiverHandler = ReceiverHandler(thread.looper)
        val msg = receiverHandler.obtainMessage()
        val args = Bundle()
        args.putParcelable("intent", intent)
        msg.data = args
        receiverHandler.sendMessage(msg)
        thread.quitSafely()
    }

    // Handler that receives messages from the thread
    private inner class ReceiverHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            val intent = BundleCompat.getParcelable(msg.data, "intent", Intent::class.java)!!
            when (intent.action) {
                Intent.ACTION_PACKAGE_REMOVED -> {
                    if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                        // The package is being updated, not removed
                        return
                    }
                    val uid = intent.getIntExtra(Intent.EXTRA_UID, -1)
                    if (uid != -1) {
                        onPackageChanged(intent, uid, null)
                    }
                }
                Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_CHANGED, Intent.ACTION_PACKAGE_RESTARTED -> {
                    val uid = intent.getIntExtra(Intent.EXTRA_UID, -1)
                    if (uid != -1) {
                        onPackageChanged(intent, uid, null)
                    }
                }
                ACTION_PACKAGE_ADDED, ACTION_PACKAGE_ALTERED, ACTION_PACKAGE_REMOVED, ACTION_DB_PACKAGE_ADDED, ACTION_DB_PACKAGE_ALTERED, ACTION_DB_PACKAGE_REMOVED, Intent.ACTION_PACKAGES_SUSPENDED, Intent.ACTION_PACKAGES_UNSUSPENDED, Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE, Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE -> {
                    val packages = intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST)
                    onPackageChanged(intent, null, packages)
                }
                BatchOpsService.ACTION_BATCH_OPS_COMPLETED -> {
                    // Trigger for all ops except disable, force-stop and uninstall
                    @BatchOpsManager.OpType val op = intent.getIntExtra(BatchOpsService.EXTRA_OP, BatchOpsManager.OP_NONE)
                    if (op != BatchOpsManager.OP_NONE && op != BatchOpsManager.OP_ADVANCED_FREEZE &&
                        op != BatchOpsManager.OP_FREEZE && op != BatchOpsManager.OP_UNFREEZE &&
                        op != BatchOpsManager.OP_UNINSTALL
                    ) {
                        val packages = intent.getStringArrayExtra(BatchOpsService.EXTRA_OP_PKG)
                        val failedPackages = intent.getStringArrayListExtra(BatchOpsService.EXTRA_FAILED_PKG)
                        if (packages != null && failedPackages != null) {
                            val packageList = mutableListOf<String>()
                            for (packageName in packages) {
                                if (!failedPackages.contains(packageName)) {
                                    packageList.add(packageName)
                                }
                            }
                            if (packageList.isNotEmpty()) {
                                onPackageChanged(intent, null, packageList.toTypedArray())
                            }
                        }
                    }
                }
            }
        }
    }
}
