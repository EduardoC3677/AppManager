// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings.crypto

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.text.method.KeyListener
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.crypto.ks.KeyPair
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreUtils
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.utils.*
import io.github.muntashirakon.adapters.SelectedArrayAdapter
import io.github.muntashirakon.dialog.TextInputDropdownDialogBuilder
import io.github.muntashirakon.widget.MaterialSpinner
import java.util.*

class KeyPairImporterDialogFragment : DialogFragment() {
    companion object {
        const val TAG = "KeyPairImporterDialogFragment"
        const val EXTRA_ALIAS = "alias"
    }

    interface OnKeySelectedListener {
        fun onKeySelected(keyPair: KeyPair?)
    }

    private var mListener: OnKeySelectedListener? = null
    private lateinit var mActivity: FragmentActivity
    private lateinit var mKsPassOrPk8Layout: TextInputLayout
    private lateinit var mKsPassOrPk8: EditText
    private var mKeyListener: KeyListener? = null
    private lateinit var mKsLocationOrPemLayout: TextInputLayout
    private lateinit var mKsLocationOrPem: EditText
    @KeyStoreUtils.KeyType
    private var mKeyType = 0
    private var mKsOrPemFile: Uri? = null
    private var mPk8File: Uri? = null
    private val mImportFile = BetterActivityResult.registerForActivityResult(this, ActivityResultContracts.GetContent())

    fun setOnKeySelectedListener(listener: OnKeySelectedListener?) {
        mListener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mActivity = requireActivity()
        val targetAlias = requireArguments().getString(EXTRA_ALIAS)
            ?: return super.onCreateDialog(savedInstanceState)
        val view = layoutInflater.inflate(R.layout.dialog_key_pair_importer, null)
        val keyTypeSpinner = view.findViewById<MaterialSpinner>(R.id.key_type_selector_spinner)
        mKsPassOrPk8Layout = view.findViewById(R.id.hint)
        mKsPassOrPk8 = view.findViewById(R.id.text)
        mKeyListener = mKsPassOrPk8.keyListener
        mKsLocationOrPemLayout = view.findViewById(R.id.hint2)
        mKsLocationOrPem = view.findViewById(R.id.text2)
        mKsLocationOrPem.setKeyListener(null)
        mKsLocationOrPem.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            if (v.isInTouchMode() && hasFocus) {
                v.performClick()
            }
        }
        mKsLocationOrPem.setOnClickListener { v ->
            mImportFile.launch("application/*") { result ->
                mKsOrPemFile = result
                if (result != null) {
                    mKsLocationOrPem.setText(result.toString())
                }
            }
        }
        keyTypeSpinner.setAdapter(
            SelectedArrayAdapter.createFromResource(
                mActivity, R.array.crypto_import_types,
                io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item
            )
        )
        keyTypeSpinner.setOnItemClickListener { parent, view1, position, id ->
            mKsPassOrPk8.setText(null)
            mKsLocationOrPem.setText(null)

            if (position == KeyStoreUtils.KeyType.PK8) {
                // PKCS #8 and PEM
                mKsPassOrPk8Layout.setHint(R.string.pk8_file)
                mKsPassOrPk8.setKeyListener(null)
                mKsPassOrPk8.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                    if (v.isInTouchMode() && hasFocus) {
                        v.performClick()
                    }
                }
                mKsPassOrPk8.setOnClickListener { v ->
                    mImportFile.launch("application/*") { result ->
                        mPk8File = result
                        if (result != null) {
                            mKsPassOrPk8.setText(result.toString())
                        }
                    }
                }
                mKsLocationOrPemLayout.setHint(R.string.pem_file)
            } else {
                // KeyStore
                setDefault()
            }
            mKeyType = position
        }
        setDefault()
        val alertDialog = MaterialAlertDialogBuilder(mActivity)
            .setTitle(R.string.import_key)
            .setView(view)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        alertDialog.setOnShowListener { dialog ->
            val dialog1 = dialog as AlertDialog
            val okButton = dialog1.getButton(AlertDialog.BUTTON_POSITIVE)
            okButton.setOnClickListener { v ->
                if (mListener == null) return@setOnClickListener
                if (mKeyType == KeyStoreUtils.KeyType.PK8) {
                    // PKCS #8 and PEM
                    try {
                        if (mPk8File == null || mKsOrPemFile == null) {
                            throw Exception("PK8 or PEM can't be null.")
                        }
                        val keyPair = KeyStoreUtils.getKeyPair(mActivity, mPk8File, mKsOrPemFile)
                        mListener!!.onKeySelected(keyPair)
                    } catch (e: Exception) {
                        Log.e(TAG, e)
                        mListener!!.onKeySelected(null)
                    }
                    dialog.dismiss()
                } else {
                    // KeyStore
                    val ksPassword = Utils.getChars(mKsPassOrPk8.text)
                    ThreadUtils.postOnBackgroundThread {
                        try {
                            if (mKsOrPemFile == null) {
                                throw Exception("KeyStore file can't be null.")
                            }
                            val aliases = KeyStoreUtils.listAliases(
                                mActivity, mKsOrPemFile, mKeyType,
                                ksPassword
                            )
                            if (mListener == null) return@postOnBackgroundThread
                            ThreadUtils.postOnMainThread {
                                if (aliases.isEmpty()) {
                                    UIUtils.displayLongToast(R.string.found_no_alias_in_keystore)
                                    ExUtils.exceptionAsIgnored { dialog.dismiss() }
                                    return@postOnMainThread
                                }
                                val builder = TextInputDropdownDialogBuilder(mActivity, R.string.choose_an_alias)
                                    .setDropdownItems(aliases, -1, true)
                                    .setAuxiliaryInputLabel(R.string.alias_pass)
                                    .setTitle(R.string.choose_an_alias)
                                    .setNegativeButton(R.string.cancel, null)
                                builder.setPositiveButton(R.string.ok) { dialog2, which, inputText, isChecked ->
                                    val aliasName = inputText?.toString()
                                    val aliasPassword = Utils.getChars(builder.auxiliaryInput)
                                    ThreadUtils.postOnBackgroundThread {
                                        try {
                                            val keyPair = KeyStoreUtils.getKeyPair(
                                                mActivity, mKsOrPemFile, mKeyType,
                                                aliasName, ksPassword, aliasPassword
                                            )
                                            mListener!!.onKeySelected(keyPair)
                                        } catch (e: Exception) {
                                            Log.e(TAG, e)
                                            mListener!!.onKeySelected(null)
                                        }
                                        ThreadUtils.postOnMainThread { ExUtils.exceptionAsIgnored { dialog.dismiss() } }
                                    }
                                }.show()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, e)
                            ThreadUtils.postOnMainThread { UIUtils.displayLongToast(R.string.failed_to_read_keystore) }
                        }
                    }
                }
            }
        }
        return alertDialog
    }

    private fun setDefault() {
        mKeyType = KeyStoreUtils.KeyType.JKS
        // KeyStore
        mKsPassOrPk8Layout.setHint(R.string.keystore_pass)
        mKsPassOrPk8.keyListener = mKeyListener
        mKsPassOrPk8.onFocusChangeListener = null
        mKsPassOrPk8.setOnClickListener(null)
        mKsLocationOrPemLayout.setHint(R.string.keystore_file)
    }
}
