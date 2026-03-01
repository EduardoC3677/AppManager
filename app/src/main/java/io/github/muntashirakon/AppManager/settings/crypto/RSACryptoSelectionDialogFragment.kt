// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings.crypto

import android.app.Application
import android.app.Dialog
import android.os.Bundle
import android.widget.Button
import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AlertDialog
import androidx.core.util.Pair
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.backup.CryptoUtils
import io.github.muntashirakon.AppManager.crypto.ks.KeyPair
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.utils.PackageUtils
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.dialog.ScrollableDialogBuilder
import java.security.cert.CertificateEncodingException
import java.security.cert.X509Certificate

class RSACryptoSelectionDialogFragment : DialogFragment() {
    companion object {
        @JvmField
        val TAG: String = RSACryptoSelectionDialogFragment::class.java.simpleName
        private const val EXTRA_ALIAS = "alias"

        @JvmStatic
        fun getInstance(alias: String): RSACryptoSelectionDialogFragment {
            val fragment = RSACryptoSelectionDialogFragment()
            val args = Bundle()
            args.putString(EXTRA_ALIAS, alias)
            fragment.arguments = args
            return fragment
        }
    }

    fun interface OnKeyPairUpdatedListener {
        @UiThread
        fun keyPairUpdated(keyPair: KeyPair?, certificateBytes: ByteArray?)
    }

    private var mBuilder: ScrollableDialogBuilder? = null
    private var mListener: OnKeyPairUpdatedListener? = null
    private var mTargetAlias: String? = null
    private var mModel: RSACryptoSelectionViewModel? = null

    fun setOnKeyPairUpdatedListener(listener: OnKeyPairUpdatedListener?) {
        mListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mModel = ViewModelProvider(this)[RSACryptoSelectionViewModel::class.java]
        mModel!!.observeStatus().observe(this) { status ->
            if (status.second == true) {
                UIUtils.displayLongToast(status.first)
            } else {
                UIUtils.displayShortToast(status.first)
            }
        }
        mModel!!.observeKeyUpdated().observe(this) { updatedKeyPair ->
            mListener?.keyPairUpdated(updatedKeyPair.first, updatedKeyPair.second)
        }
        mModel!!.observeSigningInfo().observe(this) { keyPair ->
            mBuilder?.setMessage(getSigningInfo(keyPair))
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mTargetAlias = requireArguments().getString(EXTRA_ALIAS)
        mBuilder = ScrollableDialogBuilder(requireActivity())
            .setTitle(R.string.rsa)
            .setNegativeButton(R.string.pref_import, null)
            .setNeutralButton(R.string.generate_key, null)
            .setPositiveButton(R.string.ok, null)
        mModel!!.loadSigningInfo(mTargetAlias!!)
        val dialog = mBuilder!!.create()
        dialog.setOnShowListener { dialog3 ->
            val dialog1 = dialog3 as AlertDialog
            val importButton = dialog1.getButton(AlertDialog.BUTTON_NEGATIVE)
            val generateButton = dialog1.getButton(AlertDialog.BUTTON_NEUTRAL)
            importButton.setOnClickListener { v ->
                val fragment = KeyPairImporterDialogFragment()
                val args = Bundle()
                args.putString(KeyPairImporterDialogFragment.EXTRA_ALIAS, mTargetAlias)
                fragment.arguments = args
                fragment.setOnKeySelectedListener { keyPair -> mModel!!.addKeyPair(mTargetAlias!!, keyPair) }
                fragment.show(parentFragmentManager, KeyPairImporterDialogFragment.TAG)
            }
            generateButton.setOnClickListener { v ->
                val fragment = KeyPairGeneratorDialogFragment()
                val args = Bundle()
                args.putString(KeyPairGeneratorDialogFragment.EXTRA_KEY_TYPE, CryptoUtils.MODE_RSA)
                fragment.arguments = args
                fragment.setOnGenerateListener { keyPair -> mModel!!.addKeyPair(mTargetAlias!!, keyPair) }
                fragment.show(parentFragmentManager, KeyPairGeneratorDialogFragment.TAG)
            }
        }
        return dialog
    }

    private fun getSigningInfo(keyPair: KeyPair?): CharSequence {
        if (keyPair != null) {
            return try {
                PackageUtils.getSigningCertificateInfo(
                    requireActivity(),
                    keyPair.certificate as X509Certificate
                )
            } catch (e: CertificateEncodingException) {
                getString(R.string.failed_to_load_key)
            }
        }
        return getString(R.string.key_not_set)
    }

    class RSACryptoSelectionViewModel(application: Application) : AndroidViewModel(application) {
        private val status = MutableLiveData<Pair<Int, Boolean>>()
        private val keyUpdated = MutableLiveData<Pair<KeyPair?, ByteArray?>>()
        private val signingInfo = MutableLiveData<KeyPair?>()

        fun observeStatus(): LiveData<Pair<Int, Boolean>> = status
        fun observeKeyUpdated(): LiveData<Pair<KeyPair?, ByteArray?>> = keyUpdated
        fun observeSigningInfo(): LiveData<KeyPair?> = signingInfo

        @AnyThread
        fun loadSigningInfo(targetAlias: String) {
            ThreadUtils.postOnBackgroundThread { signingInfo.postValue(getKeyPair(targetAlias)) }
        }

        @AnyThread
        fun addKeyPair(targetAlias: String, keyPair: KeyPair?) {
            ThreadUtils.postOnBackgroundThread {
                try {
                    if (keyPair == null) {
                        throw Exception("Keypair can't be null.")
                    }
                    val keyStoreManager = KeyStoreManager.getInstance()
                    keyStoreManager.addKeyPair(targetAlias, keyPair, true)
                    status.postValue(Pair(R.string.done, false))
                    keyPairUpdated(targetAlias)
                    signingInfo.postValue(getKeyPair(targetAlias))
                } catch (e: Exception) {
                    Log.e(TAG, e)
                    status.postValue(Pair(R.string.failed_to_save_key, true))
                }
            }
        }

        @WorkerThread
        private fun getKeyPair(targetAlias: String): KeyPair? {
            return try {
                val keyStoreManager = KeyStoreManager.getInstance()
                if (keyStoreManager.containsKey(targetAlias)) {
                    keyStoreManager.getKeyPair(targetAlias)
                } else null
            } catch (e: Exception) {
                Log.e(TAG, e)
                null
            }
        }

        @WorkerThread
        private fun keyPairUpdated(targetAlias: String) {
            try {
                val keyPair = getKeyPair(targetAlias)
                if (keyPair != null) {
                    val bytes = keyPair.certificate.encoded
                    keyUpdated.postValue(Pair(keyPair, bytes))
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, e)
            }
            keyUpdated.postValue(Pair(null, null))
        }
    }
}
