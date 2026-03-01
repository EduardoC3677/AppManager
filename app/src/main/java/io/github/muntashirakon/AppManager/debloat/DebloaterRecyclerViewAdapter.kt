// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat

import android.content.Context
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.fragment.app.FragmentActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.textview.MaterialTextView
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.utils.UIUtils.getColoredText
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes
import io.github.muntashirakon.util.AccessibilityUtils
import io.github.muntashirakon.util.AdapterUtils
import io.github.muntashirakon.widget.MultiSelectionView
import java.util.*

class DebloaterRecyclerViewAdapter(private val mActivity: FragmentActivity) : MultiSelectionView.Adapter<DebloaterRecyclerViewAdapter.ViewHolder>() {
    private val mAdapterList = mutableListOf<DebloatObject>()
    @ColorInt private val mRemovalSafeColor = ColorCodes.getRemovalSafeIndicatorColor(mActivity)
    @ColorInt private val mRemovalReplaceColor = ColorCodes.getRemovalReplaceIndicatorColor(mActivity)
    @ColorInt private val mRemovalUnsafeColor = ColorCodes.getRemovalUnsafeIndicatorColor(mActivity)
    @ColorInt private val mRemovalCautionColor = ColorCodes.getRemovalCautionIndicatorColor(mActivity)
    @ColorInt private val mColorSurface = MaterialColors.getColor(mActivity, com.google.android.material.R.attr.colorSurface, DebloaterRecyclerViewAdapter::class.java.canonicalName)
    private val mViewModel: DebloaterViewModel = (mActivity as DebloaterActivity).viewModel!!
    private val mDefaultIcon = mActivity.packageManager.defaultActivityIcon
    private val mLock = Any()

    fun setAdapterList(adapterList: List<DebloatObject>) {
        synchronized(mLock) { AdapterUtils.notifyDataSetChanged(this, mAdapterList, adapterList) }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_debloater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val debloatObject = synchronized(mLock) { mAdapterList[position] }
        val context = holder.itemView.context
        val icon = debloatObject.icon ?: mDefaultIcon
        val warning = debloatObject.getWarning()
        val sb = SpannableStringBuilder()
        val removalColor: Int
        @StringRes val removalRes: Int
        when (debloatObject.getRemoval()) {
            DebloatObject.REMOVAL_SAFE -> { removalColor = mRemovalSafeColor; removalRes = R.string.debloat_removal_safe_short_description }
            DebloatObject.REMOVAL_REPLACE -> { removalColor = mRemovalReplaceColor; removalRes = R.string.debloat_removal_replace_short_description }
            DebloatObject.REMOVAL_UNSAFE -> { removalColor = mRemovalUnsafeColor; removalRes = R.string.debloat_removal_unsafe }
            else -> { removalColor = mRemovalCautionColor; removalRes = R.string.debloat_removal_caution_short_description }
        }
        sb.append(getColoredText(context.getString(removalRes), removalColor))
        if (!TextUtils.isEmpty(warning)) sb.append(" — ").append(warning)
        holder.iconView.setImageDrawable(icon)
        holder.listTypeView.text = debloatObject.type
        holder.packageNameView.text = debloatObject.packageName
        holder.descriptionView.text = sb
        holder.itemView.strokeColor = removalColor
        holder.labelView.text = debloatObject.getLabelOrPackageName()
        holder.itemView.setOnLongClickListener { toggleSelection(position); AccessibilityUtils.requestAccessibilityFocus(holder.itemView); true }
        holder.iconView.setOnClickListener { toggleSelection(position); AccessibilityUtils.requestAccessibilityFocus(holder.itemView) }
        holder.itemView.setOnClickListener {
            if (isInSelectionMode) { toggleSelection(position); AccessibilityUtils.requestAccessibilityFocus(holder.itemView) }
            else BloatwareDetailsDialog.getInstance(debloatObject.packageName!!).show(mActivity.supportFragmentManager, BloatwareDetailsDialog.TAG)
        }
        super.onBindViewHolder(holder, position)
    }

    override fun getItemId(position: Int): Long = synchronized(mLock) { mAdapterList[position].id.toLong() }
    override fun getItemCount(): Int = synchronized(mLock) { mAdapterList.size }
    override fun select(position: Int): Boolean { synchronized(mLock) { mViewModel.select(mAdapterList[position]); return true } }
    override fun deselect(position: Int): Boolean { synchronized(mLock) { mViewModel.deselect(mAdapterList[position]); return true } }
    override fun cancelSelection() { super.cancelSelection(); mViewModel.deselectAll() }
    override fun isSelected(position: Int): Boolean { synchronized(mLock) { return mViewModel.isSelected(mAdapterList[position]) } }
    override fun getSelectedItemCount(): Int = mViewModel.selectedItemCount
    override fun getTotalItemCount(): Int = mViewModel.totalItemCount

    class ViewHolder(itemView: View) : MultiSelectionView.ViewHolder(itemView) {
        val itemView: MaterialCardView = itemView as MaterialCardView
        val iconView: AppCompatImageView = itemView.findViewById(R.id.icon)
        val listTypeView: MaterialTextView = itemView.findViewById(R.id.list_type)
        val labelView: MaterialTextView = itemView.findViewById(R.id.label)
        val packageNameView: MaterialTextView = itemView.findViewById(R.id.package_name)
        val descriptionView: MaterialTextView = itemView.findViewById(R.id.apk_description)
    }
}
