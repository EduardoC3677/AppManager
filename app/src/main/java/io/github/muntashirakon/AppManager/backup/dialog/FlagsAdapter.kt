// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.resources.MaterialAttributes
import io.github.muntashirakon.AppManager.backup.BackupFlags
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.util.AdapterUtils

internal class FlagsAdapter @SuppressLint("RestrictedApi") constructor(
    context: Context,
    @BackupFlags.BackupFlag private var mSelectedFlags: Int,
    @BackupFlags.BackupFlag supportedFlags: Int,
    @BackupFlags.BackupFlag private val mDisabledFlags: Int = 0
) : RecyclerView.Adapter<FlagsAdapter.ViewHolder>() {
    private val mLayoutId: Int = MaterialAttributes.resolveInteger(
        context, androidx.appcompat.R.attr.multiChoiceItemLayout,
        com.google.android.material.R.layout.mtrl_alert_select_dialog_multichoice
    )
    private val mSupportedBackupFlags: List<Int> = BackupFlags.getBackupFlagsAsArray(supportedFlags)
    private val mSupportedBackupFlagNames: Array<CharSequence> = BackupFlags.getFormattedFlagNames(context, mSupportedBackupFlags)

    init {
        notifyItemRangeInserted(0, mSupportedBackupFlags.size)
    }

    fun getSelectedFlags(): Int {
        return mSelectedFlags
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(mLayoutId, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val flag = mSupportedBackupFlags[position]
        val isSelected = (mSelectedFlags and flag) != 0
        val isDisabled = (mDisabledFlags and flag) != 0
        holder.item.isChecked = isSelected
        holder.item.isEnabled = !isDisabled
        holder.item.text = mSupportedBackupFlagNames[position]
        holder.item.setOnClickListener {
            if (isSelected) {
                // Now unselected
                mSelectedFlags = mSelectedFlags and flag.inv()
            } else {
                // Now selected
                mSelectedFlags = mSelectedFlags or flag
            }
            notifyItemChanged(position, AdapterUtils.STUB)
        }
    }

    override fun getItemCount(): Int {
        return mSupportedBackupFlags.size
    }

    internal class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var item: CheckedTextView = itemView.findViewById(android.R.id.text1)

        init {
            // textAppearanceBodyLarge
            item.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            item.setTextColor(UIUtils.getTextColorSecondary(item.context))
        }
    }
}
