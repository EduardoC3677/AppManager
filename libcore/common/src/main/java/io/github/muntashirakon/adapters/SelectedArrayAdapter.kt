// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.adapters

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.annotation.ArrayRes
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes

/**
 * Same as [ArrayAdapter] except that it selects the TextView to allow marquee mode.
 */
open class SelectedArrayAdapter<T> : ArrayAdapter<T> {

    private val mFieldId: Int

    @JvmOverloads
    constructor(context: Context, @LayoutRes resource: Int, @IdRes textViewResourceId: Int = 0) : this(
        context,
        resource,
        textViewResourceId,
        ArrayList<T>()
    )

    @JvmOverloads
    constructor(
        context: Context,
        @LayoutRes resource: Int,
        objects: Array<T>,
        @IdRes textViewResourceId: Int = 0
    ) : this(context, resource, textViewResourceId, listOf(*objects))

    @JvmOverloads
    constructor(
        context: Context,
        @LayoutRes resource: Int,
        objects: List<T>,
        @IdRes textViewResourceId: Int = 0
    ) : this(context, resource, textViewResourceId, objects)

    constructor(
        context: Context,
        @LayoutRes resource: Int,
        @IdRes textViewResourceId: Int,
        objects: List<T>
    ) : super(context, resource, textViewResourceId, objects) {
        mFieldId = textViewResourceId
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val v = super.getView(position, convertView, parent)
        return setSelected(v)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val v = super.getDropDownView(position, convertView, parent)
        return setSelected(v)
    }

    private fun setSelected(v: View): View {
        val tv: TextView = if (mFieldId == 0) {
            //  If no custom field is assigned, assume the whole resource is a TextView
            v as TextView
        } else {
            //  Otherwise, find the TextView field within the layout
            v.findViewById(mFieldId)
        }
        if (tv.isSelected) {
            tv.isSelected = false
        }
        tv.isSelected = true
        return v
    }

    companion object {
        fun createFromResource(
            context: Context,
            @ArrayRes textArrayResId: Int,
            @LayoutRes textViewResId: Int
        ): ArrayAdapter<CharSequence> {
            val strings = context.resources.getTextArray(textArrayResId)
            return SelectedArrayAdapter(context, textViewResId, 0, listOf(*strings))
        }
    }
}
