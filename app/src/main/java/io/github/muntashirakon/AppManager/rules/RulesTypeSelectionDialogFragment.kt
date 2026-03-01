// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules

import android.annotation.UserIdInt
import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.os.UserHandleHidden
import androidx.annotation.IntDef
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.settings.SettingsActivity
import io.github.muntashirakon.AppManager.utils.CpuUtils
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder
import java.io.IOException
import java.lang.ref.WeakReference

class RulesTypeSelectionDialogFragment : DialogFragment() {
    @IntDef(value = [MODE_IMPORT, MODE_EXPORT])
    @Retention(AnnotationRetention.SOURCE)
    annotation class Mode

    private var mActivity: FragmentActivity? = null
    private var mUri: Uri? = null
    private var mPackages: List<String>? = null
    private var mSelectedTypes: HashSet<RuleType>? = null
    @UserIdInt
    private var mUserIds: IntArray? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mActivity = requireActivity()
        val args = requireArguments()
        val mode = args.getInt(ARG_MODE, MODE_EXPORT)
        mPackages = args.getStringArrayList(ARG_PKG)
        mUri = BundleCompat.getParcelable(args, ARG_URI, Uri::class.java)
        mUserIds = args.getIntArray(ARG_USERS) ?: intArrayOf(UserHandleHidden.myUserId())
        if (mUri == null) return super.onCreateDialog(savedInstanceState)
        val ruleIndexes = RULE_TYPES.indices.toList()
        mSelectedTypes = HashSet(RULE_TYPES.size)
        return SearchableMultiChoiceDialogBuilder(mActivity!!, ruleIndexes, R.array.rule_types)
            .setTitle(if (mode == MODE_IMPORT) R.string.import_options else R.string.export_options)
            .addSelections(ruleIndexes)
            .setPositiveButton(if (mode == MODE_IMPORT) R.string.pref_import else R.string.pref_export) { _, _, selections ->
                selections.forEach { mSelectedTypes!!.add(RULE_TYPES[it]) }
                Log.d("TestImportExport", "Types: $mSelectedTypes
URI: $mUri")
                if (mActivity is SettingsActivity) (mActivity as SettingsActivity).progressIndicator.show()
                if (mode == MODE_IMPORT) handleImport() else handleExport()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    private fun handleExport() {
        if (mUri == null) return
        val activityRef = WeakReference(mActivity)
        ThreadUtils.postOnBackgroundThread {
            val wakeLock = CpuUtils.getPartialWakeLock("rules_exporter")
            wakeLock.acquire()
            try {
                RulesExporter(ArrayList(mSelectedTypes!!), mPackages, mUserIds!!).saveRules(mUri!!)
                ThreadUtils.postOnMainThread { UIUtils.displayShortToast(R.string.the_export_was_successful) }
            } catch (e: IOException) {
                ThreadUtils.postOnMainThread { UIUtils.displayLongToast(R.string.export_failed) }
            } finally {
                CpuUtils.releaseWakeLock(wakeLock)
            }
            hideProgressBar(activityRef)
        }
    }

    private fun handleImport() {
        if (mUri == null) return
        val activityRef = WeakReference(mActivity)
        ThreadUtils.postOnBackgroundThread {
            val wakeLock = CpuUtils.getPartialWakeLock("rules_exporter")
            wakeLock.acquire()
            try {
                RulesImporter(ArrayList(mSelectedTypes!!), mUserIds!!).use { importer ->
                    importer.addRulesFromUri(mUri!!)
                    mPackages?.let { importer.setPackagesToImport(it) }
                    importer.applyRules(true)
                    ThreadUtils.postOnMainThread { UIUtils.displayShortToast(R.string.the_import_was_successful) }
                }
            } catch (e: IOException) {
                ThreadUtils.postOnMainThread { UIUtils.displayLongToast(R.string.import_failed) }
            } finally {
                CpuUtils.releaseWakeLock(wakeLock)
            }
            hideProgressBar(activityRef)
        }
    }

    private fun hideProgressBar(activityRef: WeakReference<FragmentActivity>) {
        if (activityRef.get() is SettingsActivity) {
            ThreadUtils.postOnMainThread {
                activityRef.get()?.let { (it as SettingsActivity).progressIndicator.hide() }
            }
        }
    }

    companion object {
        const val TAG = "RulesTypeSelectionDialogFragment"
        const val ARG_MODE = "ARG_MODE"
        const val ARG_URI = "ARG_URI"
        const val ARG_PKG = "ARG_PKG"
        const val ARG_USERS = "ARG_USERS"

        const val MODE_IMPORT = 1
        const val MODE_EXPORT = 2

        @JvmField
        val RULE_TYPES = arrayOf(RuleType.ACTIVITY, RuleType.SERVICE, RuleType.RECEIVER, RuleType.PROVIDER, RuleType.APP_OP, RuleType.PERMISSION)
    }
}
