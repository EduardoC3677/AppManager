// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings.crypto

import android.app.Dialog
import android.os.Bundle
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager.AM_KEYSTORE_FILE
import io.github.muntashirakon.AppManager.io.IoUtils
import io.github.muntashirakon.AppManager.utils.ExUtils
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import java.io.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ImportExportKeyStoreDialogFragment : DialogFragment() {
    companion object {
        const val TAG = "IEKeyStoreDialogFragment"\n}

    private lateinit var mActivity: FragmentActivity
    private val mExportKeyStore = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri == null) {
            dismiss()
            return@registerForActivityResult
        }
        ThreadUtils.postOnBackgroundThread {
            try {
                FileInputStream(AM_KEYSTORE_FILE).use { `is` ->
                    mActivity.contentResolver.openOutputStream(uri).use { os ->
                        if (os == null) throw IOException("Unable to open URI")
                        IoUtils.copy(`is`, os)
                        ThreadUtils.postOnMainThread {
                            UIUtils.displayShortToast(R.string.done)
                            ExUtils.exceptionAsIgnored { dismiss() }
                        }
                    }
                }
            } catch (e: IOException) {
                ThreadUtils.postOnMainThread {
                    UIUtils.displayShortToast(R.string.failed)
                    ExUtils.exceptionAsIgnored { dismiss() }
                }
            }
        }
    }
    private val mImportKeyStore = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) {
            dismiss()
            return@registerForActivityResult
        }
        MaterialAlertDialogBuilder(mActivity)
            .setTitle(R.string.import_keystore)
            .setMessage(R.string.confirm_import_keystore)
            .setPositiveButton(R.string.yes) { dialog, which ->
                ThreadUtils.postOnBackgroundThread {
                    // Rename old file that will be restored in case of error
                    val tmpFile = File(AM_KEYSTORE_FILE.absolutePath + ".tmp")
                    if (AM_KEYSTORE_FILE.exists()) {
                        AM_KEYSTORE_FILE.renameTo(tmpFile)
                    }
                    try {
                        mActivity.contentResolver.openInputStream(uri).use { `is` ->
                            FileOutputStream(AM_KEYSTORE_FILE).use { os ->
                                if (`is` == null) throw IOException("Unable to open URI")
                                IoUtils.copy(`is`, os)
                                if (KeyStoreManager.hasKeyStorePassword()) {
                                    val waitForKs = CountDownLatch(1)
                                    KeyStoreManager.inputKeyStorePassword(mActivity) { waitForKs.countDown() }
                                    waitForKs.await(2, TimeUnit.MINUTES)
                                    if (waitForKs.count == 1L) {
                                        throw Exception()
                                    }
                                }
                                KeyStoreManager.reloadKeyStore()
                                // TODO: 21/4/21 Only import the keys that we use instead of replacing the entire keystore
                                ThreadUtils.postOnMainThread {
                                    UIUtils.displayShortToast(R.string.done)
                                    ExUtils.exceptionAsIgnored { dismiss() }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (tmpFile.exists()) {
                            AM_KEYSTORE_FILE.delete()
                            tmpFile.renameTo(AM_KEYSTORE_FILE)
                            try {
                                KeyStoreManager.reloadKeyStore()
                            } catch (ignore: Exception) {
                            }
                        }
                        ThreadUtils.postOnMainThread {
                            UIUtils.displayShortToast(R.string.failed)
                            ExUtils.exceptionAsIgnored { dismiss() }
                        }
                    }
                }
            }
            .setNegativeButton(R.string.close) { dialog, which -> dismiss() }
            .setCancelable(false)
            .show()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mActivity = requireActivity()
        val builder = MaterialAlertDialogBuilder(mActivity)
            .setTitle(R.string.pref_import_export_keystore)
            .setMessage(R.string.choose_what_to_do)
            .setPositiveButton(R.string.pref_export, null)
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.pref_import, null)
        val alertDialog = builder.create()
        alertDialog.setOnShowListener { dialog ->
            val dialog1 = dialog as AlertDialog
            val exportButton = dialog1.getButton(AlertDialog.BUTTON_POSITIVE)
            val importButton = dialog1.getButton(AlertDialog.BUTTON_NEUTRAL)
            if (AM_KEYSTORE_FILE.exists()) {
                exportButton.setOnClickListener { v -> mExportKeyStore.launch(KeyStoreManager.AM_KEYSTORE_FILE_NAME) }
            }
            importButton.setOnClickListener { v -> mImportKeyStore.launch("application/*") }
        }
        return alertDialog
    }
}
