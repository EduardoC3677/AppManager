// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer

import android.app.Application
import android.app.Dialog
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.core.os.BundleCompat
import androidx.core.util.Pair
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputLayout
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat
import io.github.muntashirakon.AppManager.compat.ManifestCompat
import io.github.muntashirakon.AppManager.compat.UserHandleHidden
import io.github.muntashirakon.AppManager.db.utils.AppDb
import io.github.muntashirakon.AppManager.lifecycle.SingleLiveEvent
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.settings.InstallerPreferences.*
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.users.UserInfo
import io.github.muntashirakon.AppManager.users.Users
import io.github.muntashirakon.AppManager.utils.*
import io.github.muntashirakon.AppManager.utils.UIUtils.getSecondaryText
import io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText
import io.github.muntashirakon.adapters.SelectedArrayAdapter
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder
import io.github.muntashirakon.dialog.TextInputDialogBuilder
import io.github.muntashirakon.view.TextInputLayoutCompat
import io.github.muntashirakon.widget.MaterialSpinner
import java.text.Collator
import java.util.*

class InstallerOptionsFragment : DialogFragment() {
    fun interface OnClickListener {
        fun onClick(dialog: DialogInterface?, which: Int, options: InstallerOptions?)
    }

    private var mModel: InstallerOptionsViewModel? = null
    private var mDialogView: View? = null
    private var mUserSelectionSpinner: MaterialSpinner? = null
    private var mInstallLocationSpinner: MaterialSpinner? = null
    private var mPackageSourceSpinner: MaterialSpinner? = null
    private var mInstallerAppLayout: TextInputLayout? = null
    private var mInstallerAppField: EditText? = null
    private var mBlockTrackersSwitch: MaterialSwitch? = null
    private var mClickListener: OnClickListener? = null
    private var mPackageName: String? = null
    private var mIsTestOnly: Boolean = false
    private var mOptions: InstallerOptions? = null
    private var mPm: PackageManager? = null

    fun setOnClickListener(clickListener: OnClickListener?) {
        mClickListener = clickListener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mModel = ViewModelProvider(this).get(InstallerOptionsViewModel::class.java)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mPackageName = requireArguments().getString(ARG_PACKAGE_NAME)
        mIsTestOnly = requireArguments().getBoolean(ARG_TEST_ONLY_APP, true)
        mOptions = BundleCompat.getParcelable(requireArguments(), ARG_REF_INSTALLER_OPTIONS, InstallerOptions::class.java)!!
        mDialogView = View.inflate(requireActivity(), R.layout.dialog_installer_options, null)
        mUserSelectionSpinner = mDialogView!!.findViewById(R.id.user)
        mInstallLocationSpinner = mDialogView!!.findViewById(R.id.install_location)
        mPackageSourceSpinner = mDialogView!!.findViewById(R.id.package_source)
        mInstallerAppLayout = mDialogView!!.findViewById(R.id.installer)
        mInstallerAppField = mInstallerAppLayout!!.editText!!
        val disableVerificationSwitch: MaterialSwitch = mDialogView!!.findViewById(R.id.action_disable_verification)
        val setOriginSwitch: MaterialSwitch = mDialogView!!.findViewById(R.id.action_set_origin)
        val reqUpdateOwnershipSwitch: MaterialSwitch = mDialogView!!.findViewById(R.id.action_update_ownership)
        val signApkSwitch: MaterialSwitch = mDialogView!!.findViewById(R.id.action_sign_apk)
        val forceDexOptSwitch: MaterialSwitch = mDialogView!!.findViewById(R.id.action_optimize)
        mBlockTrackersSwitch = mDialogView!!.findViewById(R.id.action_block_trackers)

        mPm = requireContext().packageManager
        val canInstallForOtherUsers = SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.INTERACT_ACROSS_USERS_FULL)
        val selectedUser = getSelectedUserId(canInstallForOtherUsers)
        val canBlockTrackers = SelfPermissions.canModifyAppComponentStates(selectedUser, mPackageName, mIsTestOnly)

        initUserSpinner(canInstallForOtherUsers)
        initInstallLocationSpinner()
        initPackageSourceSpinner()
        initInstallerAppSpinner()

