// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.DocumentsContract
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.format.Formatter
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.DrawableRes
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
import androidx.core.provider.DocumentsContractCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.leinardi.android.speeddial.SpeedDialActionItem
import com.leinardi.android.speeddial.SpeedDialView
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.fm.dialogs.*
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.settings.SettingsActivity
import io.github.muntashirakon.AppManager.shortcut.CreateShortcutDialogFragment
import io.github.muntashirakon.AppManager.utils.*
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import io.github.muntashirakon.multiselection.MultiSelectionActionsView
import io.github.muntashirakon.util.UiUtils
import io.github.muntashirakon.widget.FloatingActionButtonGroup
import io.github.muntashirakon.widget.MultiSelectionView
import io.github.muntashirakon.widget.RecyclerView
import io.github.muntashirakon.widget.SwipeRefreshLayout
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class FmFragment : Fragment(), MenuProvider, SearchView.OnQueryTextListener,
    SwipeRefreshLayout.OnRefreshListener, SpeedDialView.OnActionSelectedListener,
    MultiSelectionActionsView.OnItemSelectedListener,
    MultiSelectionView.OnSelectionModeChangeListener {

    private var mModel: FmViewModel? = null
    private var mRecyclerView: RecyclerView? = null
    private var mEmptyView: LinearLayoutCompat? = null
    private var mEmptyViewIcon: ImageView? = null
    private var mEmptyViewTitle: TextView? = null
    private var mEmptyViewDetails: TextView? = null
    private var mAdapter: FmAdapter? = null
    private var mSwipeRefresh: SwipeRefreshLayout? = null
    private var mMultiSelectionView: MultiSelectionView? = null
    private var mFabGroup: FloatingActionButtonGroup? = null
    private var mPathListAdapter: FmPathListAdapter? = null
    private var mActivity: FmActivity? = null

    private var mFolderShortInfo: FolderShortInfo? = null

    private val mMultiSelectionViewChangeListener = ViewTreeObserver.OnGlobalLayoutListener {
        if (mFabGroup != null && activity != null) {
            val defaultMargin = UiUtils.dpToPx(requireContext(), 16)
            val newMargin = if (mMultiSelectionView!!.visibility == View.VISIBLE) {
                defaultMargin + mMultiSelectionView!!.height
            } else defaultMargin
            val marginLayoutParams = mFabGroup!!.layoutParams as ViewGroup.MarginLayoutParams
            if (marginLayoutParams.bottomMargin != newMargin) {
                marginLayoutParams.bottomMargin = newMargin
                mFabGroup!!.layoutParams = marginLayoutParams
            }
        }
    }

    private val mExitSelectionBackPressedCallback: OnBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (mAdapter != null && mMultiSelectionView != null && mAdapter!!.isInSelectionMode) {
                mMultiSelectionView!!.cancel()
                return
            }
            isEnabled = false
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private val mGoUpBackPressedCallback: OnBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (mPathListAdapter != null && mPathListAdapter!!.getCurrentPosition() > 0) {
                mModel!!.loadFiles(mPathListAdapter!!.calculateUri(mPathListAdapter!!.getCurrentPosition() - 1))
                return
            }
            isEnabled = false
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mModel = ViewModelProvider(this).get(FmViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_fm, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        var options: FmActivity.Options? = null
        var uri: Uri? = null
        val scrollPosition = AtomicInteger(RecyclerView.NO_POSITION)
        if (savedInstanceState != null) {
            uri = BundleCompat.getParcelable(savedInstanceState, ARG_URI, Uri::class.java)
            options = BundleCompat.getParcelable(savedInstanceState, ARG_OPTIONS, FmActivity.Options::class.java)
            scrollPosition.set(savedInstanceState.getInt(ARG_POSITION, RecyclerView.NO_POSITION))
        } else {
            val args = arguments
            if (args != null) {
                options = BundleCompat.getParcelable(args, ARG_OPTIONS, FmActivity.Options::class.java)
                scrollPosition.set(args.getInt(ARG_POSITION, RecyclerView.NO_POSITION))
            }
        }
        if (options == null) {
            options = FmActivity.Options(Uri.fromFile(File("/")))
        }

        mActivity = requireActivity() as FmActivity
        mActivity!!.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        mSwipeRefresh = view.findViewById(R.id.swipe_refresh)
        mSwipeRefresh!!.setOnRefreshListener(this)
        mRecyclerView = view.findViewById(R.id.list)
        mRecyclerView!!.layoutManager = LinearLayoutManager(requireContext())
        mAdapter = FmAdapter(mModel!!, mActivity!!)
        mRecyclerView!!.adapter = mAdapter
        mEmptyView = view.findViewById(R.id.empty_view)
        mEmptyViewIcon = view.findViewById(R.id.empty_view_icon)
        mEmptyViewTitle = view.findViewById(R.id.empty_view_title)
        mEmptyViewDetails = view.findViewById(R.id.empty_view_details)
        mMultiSelectionView = view.findViewById(R.id.multi_selection_view)
        mMultiSelectionView!!.adapter = mAdapter
        mMultiSelectionView!!.onSelectionModeChangeListener = this
        mMultiSelectionView!!.onItemSelectedListener = this
        mMultiSelectionView!!.viewTreeObserver.addOnGlobalLayoutListener(mMultiSelectionViewChangeListener)
        val batchOpsHandler = BatchOpsHandler(mMultiSelectionView!!)
        mMultiSelectionView!!.onSelectionChangeListener = batchOpsHandler
        mFabGroup = view.findViewById(R.id.fab_group)
        mFabGroup!!.speedDialView!!.setOnActionSelectedListener(this)

        val pathListView: androidx.recyclerview.widget.RecyclerView = view.findViewById(R.id.path_list)
        pathListView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        mPathListAdapter = FmPathListAdapter(mModel!!)
        pathListView.adapter = mPathListAdapter

        mModel!!.getFmItemsLiveData().observe(viewLifecycleOwner) { fmItems ->
            mAdapter!!.setFmList(fmItems)
            if (fmItems.isEmpty()) {
                handleEmptyView(R.drawable.ic_empty_folder, getString(R.string.folder_is_empty), null)
            } else {
                mEmptyView!!.visibility = View.GONE
            }
            if (scrollPosition.get() != RecyclerView.NO_POSITION) {
                mRecyclerView!!.scrollToPosition(scrollPosition.get())
                scrollPosition.set(RecyclerView.NO_POSITION)
            } else {
                mRecyclerView!!.scrollToPosition(mModel!!.getCurrentScrollPosition())
            }
            mSwipeRefresh!!.isRefreshing = false
        }
        mModel!!.getFmErrorLiveData().observe(viewLifecycleOwner) { th ->
            handleEmptyView(R.drawable.ic_error, getString(R.string.error), th)
            mSwipeRefresh!!.isRefreshing = false
        }
        mModel!!.getUriLiveData().observe(viewLifecycleOwner) { currentUri ->
            mPathListAdapter!!.setCurrentUri(currentUri)
            pathListView.scrollToPosition(mPathListAdapter!!.itemCount - 1)
            mGoUpBackPressedCallback.isEnabled = mPathListAdapter!!.getCurrentPosition() > 0
            mMultiSelectionView!!.cancel()
        }
        mModel!!.getFolderShortInfoLiveData().observe(viewLifecycleOwner) { folderShortInfo ->
            mFolderShortInfo = folderShortInfo
            val subtitle = SpannableStringBuilder()
            if (folderShortInfo.size != -1L) {
                subtitle.append(Formatter.formatShortFileSize(requireContext(), folderShortInfo.size)).append(" • ")
            }
            subtitle.append(resources.getQuantityString(R.plurals.folder_count, folderShortInfo.folderCount, folderShortInfo.folderCount))
                .append(", ")
                .append(resources.getQuantityString(R.plurals.file_count, folderShortInfo.fileCount, folderShortInfo.fileCount))
            if (folderShortInfo.canRead || folderShortInfo.canWrite) {
                subtitle.append(" • ")
                if (folderShortInfo.canRead) subtitle.append("R")
                if (folderShortInfo.canWrite) subtitle.append("W")
            }
            if (!folderShortInfo.canWrite) {
                if (mFabGroup!!.isShown) mFabGroup!!.hide()
            } else {
                if (!mFabGroup!!.isShown) mFabGroup!!.show()
            }
            mActivity!!.supportActionBar?.subtitle = subtitle
        }
        mModel!!.getDisplayPropertiesLiveData().observe(viewLifecycleOwner) { uri1 ->
            FilePropertiesDialogFragment.getInstance(Paths.get(uri1)).show(mActivity!!.supportFragmentManager, FilePropertiesDialogFragment.TAG)
        }
        mModel!!.getShortcutCreatorLiveData().observe(viewLifecycleOwner) { pathBitmapPair ->
            val path = pathBitmapPair.first
            val icon = pathBitmapPair.second
            val shortcutInfo = FmShortcutInfo(path, null)
            if (icon != null) {
                shortcutInfo.icon = icon
            } else {
                val drawable = ContextCompat.getDrawable(requireContext(), if (path.isDirectory()) R.drawable.ic_folder else R.drawable.ic_file)!!
                shortcutInfo.icon = UIUtils.getBitmapFromDrawable(drawable)
            }
            CreateShortcutDialogFragment.getInstance(shortcutInfo).show(childFragmentManager, CreateShortcutDialogFragment.TAG)
        }
        mModel!!.getSharableItemsLiveData().observe(viewLifecycleOwner) { sharableItems ->
            mActivity!!.startActivity(sharableItems.toSharableIntent())
        }
        mModel!!.setOptions(options, uri)
    }

    override fun onStop() {
        super.onStop()
        if (mModel != null && mRecyclerView != null) {
            Prefs.FileManager.setLastOpenedPath(mModel!!.getOptions(), mModel!!.currentUri, getRecyclerViewFirstChildPosition())
        }
    }

    override fun onDestroyView() {
        mMultiSelectionView?.viewTreeObserver?.removeOnGlobalLayoutListener(mMultiSelectionViewChangeListener)
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (mModel != null) {
            outState.putParcelable(ARG_URI, mModel!!.currentUri)
            outState.putParcelable(ARG_OPTIONS, mModel!!.getOptions())
        }
        if (mRecyclerView != null) {
            val v = mRecyclerView!!.getChildAt(0)
            if (v != null) {
                outState.putInt(ARG_POSITION, mRecyclerView!!.getChildAdapterPosition(v))
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        requireActivity().onBackPressedDispatcher.addCallback(this, mGoUpBackPressedCallback)
        requireActivity().onBackPressedDispatcher.addCallback(this, mExitSelectionBackPressedCallback)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.activity_fm_actions, menu)
    }

    override fun onPrepareMenu(menu: Menu) {
        val pasteMenu = menu.findItem(R.id.action_paste)
        if (pasteMenu != null) {
            val fmTask = FmTasks.instance.peek()
            pasteMenu.isEnabled = mFolderShortInfo != null && fmTask != null && mFolderShortInfo!!.canWrite && fmTask.canPaste()
        }
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                mModel!!.reload()
                true
            }
            R.id.action_shortcut -> {
                mPathListAdapter!!.getCurrentUri()?.let { mModel!!.createShortcut(it) }
                true
            }
            R.id.action_list_options -> {
                val listOptions = FmListOptions()
                listOptions.setListOptionActions(mModel)
                listOptions.show(childFragmentManager, FmListOptions.TAG)
                true
            }
            R.id.action_paste -> {
                FmTasks.instance.dequeue()?.let { startBatchPaste(it) }
                true
            }
            R.id.action_new_window -> {
                val intent = Intent(mActivity, FmActivity::class.java).apply {
                    if (!mModel!!.getOptions()!!.isVfs) {
                        setDataAndType(mModel!!.currentUri, DocumentsContract.Document.MIME_TYPE_DIR)
                    }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                }
                startActivity(intent)
                true
            }
            R.id.action_add_to_favorites -> {
                mPathListAdapter!!.getCurrentUri()?.let { mModel!!.addToFavorite(Paths.get(it), mModel!!.getOptions()!!) }
                true
            }
            R.id.action_settings -> {
                startActivity(SettingsActivity.getSettingsIntent(requireContext(), "files_prefs"))
                true
            }
            else -> false
        }
    }

    override fun onActionSelected(actionItem: SpeedDialActionItem): Boolean {
        when (actionItem.id) {
            R.id.action_file -> NewFileDialogFragment.getInstance(object : NewFileDialogFragment.OnCreateNewFileInterface {
                override fun onCreate(prefix: String, extension: String?, template: String) = createNewFile(prefix, extension, template)
            }).show(childFragmentManager, NewFileDialogFragment.TAG)
            R.id.action_folder -> NewFolderDialogFragment.getInstance(object : NewFolderDialogFragment.OnCreateNewFolderInterface {
                override fun onCreate(name: String) = createNewFolder(name)
            }).show(childFragmentManager, NewFolderDialogFragment.TAG)
            R.id.action_symbolic_link -> {
                val uri = mPathListAdapter!!.getCurrentUri() ?: return false
                val path = Paths.get(uri)
                if (path.getFile() == null) {
                    UIUtils.displayLongToast(R.string.symbolic_link_not_supported)
                    return false
                }
                NewSymbolicLinkDialogFragment.getInstance(object : NewSymbolicLinkDialogFragment.OnCreateNewLinkInterface {
                    override fun onCreate(prefix: String, extension: String?, targetPath: String) = createNewSymbolicLink(prefix, extension, targetPath)
                }).show(childFragmentManager, NewSymbolicLinkDialogFragment.TAG)
            }
        }
        return false
    }

    override fun onSelectionModeEnabled() {
        mExitSelectionBackPressedCallback.isEnabled = true
    }

    override fun onSelectionModeDisabled() {
        mExitSelectionBackPressedCallback.isEnabled = false
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val selectedFiles = mModel!!.getSelectedItems()
        if (selectedFiles.isEmpty()) return false
        when (item.itemId) {
            R.id.action_share -> mModel!!.shareFiles(selectedFiles)
            R.id.action_rename -> RenameDialogFragment.getInstance(null) { prefix, extension ->
                startBatchRenaming(selectedFiles, prefix, extension)
            }.show(childFragmentManager, RenameDialogFragment.TAG)
            R.id.action_delete -> MaterialAlertDialogBuilder(mActivity!!)
                .setTitle(R.string.title_confirm_deletion)
                .setMessage(R.string.are_you_sure)
                .setPositiveButton(R.string.cancel, null)
                .setNegativeButton(R.string.confirm_file_deletion) { _, _ -> startBatchDeletion(selectedFiles) }
                .show()
            R.id.action_cut -> {
                FmTasks.instance.enqueue(FmTasks.FmTask(FmTasks.FmTask.TYPE_CUT, selectedFiles))
                UIUtils.displayShortToast(R.string.copied_to_clipboard)
            }
            R.id.action_copy -> {
                FmTasks.instance.enqueue(FmTasks.FmTask(FmTasks.FmTask.TYPE_COPY, selectedFiles))
                UIUtils.displayShortToast(R.string.copied_to_clipboard)
            }
            R.id.action_copy_path -> {
                val paths = selectedFiles.map { FmUtils.getDisplayablePath(it) }
                Utils.copyToClipboard(mActivity, "Paths", TextUtils.join("
", paths))
            }
        }
        return false
    }

    override fun onQueryTextSubmit(query: String?): Boolean = false
    override fun onQueryTextChange(newText: String?): Boolean = false

    override fun onRefresh() {
        mModel?.reload()
    }

    fun getRecyclerViewFirstChildPosition(): Int {
        val v = mRecyclerView?.getChildAt(0)
        return if (v != null) mRecyclerView!!.getChildAdapterPosition(v) else RecyclerView.NO_POSITION
    }

    private fun handleEmptyView(@DrawableRes icon: Int, title: CharSequence?, th: Throwable?) {
        if (!mEmptyView!!.isShown) mEmptyView!!.visibility = View.VISIBLE
        mEmptyViewIcon!!.setImageResource(icon)
        mEmptyViewTitle!!.text = title
        if (th == null) {
            mEmptyViewDetails!!.visibility = View.GONE
            return
        }
        val report = StringBuilder(th.toString() + "
")
        th.stackTrace.take(3).forEach { report.append("    at $it
") }
        var cause = th.cause
        while (cause != null) {
            report.append(" Caused by: $cause
")
            cause.stackTrace.take(3).forEach { report.append("   at $it
") }
            cause = cause.cause
        }
        mEmptyViewDetails!!.visibility = View.VISIBLE
        mEmptyViewDetails!!.text = report
    }

    private fun createNewFolder(name: String) {
        val uri = mPathListAdapter!!.getCurrentUri() ?: return
        val path = Paths.get(uri)
        val displayName = findNextBestDisplayName(path, name, null)
        try {
            val newDir = path.createNewDirectory(displayName)
            UIUtils.displayShortToast(R.string.done)
            mModel!!.reload(newDir.getName())
        } catch (e: IOException) {
            e.printStackTrace()
            UIUtils.displayShortToast(R.string.failed)
        }
    }

    private fun createNewFile(prefix: String, extension: String?, template: String) {
        val uri = mPathListAdapter!!.getCurrentUri() ?: return
        val path = Paths.get(uri)
        val displayName = findNextBestDisplayName(path, prefix, extension)
        try {
            val newFile = path.createNewFile(displayName, null)
            FileUtils.copyFromAsset(requireContext(), "blanks/$template", newFile)
            UIUtils.displayShortToast(R.string.done)
            mModel!!.reload(newFile.getName())
        } catch (e: IOException) {
            e.printStackTrace()
            UIUtils.displayShortToast(R.string.failed)
        }
    }

    private fun createNewSymbolicLink(prefix: String, extension: String?, targetPath: String) {
        val uri = mPathListAdapter!!.getCurrentUri() ?: return
        val basePath = Paths.get(uri)
        val displayName = findNextBestDisplayName(basePath, prefix, extension)
        val sourcePath = Paths.build(basePath, displayName)
        if (sourcePath != null && sourcePath.createNewSymbolicLink(targetPath)) {
            UIUtils.displayShortToast(R.string.done)
            mModel!!.reload(sourcePath.getName())
        } else {
            UIUtils.displayShortToast(R.string.failed)
        }
    }

    private fun startBatchDeletion(paths: List<Path>) {
        val deletionThread = AtomicReference<Future<*>>()
        val view = View.inflate(requireContext(), R.layout.dialog_progress, null)
        val progress: LinearProgressIndicator = view.findViewById(R.id.progress_linear)
        val label: TextView = view.findViewById(android.R.id.text1)
        val counter: TextView = view.findViewById(android.R.id.text2)
        counter.text = String.format(Locale.getDefault(), "%d/%d", 0, paths.size)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete)
            .setView(view)
            .setPositiveButton(R.string.action_stop_service) { _, _ -> deletionThread.get()?.cancel(true) }
            .setCancelable(false)
            .show()
        deletionThread.set(ThreadUtils.postOnBackgroundThread {
            val progressRef = WeakReference(progress)
            val labelRef = WeakReference(label)
            val counterRef = WeakReference(counter)
            val dialogRef = WeakReference(dialog)
            try {
                progressRef.get()?.let {
                    it.max = paths.size
                    it.progress = 0
                    it.isIndeterminate = false
                }
                paths.forEachIndexed { i, path ->
                    labelRef.get()?.let { l -> ThreadUtils.postOnMainThread { l.text = path.getName() } }
                    if (ThreadUtils.isInterrupted()) return@forEachIndexed
                    SystemClock.sleep(2000)
                    if (ThreadUtils.isInterrupted()) return@forEachIndexed
                    path.delete()
                    val idx = i + 1
                    ThreadUtils.postOnMainThread {
                        counterRef.get()?.text = String.format(Locale.getDefault(), "%d/%d", idx, paths.size)
                        progressRef.get()?.progress = idx
                    }
                }
            } finally {
                dialogRef.get()?.let { d ->
                    ThreadUtils.postOnMainThread {
                        d.dismiss()
                        UIUtils.displayShortToast(R.string.deleted_successfully)
                        mModel!!.reload()
                    }
                }
            }
        })
    }

    private fun startBatchRenaming(paths: List<Path>, prefix: String, extension: String?) {
        val renameThread = AtomicReference<Future<*>>()
        val view = View.inflate(requireContext(), R.layout.dialog_progress, null)
        val progress: LinearProgressIndicator = view.findViewById(R.id.progress_linear)
        val label: TextView = view.findViewById(android.R.id.text1)
        val counter: TextView = view.findViewById(android.R.id.text2)
        counter.text = String.format(Locale.getDefault(), "%d/%d", 0, paths.size)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.rename)
            .setView(view)
            .setPositiveButton(R.string.action_stop_service) { _, _ -> renameThread.get()?.cancel(true) }
            .setCancelable(false)
            .show()
        renameThread.set(ThreadUtils.postOnBackgroundThread {
            val progressRef = WeakReference(progress)
            val labelRef = WeakReference(label)
            val counterRef = WeakReference(counter)
            val dialogRef = WeakReference(dialog)
            try {
                progressRef.get()?.let {
                    it.max = paths.size
                    it.progress = 0
                    it.isIndeterminate = false
                }
                paths.forEachIndexed { i, path ->
                    labelRef.get()?.let { l -> ThreadUtils.postOnMainThread { l.text = path.getName() } }
                    if (ThreadUtils.isInterrupted()) return@forEachIndexed
                    SystemClock.sleep(2000)
                    if (ThreadUtils.isInterrupted()) return@forEachIndexed
                    val basePath = path.getParent()
                    if (basePath != null) {
                        val displayName = findNextBestDisplayName(basePath, prefix, extension, i + 1)
                        path.renameTo(displayName)
                    }
                    val idx = i + 1
                    ThreadUtils.postOnMainThread {
                        counterRef.get()?.text = String.format(Locale.getDefault(), "%d/%d", idx, paths.size)
                        progressRef.get()?.progress = idx
                    }
                }
            } finally {
                dialogRef.get()?.let { d ->
                    ThreadUtils.postOnMainThread {
                        d.dismiss()
                        UIUtils.displayShortToast(R.string.renamed_successfully)
                        mModel!!.reload()
                    }
                }
            }
        })
    }

    private fun startBatchPaste(task: FmTasks.FmTask) {
        val uri = mPathListAdapter!!.getCurrentUri() ?: return
        val pasteThread = AtomicReference<Future<*>>()
        val view = View.inflate(requireContext(), R.layout.dialog_progress, null)
        val progress: LinearProgressIndicator = view.findViewById(R.id.progress_linear)
        val label: TextView = view.findViewById(android.R.id.text1)
        val counter: TextView = view.findViewById(android.R.id.text2)
        counter.text = String.format(Locale.getDefault(), "%d/%d", 0, task.files.size)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.paste)
            .setView(view)
            .setPositiveButton(R.string.action_stop_service) { _, _ -> pasteThread.get()?.cancel(true) }
            .setCancelable(false)
            .show()
        pasteThread.set(ThreadUtils.postOnBackgroundThread {
            val progressRef = WeakReference(progress)
            val labelRef = WeakReference(label)
            val counterRef = WeakReference(counter)
            val dialogRef = WeakReference(dialog)
            val targetPath = Paths.get(uri)
            try {
                progressRef.get()?.let {
                    it.max = task.files.size
                    it.progress = 0
                    it.isIndeterminate = false
                }
                task.files.forEachIndexed { i, sourcePath ->
                    labelRef.get()?.let { l -> ThreadUtils.postOnMainThread { l.text = sourcePath.getName() } }
                    if (ThreadUtils.isInterrupted()) return@forEachIndexed
                    SystemClock.sleep(2000)
                    if (ThreadUtils.isInterrupted()) return@forEachIndexed
                    if (!copy(sourcePath, targetPath)) {
                        ThreadUtils.postOnMainThread {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle(R.string.error)
                                .setMessage(getString(R.string.failed_to_copy_specified_file, sourcePath.getName()))
                                .setPositiveButton(R.string.close, null)
                                .show()
                        }
                        return@postOnBackgroundThread
                    }
                    if (task.type == FmTasks.FmTask.TYPE_CUT) {
                        if (!sourcePath.delete()) {
                            ThreadUtils.postOnMainThread {
                                MaterialAlertDialogBuilder(requireContext())
                                    .setTitle(R.string.error)
                                    .setMessage(getString(R.string.failed_to_delete_specified_file_after_copying, sourcePath.getName()))
                                    .setPositiveButton(R.string.close, null)
                                    .show()
                            }
                            return@postOnBackgroundThread
                        }
                    }
                    val idx = i + 1
                    ThreadUtils.postOnMainThread {
                        counterRef.get()?.text = String.format(Locale.getDefault(), "%d/%d", idx, task.files.size)
                        progressRef.get()?.progress = idx
                    }
                }
                UIUtils.displayShortToast(if (task.type == FmTasks.FmTask.TYPE_CUT) R.string.moved_successfully else R.string.copied_successfully)
            } finally {
                dialogRef.get()?.let { d ->
                    ThreadUtils.postOnMainThread {
                        d.dismiss()
                        mModel!!.reload()
                    }
                }
            }
        })
    }

    @WorkerThread
    private fun copy(source: Path, dest: Path): Boolean {
        val name = source.getName()
        if (dest.hasFile(name)) {
            val waitForUser = CountDownLatch(1)
            val keepBoth = AtomicReference<Boolean?>(null)
            ThreadUtils.postOnMainThread {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.conflict_detected_while_copying)
                    .setMessage(getString(R.string.conflict_detected_while_copying_message, name))
                    .setCancelable(false)
                    .setOnDismissListener { waitForUser.countDown() }
                    .setPositiveButton(R.string.replace) { _, _ -> keepBoth.set(false) }
                    .setNegativeButton(R.string.action_stop_service) { _, _ -> keepBoth.set(null) }
                    .setNeutralButton(R.string.copy_keep_both_file) { _, _ -> keepBoth.set(true) }
                    .show()
            }
            try { waitForUser.await() } catch (ignore: InterruptedException) {}
            val decision = keepBoth.get() ?: return false
            return if (decision) {
                val prefix = if (!source.isDirectory()) Paths.trimPathExtension(name) else name
                val extension = if (!source.isDirectory()) Paths.getPathExtension(name) else null
                val newName = findNextBestDisplayName(dest, prefix, extension)
                try {
                    val newPath = if (source.isDirectory()) dest.createNewDirectory(newName) else dest.createNewFile(newName, null)
                    newPath.delete()
                    source.copyTo(newPath) != null
                } catch (e: IOException) { false }
            } else {
                source.copyTo(dest, true) != null
            }
        }
        return source.copyTo(dest, false) != null
    }

    private fun findNextBestDisplayName(basePath: Path, prefix: String, extension: String?, startIndex: Int = 1): String {
        val ext = if (TextUtils.isEmpty(extension)) "" else ".$extension"
        var displayName = prefix + ext
        var i = startIndex
        while (basePath.hasFile(displayName)) {
            displayName = String.format(Locale.ROOT, "%s (%d)%s", prefix, i, ext)
            i++
        }
        return displayName
    }

    private inner class BatchOpsHandler(multiSelectionView: MultiSelectionView) : MultiSelectionView.OnSelectionChangeListener {
        private val mShareMenu: MenuItem = multiSelectionView.menu.findItem(R.id.action_share)
        private val mRenameMenu: MenuItem = multiSelectionView.menu.findItem(R.id.action_rename)
        private val mDeleteMenu: MenuItem = multiSelectionView.menu.findItem(R.id.action_delete)
        private val mCutMenu: MenuItem = multiSelectionView.menu.findItem(R.id.action_cut)
        private val mCopyMenu: MenuItem = multiSelectionView.menu.findItem(R.id.action_copy)
        private val mCopyPathsMenu: MenuItem = multiSelectionView.menu.findItem(R.id.action_copy_path)

        override fun onSelectionChange(selectionCount: Int): Boolean {
            val nonZeroSelection = selectionCount > 0
            val canRead = mFolderShortInfo?.canRead ?: false
            val canWrite = mFolderShortInfo?.canWrite ?: false
            mShareMenu.isEnabled = nonZeroSelection && canRead
            mRenameMenu.isEnabled = nonZeroSelection && canWrite
            mDeleteMenu.isEnabled = nonZeroSelection && canWrite
            mCutMenu.isEnabled = nonZeroSelection && canWrite
            mCopyMenu.isEnabled = nonZeroSelection && canRead
            mCopyPathsMenu.isEnabled = nonZeroSelection
            return false
        }
    }

    companion object {
        val TAG: String = FmFragment::class.java.simpleName
        private const val ARG_URI = "uri"
        const val ARG_OPTIONS = "opt"
        const val ARG_POSITION = "pos"

        @JvmStatic
        fun getNewInstance(options: FmActivity.Options, position: Int?): FmFragment {
            val fragment = FmFragment()
            val args = Bundle()
            args.putParcelable(ARG_OPTIONS, options)
            if (position != null) args.putInt(ARG_POSITION, position)
            fragment.arguments = args
            return fragment
        }
    }
}
