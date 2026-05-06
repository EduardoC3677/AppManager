// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.dialog

import android.content.Context
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.backup.BackupFlags
import io.github.muntashirakon.widget.MaterialAlertView

class RestoreMultipleFragment : Fragment() {
    companion object {
        @JvmStatic
        fun getInstance(): RestoreMultipleFragment {
            return RestoreMultipleFragment()
        }
    }

    private var mViewModel: BackupRestoreDialogViewModel? = null
    private var mContext: Context? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dialog_restore_multiple, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mViewModel = ViewModelProvider(requireParentFragment()).get(BackupRestoreDialogViewModel::class.java)
        mContext = requireContext()

        val messageView: MaterialAlertView = view.findViewById(R.id.message)
        val recyclerView: RecyclerView = view.findViewById(android.R.id.list)
        recyclerView.layoutManager = LinearLayoutManager(mContext, LinearLayoutManager.VERTICAL, false)
        var supportedFlags = mViewModel!!.worstBackupFlag
        // Inject no signatures
        supportedFlags = supportedFlags or BackupFlags.SKIP_SIGNATURE_CHECK
        supportedFlags = supportedFlags or BackupFlags.BACKUP_CUSTOM_USERS
        var checkedFlags = BackupFlags.fromPref().flags and supportedFlags
        var disabledFlags = 0
        if (mViewModel!!.uninstalledApps.isNotEmpty()) {
            checkedFlags = checkedFlags or BackupFlags.BACKUP_APK_FILES
            disabledFlags = disabledFlags or BackupFlags.BACKUP_APK_FILES
        }
        val adapter = FlagsAdapter(mContext!!, checkedFlags, supportedFlags, disabledFlags)
        recyclerView.adapter = adapter

        val appsWithoutBackups = mViewModel!!.appsWithoutBackups
        if (appsWithoutBackups.isNotEmpty()) {
            val sb = SpannableStringBuilder(getString(R.string.backup_apps_cannot_be_restored))
            for (appLabel in appsWithoutBackups) {
                sb.append("\n● ").append(appLabel)
            }
            messageView.text = sb
            messageView.visibility = View.VISIBLE
        }
        view.findViewById<View>(R.id.action_restore).setOnClickListener {
            val newFlags = adapter.getSelectedFlags()
            handleRestore(newFlags)
        }
    }

    private fun handleRestore(flags: Int) {
        MaterialAlertDialogBuilder(mContext!!)
            .setTitle(R.string.restore)
            .setMessage(R.string.are_you_sure)
            .setPositiveButton(R.string.yes) { _, _ ->
                mViewModel!!.startRestore(flags, null, null)
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }
}
