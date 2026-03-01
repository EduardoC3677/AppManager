// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import io.github.muntashirakon.AppManager.R

class ConfFragment : Fragment() {
    private var mActivity: AppsBaseProfileActivity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mActivity = requireActivity() as AppsBaseProfileActivity
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_container, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val model = ViewModelProvider(requireActivity()).get(AppsProfileViewModel::class.java)
        model.observeProfileLoaded().observe(viewLifecycleOwner) { profileName ->
            if (profileName == null) return@observe
            childFragmentManager
                .beginTransaction()
                .replace(R.id.fragment_container_view_tag, ConfPreferences())
                .commit()
        }
    }

    override fun onResume() {
        super.onResume()
        mActivity!!.supportActionBar?.setSubtitle(R.string.configurations)
        mActivity!!.fab.hide()
    }
}
