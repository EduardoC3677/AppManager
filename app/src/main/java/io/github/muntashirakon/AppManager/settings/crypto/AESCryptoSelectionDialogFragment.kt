// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings.crypto

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.TextUtils
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import aosp.libcore.util.HexEncoding
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.backup.CryptoUtils
import io.github.muntashirakon.AppManager.crypto.AESCrypto.AES_KEY_ALIAS
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager
import io.github.muntashirakon.AppManager.crypto.ks.SecretKeyCompat
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.AppManager.utils.Utils
import io.github.muntashirakon.dialog.TextInputDialogBuilder
import io.github.muntashirakon.dialog.TextInputDropdownDialogBuilder
import java.nio.CharBuffer
import java.security.SecureRandom
import javax.crypto.spec.SecretKeySpec

class AESCryptoSelectionDialogFragment : DialogFragment() {
    companion object {
        const val TAG = "AESCryptoSelectionDialogFragment"\n}

    private lateinit var mActivity: FragmentActivity
    private lateinit var mBuilder: TextInputDialogBuilder
    private var mKeyStoreManager: KeyStoreManager? = null
    private var mKeyChars: CharArray? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mActivity = requireActivity()
        mBuilder = TextInputDialogBuilder(mActivity, R.string.input_key)
            .setTitle(R.string.aes)
            .setNeutralButton(R.string.generate_key, null)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .setOnShowListener { dialog ->
                val dialog1 = dialog as AlertDialog
                val positiveButton = dialog1.getButton(AlertDialog.BUTTON_POSITIVE)
                val neutralButton = dialog1.getButton(AlertDialog.BUTTON_NEUTRAL)
                // Save
                positiveButton.setOnClickListener { v ->
                    val inputText = mBuilder.inputText
                    if (TextUtils.isEmpty(inputText)) return@setOnClickListener
                    if (mKeyStoreManager == null) {
                        UIUtils.displayLongToast(R.string.failed_to_initialize_key_store)
                        return@setOnClickListener
                    }
                    val keyChars = CharArray(inputText!!.length)
                    inputText.getChars(0, inputText.length, keyChars, 0)
                    mKeyChars = keyChars
                    val keyBytes: ByteArray
                    try {
                        keyBytes = HexEncoding.decode(keyChars)
                    } catch (e: IllegalArgumentException) {
                        UIUtils.displayLongToast(R.string.invalid_aes_key_size)
                        return@setOnClickListener
                    }
                    if (keyBytes.size != 16 && keyBytes.size != 32) {
                        UIUtils.displayLongToast(R.string.invalid_aes_key_size)
                        return@setOnClickListener
                    }
                    val secretKey = SecretKeySpec(keyBytes, "AES")
                    try {
                        mKeyStoreManager!!.addSecretKey(AES_KEY_ALIAS, secretKey, true)
                        Prefs.Encryption.setEncryptionMode(CryptoUtils.MODE_AES)
                    } catch (e: Exception) {
                        Log.e(TAG, e)
                        UIUtils.displayLongToast(R.string.failed_to_save_key)
                    }
                    Utils.clearBytes(keyBytes)
                    try {
                        SecretKeyCompat.destroy(secretKey)
                    } catch (e: Exception) {
                        Log.e(TAG, e)
                    }
                    dialog.dismiss()
                }
                // Key generator
                neutralButton.setOnClickListener { v ->
                    TextInputDropdownDialogBuilder(mActivity, R.string.crypto_key_size)
                        .setDropdownItems(listOf(128, 256), 0, false)
                        .setTitle(R.string.generate_key)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.generate_key) { dialog2, which, inputTextDropdown, isChecked ->
                            if (TextUtils.isEmpty(inputTextDropdown)) return@setPositiveButton
                            var keySize = 128 / 8
                            try {
                                keySize = Integer.decode(inputTextDropdown.toString().trim()) / 8
                            } catch (ignore: NumberFormatException) {
                            }
                            val random = SecureRandom()
                            val key = ByteArray(keySize)
                            random.nextBytes(key)
                            mKeyChars = HexEncoding.encode(key)
                            mBuilder.setInputText(CharBuffer.wrap(mKeyChars))
                        }
                        .show()
                }
            }
        ThreadUtils.postOnBackgroundThread {
            try {
                mKeyStoreManager = KeyStoreManager.getInstance()
                val secretKey = mKeyStoreManager!!.getSecretKey(AES_KEY_ALIAS)
                if (secretKey != null) {
                    mKeyChars = HexEncoding.encode(secretKey.encoded)
                    try {
                        SecretKeyCompat.destroy(secretKey)
                    } catch (ex: Exception) {
                        Log.e(TAG, ex)
                    }
                    mActivity.runOnUiThread { mBuilder.setInputText(CharBuffer.wrap(mKeyChars)) }
                }
            } catch (e: Exception) {
                Log.e(TAG, e)
            }
        }
        return mBuilder.create()
    }

    override fun onDismiss(dialog: DialogInterface) {
        mKeyChars?.let { Utils.clearChars(it) }
        super.onDismiss(dialog)
    }

    override fun onDestroy() {
        mKeyChars?.let { Utils.clearChars(it) }
        super.onDestroy()
    }
}
