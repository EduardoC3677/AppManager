// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings.crypto

import android.app.Dialog
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.EditText
import androidx.annotation.NonNull
import androidx.annotation.UiThread
import androidx.fragment.app.DialogFragment
import com.google.android.material.datepicker.MaterialDatePicker
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.backup.CryptoUtils
import io.github.muntashirakon.AppManager.crypto.ks.KeyPair
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreUtils
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.utils.DateUtils
import io.github.muntashirakon.AppManager.utils.ExUtils
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.adapters.SelectedArrayAdapter
import io.github.muntashirakon.dialog.AlertDialogBuilder
import io.github.muntashirakon.widget.MaterialSpinner
import java.util.*
import java.util.concurrent.atomic.AtomicReference

class KeyPairGeneratorDialogFragment : DialogFragment() {
    companion object {
        const val TAG = "KeyPairGeneratorDialogFragment"
        const val EXTRA_KEY_TYPE = "type"
        @JvmField
        val SUPPORTED_RSA_KEY_SIZES: List<Int> = listOf(2048, 4096)
    }

    interface OnGenerateListener {
        fun onGenerate(keyPair: KeyPair?)
    }

    private var mListener: OnGenerateListener? = null
    private var mKeySize = 0
    private var mExpiryDate: Long = 0
    @CryptoUtils.Mode
    private var mKeyType: String? = null

    fun setOnGenerateListener(listener: OnGenerateListener?) {
        mListener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity = requireActivity()
        mKeyType = requireArguments().getString(EXTRA_KEY_TYPE, CryptoUtils.MODE_RSA)
        val view = View.inflate(activity, R.layout.dialog_certificate_generator, null)
        val keySizeSpinner = view.findViewById<MaterialSpinner>(R.id.key_size_selector_spinner)
        if (mKeyType == CryptoUtils.MODE_RSA) {
            mKeySize = 2048
            keySizeSpinner.setAdapter(
                SelectedArrayAdapter(
                    activity, androidx.appcompat.R.layout.support_simple_spinner_dropdown_item,
                    SUPPORTED_RSA_KEY_SIZES
                )
            )
            keySizeSpinner.setOnItemClickListener { parent, view1, position, id ->
                mKeySize = SUPPORTED_RSA_KEY_SIZES[position]
            }
        } else {
            // There's no keysize for ECC
            keySizeSpinner.visibility = View.GONE
        }
        val expiryDate = view.findViewById<EditText>(R.id.expiry_date)
        expiryDate.setKeyListener(null)
        expiryDate.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            if (v.isInTouchMode && hasFocus) {
                v.performClick()
            }
        }
        expiryDate.setOnClickListener { v -> pickExpiryDate(expiryDate) }
        val commonName = view.findViewById<EditText>(R.id.common_name)
        val orgUnit = view.findViewById<EditText>(R.id.organization_unit)
        val orgName = view.findViewById<EditText>(R.id.organization_name)
        val locality = view.findViewById<EditText>(R.id.locality_name)
        val state = view.findViewById<EditText>(R.id.state_name)
        val country = view.findViewById<EditText>(R.id.country_name)
        val builder = AlertDialogBuilder(activity, true)
            .setTitle(R.string.generate_key)
            .setView(view)
            .setExitOnButtonPress(false)
            .setPositiveButton(R.string.generate_key) { dialog, which ->
                ThreadUtils.postOnBackgroundThread {
                    val keyPair = AtomicReference<KeyPair?>(null)
                    var formattedSubject = getFormattedSubject(
                        commonName.text.toString(),
                        orgUnit.text.toString(), orgName.text.toString(),
                        locality.text.toString(), state.text.toString(),
                        country.text.toString()
                    )
                    if (mExpiryDate == 0L) {
                        ThreadUtils.postOnMainThread { UIUtils.displayShortToast(R.string.expiry_date_cannot_be_empty) }
                        return@postOnBackgroundThread
                    }
                    if (formattedSubject.isEmpty()) {
                        formattedSubject = "CN=App Manager"
                    }
                    try {
                        if (mKeyType == CryptoUtils.MODE_RSA) {
                            keyPair.set(KeyStoreUtils.generateRSAKeyPair(formattedSubject, mKeySize, mExpiryDate))
                        } else if (mKeyType == CryptoUtils.MODE_ECC) {
                            keyPair.set(KeyStoreUtils.generateECCKeyPair(formattedSubject, mExpiryDate))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, e)
                    } finally {
                        ThreadUtils.postOnMainThread {
                            mListener?.onGenerate(keyPair.get())
                            ExUtils.exceptionAsIgnored { dialog.dismiss() }
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
        return builder.create()
    }

    fun getFormattedSubject(
        commonName: String?,
        organizationUnit: String?,
        organizationName: String?,
        localityName: String?,
        stateName: String?,
        countryName: String?
    ): String {
        val subjectArray = ArrayList<String>(6)
        if (!TextUtils.isEmpty(commonName)) subjectArray.add("CN=$commonName")
        if (!TextUtils.isEmpty(organizationUnit)) subjectArray.add("OU=$organizationUnit")
        if (!TextUtils.isEmpty(organizationName)) subjectArray.add("O=$organizationName")
        if (!TextUtils.isEmpty(localityName)) subjectArray.add("L=$localityName")
        if (!TextUtils.isEmpty(stateName)) subjectArray.add("ST=$stateName")
        if (!TextUtils.isEmpty(countryName)) subjectArray.add("C=$countryName")
        return TextUtils.join(", ", subjectArray)
    }

    @UiThread
    fun pickExpiryDate(expiryDate: EditText) {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.expiry_date)
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()
        datePicker.addOnPositiveButtonClickListener { selection: Long ->
            mExpiryDate = selection
            expiryDate.setText(DateUtils.formatDate(requireContext(), selection))
        }
        datePicker.show(childFragmentManager, "DatePicker")
    }
}
