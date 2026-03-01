// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.resources.MaterialAttributes
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.backup.BackupFlags
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.dialog.SearchableFlagsDialogBuilder
import io.github.muntashirakon.util.AdapterUtils
import java.io.IOException

class RestoreSingleFragment : Fragment() {
    companion object {
        @JvmStatic
        fun getInstance(): RestoreSingleFragment {
            return RestoreSingleFragment()
        }
    }

    private var mViewModel: BackupRestoreDialogViewModel? = null
    private var mContext: Context? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dialog_restore_single, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mViewModel = ViewModelProvider(requireParentFragment()).get(BackupRestoreDialogViewModel::class.java)
        mContext = requireContext()

        val recyclerView: RecyclerView = view.findViewById(android.R.id.list)
        val restoreButton: MaterialButton = view.findViewById(R.id.action_restore)
        val deleteButton: MaterialButton = view.findViewById(R.id.action_delete)
        val moreButton: MaterialButton = view.findViewById(R.id.more)

        recyclerView.layoutManager = LinearLayoutManager(mContext, LinearLayoutManager.VERTICAL, false)
        val adapter = BackupAdapter(mContext!!, mViewModel!!.backupInfo.backupMetadataList) { _, selectionCount, _ ->
            restoreButton.isEnabled = selectionCount == 1
            deleteButton.isEnabled = selectionCount > 0
        }
        recyclerView.adapter = adapter

