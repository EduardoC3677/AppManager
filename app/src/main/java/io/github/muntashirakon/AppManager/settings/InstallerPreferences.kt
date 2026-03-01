// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.UserHandleHidden
import android.text.InputType
import android.text.SpannableStringBuilder
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.util.Pair
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.lifecycle.ViewModelProvider
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialSharedAxis
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.apk.signing.Signer
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.utils.*
import io.github.muntashirakon.AppManager.utils.UIUtils.getSecondaryText
import io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText
import io.github.muntashirakon.dialog.ScrollableDialogBuilder
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder
import io.github.muntashirakon.dialog.TextInputDialogBuilder
import io.github.muntashirakon.preference.TopSwitchPreference

class InstallerPreferences : PreferenceFragment() {
    companion object {
        @JvmField
        val INSTALL_LOCATIONS = arrayOf(
            PackageInfo.INSTALL_LOCATION_AUTO,
            PackageInfo.INSTALL_LOCATION_INTERNAL_ONLY,
            PackageInfo.INSTALL_LOCATION_PREFER_EXTERNAL
        )

        @JvmField
        val INSTALL_LOCATION_NAMES = intArrayOf(
            R.string.auto,  // PackageInfo.INSTALL_LOCATION_AUTO
            R.string.install_location_internal_only,  // PackageInfo.INSTALL_LOCATION_INTERNAL_ONLY
            R.string.install_location_prefer_external // PackageInfo.INSTALL_LOCATION_PREFER_EXTERNAL
        )

        @SuppressLint("InlinedApi")
        @JvmField
        val PKG_SOURCES = arrayOf(
            PackageInstaller.PACKAGE_SOURCE_UNSPECIFIED,
            PackageInstaller.PACKAGE_SOURCE_OTHER,
            PackageInstaller.PACKAGE_SOURCE_STORE,
            PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE,
            PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE
        )

        @JvmField
        val PKG_SOURCES_NAMES = intArrayOf(
            R.string._undefined,  // PackageInstaller.PACKAGE_SOURCE_UNSPECIFIED
            R.string.package_source_other,  // PackageInstaller.PACKAGE_SOURCE_OTHER
            R.string.package_source_store,  // PackageInstaller.PACKAGE_SOURCE_STORE
            R.string.package_source_local_file,  // PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE
            R.string.package_source_downloaded_file // PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE
        )
    }

