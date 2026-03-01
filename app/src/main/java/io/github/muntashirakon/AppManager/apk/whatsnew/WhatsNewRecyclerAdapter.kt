// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.whatsnew

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.textview.MaterialTextView
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes
import io.github.muntashirakon.util.AdapterUtils
import io.github.muntashirakon.widget.RecyclerView

internal class WhatsNewRecyclerAdapter(context: Context, private val mPackageName: String) : RecyclerView.Adapter<WhatsNewRecyclerAdapter.ViewHolder>() {
    private val mAdapterList = mutableListOf<ApkWhatsNewFinder.Change>()
    private val mColorAdd = ColorCodes.getWhatsNewPlusIndicatorColor(context)
    private val mColorRemove = ColorCodes.getWhatsNewMinusIndicatorColor(context)
    private val mColorNeutral = UIUtils.getTextColorPrimary(context)
    private val mTypefaceMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun setAdapterList(list: List<ApkWhatsNewFinder.Change>) {
        AdapterUtils.notifyDataSetChanged(this, mAdapterList, list)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutId = if (viewType == ApkWhatsNewFinder.CHANGE_INFO) R.layout.item_text_view else R.layout.item_whats_new
        return ViewHolder(LayoutInflater.from(parent.context).inflate(layoutId, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val change = mAdapterList[position]
        var value = change.value
        if (value.startsWith(mPackageName)) value = value.replaceFirst(mPackageName, "")
        when (change.changeType) {
            ApkWhatsNewFinder.CHANGE_ADD -> {
                holder.changeSign?.text = "+"
                holder.changeSign?.setTextColor(mColorAdd)
                holder.textView.text = value
                holder.textView.setTextColor(mColorAdd)
            }
            ApkWhatsNewFinder.CHANGE_INFO -> {
                holder.textView.text = value
                holder.textView.setTextColor(mColorNeutral)
                holder.textView.typeface = mTypefaceMedium
                holder.textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            }
            ApkWhatsNewFinder.CHANGE_REMOVED -> {
                holder.changeSign?.text = "-"
                holder.changeSign?.setTextColor(mColorRemove)
                holder.textView.text = value
                holder.textView.setTextColor(mColorRemove)
            }
        }
    }

    override fun getItemCount(): Int = mAdapterList.size
    override fun getItemViewType(position: Int): Int = mAdapterList[position].changeType

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val changeSign: MaterialTextView? = itemView.findViewById(android.R.id.text2)
        val textView: MaterialTextView = itemView.findViewById(android.R.id.text1)
    }
}
