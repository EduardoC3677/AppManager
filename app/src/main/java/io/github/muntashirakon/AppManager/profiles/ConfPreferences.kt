// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles

import android.os.Bundle
import android.text.TextUtils
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.backup.BackupFlags
import io.github.muntashirakon.AppManager.profiles.struct.AppsBaseProfile
import io.github.muntashirakon.AppManager.profiles.struct.BaseProfile
import io.github.muntashirakon.AppManager.rules.RulesTypeSelectionDialogFragment
import io.github.muntashirakon.AppManager.users.UserInfo
import io.github.muntashirakon.AppManager.users.Users
import io.github.muntashirakon.AppManager.utils.ArrayUtils
import io.github.muntashirakon.AppManager.utils.TextUtilsCompat
import io.github.muntashirakon.AppManager.utils.Utils
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder
import io.github.muntashirakon.dialog.TextInputDialogBuilder
import io.github.muntashirakon.util.UiUtils
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

class ConfPreferences : PreferenceFragmentCompat() {
    private var mActivity: AppsBaseProfileActivity? = null
    private var mModel: AppsProfileViewModel? = null

    private val mStates = listOf(BaseProfile.STATE_ON, BaseProfile.STATE_OFF)
    private var mComponents: Array<String>? = null
    private var mAppOps: Array<String>? = null
    private var mPermissions: Array<String>? = null
    private var mBackupInfo: AppsBaseProfile.BackupInfo? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView: RecyclerView = view.findViewById(R.id.recycler_view)
        recyclerView.isFitsSystemWindows = true
        recyclerView.clipToPadding = false
        UiUtils.applyWindowInsetsAsPadding(recyclerView, false, true)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_profile_config, rootKey)
        preferenceManager.preferenceDataStore = ConfDataStore()
        mActivity = requireActivity() as AppsBaseProfileActivity
        if (mActivity!!.model == null) return
        mModel = mActivity!!.model
        val profileIdPref = findPreference<Preference>("profile_id")!!
        profileIdPref.summary = mModel!!.profileId
        profileIdPref.setOnPreferenceClickListener {
            Utils.copyToClipboard(mActivity, mModel!!.profileName, mModel!!.profileId)
            true
        }
        val commentPref = findPreference<Preference>("comment")!!
        commentPref.summary = mModel!!.comment
        commentPref.setOnPreferenceClickListener {
            TextInputDialogBuilder(mActivity!!, R.string.comment)
                .setTitle(R.string.comment)
                .setInputText(mModel!!.comment)
                .setPositiveButton(R.string.ok) { _, _, inputText, _ ->
                    mModel!!.comment = if (TextUtils.isEmpty(inputText)) null else inputText.toString()
                    commentPref.summary = mModel!!.comment
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            true
        }
        val statePref = findPreference<Preference>("state")!!
        val statesL = arrayOf(getString(R.string.on), getString(R.string.off))
        statePref.title = getString(R.string.process_state, statesL[mStates.indexOf(mModel!!.state)])
        statePref.setOnPreferenceClickListener {
            SearchableSingleChoiceDialogBuilder(mActivity!!, mStates, statesL)
                .setTitle(R.string.profile_state)
                .setSelection(mModel!!.state)
                .setOnSingleChoiceClickListener { dialog, which, _, isChecked ->
                    if (!isChecked) return@setOnSingleChoiceClickListener
                    mModel!!.state = mStates[which]
                    statePref.title = getString(R.string.process_state, statesL[which])
                    dialog.dismiss()
                }
                .show()
            true
        }
        val usersPref = findPreference<Preference>("users")!!
        handleUsersPref(usersPref)
        val componentsPref = findPreference<Preference>("components")!!
        updateComponentsPref(componentsPref)
        componentsPref.setOnPreferenceClickListener {
            TextInputDialogBuilder(mActivity!!, R.string.input_signatures)
                .setTitle(R.string.components)
                .setInputText(if (mComponents == null) "" else TextUtils.join(" ", mComponents!!))
                .setHelperText(R.string.input_signatures_description)
                .setPositiveButton(R.string.ok) { _, _, inputText, _ ->
                    if (!TextUtils.isEmpty(inputText)) {
                        val newComponents = inputText.toString().split("\s+".toRegex()).toTypedArray()
                        mModel!!.components = newComponents
                    } else mModel!!.components = null
                    updateComponentsPref(componentsPref)
                }
                .setNegativeButton(R.string.disable) { _, _, _, _ ->
                    mModel!!.components = null
                    updateComponentsPref(componentsPref)
                }
                .show()
            true
        }
        val appOpsPref = findPreference<Preference>("app_ops")!!
        updateAppOpsPref(appOpsPref)
        appOpsPref.setOnPreferenceClickListener {
            TextInputDialogBuilder(mActivity!!, R.string.input_app_ops)
                .setTitle(R.string.app_ops)
                .setInputText(if (mAppOps == null) "" else TextUtils.join(" ", mAppOps!!))
                .setHelperText(R.string.input_app_ops_description_profile)
                .setPositiveButton(R.string.ok) { _, _, inputText, _ ->
                    if (!TextUtils.isEmpty(inputText)) {
                        val newAppOps = inputText.toString().split("\s+".toRegex()).toTypedArray()
                        mModel!!.appOps = newAppOps
                    } else mModel!!.appOps = null
                    updateAppOpsPref(appOpsPref)
                }
                .setNegativeButton(R.string.disable) { _, _, _, _ ->
                    mModel!!.appOps = null
                    updateAppOpsPref(appOpsPref)
                }
                .show()
            true
        }
        val permissionsPref = findPreference<Preference>("permissions")!!
        updatePermissionsPref(permissionsPref)
        permissionsPref.setOnPreferenceClickListener {
            TextInputDialogBuilder(mActivity!!, R.string.input_permissions)
                .setTitle(R.string.declared_permission)
                .setInputText(if (mPermissions == null) "" else TextUtils.join(" ", mPermissions!!))
                .setHelperText(R.string.input_permissions_description)
                .setPositiveButton(R.string.ok) { _, _, inputText, _ ->
                    if (!TextUtils.isEmpty(inputText)) {
                        val newPermissions = inputText.toString().split("\s+".toRegex()).toTypedArray()
                        mModel!!.permissions = newPermissions
                    } else mModel!!.permissions = null
                    updatePermissionsPref(permissionsPref)
                }
                .setNegativeButton(R.string.disable) { _, _, _, _ ->
                    mModel!!.permissions = null
                    updatePermissionsPref(permissionsPref)
                }
                .show()
            true
        }
        val backupDataPref = findPreference<Preference>("backup_data")!!
        mBackupInfo = mModel!!.backupInfo
        backupDataPref.setSummary(if (mBackupInfo != null) R.string.enabled else R.string.disabled_app)
        backupDataPref.setOnPreferenceClickListener {
            val view = View.inflate(mActivity, R.layout.dialog_profile_backup_restore, null)
            val flags = if (mBackupInfo != null) BackupFlags(mBackupInfo!!.flags) else BackupFlags.fromPref()
            val backupFlags = AtomicInteger(flags.flags)
            view.findViewById<View>(R.id.dialog_button).setOnClickListener {
                val supportedBackupFlags = BackupFlags.getSupportedBackupFlagsAsArray()
                SearchableMultiChoiceDialogBuilder(requireActivity(), supportedBackupFlags,
                    BackupFlags.getFormattedFlagNames(requireContext(), supportedBackupFlags))
                    .setTitle(R.string.backup_options)
                    .addSelections(flags.flagsToCheckedIndexes(supportedBackupFlags))
                    .hideSearchBar(true)
                    .showSelectAll(false)
                    .setPositiveButton(R.string.save) { _, _, selectedItems ->
                        var flagsInt = 0
                        for (flag in selectedItems) {
                            flagsInt = flagsInt or flag
                        }
                        flags.flags = flagsInt
                        backupFlags.set(flags.flags)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
            val editText = view.findViewById<TextInputEditText>(android.R.id.input)
            if (mBackupInfo != null) {
                editText.setText(mBackupInfo!!.name)
            }
            MaterialAlertDialogBuilder(mActivity!!)
                .setTitle(R.string.backup_restore)
                .setView(view)
                .setPositiveButton(R.string.ok) { _, _ ->
                    if (mBackupInfo == null) {
                        mBackupInfo = AppsBaseProfile.BackupInfo()
                    }
                    val backupName = editText.text
                    val backupFlags1 = BackupFlags(backupFlags.get())
                    if (!TextUtils.isEmpty(backupName)) {
                        backupFlags1.addFlag(BackupFlags.BACKUP_MULTIPLE)
                        mBackupInfo!!.name = backupName.toString()
                    } else {
                        backupFlags1.removeFlag(BackupFlags.BACKUP_MULTIPLE)
                        mBackupInfo!!.name = null
                    }
                    mBackupInfo!!.flags = backupFlags1.flags
                    mModel!!.backupInfo = mBackupInfo
                    backupDataPref.setSummary(R.string.enabled)
                }
                .setNegativeButton(R.string.disable) { _, _ ->
                    mModel!!.backupInfo = null
                    mBackupInfo = null
                    backupDataPref.setSummary(R.string.disabled_app)
                }
                .show()
            true
        }
        val exportRulesPref = findPreference<Preference>("export_rules")!!
        val rulesCount = RulesTypeSelectionDialogFragment.RULE_TYPES.size
        val checkedItems = ArrayList<Int>(rulesCount)
        val selectedRules = updateExportRulesPref(exportRulesPref)
        for (i in 0 until rulesCount) checkedItems.add(1 shl i)
        exportRulesPref.setOnPreferenceClickListener {
            SearchableMultiChoiceDialogBuilder(mActivity!!, checkedItems, R.array.rule_types)
                .setTitle(R.string.options)
                .hideSearchBar(true)
                .addSelections(selectedRules)
                .setPositiveButton(R.string.ok) { _, _, selectedItems ->
                    var value = 0
                    for (item in selectedItems) value = value or item
                    if (value != 0) {
                        mModel!!.exportRules = value
                    } else mModel!!.exportRules = null
                    selectedRules.clear()
                    selectedRules.addAll(updateExportRulesPref(exportRulesPref))
                }
                .setNegativeButton(R.string.disable) { _, _, _ ->
                    mModel!!.exportRules = null
                    selectedRules.clear()
                    selectedRules.addAll(updateExportRulesPref(exportRulesPref))
                }
                .show()
            true
        }
        (findPreference<Preference>("freeze") as SwitchPreferenceCompat).isChecked = mModel!!.getBoolean("freeze", false)
        (findPreference<Preference>("force_stop") as SwitchPreferenceCompat).isChecked = mModel!!.getBoolean("force_stop", false)
        (findPreference<Preference>("clear_cache") as SwitchPreferenceCompat).isChecked = mModel!!.getBoolean("clear_cache", false)
        (findPreference<Preference>("clear_data") as SwitchPreferenceCompat).isChecked = mModel!!.getBoolean("clear_data", false)
        (findPreference<Preference>("block_trackers") as SwitchPreferenceCompat).isChecked = mModel!!.getBoolean("block_trackers", false)
        (findPreference<Preference>("save_apk") as SwitchPreferenceCompat).isChecked = mModel!!.getBoolean("save_apk", false)
        (findPreference<Preference>("allow_routine") as SwitchPreferenceCompat).isChecked = mModel!!.getBoolean("allow_routine", false)
    }

    private fun updateExportRulesPref(pref: Preference): List<Int> {
        var rules = mModel!!.exportRules
        val selectedRules = ArrayList<Int>()
        if (rules == null || rules == 0) pref.setSummary(R.string.disabled_app)
        else {
            val selectedRulesStr = ArrayList<String>()
            var i = 0
            while (rules != 0) {
                val flag = rules!! and (1 shl i).inv()
                if (flag != rules) {
                    selectedRulesStr.add(RulesTypeSelectionDialogFragment.RULE_TYPES[i].toString())
                    rules = flag
                    selectedRules.add(1 shl i)
                }
                ++i
            }
            pref.summary = TextUtils.join(", ", selectedRulesStr)
        }
        return selectedRules
    }

    private fun updateComponentsPref(pref: Preference) {
        mComponents = mModel!!.components
        if (mComponents == null || mComponents!!.isEmpty()) pref.setSummary(R.string.disabled_app)
        else {
            pref.summary = TextUtils.join(", ", mComponents!!)
        }
    }

    private fun updateAppOpsPref(pref: Preference) {
        mAppOps = mModel!!.appOps
        if (mAppOps == null || mAppOps!!.isEmpty()) pref.setSummary(R.string.disabled_app)
        else {
            pref.summary = TextUtils.join(", ", mAppOps!!)
        }
    }

    private fun updatePermissionsPref(pref: Preference) {
        mPermissions = mModel!!.permissions
        if (mPermissions == null || mPermissions!!.isEmpty()) pref.setSummary(R.string.disabled_app)
        else {
            pref.summary = TextUtils.join(", ", mPermissions!!)
        }
    }

    private var mSelectedUsers: MutableList<Int>? = null

    private fun handleUsersPref(pref: Preference) {
        val users = Users.getUsers()
        if (users.size > 1) {
            pref.isVisible = true
            val userNames = arrayOfNulls<CharSequence>(users.size)
            val userHandles = ArrayList<Int>(users.size)
            var i = 0
            for (info in users) {
                userNames[i] = info.toLocalizedString(requireContext())
                userHandles.add(info.id)
                ++i
            }
            mSelectedUsers = ArrayList()
            for (user in mModel!!.users) {
                mSelectedUsers!!.add(user)
            }
            mActivity!!.runOnUiThread {
                pref.summary = TextUtilsCompat.joinSpannable(", ", getUserInfo(users, mSelectedUsers!!))
                pref.setOnPreferenceClickListener {
                    SearchableMultiChoiceDialogBuilder(mActivity!!, userHandles, userNames)
                        .setTitle(R.string.select_user)
                        .addSelections(mSelectedUsers!!)
                        .showSelectAll(false)
                        .setPositiveButton(R.string.ok) { _, _, selectedUserHandles ->
                            if (selectedUserHandles.isEmpty()) {
                                mSelectedUsers = userHandles
                            } else mSelectedUsers = selectedUserHandles.toMutableList()
                            pref.summary = TextUtilsCompat.joinSpannable(", ", getUserInfo(users, mSelectedUsers!!))
                            mModel!!.users = ArrayUtils.convertToIntArray(mSelectedUsers)
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                    true
                }
            }
        } else {
            mActivity!!.runOnUiThread { pref.isVisible = false }
        }
    }

    private fun getUserInfo(userInfoList: List<UserInfo>, userHandles: List<Int>): List<CharSequence> {
        val userInfoOut = ArrayList<CharSequence>()
        for (info in userInfoList) {
            if (userHandles.contains(info.id)) {
                userInfoOut.add(info.toLocalizedString(requireContext()))
            }
        }
        return userInfoOut
    }

    inner class ConfDataStore : PreferenceDataStore() {
        override fun putBoolean(key: String, value: Boolean) {
            mModel!!.putBoolean(key, value)
        }

        override fun getBoolean(key: String, defValue: Boolean): Boolean {
            return mModel!!.getBoolean(key, defValue)
        }
    }
}
