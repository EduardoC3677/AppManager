// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.adapters

import android.content.Context
import android.widget.Filter
import androidx.annotation.LayoutRes

/**
 * An [android.widget.ArrayAdapter] incapable of filtering i.e. returns everything regardless of the filtered text.
 */
class NoFilterArrayAdapter<T>(context: Context, @LayoutRes resource: Int, objects: List<T>) :
    SelectedArrayAdapter<T>(context, resource, objects) {
    private val mDummyFilter: Filter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults? {
            return null
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            notifyDataSetChanged()
        }
    }

    override fun getFilter(): Filter {
        return mDummyFilter
    }
}
