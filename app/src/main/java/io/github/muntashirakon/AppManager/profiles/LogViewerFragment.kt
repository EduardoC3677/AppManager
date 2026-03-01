// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles

import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatEditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.util.UiUtils

class LogViewerFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_log_viewer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val model = ViewModelProvider(requireActivity()).get(AppsProfileViewModel::class.java)
        val tv: AppCompatEditText = view.findViewById(R.id.log_content)
        tv.keyListener = null
        val efab: ExtendedFloatingActionButton = view.findViewById(R.id.floatingActionButton)
        UiUtils.applyWindowInsetsAsMargin(efab, false, true)
        efab.setOnClickListener {
            ProfileLogger.clearLogs(model.getProfileId())
            tv.setText("")
        }
        model.getLogs().observe(viewLifecycleOwner) { logs -> tv.setText(getFormattedLogs(logs)) }
        model.observeProfileLoaded().observe(viewLifecycleOwner) { model.loadLogs() }
    }

    override fun onResume() {
        val activity = requireActivity() as AppsBaseProfileActivity
        activity.supportActionBar?.setSubtitle(R.string.log_viewer)
        activity.fab.hide()
        super.onResume()
    }

    fun getFormattedLogs(logs: String): CharSequence {
        val str = SpannableString(logs)
        var fIndex = 0
        while (true) {
            fIndex = logs.indexOf("====> ", fIndex)
            if (fIndex == -1) {
                return str
            }
            val lIndex = logs.indexOf('
', fIndex)
            if (lIndex == -1) {
                str.setSpan(StyleSpan(Typeface.BOLD), fIndex, logs.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
                return str
            }
            str.setSpan(StyleSpan(Typeface.BOLD), fIndex, lIndex, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
            fIndex = lIndex
        }
    }
}
