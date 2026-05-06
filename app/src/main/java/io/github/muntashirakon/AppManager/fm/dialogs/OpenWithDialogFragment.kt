// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.dialogs

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import android.os.UserHandleHidden
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.compat.ActivityManagerCompat
import io.github.muntashirakon.AppManager.fm.FmProvider
import io.github.muntashirakon.AppManager.intercept.ActivityInterceptor
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader
import io.github.muntashirakon.AppManager.settings.FeatureController
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.dialog.DialogTitleBuilder
import io.github.muntashirakon.dialog.SearchableItemsDialogBuilder
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.PathContentInfo
import io.github.muntashirakon.io.Paths
import io.github.muntashirakon.lifecycle.SingleLiveEvent
import io.github.muntashirakon.util.AdapterUtils
import io.github.muntashirakon.widget.SearchView
import java.util.*

class OpenWithDialogFragment : DialogFragment() {
    private var mPath: Path? = null
    private var mCustomType: String? = null
    private var mCloseActivity: Boolean = false
    private var mDialogView: View? = null
    private var mSearchView: SearchView? = null
    private var mViewModel: OpenWithViewModel? = null
    private var mAdapter: MatchingActivitiesRecyclerViewAdapter? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mViewModel = ViewModelProvider(this).get(OpenWithViewModel::class.java)
        mPath = Paths.get(Objects.requireNonNull(BundleCompat.getParcelable(requireArguments(), ARG_PATH, Uri::class.java)))
        mCustomType = requireArguments().getString(ARG_TYPE, null)
        mCloseActivity = requireArguments().getBoolean(ARG_CLOSE_ACTIVITY, false)
        mAdapter = MatchingActivitiesRecyclerViewAdapter(mViewModel!!, requireActivity())
        mAdapter!!.intent = getIntent(mPath!!, mCustomType)
        mDialogView = View.inflate(requireActivity(), R.layout.dialog_open_with, null)
        mSearchView = mDialogView!!.findViewById(io.github.muntashirakon.ui.R.id.action_search)
        mSearchView!!.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean = false
            override fun onQueryTextChange(newText: String): Boolean {
                mAdapter!!.setFilteredItems(newText)
                return true
            }
        })
        val matchingActivitiesView: RecyclerView = mDialogView!!.findViewById(R.id.intent_matching_activities)
        matchingActivitiesView.layoutManager = LinearLayoutManager(requireContext())
        matchingActivitiesView.adapter = mAdapter
        val alwaysOpen: CheckBox = mDialogView!!.findViewById(R.id.always_open)
        val openForThisFileOnly: CheckBox = mDialogView!!.findViewById(R.id.only_for_this_file)
        alwaysOpen.visibility = View.GONE
        openForThisFileOnly.visibility = View.GONE
        val titleBuilder = DialogTitleBuilder(requireActivity())
            .setTitle(R.string.file_open_with)
            .setSubtitle(mPath!!.getName())
            .setEndIcon(R.drawable.ic_open_in_new) {
                if (mAdapter!!.intent.resolveActivityInfo(requireActivity().packageManager, 0) != null) {
                    startActivity(mAdapter!!.intent)
                }
                dismiss()
            }
            .setEndIconContentDescription(R.string.file_open_with_os_default_dialog)
        val alertDialog = MaterialAlertDialogBuilder(requireActivity())
            .setCustomTitle(titleBuilder.build())
            .setView(mDialogView)
            .setPositiveButton(R.string.file_open_as, null)
            .setNeutralButton(R.string.file_open_with_custom_activity, null)
            .create()
        alertDialog.setOnShowListener {
            val fileOpenAsButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val customButton = alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            fileOpenAsButton.setOnClickListener {
                val customTypes = requireContext().resources.getStringArray(R.array.file_open_as_option_types)
                SearchableItemsDialogBuilder<String>(requireActivity(), R.array.file_open_as_options)
                    .setTitle(R.string.file_open_as)
                    .hideSearchBar(true)
                    .setOnItemClickListener { dialog1, which, _ ->
                        mCustomType = customTypes[which]
                        mAdapter!!.intent = getIntent(mPath!!, mCustomType)
                        mViewModel!!.loadMatchingActivities(mAdapter!!.intent)
                        dialog1.dismiss()
                    }
                    .setNegativeButton(R.string.close, null)
                    .show()
            }
            customButton.visibility = View.GONE
        }
        return alertDialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return mDialogView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mViewModel?.let { viewModel ->
            it.getMatchingActivitiesLiveData().observe(viewLifecycleOwner) { matchingActivities ->
                mAdapter!!.setDefaultList(matchingActivities)
                mSearchView!!.visibility = if (matchingActivities.size < 6) View.GONE else View.VISIBLE
            }
            it.getPathContentInfoLiveData().observe(viewLifecycleOwner) { pathContentInfo ->
                mAdapter!!.intent = getIntent(mPath!!, pathContentInfo.getMimeType())
                viewModel.loadMatchingActivities(mAdapter!!.intent)
            }
            it.getIntentLiveData().observe(viewLifecycleOwner) { intent ->
                try {
                    ActivityManagerCompat.startActivity(intent, UserHandleHidden.myUserId())
                    dismiss()
                } catch (e: SecurityException) {
                    UIUtils.displayLongToast("Failed: " + e.message)
                }
            }
            if (mCustomType == null) {
                viewModel.loadFileContentInfo(mPath!!)
            }
            viewModel.loadMatchingActivities(mAdapter!!.intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mCloseActivity) {
            requireActivity().finish()
        }
    }

    private fun getIntent(path: Path, customType: String?): Intent {
        var flags = Intent.FLAG_ACTIVITY_NEW_TASK
        if (path.canRead()) flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (path.canWrite()) flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(FmProvider.getContentUri(path), customType ?: path.getType())
        intent.flags = flags
        return intent
    }

    private class MatchingActivitiesRecyclerViewAdapter(private val mViewModel: OpenWithViewModel, private val mActivity: Activity) :
        RecyclerView.Adapter<MatchingActivitiesRecyclerViewAdapter.ViewHolder>() {
        private val mMatchingActivities = mutableListOf<ResolvedActivityInfo>()
        private val mFilteredItems = mutableListOf<Int>()
        private val mImageLoader = ImageLoader.getInstance()
        var intent: Intent = Intent()
        private var mConstraint: String? = null

        fun setDefaultList(matchingActivities: List<ResolvedActivityInfo>?) {
            mMatchingActivities.clear()
            if (matchingActivities != null) {
                mMatchingActivities.addAll(matchingActivities)
            }
            filterItems()
        }

        fun setFilteredItems(constraint: String?) {
            mConstraint = if (TextUtils.isEmpty(constraint)) null else constraint!!.lowercase(Locale.getDefault())
            filterItems()
        }

        private fun filterItems() {
            synchronized(mFilteredItems) {
                val lastCount = mFilteredItems.size
                mFilteredItems.clear()
                for (i in mMatchingActivities.indices) {
                    if (mConstraint == null || mMatchingActivities[i].matches(mConstraint)) {
                        mFilteredItems.add(i)
                    }
                }
                AdapterUtils.notifyDataSetChanged(this, lastCount, mFilteredItems.size)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(io.github.muntashirakon.ui.R.layout.m3_preference, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val index = synchronized(mFilteredItems) { mFilteredItems[position] }
            val resolvedInfo = mMatchingActivities[index]
            holder.title.text = resolvedInfo.label
            val activityName = resolvedInfo.name
            val summary = resolvedInfo.appLabel.toString() + "\n" + resolvedInfo.shortName
            holder.summary.text = summary
            val tag = resolvedInfo.packageName + "_" + resolvedInfo.label
            holder.icon.tag = tag
            mImageLoader.displayImage(tag, holder.icon, ResolveInfoImageFetcher(resolvedInfo.resolveInfo))
            holder.itemView.setOnClickListener {
                val newIntent = Intent(intent)
                newIntent.setClassName(resolvedInfo.packageName, activityName)
                mViewModel.openIntent(newIntent)
            }
            holder.itemView.setOnLongClickListener {
                if (!FeatureController.isInterceptorEnabled()) return@setOnLongClickListener false
                val newIntent = Intent(intent)
                newIntent.putExtra(ActivityInterceptor.EXTRA_PACKAGE_NAME, resolvedInfo.packageName)
                newIntent.putExtra(ActivityInterceptor.EXTRA_CLASS_NAME, activityName)
                newIntent.setClassName(mActivity, ActivityInterceptor::class.java.name)
                mViewModel.openIntent(newIntent)
                true
            }
        }

        override fun getItemCount(): Int = synchronized(mFilteredItems) { mFilteredItems.size }

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val title: TextView = itemView.findViewById(android.R.id.title)
            val summary: TextView = itemView.findViewById(android.R.id.summary)
            val icon: ImageView = itemView.findViewById(android.R.id.icon)

            init {
                icon.contentDescription = itemView.context.getString(R.string.icon)
            }
        }
    }

    class OpenWithViewModel(application: Application) : AndroidViewModel(application) {
        private val mMatchingActivitiesLiveData = MutableLiveData<List<ResolvedActivityInfo>>()
        private val mPathContentInfoLiveData = MutableLiveData<PathContentInfo>()
        private val mIntentLiveData = SingleLiveEvent<Intent>()
        private val mPm: PackageManager = application.packageManager

        fun loadMatchingActivities(intent: Intent) {
            ThreadUtils.postOnBackgroundThread {
                val resolveInfoList = mPm.queryIntentActivities(intent, 0)
                val resolvedActivityInfoList = resolveInfoList.map {
                    ResolvedActivityInfo(it, it.loadLabel(mPm), it.activityInfo.applicationInfo.loadLabel(mPm))
                }
                mMatchingActivitiesLiveData.postValue(resolvedActivityInfoList)
            }
        }

        fun loadFileContentInfo(path: Path) {
            ThreadUtils.postOnBackgroundThread { mPathContentInfoLiveData.postValue(path.getPathContentInfo()) }
        }

        fun openIntent(intent: Intent) {
            mIntentLiveData.value = intent
        }

        fun getMatchingActivitiesLiveData(): LiveData<List<ResolvedActivityInfo>> = mMatchingActivitiesLiveData
        fun getPathContentInfoLiveData(): LiveData<PathContentInfo> = mPathContentInfoLiveData
        fun getIntentLiveData(): LiveData<Intent> = mIntentLiveData
    }

    private class ResolvedActivityInfo(val resolveInfo: ResolveInfo, val label: CharSequence, val appLabel: CharSequence) {
        val packageName: String = resolveInfo.activityInfo.packageName
        val name: String = resolveInfo.activityInfo.name
        val shortName: String = getShortActivityName(name)

        fun matches(constraint: String?): Boolean {
            if (constraint == null) return true
            return label.toString().lowercase(Locale.getDefault()).contains(constraint)
                    || shortName.contains(constraint)
                    || appLabel.toString().lowercase(Locale.getDefault()).contains(constraint)
        }

        private fun getShortActivityName(longName: String): String {
            val idxOfDot = longName.lastIndexOf('.')
            return if (idxOfDot == -1) longName else longName.substring(idxOfDot + 1)
        }
    }

    private class ResolveInfoImageFetcher(private val mInfo: ResolveInfo?) : ImageLoader.ImageFetcherInterface {
        override fun fetchImage(tag: String): ImageLoader.ImageFetcherResult {
            val pm = ContextUtils.getContext().packageManager
            val drawable = mInfo?.loadIcon(pm)
            return ImageLoader.ImageFetcherResult(
                tag,
                if (drawable != null) UIUtils.getBitmapFromDrawable(drawable) else null,
                false, true,
                ImageLoader.DefaultImageDrawable("android_default_icon", pm.defaultActivityIcon)
            )
        }
    }

    companion object {
        val TAG: String = OpenWithDialogFragment::class.java.simpleName
        private const val ARG_PATH = "path"\nprivate const val ARG_TYPE = "type"\nprivate const val ARG_CLOSE_ACTIVITY = "close"

        @JvmStatic
        fun getInstance(path: Path): OpenWithDialogFragment = getInstance(path, null)

        @JvmStatic
        fun getInstance(path: Path, type: String?): OpenWithDialogFragment = getInstance(path.getUri(), type, false)

        @JvmStatic
        fun getInstance(uri: Uri, type: String?, closeActivity: Boolean): OpenWithDialogFragment {
            val fragment = OpenWithDialogFragment()
            val args = Bundle()
            args.putParcelable(ARG_PATH, uri)
            args.putString(ARG_TYPE, type)
            args.putBoolean(ARG_CLOSE_ACTIVITY, closeActivity)
            fragment.arguments = args
            return fragment
        }
    }
}
