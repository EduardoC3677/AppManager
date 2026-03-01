// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings

import android.os.Bundle
import android.os.UserHandleHidden
import android.text.SpannableStringBuilder
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.util.Pair
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialSharedAxis
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.db.utils.AppDb
import io.github.muntashirakon.AppManager.oneclickops.ItemCount
import io.github.muntashirakon.AppManager.rules.RulesTypeSelectionDialogFragment
import io.github.muntashirakon.AppManager.rules.compontents.ExternalComponentsImporter
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.users.Users
import io.github.muntashirakon.AppManager.utils.*
import io.github.muntashirakon.dialog.SearchableItemsDialogBuilder
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder
import io.github.muntashirakon.view.ProgressIndicatorCompat
import java.util.*
import java.util.concurrent.Future

class ImportExportRulesPreferences : PreferenceFragment() {
    companion object {
        private const val MIME_JSON = "application/json"
        private const val MIME_TSV = "text/tab-separated-values"
        private const val MIME_XML = "text/xml"
    }

    private val mUserHandle = UserHandleHidden.myUserId()
    private lateinit var mActivity: SettingsActivity
    private var importExistingFuture: Future<*>? = null

    private val mExportRules = registerForActivityResult(ActivityResultContracts.CreateDocument(MIME_TSV)) { uri ->
        if (uri == null) {
            // Back button pressed.
            return@registerForActivityResult
        }
        val dialogFragment = RulesTypeSelectionDialogFragment()
        val args = Bundle()
        args.putInt(RulesTypeSelectionDialogFragment.ARG_MODE, RulesTypeSelectionDialogFragment.MODE_EXPORT)
        args.putParcelable(RulesTypeSelectionDialogFragment.ARG_URI, uri)
        args.putStringArrayList(RulesTypeSelectionDialogFragment.ARG_PKG, null)
        args.putIntArray(RulesTypeSelectionDialogFragment.ARG_USERS, Users.getUsersIds())
        dialogFragment.arguments = args
        dialogFragment.show(parentFragmentManager, RulesTypeSelectionDialogFragment.TAG)
    }

