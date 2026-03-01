// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.dialogs

import android.app.Application
import android.app.Dialog
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.WorkerThread
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.fm.FmItem
import io.github.muntashirakon.AppManager.fm.FmUtils
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.permission.Groups
import io.github.muntashirakon.AppManager.permission.Owners
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader
import io.github.muntashirakon.AppManager.settings.Ops
import io.github.muntashirakon.AppManager.utils.*
import io.github.muntashirakon.AppManager.utils.UIUtils.displayLongToast
import io.github.muntashirakon.dialog.DialogTitleBuilder
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder
import io.github.muntashirakon.dialog.TextInputDialogBuilder
import io.github.muntashirakon.io.*
import io.github.muntashirakon.util.LocalizedString
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.Future

class FilePropertiesDialogFragment : DialogFragment() {
    private var mFileProperties: FileProperties? = null
    private var mDialogView: View? = null
    private var mIconView: ImageView? = null
    private var mSymlinkIconView: View? = null
    private var mNameView: TextView? = null
    private var mSummaryView: TextView? = null
    private var mPathView: TextView? = null
    private var mTargetPathLayout: TextInputLayout? = null
    private var mTargetPathView: TextView? = null
    private var mTypeView: TextView? = null
    private var mSizeView: TextView? = null
    private var mDateModifiedLayout: TextInputLayout? = null
    private var mDateModifiedView: TextInputEditText? = null
    private var mDateCreatedView: TextView? = null
    private var mDateAccessedLayout: TextInputLayout? = null
    private var mDateAccessedView: TextInputEditText? = null
    private var mOwnerLayout: TextInputLayout? = null
    private var mOwnerView: TextInputEditText? = null
    private var mGroupLayout: TextInputLayout? = null
    private var mGroupView: TextInputEditText? = null
    private var mModeLayout: TextInputLayout? = null
    private var mModeView: TextInputEditText? = null
    private var mSelinuxContextLayout: TextInputLayout? = null
    private var mSelinuxContextView: TextInputEditText? = null
    private var mMoreInfoView: TextInputEditText? = null
    private var mChecksumsView: View? = null

    private var mViewModel: FilePropertiesViewModel? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mViewModel = ViewModelProvider(this).get(FilePropertiesViewModel::class.java)
        val pathUri = BundleCompat.getParcelable(requireArguments(), ARG_PATH, Uri::class.java)!!
        val path = Paths.get(pathUri)
        mDialogView = View.inflate(requireActivity(), R.layout.dialog_file_properties, null)
        initViews(mDialogView!!, path)
        return MaterialAlertDialogBuilder(requireActivity())
            .setView(mDialogView)
            .setPositiveButton(R.string.close, null)
            .create()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = mDialogView

