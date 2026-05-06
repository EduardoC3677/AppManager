// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings.crypto

import android.app.Dialog
import android.os.Bundle
import android.widget.Button
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.backup.CryptoUtils
import io.github.muntashirakon.AppManager.crypto.ECCCrypto
import io.github.muntashirakon.AppManager.crypto.ks.KeyPair
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.utils.PackageUtils
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.dialog.ScrollableDialogBuilder
import java.security.cert.CertificateEncodingException
import java.security.cert.X509Certificate

class ECCCryptoSelectionDialogFragment : DialogFragment() {
    companion object {
        const val TAG = "ECCCryptoSelectionDialogFragment"\n}

    interface OnKeyPairUpdatedListener {
        fun keyPairUpdated(keyPair: KeyPair?, certificateBytes: ByteArray?)
    }

    private lateinit var mActivity: FragmentActivity
    private lateinit var mBuilder: ScrollableDialogBuilder
    private var mListener: OnKeyPairUpdatedListener? = null
    private val mTargetAlias = ECCCrypto.ECC_KEY_ALIAS
    private var mKeyStoreManager: KeyStoreManager? = null

    fun setOnKeyPairUpdatedListener(listener: OnKeyPairUpdatedListener?) {
        mListener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mActivity = requireActivity()
        mBuilder = ScrollableDialogBuilder(mActivity)
            .setTitle(R.string.ecc)
            .setNegativeButton(R.string.pref_import, null)
            .setNeutralButton(R.string.generate_key, null)
            .setPositiveButton(R.string.ok, null)
        ThreadUtils.postOnBackgroundThread {
            val info = getSigningInfo()
            ThreadUtils.postOnMainThread { mBuilder.setMessage(info) }
        }
        val alertDialog = mBuilder.create()
        alertDialog.setOnShowListener { dialog ->
            val dialog1 = dialog as AlertDialog
            val importButton = dialog1.getButton(AlertDialog.BUTTON_NEGATIVE)
            val generateButton = dialog1.getButton(AlertDialog.BUTTON_NEUTRAL)
            val defaultOrOkButton = dialog1.getButton(AlertDialog.BUTTON_POSITIVE)
            importButton.setOnClickListener { v ->
                val fragment = KeyPairImporterDialogFragment()
                val args = Bundle()
                args.putString(KeyPairImporterDialogFragment.EXTRA_ALIAS, mTargetAlias)
                fragment.arguments = args
                fragment.setOnKeySelectedListener { keyPair ->
                    ThreadUtils.postOnBackgroundThread {
                        addKeyPair(keyPair)
                    }
                }
                fragment.show(parentFragmentManager, KeyPairImporterDialogFragment.TAG)
            }
            generateButton.setOnClickListener { v ->
                val fragment = KeyPairGeneratorDialogFragment()
                val args = Bundle()
                args.putString(KeyPairGeneratorDialogFragment.EXTRA_KEY_TYPE, CryptoUtils.MODE_ECC)
                fragment.arguments = args
                fragment.setOnGenerateListener { keyPair ->
                    ThreadUtils.postOnBackgroundThread {
                        addKeyPair(keyPair)
                    }
                }
                fragment.show(parentFragmentManager, KeyPairGeneratorDialogFragment.TAG)
            }
            defaultOrOkButton.setOnClickListener { v ->
                ThreadUtils.postOnBackgroundThread {
                    try {
                        if (ThreadUtils.isInterrupted()) return@postOnBackgroundThread
                        ThreadUtils.postOnMainThread { UIUtils.displayShortToast(R.string.done) }
                        keyPairUpdated()
                    } catch (e: Exception) {
                        Log.e(TAG, e)
                        ThreadUtils.postOnMainThread { UIUtils.displayLongToast(R.string.failed_to_save_key) }
                    } finally {
                        alertDialog.dismiss()
                    }
                }
            }
        }
        return alertDialog
    }

    @WorkerThread
    private fun getSigningInfo(): CharSequence {
        val keyPair = getKeyPair()
        if (keyPair != null) {
            return try {
                PackageUtils.getSigningCertificateInfo(mActivity, keyPair.certificate as X509Certificate)
            } catch (e: CertificateEncodingException) {
                getString(R.string.failed_to_load_key)
            }
        }
        return getString(R.string.key_not_set)
    }

    @WorkerThread
    private fun addKeyPair(keyPair: KeyPair?) {
        try {
            if (keyPair == null) {
                throw Exception("Keypair can't be null.")
            }
            mKeyStoreManager = KeyStoreManager.getInstance()
            mKeyStoreManager!!.addKeyPair(mTargetAlias, keyPair, true)
            if (ThreadUtils.isInterrupted()) return
            ThreadUtils.postOnMainThread { UIUtils.displayShortToast(R.string.done) }
            keyPairUpdated()
            if (ThreadUtils.isInterrupted()) return
            val info = getSigningInfo()
            ThreadUtils.postOnMainThread { mBuilder.setMessage(info) }
        } catch (e: Exception) {
            Log.e(TAG, e)
            ThreadUtils.postOnMainThread { UIUtils.displayLongToast(R.string.failed_to_save_key) }
        }
    }

    @WorkerThread
    private fun keyPairUpdated() {
        try {
            val keyPair = getKeyPair()
            if (keyPair != null) {
                if (mListener != null) {
                    val bytes = keyPair.certificate.encoded
                    ThreadUtils.postOnMainThread { mListener!!.keyPairUpdated(keyPair, bytes) }
                }
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, e)
        }
        if (mListener != null) {
            ThreadUtils.postOnMainThread { mListener!!.keyPairUpdated(null, null) }
        }
    }

    @WorkerThread
    private fun getKeyPair(): KeyPair? {
        return try {
            mKeyStoreManager = KeyStoreManager.getInstance()
            if (mKeyStoreManager!!.containsKey(mTargetAlias)) {
                mKeyStoreManager!!.getKeyPair(mTargetAlias)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, e)
            null
        }
    }
}
