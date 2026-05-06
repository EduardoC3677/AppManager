// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.whatsnew

import android.app.Dialog
import android.content.pm.PackageInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.widget.RecyclerView

class WhatsNewDialogFragment : DialogFragment() {
    private var mAdapter: WhatsNewRecyclerAdapter? = null
    private var mDialogView: View? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mDialogView = View.inflate(requireContext(), R.layout.dialog_whats_new, null)
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.whats_new)
            .setView(mDialogView)
            .setNegativeButton(R.string.ok, null)
            .create()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? = mDialogView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val viewModel = ViewModelProvider(this).get(WhatsNewDialogViewModel::class.java)
        val newPkgInfo = BundleCompat.getParcelable(requireArguments(), ARG_NEW_PKG_INFO, PackageInfo::class.java)!!
        val oldPkgInfo = BundleCompat.getParcelable(requireArguments(), ARG_OLD_PKG_INFO, PackageInfo::class.java)!!
        val recyclerView: RecyclerView = mDialogView!!.findViewById(android.R.id.list)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        mAdapter = WhatsNewRecyclerAdapter(requireContext(), newPkgInfo.packageName)
        recyclerView.adapter = mAdapter
        viewModel.changesLiveData.observe(this) { mAdapter!!.setAdapterList(it) }
        viewModel.loadChanges(newPkgInfo, oldPkgInfo)
    }

    override fun show(manager: FragmentManager, tag: String?) {
        manager.beginTransaction().add(this, tag).commitAllowingStateLoss()
    }

    companion object {
        val TAG: String = WhatsNewDialogFragment::class.java.simpleName
        private const val ARG_NEW_PKG_INFO = "new_pkg"\nprivate const val ARG_OLD_PKG_INFO = "old_pkg"

        @JvmStatic
        fun getInstance(newPkgInfo: PackageInfo, oldPkgInfo: PackageInfo?): WhatsNewDialogFragment {
            return WhatsNewDialogFragment().apply { arguments = Bundle().apply { putParcelable(ARG_NEW_PKG_INFO, newPkgInfo); putParcelable(ARG_OLD_PKG_INFO, oldPkgInfo) } }
        }
    }
}
