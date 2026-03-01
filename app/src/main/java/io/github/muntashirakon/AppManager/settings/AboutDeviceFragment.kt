// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.misc.DeviceInfo2
import io.github.muntashirakon.util.UiUtils

class AboutDeviceFragment : Fragment() {
    private lateinit var mModel: MainPreferencesViewModel
    private var mTextView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mModel = ViewModelProvider(requireActivity())[MainPreferencesViewModel::class.java]
    }

    override fun onStart() {
        super.onStart()
        requireActivity().setTitle(R.string.about_device)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(io.github.muntashirakon.ui.R.layout.dialog_scrollable_text_view, container, false) as NestedScrollView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            view.scrollIndicators = 0
        }
        val lp = view.layoutParams
        if (lp is ViewGroup.MarginLayoutParams) {
            lp.topMargin = 0
            view.layoutParams = lp
        }
        var secondary = false
        val args = arguments
        if (args != null) {
            secondary = args.getBoolean(PreferenceFragment.PREF_SECONDARY)
            args.remove(PreferenceFragment.PREF_KEY)
            args.remove(PreferenceFragment.PREF_SECONDARY)
        }
        if (secondary) {
            UiUtils.applyWindowInsetsAsPadding(view, false, true, false, true)
        } else {
            UiUtils.applyWindowInsetsAsPaddingNoTop(view)
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mTextView = view.findViewById(android.R.id.content)
        view.findViewById<View>(android.R.id.checkbox).visibility = View.GONE
        mModel.getDeviceInfo().observe(viewLifecycleOwner) { deviceInfo ->
            mTextView!!.text = deviceInfo.toLocalizedString(requireActivity())
        }
        mModel.loadDeviceInfo(DeviceInfo2(requireActivity()))
    }
}
