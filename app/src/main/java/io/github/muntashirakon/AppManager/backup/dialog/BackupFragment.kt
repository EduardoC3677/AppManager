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
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.backup.BackupFlags
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager
import io.github.muntashirakon.widget.MaterialAlertView

class BackupFragment : Fragment() {
    companion object {
        const val ARG_ALLOW_CUSTOM_USERS = "allow_custom"\n@JvmStatic
        fun getInstance(allowCustomUsers: Boolean): BackupFragment {
            val fragment = BackupFragment()
            val args = Bundle()
            args.putBoolean(ARG_ALLOW_CUSTOM_USERS, allowCustomUsers)
            fragment.arguments = args
            return fragment
        }
    }

    private var mViewModel: BackupRestoreDialogViewModel? = null
    private var mContext: Context? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dialog_backup, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mViewModel = ViewModelProvider(requireParentFragment()).get(BackupRestoreDialogViewModel::class.java)
        mContext = requireContext()
        val allowCustomUsers = requireArguments().getBoolean(ARG_ALLOW_CUSTOM_USERS)

        val messageView: MaterialAlertView = view.findViewById(R.id.message)
        val recyclerView: RecyclerView = view.findViewById(android.R.id.list)
        recyclerView.layoutManager = LinearLayoutManager(mContext, LinearLayoutManager.VERTICAL, false)
        var supportedFlags = BackupFlags.getSupportedBackupFlags()
        // Remove unsupported flags
        supportedFlags = supportedFlags and BackupFlags.SKIP_SIGNATURE_CHECK.inv()
        if (!allowCustomUsers) {
            supportedFlags = supportedFlags and BackupFlags.BACKUP_CUSTOM_USERS.inv()
        }
        val adapter = FlagsAdapter(mContext!!, BackupFlags.fromPref().flags, supportedFlags)
        recyclerView.adapter = adapter

        val uninstalledApps = mViewModel!!.uninstalledApps
        if (uninstalledApps.isNotEmpty()) {
            val sb = SpannableStringBuilder(getString(R.string.backup_apps_cannot_be_backed_up))
            for (appLabel in uninstalledApps) {
                sb.append("
● ").append(appLabel)
            }
            messageView.text = sb
            messageView.visibility = View.VISIBLE
        }
        view.findViewById<View>(R.id.action_backup).setOnClickListener {
            val newFlags = adapter.getSelectedFlags()
            mViewModel!!.startBackup(newFlags, null)
        }
    }
}
