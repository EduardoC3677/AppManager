// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.annotation.UiThread
import androidx.appcompat.app.AlertDialog
import androidx.collection.ArrayMap
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialSharedAxis
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.backup.BackupFlags
import io.github.muntashirakon.AppManager.backup.BackupUtils
import io.github.muntashirakon.AppManager.backup.CryptoUtils
import io.github.muntashirakon.AppManager.backup.convert.ImportType
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager
import io.github.muntashirakon.AppManager.batchops.BatchOpsService
import io.github.muntashirakon.AppManager.batchops.BatchQueueItem
import io.github.muntashirakon.AppManager.batchops.struct.BatchBackupImportOptions
import io.github.muntashirakon.AppManager.crypto.RSACrypto
import io.github.muntashirakon.AppManager.settings.crypto.AESCryptoSelectionDialogFragment
import io.github.muntashirakon.AppManager.settings.crypto.ECCCryptoSelectionDialogFragment
import io.github.muntashirakon.AppManager.settings.crypto.OpenPgpKeySelectionDialogFragment
import io.github.muntashirakon.AppManager.settings.crypto.RSACryptoSelectionDialogFragment
import io.github.muntashirakon.AppManager.utils.Paths
import io.github.muntashirakon.AppManager.utils.UIUtils.getSecondaryText
import io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText
import io.github.muntashirakon.dialog.DialogTitleBuilder
import io.github.muntashirakon.dialog.SearchableItemsDialogBuilder
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder
import java.util.concurrent.atomic.AtomicReference

class BackupRestorePreferences : PreferenceFragment() {
    companion object {
        private val ENCRYPTION = arrayOf(
            CryptoUtils.MODE_NO_ENCRYPTION,
            CryptoUtils.MODE_OPEN_PGP,
            CryptoUtils.MODE_AES,
            CryptoUtils.MODE_RSA,
            CryptoUtils.MODE_ECC
        )

        @StringRes
        private val ENCRYPTION_NAMES = arrayOf(
            R.string.none,
            R.string.open_pgp_provider,
            R.string.aes,
            R.string.rsa,
            R.string.ecc
        )
    }

    private lateinit var mActivity: SettingsActivity
    private var mCurrentCompressionMethod: String? = null
    private var mBackupVolume: Uri? = null
    @ImportType
    private var mImportType = 0
    private var mDeleteBackupsAfterImport = false
    private lateinit var mModel: MainPreferencesViewModel