        disableVerificationSwitch.isEnabled = SelfPermissions.isSystemOrRootOrShell()
        disableVerificationSwitch.isChecked = mOptions!!.isDisableApkVerification
        disableVerificationSwitch.setOnCheckedChangeListener { _, isChecked -> mOptions!!.isDisableApkVerification = isChecked }
        setOriginSwitch.isChecked = mOptions!!.isSetOriginatingPackage
        setOriginSwitch.setOnCheckedChangeListener { _, isChecked -> mOptions!!.isSetOriginatingPackage = isChecked }
        reqUpdateOwnershipSwitch.visibility = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) View.VISIBLE else View.GONE
        reqUpdateOwnershipSwitch.isChecked = mOptions!!.requestUpdateOwnership
        reqUpdateOwnershipSwitch.setOnCheckedChangeListener { _, isChecked -> mOptions!!.requestUpdateOwnership = isChecked }
        signApkSwitch.isChecked = mOptions!!.isSignApkFiles
        signApkSwitch.setOnCheckedChangeListener { _, isChecked -> mOptions!!.isSignApkFiles = isChecked }
        forceDexOptSwitch.visibility = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) View.VISIBLE else View.GONE
        forceDexOptSwitch.isChecked = mOptions!!.isForceDexOpt
        forceDexOptSwitch.setOnCheckedChangeListener { _, isChecked -> mOptions!!.isForceDexOpt = isChecked }
        mBlockTrackersSwitch!!.isChecked = canBlockTrackers && mOptions!!.isBlockTrackers
        mBlockTrackersSwitch!!.isEnabled = canBlockTrackers
        mBlockTrackersSwitch!!.setOnCheckedChangeListener { _, isChecked -> mOptions!!.isBlockTrackers = isChecked }

        return MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.installer_options)
            .setView(mDialogView)
            .setCancelable(false)
            .setPositiveButton(R.string.ok) { dialog, which -> mClickListener?.onClick(dialog, which, mOptions) }
            .setNegativeButton(R.string.cancel) { dialog, which -> mClickListener?.onClick(dialog, which, null) }
            .create()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return mDialogView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mModel!!.packageNameLabelPairLiveData.observe(viewLifecycleOwner) { displayInstallerAppSelectionDialog(it) }
    }

    private fun getSelectedUserId(canInstallForOtherUsers: Boolean): Int {
        return if (canInstallForOtherUsers) mOptions!!.userId else UserHandleHidden.myUserId()
    }

    private fun initUserSpinner(canInstallForOtherUsers: Boolean) {
        val selectedUser = getSelectedUserId(canInstallForOtherUsers)
        val userInfoList = Users.getUsers()
        val userNames = arrayOfNulls<CharSequence>(userInfoList.size + 1)
        val userIds = arrayOfNulls<Int>(userInfoList.size + 1)
        userNames[0] = getString(R.string.backup_all_users)
        userIds[0] = UserHandleHidden.USER_ALL
        var selectedUserPosition = 0
        for (i in userInfoList.indices) {
            val info = userInfoList[i]
            userNames[i + 1] = info.toLocalizedString(requireContext())
            userIds[i + 1] = info.id
            if (selectedUser == info.id) selectedUserPosition = i + 1
        }
        val userAdapter = SelectedArrayAdapter(requireContext(), io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item_small, userNames)
        mUserSelectionSpinner!!.adapter = userAdapter
        mUserSelectionSpinner!!.setSelection(selectedUserPosition)
        mUserSelectionSpinner!!.setOnItemClickListener { _, _, position, _ ->
            mOptions!!.userId = userIds[position]!!
            val canBlockTrackers = SelfPermissions.canModifyAppComponentStates(getSelectedUserId(canInstallForOtherUsers), mPackageName, mIsTestOnly)
            mBlockTrackersSwitch!!.isChecked = canBlockTrackers && mOptions!!.isBlockTrackers
            mBlockTrackersSwitch!!.isEnabled = canBlockTrackers
        }
        mUserSelectionSpinner!!.isEnabled = canInstallForOtherUsers
    }

    private fun initInstallLocationSpinner() {
        val installLocation = mOptions!!.installLocation
        var installLocationPosition = 0
        val installLocationNames = arrayOfNulls<CharSequence>(INSTALL_LOCATIONS.size)
        for (i in INSTALL_LOCATIONS.indices) {
            installLocationNames[i] = getString(INSTALL_LOCATION_NAMES[i])
            if (INSTALL_LOCATIONS[i] == installLocation) installLocationPosition = i
        }
        val installerLocationAdapter = SelectedArrayAdapter(requireContext(), io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item_small, installLocationNames)
        mInstallLocationSpinner!!.adapter = installerLocationAdapter
        mInstallLocationSpinner!!.setSelection(installLocationPosition)
        mInstallLocationSpinner!!.setOnItemClickListener { _, _, position, _ -> mOptions!!.installLocation = INSTALL_LOCATIONS[position] }
    }

    private fun initPackageSourceSpinner() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            mPackageSourceSpinner!!.visibility = View.GONE
            return
        }
        val pkgSource = mOptions!!.packageSource
        val pkgSourceTexts = Array(PKG_SOURCES_NAMES.size) { i -> getString(PKG_SOURCES_NAMES[i]) }
        val pkgSourceAdapter = SelectedArrayAdapter(requireContext(), io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item_small, pkgSourceTexts)
        mPackageSourceSpinner!!.adapter = pkgSourceAdapter
        mPackageSourceSpinner!!.setSelection(pkgSource)
        mPackageSourceSpinner!!.setOnItemClickListener { _, _, position, _ -> mOptions!!.packageSource = PKG_SOURCES[position] }
    }

    private fun initInstallerAppSpinner() {
        val canInstallApps = SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.INSTALL_PACKAGES)
        val installer = if (canInstallApps) mOptions!!.getInstallerNameNonNull() else BuildConfig.APPLICATION_ID
        mInstallerAppField!!.setText(PackageUtils.getPackageLabel(mPm!!, installer))
        TextInputLayoutCompat.fixEndIcon(mInstallerAppLayout!!)
        mInstallerAppLayout!!.isEnabled = canInstallApps
        mInstallerAppLayout!!.setEndIconOnClickListener {
            MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.installer_app)
                .setMessage(R.string.installer_app_message)
                .setPositiveButton(R.string.choose) { _, _ -> mModel!!.loadPackageNameLabelPair() }
                .setNegativeButton(R.string.specify_custom_name) { _, _ ->
                    TextInputDialogBuilder(requireActivity(), R.string.installer_app)
                        .setTitle(R.string.installer_app)
                        .setInputText(mOptions!!.installerName)
                        .setPositiveButton(R.string.ok) { _, _, inputText, _ ->
                            inputText?.toString()?.trim()?.let {
                                if (it.isNotEmpty()) {
                                    mOptions!!.installerName = it
                                    mInstallerAppField!!.setText(PackageUtils.getPackageLabel(mPm!!, it))
                                }
                            }
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
                .setNeutralButton(R.string.reset_to_default) { _, _ ->
                    val installerApp = Prefs.Installer.getInstallerPackageName()
                    mOptions!!.installerName = installerApp
                    mInstallerAppField!!.setText(PackageUtils.getPackageLabel(mPm!!, installerApp))
                }
                .show()
        }
    }

    private fun displayInstallerAppSelectionDialog(appInfo: List<Pair<String, CharSequence>>) {
        val items = ArrayList<String>(appInfo.size)
        val itemNames = ArrayList<CharSequence>(appInfo.size)
        for (pair in appInfo) {
            items.add(pair.first!!)
            itemNames.add(SpannableStringBuilder(pair.second)
                .append("
")
                .append(getSecondaryText(requireContext(), getSmallerText(pair.first!!))))
        }
        SearchableSingleChoiceDialogBuilder(requireActivity(), items, itemNames)
            .setTitle(R.string.installer_app)
            .setSelection(mOptions!!.installerName)
            .setPositiveButton(R.string.save) { _, _, selectedInstallerApp ->
                selectedInstallerApp?.trim()?.let {
                    if (it.isNotEmpty()) {
                        mOptions!!.installerName = it
                        mInstallerAppField!!.setText(PackageUtils.getPackageLabel(mPm!!, it))
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    class InstallerOptionsViewModel(application: Application) : AndroidViewModel(application) {
        val packageNameLabelPairLiveData: MutableLiveData<List<Pair<String, CharSequence>>> = SingleLiveEvent()

        fun loadPackageNameLabelPair() {
            ThreadUtils.postOnBackgroundThread {
                val appList = AppDb().allApplications
                val packageNameLabelMap = mutableMapOf<String, CharSequence>()
                for (app in appList) packageNameLabelMap[app.packageName] = app.packageLabel
                val appInfo = packageNameLabelMap.map { Pair(it.key, it.value) }.toMutableList()
                val collator = Collator.getInstance()
                appInfo.sortWith { o1, o2 -> collator.compare(o1!!.second.toString(), o2!!.second.toString()) }
                packageNameLabelPairLiveData.postValue(appInfo)
            }
        }
    }

    companion object {
        val TAG: String = InstallerOptionsFragment::class.java.simpleName
        private const val ARG_PACKAGE_NAME = "pkg"
        private const val ARG_TEST_ONLY_APP = "test_only"
        private const val ARG_REF_INSTALLER_OPTIONS = "ref_opt"

        @JvmStatic
        fun getInstance(packageName: String?, isTestOnly: Boolean?, options: InstallerOptions, clickListener: OnClickListener?): InstallerOptionsFragment {
            return InstallerOptionsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PACKAGE_NAME, packageName)
                    isTestOnly?.let { putBoolean(ARG_TEST_ONLY_APP, it) }
                    putParcelable(ARG_REF_INSTALLER_OPTIONS, options)
                }
                setOnClickListener(clickListener)
            }
        }
    }
}