    private lateinit var mActivity: SettingsActivity
    private lateinit var mPm: PackageManager
    private var mInstallerApp: String? = null
    private var mInstallerAppPref: Preference? = null
    private lateinit var mModel: MainPreferencesViewModel

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_installer, rootKey)
        preferenceManager.preferenceDataStore = SettingsDataStore()
        mModel = ViewModelProvider(requireActivity())[MainPreferencesViewModel::class.java]
        mActivity = requireActivity() as SettingsActivity
        mPm = mActivity.packageManager
        val isInstallerEnabled = FeatureController.isInstallerEnabled()
        val catGeneral = requirePreference<PreferenceCategory>("cat_general")
        val catAdvanced = requirePreference<PreferenceCategory>("cat_advanced")
        val useInstaller = requirePreference<TopSwitchPreference>("use_installer")
        useInstaller.isChecked = isInstallerEnabled
        useInstaller.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
            val isEnabled = newValue as Boolean
            enablePrefs(isEnabled, catGeneral, catAdvanced)
            FeatureController.getInstance().modifyState(FeatureController.FEAT_INSTALLER, isEnabled)
            true
        }
        enablePrefs(isInstallerEnabled, catGeneral, catAdvanced)

        // Set installation locations
        val installLocationPref = findPreference<Preference>("installer_install_location")!!
        installLocationPref.summary = getString(INSTALL_LOCATION_NAMES[Prefs.Installer.getInstallLocation()])
        installLocationPref.setOnPreferenceClickListener {
            val installLocationTexts = Array<CharSequence>(INSTALL_LOCATION_NAMES.size) { i -> getString(INSTALL_LOCATION_NAMES[i]) }
            val defaultChoice = Prefs.Installer.getInstallLocation()
            SearchableSingleChoiceDialogBuilder(requireActivity(), INSTALL_LOCATIONS, installLocationTexts)
                .setTitle(R.string.install_location)
                .setSelection(defaultChoice)
                .setPositiveButton(R.string.save) { dialog, which, newInstallLocation ->
                    Prefs.Installer.setInstallLocation(newInstallLocation!!)
                    installLocationPref.summary = getString(INSTALL_LOCATION_NAMES[newInstallLocation])
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            true
        }
        // Set installer app
        mInstallerAppPref = findPreference("installer_installer_app")!!
        mInstallerAppPref!!.isEnabled = SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.INSTALL_PACKAGES)
        mInstallerApp = Prefs.Installer.getInstallerPackageName()
        mInstallerAppPref!!.summary = PackageUtils.getPackageLabel(mPm, mInstallerApp)
        mInstallerAppPref!!.setOnPreferenceClickListener {
            MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.installer_app)
                .setMessage(R.string.installer_app_message)
                .setPositiveButton(R.string.choose) { dialog1, which1 ->
                    mActivity.progressIndicator.show()
                    mModel.loadPackageNameLabelPair()
                }
                .setNegativeButton(R.string.specify_custom_name) { dialog, which ->
                    TextInputDialogBuilder(requireActivity(), R.string.installer_app)
                        .setTitle(R.string.installer_app)
                        .setInputText(mInstallerApp)
                        .setInputInputType(InputType.TYPE_CLASS_TEXT)
                        .setInputImeOptions(EditorInfo.IME_ACTION_DONE or EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING)
                        .setPositiveButton(R.string.ok) { dialog1, which1, inputText, isChecked ->
                            if (inputText == null) return@setPositiveButton
                            mInstallerApp = inputText.toString().trim()
                            Prefs.Installer.setInstallerPackageName(mInstallerApp!!)
                            mInstallerAppPref!!.summary = PackageUtils.getPackageLabel(mPm, mInstallerApp)
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
                .setNeutralButton(R.string.reset_to_default) { dialog, which ->
                    mInstallerApp = BuildConfig.APPLICATION_ID
                    Prefs.Installer.setInstallerPackageName(mInstallerApp!!)
                    mInstallerAppPref!!.summary = PackageUtils.getPackageLabel(mPm, mInstallerApp)
                }
                .show()
            true
        }
        // Disable verification
        val disableVerification = findPreference<SwitchPreferenceCompat>("installer_disable_verification")!!
        disableVerification.isEnabled = SelfPermissions.isSystemOrRootOrShell()
        disableVerification.isChecked = Prefs.Installer.isDisableApkVerification()
        // Update ownership
        val updateOwnership = findPreference<SwitchPreferenceCompat>("installer_update_ownership")!!
        updateOwnership.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        updateOwnership.isChecked = Prefs.Installer.requestUpdateOwnership()
        // Package source
        val pkgSource = findPreference<Preference>("installer_default_pkg_source")!!
        pkgSource.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        pkgSource.summary = getString(PKG_SOURCES_NAMES[Prefs.Installer.getPackageSource()])
        pkgSource.setOnPreferenceClickListener {
            val pkgSourceTexts = Array<CharSequence>(PKG_SOURCES_NAMES.size) { i -> getString(PKG_SOURCES_NAMES[i]) }
            val defaultChoice = Prefs.Installer.getPackageSource()
            SearchableSingleChoiceDialogBuilder(requireActivity(), PKG_SOURCES, pkgSourceTexts)
                .setTitle(R.string.pref_default_package_source)
                .setSelection(defaultChoice)
                .setPositiveButton(R.string.save) { dialog, which, newPkgSource ->
                    Prefs.Installer.setPackageSource(newPkgSource!!)
                    pkgSource.summary = getString(PKG_SOURCES_NAMES[newPkgSource])
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            true
        }
        // Set origin
        val setOrigin = findPreference<SwitchPreferenceCompat>("installer_set_origin")!!
        setOrigin.isChecked = Prefs.Installer.isSetOriginatingPackage()
        // Sign apk before installing
        val signApk = findPreference<SwitchPreferenceCompat>("installer_sign_apk")!!
        signApk.isChecked = Prefs.Installer.canSignApk()
        signApk.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, enabled ->
            if (enabled as Boolean && !Signer.canSign()) {
                ScrollableDialogBuilder(requireActivity())
                    .setTitle(R.string.pref_sign_apk_no_signing_key)
                    .setMessage(R.string.pref_sign_apk_error_signing_key_not_added)
                    .enableAnchors()
                    .setPositiveButton(R.string.add) { dialog, which, isChecked ->
                        val intent = Intent(Intent.ACTION_VIEW)
                            .setData(Uri.parse("app-manager://settings/apk_signing_prefs/signing_keys"))
                        startActivity(intent)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                return@OnPreferenceChangeListener false
            }
            true
        }
        val forceDexOpt = findPreference<SwitchPreferenceCompat>("installer_force_dex_opt")!!
        forceDexOpt.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
        forceDexOpt.isChecked = Prefs.Installer.forceDexOpt()
        // Display changes
        findPreference<SwitchPreferenceCompat>("installer_display_changes")!!
            .isChecked = Prefs.Installer.displayChanges()
        // Block trackers
        val blockTrackersPref = findPreference<SwitchPreferenceCompat>("installer_block_trackers")!!
        blockTrackersPref.isVisible = SelfPermissions.canModifyAppComponentStates(UserHandleHidden.myUserId(), null, true)
        blockTrackersPref.isChecked = Prefs.Installer.blockTrackers()
        // Running installer in the background
        val backgroundPref = findPreference<SwitchPreferenceCompat>("installer_always_on_background")!!
        backgroundPref.isVisible = Utils.canDisplayNotification(requireContext())
        backgroundPref.isChecked = Prefs.Installer.installInBackground()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Observe installer app selection
        mModel.getPackageNameLabelPairLiveData().observe(viewLifecycleOwner) { appInfo -> displayInstallerAppSelectionDialog(appInfo) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)
    }

    override fun getTitle(): Int {
        return R.string.installer
    }

    fun displayInstallerAppSelectionDialog(appInfo: List<Pair<String, CharSequence>>) {
        val items = ArrayList<String>(appInfo.size)
        val itemNames = ArrayList<CharSequence>(appInfo.size)
        for (pair in appInfo) {
            items.add(pair.first)
            itemNames.add(
                SpannableStringBuilder(pair.second)
                    .append("
")
                    .append(getSecondaryText(requireContext(), getSmallerText(pair.first)))
            )
        }
        mActivity.progressIndicator.hide()
        SearchableSingleChoiceDialogBuilder(requireActivity(), items, itemNames)
            .setTitle(R.string.installer_app)
            .setSelection(mInstallerApp)
            .setPositiveButton(R.string.save) { dialog, which, selectedInstallerApp ->
                if (selectedInstallerApp != null) {
                    mInstallerApp = selectedInstallerApp
                    Prefs.Installer.setInstallerPackageName(mInstallerApp!!)
                    mInstallerAppPref!!.summary = PackageUtils.getPackageLabel(mPm, mInstallerApp)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