    private val mSafSelectBackupVolume = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult
            val treeUri = data.data ?: return@registerForActivityResult
            val takeFlags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            requireContext().contentResolver.takePersistableUriPermission(treeUri, takeFlags)
        } finally {
            // Display backup volumes again
            mModel.loadStorageVolumes()
        }
    }
    private val mSafSelectImportDirectory = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val treeUri = data.data ?: return@registerForActivityResult
        val takeFlags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION
                or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        requireContext().contentResolver.takePersistableUriPermission(treeUri, takeFlags)
        startImportOperation(mImportType, treeUri, mDeleteBackupsAfterImport)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_backup_restore, rootKey)
        preferenceManager.preferenceDataStore = SettingsDataStore()
        mModel = ViewModelProvider(requireActivity())[MainPreferencesViewModel::class.java]
        mActivity = requireActivity() as SettingsActivity
        // Backup compression method
        mCurrentCompressionMethod = Prefs.BackupRestore.getCompressionMethod()
        val compressionMethod = findPreference<Preference>("backup_compression_method")!!
        compressionMethod.summary = BackupUtils.getReadableTarType(mCurrentCompressionMethod)
        compressionMethod.setOnPreferenceClickListener {
            SearchableSingleChoiceDialogBuilder(mActivity, BackupUtils.TAR_TYPES, BackupUtils.TAR_TYPES_READABLE)
                .setTitle(R.string.pref_compression_method)
                .setSelection(mCurrentCompressionMethod)
                .setPositiveButton(R.string.save) { dialog, which, selectedTarType ->
                    if (selectedTarType != null) {
                        mCurrentCompressionMethod = selectedTarType
                        Prefs.BackupRestore.setCompressionMethod(mCurrentCompressionMethod!!)
                        compressionMethod.summary = BackupUtils.getReadableTarType(mCurrentCompressionMethod)
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            true
        }
        // Backup flags
        val flags = BackupFlags.fromPref()
        findPreference<Preference>("backup_flags")!!.setOnPreferenceClickListener {
            val supportedBackupFlags = BackupFlags.getSupportedBackupFlagsAsArray()
            SearchableMultiChoiceDialogBuilder(
                requireActivity(),
                supportedBackupFlags,
                BackupFlags.getFormattedFlagNames(requireContext(), supportedBackupFlags)
            )
                .setTitle(R.string.backup_options)
                .addSelections(flags.flagsToCheckedIndexes(supportedBackupFlags))
                .hideSearchBar(true)
                .showSelectAll(false)
                .setPositiveButton(R.string.save) { dialog, which, selectedItems ->
                    var flagsInt = 0
                    for (flag in selectedItems) {
                        flagsInt = flagsInt or flag
                    }
                    flags.flags = flagsInt
                    Prefs.BackupRestore.setBackupFlags(flags.flags)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            true
        }
        // Keystore toggle
        val backupKeyStore = findPreference<SwitchPreferenceCompat>("backup_android_keystore")!!
        backupKeyStore.isChecked = Prefs.BackupRestore.backupAppsWithKeyStore()
        // Encryption
        findPreference<Preference>("encryption")!!.setOnPreferenceClickListener {
            val encryptionNamesText = Array<CharSequence>(ENCRYPTION_NAMES.size) { i -> getString(ENCRYPTION_NAMES[i]) }
            SearchableSingleChoiceDialogBuilder(mActivity, ENCRYPTION, encryptionNamesText)
                .setTitle(R.string.encryption)
                .setSelection(Prefs.Encryption.getEncryptionMode())
                .setOnSingleChoiceClickListener { dialog, which, encryptionMode, isChecked ->
                    if (!isChecked) return@setOnSingleChoiceClickListener
                    when (encryptionMode) {
                        CryptoUtils.MODE_NO_ENCRYPTION -> Prefs.Encryption.setEncryptionMode(encryptionMode)
                        CryptoUtils.MODE_AES -> {
                            val fragment = AESCryptoSelectionDialogFragment()
                            fragment.show(parentFragmentManager, AESCryptoSelectionDialogFragment.TAG)
                        }
                        CryptoUtils.MODE_RSA -> {
                            val fragment = RSACryptoSelectionDialogFragment.getInstance(RSACrypto.RSA_KEY_ALIAS)
                            fragment.setOnKeyPairUpdatedListener { keyPair, certificateBytes ->
                                if (keyPair != null) {
                                    Prefs.Encryption.setEncryptionMode(CryptoUtils.MODE_RSA)
                                }
                            }
                            fragment.show(parentFragmentManager, RSACryptoSelectionDialogFragment.TAG)
                        }
                        CryptoUtils.MODE_ECC -> {
                            val fragment = ECCCryptoSelectionDialogFragment()
                            fragment.setOnKeyPairUpdatedListener { keyPair, certificateBytes ->
                                if (keyPair != null) {
                                    Prefs.Encryption.setEncryptionMode(CryptoUtils.MODE_ECC)
                                }
                            }
                            fragment.show(parentFragmentManager, RSACryptoSelectionDialogFragment.TAG)
                        }
                        CryptoUtils.MODE_OPEN_PGP -> {
                            Prefs.Encryption.setEncryptionMode(encryptionMode)
                            val fragment = OpenPgpKeySelectionDialogFragment()
                            fragment.show(parentFragmentManager, OpenPgpKeySelectionDialogFragment.TAG)
                        }
                    }
                }
                .setPositiveButton(R.string.ok, null)
                .show()
            true
        }
        // Backup volume
        mBackupVolume = Prefs.Storage.getVolumePath()
        findPreference<Preference>("backup_volume")!!
            .setOnPreferenceClickListener {
                mModel.loadStorageVolumes()
                true
            }
        // Import backups
        findPreference<Preference>("import_backups")!!
            .setOnPreferenceClickListener {
                SearchableItemsDialogBuilder(mActivity, R.array.import_backup_options)
                    .setTitle(
                        DialogTitleBuilder(mActivity)
                            .setTitle(R.string.pref_import_backups)
                            .setSubtitle(R.string.pref_import_backups_hint)
                            .build()
                    )
                    .setOnItemClickListener { dialog, which, item ->
                        mImportType = which
                        val path = when (mImportType) {
                            ImportType.OAndBackup -> "oandbackups"\nImportType.TitaniumBackup -> "TitaniumBackup"\nImportType.SwiftBackup -> "SwiftBackup"\nelse -> ""\n}
                        MaterialAlertDialogBuilder(mActivity)
                            .setTitle(R.string.pref_import_backups)
                            .setMessage(R.string.import_backups_warning_delete_backups_after_import)
                            .setPositiveButton(R.string.no) { dialog1, which1 ->
                                mDeleteBackupsAfterImport = false
                                mSafSelectImportDirectory.launch(getSafIntent(path))
                            }
                            .setNegativeButton(R.string.yes) { dialog1, which1 ->
                                mDeleteBackupsAfterImport = true
                                mSafSelectImportDirectory.launch(getSafIntent(path))
                            }
                            .setNeutralButton(R.string.cancel, null)
                            .show()
                    }
                    .setNegativeButton(R.string.close, null)
                    .show()
                true
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mModel.getStorageVolumesLiveData().observe(viewLifecycleOwner) { storageLocations ->
            displayVolumeSelectionDialog(storageLocations)
        }
    }

    override fun getTitle(): Int {
        return R.string.backup_restore
    }

    @UiThread
    private fun startImportOperation(@ImportType backupType: Int, uri: Uri, removeImported: Boolean) {
        // Start batch ops service
        val input = BatchOpsManager.Result(emptyList())
        val options = BatchBackupImportOptions(backupType, uri, removeImported)
        val item = BatchQueueItem.getBatchOpQueue(
            BatchOpsManager.OP_IMPORT_BACKUPS,
            input.failedPackages, input.associatedUsers, options
        )
        val intent = BatchOpsService.getServiceIntent(mActivity, item)
        ContextCompat.startForegroundService(mActivity, intent)
    }

    private fun displayVolumeSelectionDialog(storageLocations: ArrayMap<String, Uri>) {
        // TODO: 13/8/22 Move to a separate BottomSheet dialog fragment
        val alertDialog = AtomicReference<AlertDialog>(null)
        val titleBuilder = DialogTitleBuilder(mActivity)
            .setTitle(R.string.backup_volume)
            .setSubtitle(R.string.backup_volume_dialog_description)
            .setStartIcon(R.drawable.ic_zip_disk)
            .setEndIcon(R.drawable.ic_add) { v ->
                MaterialAlertDialogBuilder(mActivity)
                    .setTitle(R.string.notice)
                    .setMessage(R.string.notice_saf)
                    .setPositiveButton(R.string.go) { dialog1, which1 ->
                        if (alertDialog.get() != null) {
                            alertDialog.get().dismiss()
                        }
                        mSafSelectBackupVolume.launch(getSafIntent("AppManager"))
                    }
                    .setNeutralButton(R.string.cancel, null)
                    .show()
            }

        if (storageLocations.isEmpty()) {
            alertDialog.set(
                MaterialAlertDialogBuilder(mActivity)
                    .setCustomTitle(titleBuilder.build())
                    .setMessage(R.string.no_volumes_found)
                    .setNegativeButton(R.string.ok, null)
                    .show()
            )
            return
        }
        val backupVolumes = arrayOfNulls<Uri>(storageLocations.size)
        val backupVolumesStr = arrayOfNulls<CharSequence>(storageLocations.size)
        for (i in 0 until storageLocations.size) {
            backupVolumes[i] = storageLocations.valueAt(i)
            backupVolumesStr[i] = SpannableStringBuilder(storageLocations.keyAt(i)).append("\n")
                .append(getSecondaryText(mActivity, getSmallerText(backupVolumes[i]!!.path)))
        }
        alertDialog.set(
            SearchableSingleChoiceDialogBuilder(mActivity, backupVolumes as Array<Uri>, backupVolumesStr as Array<CharSequence>)
                .setTitle(titleBuilder.build())
                .setSelection(mBackupVolume)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save) { dialog, which, selectedBackupVolume ->
                    mBackupVolume = selectedBackupVolume
                    val lastBackupVolume = Prefs.Storage.getVolumePath()
                    if (lastBackupVolume != mBackupVolume) {
                        Prefs.Storage.setVolumePath(mBackupVolume.toString())
                        mModel.reloadApps()
                    }
                }
                .show())
    }

    private fun getSafIntent(path: String): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            .putExtra("android.provider.extra.SHOW_ADVANCED", true)
            .putExtra("android.provider.extra.INITIAL_URI", io.github.muntashirakon.io.Paths.getPrimaryPath(path).getUri())
    }
}