    private val mImportRules = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) {
            // Back button pressed.
            return@registerForActivityResult
        }
        val dialogFragment = RulesTypeSelectionDialogFragment()
        val args = Bundle()
        args.putInt(RulesTypeSelectionDialogFragment.ARG_MODE, RulesTypeSelectionDialogFragment.MODE_IMPORT)
        args.putParcelable(RulesTypeSelectionDialogFragment.ARG_URI, uri)
        args.putStringArrayList(RulesTypeSelectionDialogFragment.ARG_PKG, null)
        args.putIntArray(RulesTypeSelectionDialogFragment.ARG_USERS, Users.getUsersIds())
        dialogFragment.arguments = args
        dialogFragment.show(parentFragmentManager, RulesTypeSelectionDialogFragment.TAG)
    }

    private val mImportFromWatt = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) {
            // Back button pressed.
            return@registerForActivityResult
        }
        ProgressIndicatorCompat.setVisibility(mActivity.progressIndicator, true)
        ThreadUtils.postOnBackgroundThread {
            val failedFiles = ExternalComponentsImporter.importFromWatt(uri, mUserHandle)
            ThreadUtils.postOnMainThread { displayImportExternalRulesFailedPackagesDialog(failedFiles) }
        }
    }

    private val mImportFromBlocker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) {
            // Back button pressed.
            return@registerForActivityResult
        }
        ProgressIndicatorCompat.setVisibility(mActivity.progressIndicator, true)
        ThreadUtils.postOnBackgroundThread {
            val failedFiles = ExternalComponentsImporter.importFromBlocker(uri, mUserHandle)
            ThreadUtils.postOnMainThread { displayImportExternalRulesFailedPackagesDialog(failedFiles) }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_rules_import_export)
        preferenceManager.preferenceDataStore = SettingsDataStore()
        mActivity = requireActivity() as SettingsActivity
        findPreference<Preference>("export")!!
            .setOnPreferenceClickListener {
                val fileName = "app_manager_rules_export-" + DateUtils.formatDateTime(mActivity, System.currentTimeMillis()) + ".am.tsv"
                mExportRules.launch(fileName)
                true
            }
        findPreference<Preference>("import")!!
            .setOnPreferenceClickListener {
                mImportRules.launch(MIME_TSV)
                true
            }
        findPreference<Preference>("import_existing")!!
            .setOnPreferenceClickListener {
                MaterialAlertDialogBuilder(requireActivity())
                    .setTitle(R.string.pref_import_existing)
                    .setMessage(R.string.apply_to_system_apps_question)
                    .setPositiveButton(R.string.no) { dialog, which -> importExistingRules(false) }
                    .setNegativeButton(R.string.yes) { dialog, which -> importExistingRules(true) }
                    .show()
                true
            }
        findPreference<Preference>("import_watt")!!
            .setOnPreferenceClickListener {
                mImportFromWatt.launch(MIME_XML)
                true
            }
        findPreference<Preference>("import_blocker")!!
            .setOnPreferenceClickListener {
                mImportFromBlocker.launch(MIME_JSON)
                true
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)
    }

    override fun onDestroy() {
        if (importExistingFuture != null) {
            importExistingFuture!!.cancel(true)
        }
        super.onDestroy()
    }

    override fun getTitle(): Int {
        return R.string.pref_import_export_blocking_rules
    }

    private fun importExistingRules(systemApps: Boolean) {
        if (!SelfPermissions.canModifyAppComponentStates(UserHandleHidden.myUserId(), null, true)) {
            UIUtils.displayShortToast(R.string.only_works_in_root_or_adb_mode)
            return
        }
        ProgressIndicatorCompat.setVisibility(mActivity.progressIndicator, true)
        importExistingFuture = ThreadUtils.postOnBackgroundThread {
            val itemCounts = mutableListOf<ItemCount>()
            for (app in AppDb().allInstalledApplications) {
                if (ThreadUtils.isInterrupted()) return@postOnBackgroundThread
                if (!systemApps && app.isSystemApp) continue
                val itemCount = ItemCount()
                itemCount.packageName = app.packageName
                itemCount.packageLabel = app.packageLabel
                itemCount.count = PackageUtils.getUserDisabledComponentsForPackage(app.packageName, mUserHandle).size
                if (itemCount.count > 0) itemCounts.add(itemCount)
            }
            ThreadUtils.postOnMainThread { displayImportExistingRulesPackageSelectionDialog(itemCounts) }
        }
    }

    private fun displayImportExistingRulesPackageSelectionDialog(itemCounts: List<ItemCount>) {
        if (itemCounts.isEmpty()) {
            ProgressIndicatorCompat.setVisibility(mActivity.progressIndicator, false)
            UIUtils.displayShortToast(R.string.no_matching_package_found)
            return
        }
        val packages = mutableListOf<String>()
        val packagesWithItemCounts = arrayOfNulls<CharSequence>(itemCounts.size)
        for (i in itemCounts.indices) {
            val itemCount = itemCounts[i]
            packages.add(itemCount.packageName)
            packagesWithItemCounts[i] = SpannableStringBuilder(itemCount.packageLabel).append("
")
                .append(
                    UIUtils.getSmallerText(
                        UIUtils.getSecondaryText(
                            mActivity, resources
                                .getQuantityString(
                                    R.plurals.no_of_components, itemCount.count,
                                    itemCount.count
                                )
                        )
                    )
                )
        }
        ProgressIndicatorCompat.setVisibility(mActivity.progressIndicator, false)
        SearchableMultiChoiceDialogBuilder(requireActivity(), packages, packagesWithItemCounts as Array<CharSequence>)
            .setTitle(R.string.filtered_packages)
            .setPositiveButton(R.string.apply) { dialog, which, selectedPackages ->
                ProgressIndicatorCompat.setVisibility(mActivity.progressIndicator, true)
                ThreadUtils.postOnBackgroundThread {
                    val failedPackages = ExternalComponentsImporter
                        .applyFromExistingBlockList(selectedPackages, mUserHandle)
                    ThreadUtils.postOnMainThread { displayImportExistingRulesFailedPackagesDialog(failedPackages) }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun displayImportExistingRulesFailedPackagesDialog(failedPackages: List<String>) {
        if (isDetached) {
            if (failedPackages.isEmpty()) {
                UIUtils.displayShortToast(R.string.the_import_was_successful)
            } else {
                UIUtils.displayShortToast(R.string.failed)
            }
            return
        }
        ProgressIndicatorCompat.setVisibility(mActivity.progressIndicator, false)
        if (failedPackages.isEmpty()) {
            UIUtils.displayShortToast(R.string.the_import_was_successful)
            return
        }
        SearchableItemsDialogBuilder(requireActivity(), failedPackages)
            .setTitle(R.string.failed_packages)
            .setNegativeButton(R.string.ok, null)
            .show()
    }

    private fun displayImportExternalRulesFailedPackagesDialog(failedFiles: List<String>) {
        if (isDetached) {
            if (failedFiles.isEmpty()) {
                UIUtils.displayShortToast(R.string.the_import_was_successful)
            } else {
                UIUtils.displayLongToastPl(R.plurals.failed_to_import_files, failedFiles.size, failedFiles.size)
            }
            return
        }
        ProgressIndicatorCompat.setVisibility(mActivity.progressIndicator, false)
        if (failedFiles.isEmpty()) {
            UIUtils.displayShortToast(R.string.the_import_was_successful)
            return
        }
        SearchableItemsDialogBuilder(requireActivity(), failedFiles)
            .setTitle(
                resources.getQuantityString(
                    R.plurals.failed_to_import_files, failedFiles.size,
                    failedFiles.size
                )
            )
            .setNegativeButton(R.string.close, null)
            .show()
    }
}
