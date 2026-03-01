// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings.crypto

import android.app.Dialog
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.TextUtils
import android.widget.Button
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.intercept.IntentCompat
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder
import org.openintents.openpgp.IOpenPgpService2
import org.openintents.openpgp.OpenPgpError
import org.openintents.openpgp.util.OpenPgpApi
import org.openintents.openpgp.util.OpenPgpServiceConnection
import org.openintents.openpgp.util.OpenPgpUtils
import java.util.concurrent.Executors

class OpenPgpKeySelectionDialogFragment : DialogFragment() {
    companion object {
        const val TAG = "OpenPgpKeySelectionDialogFragment"
    }

    private var mOpenPgpProvider: String? = null
    private var mServiceConnection: OpenPgpServiceConnection? = null
    private var mDialog: AlertDialog? = null
    private lateinit var mActivity: FragmentActivity
    private val mKeyIdResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.data != null) {
            getUserId(result.data!!)
        }
    }
    private val mExecutor = Executors.newSingleThreadExecutor { runnable ->
        val thread = Thread(runnable)
        thread.isDaemon = true
        thread
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mActivity = requireActivity()
        mOpenPgpProvider = Prefs.Encryption.getOpenPgpProvider()
        val serviceInfoList = OpenPgpUtils.getPgpClientServices(mActivity)
        val packageLabels = arrayOfNulls<CharSequence>(serviceInfoList.size)
        val packageNames = arrayOfNulls<String>(serviceInfoList.size)
        val pm = mActivity.packageManager
        for (i in packageLabels.indices) {
            val serviceInfo = serviceInfoList[i]
            packageLabels[i] = serviceInfo.loadLabel(pm)
            packageNames[i] = serviceInfo.packageName
        }
        mDialog = SearchableSingleChoiceDialogBuilder(mActivity, packageNames as Array<String>, packageLabels as Array<CharSequence>)
            .setTitle(R.string.open_pgp_provider)
            .setSelection(mOpenPgpProvider)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { dialog1, which, selectedItem ->
                if (selectedItem != null) {
                    mOpenPgpProvider = selectedItem
                    Prefs.Encryption.setOpenPgpProvider(mOpenPgpProvider!!)
                }
            }
            .create()
        mDialog!!.setOnShowListener { dialog1 ->
            val positiveButton = (dialog1 as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener { v -> chooseKey() }
        }
        return mDialog!!
    }

    private fun chooseKey() {
        // Bind to service
        mServiceConnection = OpenPgpServiceConnection(requireContext(), mOpenPgpProvider,
            object : OpenPgpServiceConnection.OnBound {
                override fun onBound(service: IOpenPgpService2) {
                    getUserId(Intent())
                }

                override fun onError(e: Exception) {
                    Log.e(OpenPgpApi.TAG, "exception on binding!", e)
                }
            }
        )
        mServiceConnection!!.bindToService()
    }

    private fun getUserId(data: Intent) {
        data.action = OpenPgpApi.ACTION_GET_KEY_IDS
        data.putExtra(OpenPgpApi.EXTRA_USER_IDS, arrayOf<String>())
        val api = OpenPgpApi(mActivity, mServiceConnection!!.service)
        api.executeApiAsync(mExecutor, data, null, null) { result ->
            when (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
                OpenPgpApi.RESULT_CODE_SUCCESS -> {
                    val keyIds = result.getLongArrayExtra(OpenPgpApi.EXTRA_KEY_IDS)
                    if (keyIds == null || keyIds.isEmpty()) {
                        // Remove encryption
                        Prefs.Encryption.setOpenPgpProvider("")
                        Prefs.Encryption.setOpenPgpKeyIds("")
                    } else {
                        val keyIdsStr = arrayOfNulls<String>(keyIds.size)
                        for (i in keyIds.indices) {
                            keyIdsStr[i] = keyIds[i].toString()
                        }
                        Prefs.Encryption.setOpenPgpKeyIds(TextUtils.join(",", keyIdsStr))
                    }
                    mDialog!!.dismiss()
                }
                OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED -> {
                    val pi = IntentCompat.getParcelableExtra(result, OpenPgpApi.RESULT_INTENT, PendingIntent::class.java)!!
                    mKeyIdResultLauncher.launch(IntentSenderRequest.Builder(pi).build())
                }
                OpenPgpApi.RESULT_CODE_ERROR -> {
                    val error = IntentCompat.getParcelableExtra(result, OpenPgpApi.RESULT_ERROR, OpenPgpError::class.java)
                    if (error != null) {
                        Log.e(OpenPgpApi.TAG, "RESULT_CODE_ERROR: %s", error.message)
                    }
                    mDialog!!.dismiss()
                }
            }
        }
    }
}