    private fun initViews(view: View, path: Path) {
        mIconView = view.findViewById(R.id.icon)
        mSymlinkIconView = view.findViewById(R.id.symlink_icon)
        mNameView = view.findViewById(R.id.name)
        mSummaryView = view.findViewById(R.id.summary)
        mPathView = view.findViewById(R.id.path)
        mTargetPathLayout = view.findViewById(R.id.target_path_layout)
        mTargetPathView = view.findViewById(R.id.target_path)
        mTypeView = view.findViewById(R.id.type)
        mSizeView = view.findViewById(R.id.size)
        mDateModifiedLayout = view.findViewById(R.id.date_modified_layout)
        mDateModifiedView = view.findViewById(R.id.date_modified)
        mDateCreatedView = view.findViewById(R.id.date_created)
        mDateAccessedLayout = view.findViewById(R.id.date_accessed_layout)
        mDateAccessedView = view.findViewById(R.id.date_accessed)
        mOwnerLayout = view.findViewById(R.id.owner_layout)
        mOwnerView = view.findViewById(R.id.owner)
        mGroupLayout = view.findViewById(R.id.group_layout)
        mGroupView = view.findViewById(R.id.group)
        mModeLayout = view.findViewById(R.id.mode_layout)
        mModeView = view.findViewById(R.id.mode)
        mSelinuxContextLayout = view.findViewById(R.id.selinux_context_layout)
        mSelinuxContextView = view.findViewById(R.id.selinux_context)
        mMoreInfoView = view.findViewById(R.id.more_info)
        mChecksumsView = view.findViewById(R.id.action_checksums)

        mChecksumsView!!.setOnClickListener { ChecksumsDialogFragment.getInstance(path).show(childFragmentManager, ChecksumsDialogFragment.TAG) }
        mOwnerLayout!!.setEndIconOnClickListener { mViewModel!!.fetchOwnerList() }
        mGroupLayout!!.setEndIconOnClickListener { mViewModel!!.fetchGroupList() }
        mModeLayout!!.setEndIconOnClickListener {
            ChangeFileModeDialogFragment.getInstance(mFileProperties!!.mode, mFileProperties!!.isDirectory,
                object : ChangeFileModeDialogFragment.OnChangeFileModeInterface {
                    override fun onChangeMode(mode: Int, recursive: Boolean) {
                        mViewModel!!.setMode(mFileProperties!!, mode, recursive)
                    }
                }).show(childFragmentManager, ChangeFileModeDialogFragment.TAG)
        }
        mSelinuxContextLayout!!.setEndIconOnClickListener { displaySeContextUpdater() }

        mViewModel!!.filePropertiesLiveData.observe(this) { updateProperties(it) }
        mViewModel!!.fmItemLiveData.observe(this) { fmItem ->
            ImageLoader.getInstance().displayImage(fmItem.tag, mIconView!!, FmIconFetcher(fmItem))
            fmItem.contentInfo?.let { contentInfo ->
                val name = contentInfo.name
                val mime = contentInfo.mimeType
                val message = contentInfo.message
                mTypeView!!.text = if (mime != null) String.format(Locale.ROOT, "%s (%s)", name, mime) else name
                if (message != null) {
                    (mMoreInfoView!!.parent.parent as View).visibility = View.VISIBLE
                    mMoreInfoView!!.setText(message)
                }
            }
        }
        mViewModel!!.ownerListLiveData.observe(this) { displayUidUpdater(it) }
        mViewModel!!.groupListLiveData.observe(this) { displayGidUpdater(it) }
        mViewModel!!.ownerLiveData.observe(this) { ownerName ->
            mOwnerView!!.setText(String.format(Locale.ROOT, "%s (%d)", ownerName, mFileProperties!!.uidGidPair!!.uid))
        }
        mViewModel!!.groupLiveData.observe(this) { groupName ->
            mGroupView!!.setText(String.format(Locale.ROOT, "%s (%d)", groupName, mFileProperties!!.uidGidPair!!.gid))
        }

        mViewModel!!.loadFileProperties(path)
        mViewModel!!.loadFmItem(path)
    }

