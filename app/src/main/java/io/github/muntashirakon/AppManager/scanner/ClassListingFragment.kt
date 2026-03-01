// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.AppManager.utils.UIUtils.getMonospacedText
import java.util.*

class ClassListingFragment : Fragment() {
    private var mViewModel: ScannerViewModel? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_class_listing, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mViewModel = ViewModelProvider(requireActivity()).get(ScannerViewModel::class.java)
        val title: TextView = view.findViewById(R.id.class_listing_title)
        val description: TextView = view.findViewById(R.id.class_listing_description)

        mViewModel!!.getAllClassesLiveData().observe(viewLifecycleOwner) { allClasses ->
            title.text = resources.getQuantityString(R.plurals.classes, allClasses.size, allClasses.size)
            val sb = SpannableStringBuilder()
            for (i in allClasses.indices) {
                sb.append(getMonospacedText(allClasses[i]))
                if (i < allClasses.size - 1) sb.append("
")
            }
            description.text = sb
        }
    }

    companion object {
        val TAG: String = ClassListingFragment::class.java.simpleName
    }
}
