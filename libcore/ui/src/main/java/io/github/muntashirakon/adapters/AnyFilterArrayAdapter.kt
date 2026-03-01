// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.adapters

import android.content.Context
import android.widget.Filter
import androidx.annotation.LayoutRes
import java.util.*

/**
 * An [android.widget.ArrayAdapter] that filters using [String.contains] (case-insensitive) rather than using
 * [String.startsWith] (i.e. prefix matching).
 */
class AnyFilterArrayAdapter<T>(context: Context, @LayoutRes resource: Int, private val mObjects: List<T>) :
    SelectedArrayAdapter<T>(context, resource, 0, ArrayList(mObjects)) {

    private val mFilter: Filter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val filterResults = FilterResults()
            if (constraint.isNullOrEmpty()) {
                filterResults.count = mObjects.size
                filterResults.values = mObjects
                return filterResults
            }

            val query = constraint.toString().lowercase(Locale.ROOT)
            val list = ArrayList<T>(mObjects.size)
            for (item in mObjects) {
                if (item.toString().lowercase(Locale.ROOT).contains(query))
                    list.add(item)
            }
            filterResults.count = list.size
            filterResults.values = list
            return filterResults
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults) {
            clear()
            addAll((results.values as List<T>))
            notifyDataSetChanged()
        }
    }

    override fun getFilter(): Filter {
        return mFilter
    }
}
