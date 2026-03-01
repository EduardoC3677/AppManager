// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.whatsnew

import android.content.pm.PackageInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.widget.RecyclerView

class WhatsNewFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return View.inflate(requireContext(), R.layout.dialog_whats_new, null)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val viewModel = ViewModelProvider(this).get(WhatsNewDialogViewModel::class.java)
        val newPkgInfo = BundleCompat.getParcelable(requireArguments(), ARG_NEW_PKG_INFO, PackageInfo::class.java)!!
        val oldPkgInfo = BundleCompat.getParcelable(requireArguments(), ARG_OLD_PKG_INFO, PackageInfo::class.java)!!
        val recyclerView: RecyclerView = view.findViewById(android.R.id.list)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        val adapter = WhatsNewRecyclerAdapter(requireContext(), newPkgInfo.packageName)
        recyclerView.adapter = adapter
        viewModel.changesLiveData.observe(viewLifecycleOwner) { adapter.setAdapterList(it) }
        viewModel.loadChanges(newPkgInfo, oldPkgInfo)
    }

    companion object {
        val TAG: String = WhatsNewFragment::class.java.simpleName
        private const val ARG_NEW_PKG_INFO = "new_pkg"
        private const val ARG_OLD_PKG_INFO = "old_pkg"

        @JvmStatic
        fun getInstance(newPkgInfo: PackageInfo, oldPkgInfo: PackageInfo?): WhatsNewFragment {
            return WhatsNewFragment().apply { arguments = Bundle().apply { putParcelable(ARG_NEW_PKG_INFO, newPkgInfo); putParcelable(ARG_OLD_PKG_INFO, oldPkgInfo) } }
        }
    }
}