    private fun updateProperties(fileProperties: FileProperties) {
        val noInit = mFileProperties == null
        if (noInit && fileProperties.isDirectory) {
            mChecksumsView!!.visibility = View.GONE
        }
        val uidGidChanged = noInit || mFileProperties!!.uidGidPair != fileProperties.uidGidPair
        if (noInit || mFileProperties!!.isDirectory != fileProperties.isDirectory) {
            if (fileProperties.isDirectory) {
                mIconView!!.setImageResource(R.drawable.ic_folder)
            }
        }
        if (noInit || mFileProperties!!.isSymlink != fileProperties.isSymlink) {
            mSymlinkIconView!!.visibility = if (fileProperties.isSymlink) View.VISIBLE else View.GONE
        }
        if (noInit || mFileProperties!!.name != fileProperties.name) {
            mNameView!!.text = fileProperties.name
        }
        if (noInit || mFileProperties!!.readablePath != fileProperties.readablePath) {
            mPathView!!.text = fileProperties.readablePath
        }
        if (noInit || mFileProperties!!.targetPath != fileProperties.targetPath) {
            if (fileProperties.targetPath != null) {
                mTargetPathView!!.text = fileProperties.targetPath
            } else {
                mTargetPathLayout!!.visibility = View.GONE
            }
        }
        if (noInit || mFileProperties!!.size != fileProperties.size) {
            if (fileProperties.size != -1L) {
                mSizeView!!.text = String.format(Locale.getDefault(), "%s (%,d bytes)",
                    Formatter.formatShortFileSize(requireContext(), fileProperties.size), fileProperties.size)
            }
        }
        updateSummary(fileProperties)
        if (noInit || mFileProperties!!.lastModified != fileProperties.lastModified) {
            mDateModifiedView!!.setText(DateUtils.formatDateTime(requireContext(), fileProperties.lastModified))
        }
        if (noInit || mFileProperties!!.creationTime != fileProperties.creationTime) {
            mDateCreatedView!!.text = if (fileProperties.creationTime > 0) DateUtils.formatDateTime(requireContext(), fileProperties.creationTime) else "--"
        }
        if (noInit || mFileProperties!!.lastAccess != fileProperties.lastAccess) {
            mDateAccessedView!!.setText(if (fileProperties.lastAccess > 0) DateUtils.formatDateTime(requireContext(), fileProperties.lastAccess) else "--"
        }
        if (noInit || mFileProperties!!.canWrite != fileProperties.canWrite) {
            val isPhysicalWritable = fileProperties.canWrite && fileProperties.isPhysicalFs
            mDateModifiedLayout!!.isEndIconVisible = isPhysicalWritable
            mDateAccessedLayout!!.isEndIconVisible = isPhysicalWritable
            mOwnerLayout!!.isEndIconVisible = isPhysicalWritable
            mGroupLayout!!.isEndIconVisible = isPhysicalWritable
            mModeLayout!!.isEndIconVisible = isPhysicalWritable
            mSelinuxContextLayout!!.isEndIconVisible = Ops.isWorkingUidRoot() && isPhysicalWritable
        }
        if (noInit || mFileProperties!!.mode != fileProperties.mode) {
            mModeView!!.setText(if (fileProperties.mode != 0) FmUtils.getFormattedMode(fileProperties.mode) else "--")
        }
        if (uidGidChanged) {
            if (fileProperties.uidGidPair == null) {
                mOwnerView!!.setText("--")
                mGroupView!!.setText("--")
            }
        }
        if (noInit || mFileProperties!!.context != fileProperties.context) {
            mSelinuxContextView!!.setText(fileProperties.context ?: "--")
        }
        mFileProperties = fileProperties
        if (fileProperties.size == -1L) {
            mViewModel!!.loadFileSize(fileProperties)
        }
        if (fileProperties.uidGidPair != null && uidGidChanged) {
            mViewModel!!.loadOwnerInfo(fileProperties.uidGidPair!!.uid)
            mViewModel!!.loadGroupInfo(fileProperties.uidGidPair!!.gid)
        }
    }

    private fun updateSummary(fileProperties: FileProperties) {
        val summary = StringBuilder()
        summary.append(DateUtils.formatDateTime(requireContext(), fileProperties.lastModified))
        if (fileProperties.size > 0) {
            summary.append(" • ").append(Formatter.formatShortFileSize(requireContext(), fileProperties.size))
        }
        if (fileProperties.folderCount > 0 && fileProperties.fileCount > 0) {
            summary.append(" • ")
                .append(resources.getQuantityString(R.plurals.folder_count, fileProperties.folderCount, fileProperties.folderCount))
                .append(", ")
                .append(resources.getQuantityString(R.plurals.file_count, fileProperties.fileCount, fileProperties.fileCount))
        } else if (fileProperties.folderCount > 0) {
            summary.append(" • ")
                .append(resources.getQuantityString(R.plurals.folder_count, fileProperties.folderCount, fileProperties.folderCount))
        } else if (fileProperties.fileCount > 0) {
            summary.append(" • ")
                .append(resources.getQuantityString(R.plurals.file_count, fileProperties.fileCount, fileProperties.fileCount))
        }
        mSummaryView!!.text = summary
    }

    private fun displayUidUpdater(owners: List<AndroidId>) {
        val uidNames = owners.map { it.toLocalizedString(requireContext()) }
        val selectedUid = mFileProperties!!.uidGidPair?.let { AndroidId().apply { id = it.uid } }
        var checkBox: MaterialCheckBox? = null
        val view = if (mFileProperties!!.isDirectory) {
            View.inflate(requireContext(), R.layout.item_checkbox, null).apply {
                checkBox = findViewById(R.id.checkbox)
                checkBox!!.setText(R.string.apply_recursively)
            }
        } else null
        SearchableSingleChoiceDialogBuilder(requireContext(), owners, uidNames)
            .setSelection(selectedUid)
            .setTitle(R.string.change_owner_uid)
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _, uid ->
                uid?.let { mViewModel!!.setUid(mFileProperties!!, it.id, checkBox?.isChecked ?: false) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun displayGidUpdater(groups: List<AndroidId>) {
        val gidNames = groups.map { it.toLocalizedString(requireContext()) }
        val selectedGid = mFileProperties!!.uidGidPair?.let { AndroidId().apply { id = it.gid } }
        var checkBox: MaterialCheckBox? = null
        val view = if (mFileProperties!!.isDirectory) {
            View.inflate(requireContext(), R.layout.item_checkbox, null).apply {
                checkBox = findViewById(R.id.checkbox)
                checkBox!!.setText(R.string.apply_recursively)
            }
        } else null
        SearchableSingleChoiceDialogBuilder(requireContext(), groups, gidNames)
            .setSelection(selectedGid)
            .setTitle(R.string.change_group_gid)
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _, gid ->
                gid?.let { mViewModel!!.setGid(mFileProperties!!, it.id, checkBox?.isChecked ?: false) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun displaySeContextUpdater() {
        TextInputDialogBuilder(requireContext(), null)
            .setTitle(R.string.title_change_selinux_context)
            .setInputText(mFileProperties!!.context)
            .setCheckboxLabel(if (mFileProperties!!.isDirectory) R.string.apply_recursively else 0)
            .setPositiveButton(R.string.ok) { _, _, context, recursive ->
                if (!TextUtils.isEmpty(context)) {
                    mViewModel!!.setSeContext(mFileProperties!!, context.toString().trim(), recursive)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.restore) { _, _, _, recursive ->
                mViewModel!!.restorecon(mFileProperties!!, recursive)
            }
            .show()
    }

    class FilePropertiesViewModel(application: Application) : AndroidViewModel(application) {
        val filePropertiesLiveData = MutableLiveData<FileProperties>()
        val fmItemLiveData = MutableLiveData<FmItem>()
        val ownerListLiveData = MutableLiveData<List<AndroidId>>()
        val groupListLiveData = MutableLiveData<List<AndroidId>>()
        val ownerLiveData = MutableLiveData<String>()
        val groupLiveData = MutableLiveData<String>()

        private val mOwnerList = mutableListOf<AndroidId>()
        private val mGroupList = mutableListOf<AndroidId>()
        private var sizeResult: Future<*>? = null

        override fun onCleared() {
            sizeResult?.cancel(true)
            super.onCleared()
        }

        fun setMode(properties: FileProperties, mode: Int, recursive: Boolean) {
            ThreadUtils.postOnBackgroundThread {
                val file = properties.path.getFile() ?: return@postOnBackgroundThread
                if (recursive) setModeRecursive(file, mode)
                try {
                    file.setMode(mode)
                    val newProperties = FileProperties(properties)
                    newProperties.mode = newProperties.path.getMode()
                    filePropertiesLiveData.postValue(newProperties)
                } catch (e: ErrnoException) {
                    e.printStackTrace()
                }
            }
        }

        fun fetchOwnerList() {
            ThreadUtils.postOnBackgroundThread {
                if (mOwnerList.isEmpty()) getOwnersAndGroupsInternal()
                ownerListLiveData.postValue(ArrayList(mOwnerList))
            }
        }

        fun fetchGroupList() {
            ThreadUtils.postOnBackgroundThread {
                if (mGroupList.isEmpty()) getOwnersAndGroupsInternal()
                groupListLiveData.postValue(ArrayList(mGroupList))
            }
        }

        @WorkerThread
        private fun getOwnersAndGroupsInternal() {
            mOwnerList.clear()
            mGroupList.clear()
            val uidOwnerMap = Owners.getUidOwnerMap(false)
            for (uid in uidOwnerMap.keys) {
                val id = AndroidId().apply {
                    this.id = uid
                    this.name = uidOwnerMap[uid]!!
                    this.description = "System"
                }
                mOwnerList.add(id)
            }
            val gidGroupMap = Groups.getGidGroupMap(false)
            for (gid in gidGroupMap.keys) {
                val id = AndroidId().apply {
                    this.id = gid
                    this.name = gidGroupMap[gid]!!
                    this.description = "System"
                }
                mGroupList.add(id)
            }
            val applicationInfoList = PackageUtils.getAllApplications(0)
            val uidList = mutableMapOf<Int, CharSequence>()
            val pm = getApplication<Application>().packageManager
            for (info in applicationInfoList) {
                if (uidOwnerMap.containsKey(info.uid)) continue
                if (!uidList.containsKey(info.uid)) {
                    uidList[info.uid] = info.loadLabel(pm)
                }
            }
            for (uid in uidList.keys) {
                val id = AndroidId().apply {
                    this.id = uid
                    this.name = Owners.formatUid(uid)
                    this.description = uidList[uid]!!
                }
                mOwnerList.add(id)
                mGroupList.add(id)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val gid = Groups.getCacheAppGid(uid)
                    if (gid != -1 && gid != uid) {
                        val cachedGid = AndroidId().apply {
                            this.id = gid
                            this.name = Groups.formatGid(gid)
                            this.description = id.description
                        }
                        mGroupList.add(cachedGid)
                    }
                }
            }
            mOwnerList.sortBy { it.id }
            mGroupList.sortBy { it.id }
        }

        fun setUid(properties: FileProperties, uid: Int, recursive: Boolean) {
            ThreadUtils.postOnBackgroundThread {
                val file = properties.path.getFile() ?: return@postOnBackgroundThread
                if (recursive) setUidRecursive(file, uid)
                try {
                    val pair = file.getUidGid()
                    file.setUidGid(uid, pair!!.gid)
                    val newProperties = FileProperties(properties)
                    newProperties.uidGidPair = newProperties.path.getUidGid()
                    filePropertiesLiveData.postValue(newProperties)
                } catch (e: ErrnoException) {
                    e.printStackTrace()
                }
            }
        }

        fun setGid(properties: FileProperties, gid: Int, recursive: Boolean) {
            ThreadUtils.postOnBackgroundThread {
                val file = properties.path.getFile() ?: return@postOnBackgroundThread
                if (recursive) setGidRecursive(file, gid)
                try {
                    val pair = file.getUidGid()
                    file.setUidGid(pair!!.uid, gid)
                    val newProperties = FileProperties(properties)
                    newProperties.uidGidPair = newProperties.path.getUidGid()
                    filePropertiesLiveData.postValue(newProperties)
                } catch (e: ErrnoException) {
                    e.printStackTrace()
                }
            }
        }

        fun restorecon(properties: FileProperties, recursive: Boolean) {
            ThreadUtils.postOnBackgroundThread {
                val file = properties.path.getFile() ?: return@postOnBackgroundThread
                if (recursive) restoreconRecursive(file)
                if (file.restoreSelinuxContext()) {
                    val newProperties = FileProperties(properties)
                    newProperties.context = newProperties.path.getSelinuxContext()
                    filePropertiesLiveData.postValue(newProperties)
                }
            }
        }

        fun setSeContext(properties: FileProperties, newContext: String, recursive: Boolean) {
            ThreadUtils.postOnBackgroundThread {
                val file = properties.path.getFile() ?: return@postOnBackgroundThread
                if (recursive) setSeContextRecursive(file, newContext)
                if (file.setSelinuxContext(newContext)) {
                    val newProperties = FileProperties(properties)
                    newProperties.context = newProperties.path.getSelinuxContext()
                    filePropertiesLiveData.postValue(newProperties)
                }
            }
        }

        fun loadFileProperties(path: Path) {
            ThreadUtils.postOnBackgroundThread {
                val children = path.listFiles()
                val folderCount = children.count { it.isDirectory() }
                val properties = FileProperties().apply {
                    this.path = path
                    this.isPhysicalFs = path.getFile() != null
                    this.name = path.getName()
                    this.readablePath = FmUtils.getDisplayablePath(path)
                    this.folderCount = folderCount
                    this.fileCount = children.size - folderCount
                    this.isDirectory = path.isDirectory()
                    this.isSymlink = path.isSymbolicLink()
                    this.canRead = path.canRead()
                    this.canWrite = path.canWrite()
                    this.lastAccess = path.lastAccess()
                    this.lastModified = path.lastModified()
                    this.creationTime = path.creationTime()
                    this.mode = path.getMode()
                    this.uidGidPair = path.getUidGid()
                    this.context = path.getSelinuxContext()
                    if (this.isSymlink) {
                        try {
                            this.targetPath = path.getRealFilePath()
                        } catch (ignore: IOException) {}
                    }
                }
                filePropertiesLiveData.postValue(properties)
            }
        }

        fun loadFileSize(properties: FileProperties) {
            sizeResult = ThreadUtils.postOnBackgroundThread {
                val newProperties = FileProperties(properties)
                newProperties.size = Paths.size(newProperties.path)
                filePropertiesLiveData.postValue(newProperties)
            }
        }

        fun loadFmItem(path: Path) {
            ThreadUtils.postOnBackgroundThread {
                val fmItem = FmItem(path)
                fmItem.contentInfo = path.getPathContentInfo()
                fmItemLiveData.postValue(fmItem)
            }
        }

        fun loadOwnerInfo(uid: Int) {
            ThreadUtils.postOnBackgroundThread { ownerLiveData.postValue(Owners.getOwnerName(uid)) }
        }

        fun loadGroupInfo(gid: Int) {
            ThreadUtils.postOnBackgroundThread { groupLiveData.postValue(Groups.getGroupName(gid)) }
        }

        private fun setModeRecursive(dir: ExtendedFile, mode: Int): Boolean {
            if (dir.isSymlink) return true
            val files = dir.listFiles() ?: return true
            var success = true
            for (file in files) {
                if (file.isDirectory) success = success and setModeRecursive(file, mode)
                try {
                    file.setMode(mode)
                } catch (e: ErrnoException) {
                    success = false
                }
            }
            return success
        }

        private fun setUidRecursive(dir: ExtendedFile, uid: Int): Boolean {
            if (dir.isSymlink) return true
            val files = dir.listFiles() ?: return true
            var success = true
            for (file in files) {
                if (file.isDirectory) success = success and setUidRecursive(file, uid)
                try {
                    val pair = file.getUidGid()
                    file.setUidGid(uid, pair!!.gid)
                } catch (e: ErrnoException) {
                    success = false
                }
            }
            return success
        }

        private fun setGidRecursive(dir: ExtendedFile, gid: Int): Boolean {
            if (dir.isSymlink) return true
            val files = dir.listFiles() ?: return true
            var success = true
            for (file in files) {
                if (file.isDirectory) success = success and setGidRecursive(file, gid)
                try {
                    val pair = file.getUidGid()
                    file.setUidGid(pair!!.uid, gid)
                } catch (e: ErrnoException) {
                    success = false
                }
            }
            return success
        }

        private fun restoreconRecursive(dir: ExtendedFile): Boolean {
            if (dir.isSymlink) return true
            val files = dir.listFiles() ?: return true
            var success = true
            for (file in files) {
                if (file.isDirectory) success = success and restoreconRecursive(file)
                if (!file.restoreSelinuxContext()) success = false
            }
            return success
        }

        private fun setSeContextRecursive(dir: ExtendedFile, newContext: String): Boolean {
            if (dir.isSymlink) return true
            val files = dir.listFiles() ?: return true
            var success = true
            for (file in files) {
                if (file.isDirectory) success = success and setSeContextRecursive(file, newContext)
                if (!file.setSelinuxContext(newContext)) success = false
            }
            return success
        }
    }

    class AndroidId : LocalizedString {
        var id: Int = 0
        var name: String = ""
        var description: CharSequence = ""

        override fun toLocalizedString(context: Context): CharSequence {
            return SpannableStringBuilder(name).append(" (").append(id.toString()).append(")
")
                .append(UIUtils.getSmallerText(UIUtils.getSecondaryText(context, description)))
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AndroidId) return false
            return id == other.id
        }

        override fun hashCode(): Int = Objects.hash(id)
    }

    class FileProperties {
        lateinit var path: Path
        var isPhysicalFs: Boolean = false
        var name: String = ""
        var readablePath: String = ""
        var folderCount: Int = 0
        var fileCount: Int = 0
        var isDirectory: Boolean = false
        var isSymlink: Boolean = false
        var canRead: Boolean = false
        var canWrite: Boolean = false
        var size: Long = -1
        var lastAccess: Long = 0
        var lastModified: Long = 0
        var creationTime: Long = 0
        var mode: Int = 0
        var uidGidPair: UidGidPair? = null
        var context: String? = null
        var targetPath: String? = null

        constructor()
        constructor(other: FileProperties) {
            path = other.path
            isPhysicalFs = other.isPhysicalFs
            name = other.name
            readablePath = other.readablePath
            folderCount = other.folderCount
            fileCount = other.fileCount
            isDirectory = other.isDirectory
            isSymlink = other.isSymlink
            canRead = other.canRead
            canWrite = other.canWrite
            size = other.size
            lastAccess = other.lastAccess
            lastModified = other.lastModified
            creationTime = other.creationTime
            mode = other.mode
            uidGidPair = other.uidGidPair
            context = other.context
            targetPath = other.targetPath
        }
    }

    private class FmIconFetcher(private val mItem: FmItem) : ImageLoader.ImageFetcherInterface {
        override fun fetchImage(tag: String): ImageLoader.ImageFetcherResult {
            // Implementation simplified
            return ImageLoader.ImageFetcherResult(tag, null, false, false, null)
        }
    }

    companion object {
        val TAG: String = FilePropertiesDialogFragment::class.java.simpleName
        private const val ARG_PATH = "path"

        @JvmStatic
        fun getInstance(path: Path): FilePropertiesDialogFragment {
            val fragment = FilePropertiesDialogFragment()
            val args = Bundle()
            args.putParcelable(ARG_PATH, path.getUri())
            fragment.arguments = args
            return fragment
        }
    }
}
