// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.dialogs

import android.app.Application
import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.BundleCompat
import androidx.core.util.Pair
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.utils.DigestUtils
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.AppManager.utils.UIUtils.displayLongToast
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import io.github.muntashirakon.util.AdapterUtils
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.IOException
import java.security.Security
import java.util.*

class ChecksumsDialogFragment : DialogFragment() {
    private var mPath: Path? = null
    private var mDialogView: View? = null
    private var mViewModel: ChecksumsViewModel? = null
    private var mAdapter: ChecksumsAdapter? = null

    init {
        Security.addProvider(BouncyCastleProvider())
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mViewModel = ViewModelProvider(this).get(ChecksumsViewModel::class.java)
        mPath = Paths.get(BundleCompat.getParcelable(requireArguments(), ARG_PATH, Uri::class.java)!!)
        mAdapter = ChecksumsAdapter()
        mDialogView = View.inflate(requireActivity(), R.layout.dialog_checksums, null)
        val recyclerView: RecyclerView = mDialogView!!.findViewById(R.id.list)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = mAdapter
        val copyAll: MaterialButton = mDialogView!!.findViewById(R.id.action_copy_all)
        copyAll.setOnClickListener {
            val sb = StringBuilder()
            for (checksum in mAdapter!!.items) {
                sb.append(checksum.first).append(": ").append(checksum.second).append("
")
            }
            if (sb.isNotEmpty()) {
                io.github.muntashirakon.util.UiUtils.copyToClipboard(requireContext(), sb.toString())
                displayLongToast(getString(R.string.copied_to_clipboard))
            }
        }
        return MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.checksums)
            .setView(mDialogView)
            .setPositiveButton(R.string.close, null)
            .create()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return mDialogView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mViewModel!!.checksumsLiveData.observe(viewLifecycleOwner) { checksums ->
            mAdapter!!.setItems(checksums)
        }
        mViewModel!!.loadChecksums(mPath!!)
    }

    private class ChecksumsAdapter : RecyclerView.Adapter<ChecksumsAdapter.ViewHolder>() {
        val items = mutableListOf<Pair<String, String>>()

        fun setItems(newItems: List<Pair<String, String>>) {
            val lastCount = items.size
            items.clear()
            items.addAll(newItems)
            AdapterUtils.notifyDataSetChanged(this, lastCount, items.size)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_checksum, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.algo.text = item.first
            holder.hash.text = item.second
            holder.itemView.setOnClickListener {
                io.github.muntashirakon.util.UiUtils.copyToClipboard(holder.itemView.context, item.second)
                displayLongToast(holder.itemView.context.getString(R.string.copied_to_clipboard))
            }
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val algo: TextView = itemView.findViewById(R.id.algo)
            val hash: TextView = itemView.findViewById(R.id.hash)
        }
    }

    class ChecksumsViewModel(application: Application) : AndroidViewModel(application) {
        val checksumsLiveData = MutableLiveData<List<Pair<String, String>>>()

        fun loadChecksums(path: Path) {
            ThreadUtils.postOnBackgroundThread {
                val algos = arrayOf(
                    DigestUtils.MD5, DigestUtils.SHA_1, DigestUtils.SHA_256,
                    DigestUtils.SHA_512, DigestUtils.SHA3_256, DigestUtils.SHA3_512
                )
                val results = mutableListOf<Pair<String, String>>()
                for (algo in algos) {
                    try {
                        val hash = DigestUtils.getHexDigest(algo, path)
                        results.add(Pair(algo, hash))
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
                checksumsLiveData.postValue(results)
            }
        }
    }

    companion object {
        private const val ARG_PATH = "path"

        @JvmStatic
        fun getInstance(path: Path): ChecksumsDialogFragment {
            val fragment = ChecksumsDialogFragment()
            val args = Bundle()
            args.putParcelable(ARG_PATH, path.getUri())
            fragment.arguments = args
            return fragment
        }
    }
}
