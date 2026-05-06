// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings

import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.annotation.NonNull
import androidx.appcompat.app.AlertDialog
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.lifecycle.ViewModelProvider
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.transition.MaterialSharedAxis
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.settings.crypto.ImportExportKeyStoreDialogFragment
import io.github.muntashirakon.AppManager.utils.*
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder
import io.github.muntashirakon.dialog.TextInputDialogBuilder
import io.github.muntashirakon.util.UiUtils

class AdvancedPreferences : PreferenceFragment() {
    companion object {
        @JvmField
        val APK_NAME_FORMATS = arrayOf(
            "%label%",
            "%package_name%",
            "%version%",
            "%version_code%",
            "%min_sdk%",
            "%target_sdk%",
            "%datetime%"\n)

        private fun addChip(apkFormats: ChipGroup, text: CharSequence): Chip {
            val chip = Chip(apkFormats.context)
            chip.text = text
            apkFormats.addView(chip)
            return chip
        }
    }

    private var mThreadCount = 0
    private lateinit var mModel: MainPreferencesViewModel

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_advanced, rootKey)
        preferenceManager.preferenceDataStore = SettingsDataStore()
        mModel = ViewModelProvider(requireActivity())[MainPreferencesViewModel::class.java]
        // Selected users
        val usersPref = findPreference<Preference>("selected_users")!!
        usersPref.setOnPreferenceClickListener {
            mModel.loadAllUsers()
            true
        }
        // Saved apk name format
        val savedApkFormatPref = findPreference<Preference>("saved_apk_format")!!
        savedApkFormatPref.setOnPreferenceClickListener {
            val view = layoutInflater.inflate(R.layout.dialog_set_apk_format, null)
            val inputApkNameFormat = view.findViewById<TextInputEditText>(R.id.input_apk_name_format)
            inputApkNameFormat.setText(AppPref.getString(AppPref.PrefKey.PREF_SAVED_APK_FORMAT_STR))
            val apkNameFormats = view.findViewById<ChipGroup>(R.id.apk_name_formats)
            for (apkNameFormatStr in APK_NAME_FORMATS) {
                if ("%min_sdk%" == apkNameFormatStr && Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    // Old devices does not support min SDK
                    continue
                }
                addChip(apkNameFormats, apkNameFormatStr).setOnClickListener { v ->
                    val apkFormat = inputApkNameFormat.text
                    if (apkFormat != null) {
                        apkFormat.insert(inputApkNameFormat.selectionStart, (v as Chip).text)
                    }
                }
            }
            val dialog = MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.pref_saved_apk_name_format)
                .setView(view)
                .setPositiveButton(R.string.save) { dialog1, which ->
                    val apkFormat = inputApkNameFormat.text
                    if (!TextUtils.isEmpty(apkFormat)) {
                        AppPref.set(AppPref.PrefKey.PREF_SAVED_APK_FORMAT_STR, apkFormat.toString().trim { it <= ' ' })
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .create()
            dialog.setOnShowListener { dialog1 ->
                inputApkNameFormat.postDelayed({
                    inputApkNameFormat.requestFocus()
                    inputApkNameFormat.requestFocusFromTouch()
                    inputApkNameFormat.setSelection(inputApkNameFormat.length())
                    UiUtils.showKeyboard(inputApkNameFormat)
                }, 200)
            }
            dialog.show()
            true
        }
        // Thread count
        val threadCountPref = findPreference<Preference>("thread_count")!!
        mThreadCount = AppExecutor.getThreadCount()
        threadCountPref.summary = resources.getQuantityString(R.plurals.pref_thread_count_msg, mThreadCount, mThreadCount)
        threadCountPref.setOnPreferenceClickListener {
            TextInputDialogBuilder(requireActivity(), null)
                .setTitle(R.string.pref_thread_count)
                .setHelperText(getString(R.string.pref_thread_count_hint, Utils.getTotalCores()))
                .setInputText(mThreadCount.toString())
                .setInputInputType(InputType.TYPE_CLASS_NUMBER)
                .setInputImeOptions(EditorInfo.IME_ACTION_DONE or EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save) { dialog, which, inputText, isChecked ->
                    if (inputText != null && TextUtils.isDigitsOnly(inputText)) {
                        val c = Integer.decode(inputText.toString())
                        AppExecutor.setThreadCount(c)
                        mThreadCount = AppExecutor.getThreadCount()
                        threadCountPref.summary = resources.getQuantityString(R.plurals.pref_thread_count_msg, mThreadCount, mThreadCount)
                    }
                }
                .show()
            true
        }
        // ADB local server port
        val adbLsPort = findPreference<Preference>("adb_local_server_port")!!
        val port = Prefs.Misc.getAdbLocalServerPort()
        adbLsPort.summary = port.toString()
        adbLsPort.setOnPreferenceClickListener {
            TextInputDialogBuilder(requireActivity(), null)
                .setTitle(R.string.adb_local_server_port)
                .setInputText(port.toString())
                .setInputInputType(InputType.TYPE_CLASS_NUMBER)
                .setInputImeOptions(EditorInfo.IME_ACTION_DONE or EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save) { dialog, which, inputText, isChecked ->
                    if (inputText != null && TextUtils.isDigitsOnly(inputText)) {
                        val c = Integer.decode(inputText.toString())
                        Prefs.Misc.setAdbLocalServerPort(c)
                        adbLsPort.summary = c.toString()
                    }
                }
                .show()
            true
        }
        // Import/export App Manager's KeyStore
        findPreference<Preference>("import_export_keystore")!!
            .setOnPreferenceClickListener {
                val fragment = ImportExportKeyStoreDialogFragment()
                fragment.show(parentFragmentManager, ImportExportKeyStoreDialogFragment.TAG)
                true
            }
        // Send notifications to the connected device
        findPreference<SwitchPreferenceCompat>("send_notifications_to_connected_devices")!!
            .isChecked = Prefs.Misc.sendNotificationsToConnectedDevices()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mModel.selectUsers().observe(viewLifecycleOwner) { users ->
            if (users == null) return@observe
            val selectedUsers = Prefs.Misc.getSelectedUsers()
            val userIds = arrayOfNulls<Int>(users.size)
            val userInfo = arrayOfNulls<CharSequence>(users.size)
            val preselectedUserIds = mutableListOf<Int>()
            for (i in users.indices) {
                userIds[i] = users[i].id
                userInfo[i] = users[i].toLocalizedString(requireContext())
                if (selectedUsers == null || ArrayUtils.contains(selectedUsers, userIds[i]!!)) {
                    preselectedUserIds.add(userIds[i]!!)
                }
            }
            SearchableMultiChoiceDialogBuilder(requireActivity(), userIds as Array<Int>, userInfo as Array<CharSequence>)
                .setTitle(R.string.pref_selected_users)
                .addSelections(preselectedUserIds)
                .setPositiveButton(R.string.save) { dialog, which, selectedUserIds ->
                    if (selectedUserIds.isNotEmpty()) {
                        Prefs.Misc.setSelectedUsers(ArrayUtils.convertToIntArray(selectedUserIds))
                    } else {
                        Prefs.Misc.setSelectedUsers(null)
                    }
                    Utils.relaunchApp(requireActivity())
                }
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.use_default) { dialog, which, selectedUserIds ->
                    Prefs.Misc.setSelectedUsers(null)
                    Utils.relaunchApp(requireActivity())
                }
                .show()
        }
    }

    override fun getTitle(): Int {
        return R.string.pref_cat_advanced
    }
}