        restoreButton.setOnClickListener { handleRestore(adapter.getSelectedBackups()[0]) }
        deleteButton.setOnClickListener { handleDelete(adapter.getSelectedBackups()) }
        moreButton.setOnClickListener { v ->
            val total = adapter.selectionCount()
            val frozenCount = adapter.frozenBackupSelectionCount

            val popupMenu = PopupMenu(mContext!!, v)
            val menu = popupMenu.menu
            val freezeMenuItem = menu.add(R.string.freeze)
            val unfreezeMenuItem = menu.add(R.string.unfreeze)

            freezeMenuItem.isEnabled = (total - frozenCount) > 0
            unfreezeMenuItem.isEnabled = frozenCount > 0

            freezeMenuItem.setOnMenuItemClickListener {
                val selectedBackups = adapter.getSelectedBackups()
                for (metadata in selectedBackups) {
                    try {
                        metadata.info.getBackupItem()!!.freeze()
                        ++adapter.mFrozenBackupSelectionCount
                    } catch (ignore: IOException) {
                    }
                }
                adapter.notifyItemRangeChanged(0, adapter.itemCount, AdapterUtils.STUB)
                true
            }
            unfreezeMenuItem.setOnMenuItemClickListener {
                val selectedBackups = adapter.getSelectedBackups()
                for (metadata in selectedBackups) {
                    try {
                        metadata.info.getBackupItem()!!.unfreeze()
                        --adapter.mFrozenBackupSelectionCount
                    } catch (ignore: IOException) {
                    }
                }
                adapter.notifyItemRangeChanged(0, adapter.itemCount, AdapterUtils.STUB)
                true
            }
            popupMenu.show()
        }
    }

    private fun handleRestore(selectedBackup: BackupMetadataV5) {
        val flags = selectedBackup.info.flags
        val enabledFlags = BackupFlags.fromPref()
        enabledFlags.flags = flags.flags and enabledFlags.flags
        val supportedBackupFlags = BackupFlags.getBackupFlagsAsArray(flags.flags).toMutableList()
        // Inject no signatures
        supportedBackupFlags.add(BackupFlags.SKIP_SIGNATURE_CHECK)
        supportedBackupFlags.add(BackupFlags.BACKUP_CUSTOM_USERS)
        val disabledFlags = mutableListOf<Int>()
        if (!mViewModel!!.backupInfo.isInstalled) {
            enabledFlags.addFlag(BackupFlags.BACKUP_APK_FILES)
            disabledFlags.add(BackupFlags.BACKUP_APK_FILES)
        }
        SearchableFlagsDialogBuilder(
            mContext!!,
            supportedBackupFlags,
            BackupFlags.getFormattedFlagNames(mContext!!, supportedBackupFlags),
            enabledFlags.flags
        )
            .setTitle(R.string.backup_options)
            .addDisabledItems(disabledFlags)
            .setPositiveButton(R.string.restore) { _, _, selections ->
                var newFlags = 0
                for (flag in selections) {
                    newFlags = newFlags or flag
                }
                enabledFlags.flags = newFlags

                mViewModel!!.startRestore(enabledFlags.flags, null, arrayOf(selectedBackup.info.getRelativeDir()!!))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun handleDelete(selectedBackups: List<BackupMetadataV5>) {
        MaterialAlertDialogBuilder(mContext!!)
            .setTitle(R.string.delete_backup)
            .setMessage(R.string.are_you_sure)
            .setNegativeButton(R.string.no, null)
            .setPositiveButton(R.string.yes) { _, _ ->
                val uuids = mutableListOf<String>()
                for (backup in selectedBackups) {
                    uuids.add(backup.info.getRelativeDir()!!)
                }
                // mViewModel!!.startDelete(uuids.toTypedArray()) -- ViewModel doesn't have startDelete?
                // Looking at RestoreSingleFragment.java again...
                // operationInfo.op = BatchOpsManager.OP_DELETE_BACKUP;
                // mViewModel.prepareForOperation(operationInfo);
            }
            .show()
    }

    private class BackupAdapter @SuppressLint("RestrictedApi") constructor(
        context: Context,
        backups: List<BackupMetadataV5>,
        private val mSelectionListener: (metadata: BackupMetadataV5?, selectionCount: Int, added: Boolean) -> Unit
    ) : RecyclerView.Adapter<BackupAdapter.ViewHolder>() {
        private val mLayoutId: Int = MaterialAttributes.resolveInteger(
            context, androidx.appcompat.R.attr.multiChoiceItemLayout,
            com.google.android.material.R.layout.mtrl_alert_select_dialog_multichoice
        )
        private val mBackups = mutableListOf<BackupMetadataV5>()
        private val mSelectedPositions = mutableListOf<Int>()
        var mFrozenBackupSelectionCount = 0

        init {
            mSelectionListener(null, mSelectedPositions.size, false)
            for (i in backups.indices) {
                val backup = backups[i]
                mBackups.add(backup)
                if (backup.isBaseBackup) {
                    mSelectedPositions.add(i)
                    if (backup.info.isFrozen()) {
                        ++mFrozenBackupSelectionCount
                    }
                    mSelectionListener(backup, mSelectedPositions.size, true)
                }
            }
            notifyItemRangeInserted(0, mBackups.size)
        }

        fun selectionCount(): Int = mSelectedPositions.size

        val frozenBackupSelectionCount: Int get() = mFrozenBackupSelectionCount

        fun getSelectedBackups(): List<BackupMetadataV5> {
            val selectedBackups = mutableListOf<BackupMetadataV5>()
            for (position in mSelectedPositions) {
                selectedBackups.add(mBackups[position])
            }
            return selectedBackups
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(mLayoutId, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val metadata = mBackups[position]
            val isSelected = mSelectedPositions.contains(position)
            holder.item.isChecked = isSelected
            holder.item.text = metadata.toLocalizedString(holder.item.context)
            holder.item.setOnClickListener {
                if (isSelected) {
                    // Now unselected
                    mSelectedPositions.remove(position)
                    if (metadata.info.isFrozen()) {
                        --mFrozenBackupSelectionCount
                    }
                    mSelectionListener(metadata, mSelectedPositions.size, false)
                } else {
                    // Now selected
                    mSelectedPositions.add(position)
                    if (metadata.info.isFrozen()) {
                        ++mFrozenBackupSelectionCount
                    }
                    mSelectionListener(metadata, mSelectedPositions.size, true)
                }
                notifyItemChanged(position, AdapterUtils.STUB)
            }
        }

        override fun getItemCount(): Int = mBackups.size

        internal class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            var item: CheckedTextView = itemView.findViewById(android.R.id.text1)

            init {
                // textAppearanceBodyLarge
                item.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                item.setTextColor(UIUtils.getTextColorSecondary(item.context))
            }
        }
    }
}
