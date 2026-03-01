// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.transition.MaterialSharedAxis
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.apk.signing.SigSchemes
import io.github.muntashirakon.AppManager.apk.signing.Signer
import io.github.muntashirakon.AppManager.settings.crypto.RSACryptoSelectionDialogFragment
import io.github.muntashirakon.AppManager.utils.DigestUtils
import io.github.muntashirakon.dialog.SearchableFlagsDialogBuilder

class ApkSigningPreferences : PreferenceFragment() {
    companion object {
        const val TAG = "ApkSigningPreferences"
    }

    private lateinit var mActivity: SettingsActivity
    private var mCustomSigPref: Preference? = null
    private lateinit var mModel: MainPreferencesViewModel

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_signature, rootKey)
        preferenceManager.preferenceDataStore = SettingsDataStore()
        mModel = ViewModelProvider(requireActivity())[MainPreferencesViewModel::class.java]
        mActivity = requireActivity() as SettingsActivity
        // Set signature schemes
        val sigSchemes = findPreference<Preference>("signature_schemes")!!
        val sigSchemeFlags = Prefs.Signing.getSigSchemes()
        sigSchemes.setOnPreferenceClickListener {
            SearchableFlagsDialogBuilder(mActivity, sigSchemeFlags.allItems, R.array.sig_schemes, sigSchemeFlags.flags)
                .setTitle(R.string.app_signing_signature_schemes)
                .setPositiveButton(R.string.save) { dialog, which, selections ->
                    var flags = 0
                    for (flag in selections) {
                        flags = flags or flag
                    }
                    sigSchemeFlags.flags = flags
                    Prefs.Signing.setSigSchemes(flags)
                }
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.reset_to_default) { dialog, which, selections ->
                    sigSchemeFlags.flags = SigSchemes.DEFAULT_SCHEMES
                    Prefs.Signing.setSigSchemes(SigSchemes.DEFAULT_SCHEMES)
                }
                .show()
            true
        }
        mCustomSigPref = findPreference<Preference>("signing_keys")!!
        mCustomSigPref!!.setOnPreferenceClickListener {
            val fragment = RSACryptoSelectionDialogFragment.getInstance(Signer.SIGNING_KEY_ALIAS)
            fragment.setOnKeyPairUpdatedListener { keyPair, certificateBytes ->
                if (keyPair != null && certificateBytes != null) {
                    val hash = DigestUtils.getHexDigest(DigestUtils.SHA_256, certificateBytes)
                    try {
                        keyPair.destroy()
                    } catch (ignore: Exception) {
                    }
                    mCustomSigPref!!.summary = hash
                } else {
                    mCustomSigPref!!.setSummary(R.string.key_not_set)
                }
            }
            fragment.show(parentFragmentManager, RSACryptoSelectionDialogFragment.TAG)
            true
        }
        findPreference<SwitchPreferenceCompat>("zip_align")!!
            .isChecked = Prefs.Signing.zipAlign()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mModel.getSigningKeySha256HashLiveData().observe(viewLifecycleOwner) { hash ->
            if (hash != null) {
                mCustomSigPref!!.summary = hash
            } else {
                mCustomSigPref!!.setSummary(R.string.key_not_set)
            }
        }
        mModel.loadSigningKeySha256Hash()
    }

    override fun getTitle(): Int {
        return R.string.apk_signing
    }
}
