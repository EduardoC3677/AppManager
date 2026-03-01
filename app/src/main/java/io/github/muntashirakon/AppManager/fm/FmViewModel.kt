// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import android.text.TextUtils
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.core.util.Pair
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.j256.simplemagic.ContentType
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.dex.DexUtils
import io.github.muntashirakon.AppManager.fm.icons.FmIconFetcher
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView
import io.github.muntashirakon.AppManager.misc.ListOptions
import io.github.muntashirakon.AppManager.self.filecache.FileCache
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.utils.*
import io.github.muntashirakon.io.IoUtils
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import io.github.muntashirakon.io.fs.VirtualFileSystem
import io.github.muntashirakon.lifecycle.SingleLiveEvent
import java.io.FileNotFoundException
import java.io.IOException
import java.util.*
import java.util.concurrent.Future

class FmViewModel(application: Application) : AndroidViewModel(application), ListOptions.ListOptionActions {
    private val mSizeLock = Any()
    private val mFmItemsLiveData = MutableLiveData<List<FmItem>>()
    private val mFmErrorLiveData = MutableLiveData<Throwable>()
    private val mFolderShortInfoLiveData = MutableLiveData<FolderShortInfo>()
    private val mUriLiveData = MutableLiveData<Uri>()
    private val mLastUriLiveData = MutableLiveData<Uri>()
    private val mDisplayPropertiesLiveData = MutableLiveData<Uri>()
    private val mShortcutCreatorLiveData = SingleLiveEvent<Pair<Path, Bitmap>>()
    private val mSharableItemsLiveData = SingleLiveEvent<SharableItems>()
    private val mFmItems = mutableListOf<FmItem>()
    private val mSelectedItems = Collections.synchronizedSet(LinkedHashSet<Path>())
    private val mPathScrollPositionMap = HashMap<Uri, Int>()
    private var mOptions: FmActivity.Options? = null
    var currentUri: Uri? = null
        private set
    private var mSortBy: Int = Prefs.FileManager.getSortOrder()
    private var mReverseSort: Boolean = Prefs.FileManager.isReverseSort()
    private var mSelectedOptions: Int = Prefs.FileManager.getOptions()
    private var mQueryString: String? = null
    private var mScrollToFilename: String? = null
    private var mFmFileLoaderResult: Future<*>? = null
    private var mFmFileSystemLoaderResult: Future<*>? = null
    private val mVfsIdSet = mutableSetOf<Int>()
    private val mFileCache = FileCache()

    override fun onCleared() {
        mFmFileLoaderResult?.cancel(true)
        mFmFileSystemLoaderResult?.cancel(true)
        for (vfsId in mVfsIdSet) {
            ExUtils.exceptionAsIgnored { VirtualFileSystem.unmount(vfsId) }
        }
        IoUtils.closeQuietly(mFileCache)
        super.onCleared()
    }

    override fun setSortBy(@FmListOptions.SortOrder sortBy: Int) {
        mSortBy = sortBy
        Prefs.FileManager.setSortOrder(sortBy)
        ThreadUtils.postOnBackgroundThread { filterAndSort() }
    }

    @FmListOptions.SortOrder
    override fun getSortBy(): Int = mSortBy

    override fun setReverseSort(reverseSort: Boolean) {
        mReverseSort = reverseSort
        Prefs.FileManager.setReverseSort(reverseSort)
        ThreadUtils.postOnBackgroundThread { filterAndSort() }
    }

    override fun isReverseSort(): Boolean = mReverseSort

    override fun isOptionSelected(@FmListOptions.Options option: Int): Boolean = (mSelectedOptions and option) != 0

    override fun onOptionSelected(@FmListOptions.Options option: Int, selected: Boolean) {
        if (selected) mSelectedOptions = mSelectedOptions or option
        else mSelectedOptions = mSelectedOptions and option.inv()
        Prefs.FileManager.setOptions(mSelectedOptions)
        ThreadUtils.postOnBackgroundThread { filterAndSort() }
    }

    fun setQueryString(queryString: String?) {
        mQueryString = queryString
        ThreadUtils.postOnBackgroundThread { filterAndSort() }
    }

    @MainThread
    fun setOptions(options: FmActivity.Options, defaultUri: Uri?) {
        mFmFileLoaderResult?.cancel(true)
        mFmFileSystemLoaderResult?.cancel(true)
        mOptions = options
        if (!options.isVfs) {
            loadFiles(defaultUri ?: options.uri, null)
            return
        }
        mFmFileSystemLoaderResult = ThreadUtils.postOnBackgroundThread {
            try {
                val fs = mountVfs()
                val vfsId = fs.fsId
                mVfsIdSet.add(vfsId)
                val newUri = if (defaultUri != null) {
                    defaultUri.buildUpon().authority(vfsId.toString()).build()
                } else {
                    fs.rootPath.getUri()
                }
                ThreadUtils.postOnMainThread { loadFiles(newUri, null) }
            } catch (e: IOException) {
                handleError(e, mOptions!!.uri)
            }
        }
    }

    fun getOptions(): FmActivity.Options? = mOptions

    fun setScrollPosition(uri: Uri, currentScrollPosition: Int) {
        Log.d(TAG, "Store: Scroll position = $currentScrollPosition, uri = $uri")
        mPathScrollPositionMap[uri] = currentScrollPosition
    }

    fun getCurrentScrollPosition(): Int {
        val scrollPosition = mPathScrollPositionMap[currentUri]
        Log.d(TAG, "Load: Scroll position = $scrollPosition, uri = $currentUri")
        return scrollPosition ?: 0
    }

    fun getSelectedItems(): List<Path> = ArrayList(mSelectedItems)

    fun getLastSelectedItem(): Path? {
        var lastItem: Path? = null
        val it = mSelectedItems.iterator()
        while (it.hasNext()) {
            lastItem = it.next()
        }
        return lastItem
    }

    fun getSelectedItemCount(): Int = mSelectedItems.size

    fun setSelectedItem(path: Path, select: Boolean) {
        if (select) mSelectedItems.add(path)
        else mSelectedItems.remove(path)
    }

    fun isSelected(path: Path): Boolean = mSelectedItems.contains(path)

    fun clearSelections() {
        mSelectedItems.clear()
    }

    @MainThread
    fun reload() {
        reload(null)
    }

    @MainThread
    fun reload(scrollToFilename: String?) {
        if (mOptions != null && currentUri != null) {
            loadFiles(currentUri!!, scrollToFilename)
        }
    }

    @MainThread
    fun loadFiles(uri: Uri) {
        if (currentUri != null) {
            if (currentUri!!.scheme != uri.scheme || currentUri!!.authority != uri.authority) {
                updateOptions(uri)
                return
            }
        }
        loadFiles(uri, null)
    }

    @MainThread
    private fun updateOptions(refUri: Uri) {
        val options = FmActivity.Options(refUri)
        setOptions(options, null)
    }

    @SuppressLint("WrongThread")
    @MainThread
    private fun loadFiles(uri: Uri, scrollToFilename: String?) {
        mFmFileLoaderResult?.cancel(true)
        mScrollToFilename = scrollToFilename
        mLastUriLiveData.value = currentUri
        currentUri = uri
        var currentPath: Path = try {
            Paths.getStrict(uri)
        } catch (e: IOException) {
            handleError(e, uri)
            return
        }
        while (currentPath.isSymbolicLink()) {
            try {
                val realPath = currentPath.getRealPath()
                if (realPath == null || realPath == currentPath) break
                currentPath = realPath
                currentUri = realPath.getUri()
            } catch (ignore: IOException) {}
        }
        val path = currentPath
        mFmFileLoaderResult = ThreadUtils.postOnBackgroundThread {
            if (!path.isDirectory()) {
                val e = if (path.exists()) {
                    FileNotFoundException(getApplication<Application>().getString(R.string.path_not_a_folder, path.getName()))
                } else {
                    IOException(getApplication<Application>().getString(R.string.path_does_not_exist, path.getName()))
                }
                handleError(e, currentUri!!)
                return@postOnBackgroundThread
            }
            mUriLiveData.postValue(currentUri)
            var s: Long
            var e: Long
            val isSaf = ContentResolver.SCHEME_CONTENT == currentUri!!.scheme
            val folderShortInfo = FolderShortInfo()
            var folderCount = 0
            synchronized(mFmItems) {
                mFmItems.clear()
                if (isSaf) {
                    s = System.currentTimeMillis()
                    val resolver = getApplication<Application>().contentResolver
                    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                        currentUri,
                        DocumentsContract.getDocumentId(currentUri)
                    )
                    resolver.query(childrenUri, null, null, null, null).use { c ->
                        if (c != null) {
                            val columns = c.columnNames
                            while (c.moveToNext()) {
                                var documentId: String? = null
                                for (i in columns.indices) {
                                    if (DocumentsContract.Document.COLUMN_DOCUMENT_ID == columns[i]) {
                                        documentId = c.getString(i)
                                    }
                                }
                                if (documentId == null) continue
                                val documentUri = DocumentsContract.buildDocumentUriUsingTree(currentUri, documentId)
                                val child = Paths.getTreeDocument(path, documentUri)
                                val attributes = Paths.getAttributesFromSafTreeCursor(documentUri, c)
                                val fmItem = FmItem(child, attributes)
                                mFmItems.add(fmItem)
                                if (fmItem.isDirectory) ++folderCount
                                if (ThreadUtils.isInterrupted()) return@postOnBackgroundThread
                            }
                        }
                    }
                    e = System.currentTimeMillis()
                    Log.d(TAG, "Time to fetch files via SAF: ${e - s} ms")
                } else {
                    s = System.currentTimeMillis()
                    val children = path.listFiles()
                    e = System.currentTimeMillis()
                    Log.d(TAG, "Time to list files: ${e - s} ms")
                    s = System.currentTimeMillis()
                    for (child in children) {
                        val fmItem = FmItem(child)
                        mFmItems.add(fmItem)
                        if (fmItem.isDirectory) ++folderCount
                        if (ThreadUtils.isInterrupted()) return@postOnBackgroundThread
                    }
                    e = System.currentTimeMillis()
                    Log.d(TAG, "Time to process file list: ${e - s} ms")
                }
            }
            folderShortInfo.folderCount = folderCount
            folderShortInfo.fileCount = mFmItems.size - folderCount
            folderShortInfo.canRead = path.canRead()
            folderShortInfo.canWrite = path.canWrite()
            if (ThreadUtils.isInterrupted()) return@postOnBackgroundThread
            mFolderShortInfoLiveData.postValue(folderShortInfo)
            s = System.currentTimeMillis()
            filterAndSort()
            e = System.currentTimeMillis()
            Log.d(TAG, "Time to sort files: ${e - s} ms")
            synchronized(mSizeLock) {
                folderShortInfo.size = Paths.size(path)
                if (ThreadUtils.isInterrupted()) return@postOnBackgroundThread
                mFolderShortInfoLiveData.postValue(folderShortInfo)
            }
        }
    }

    fun addToFavorite(path: Path, options: FmActivity.Options) {
        ThreadUtils.postOnBackgroundThread { FmFavoritesManager.addToFavorite(path, options) }
    }

    fun createShortcut(fmItem: FmItem) {
        ThreadUtils.postOnBackgroundThread {
            var bitmap = ImageLoader.getInstance().getCachedImage(fmItem.tag)
            if (bitmap == null) {
                val result = FmIconFetcher(fmItem).fetchImage(fmItem.tag)
                bitmap = result.bitmap ?: result.defaultImage.image
            }
            mShortcutCreatorLiveData.postValue(Pair(fmItem.path, bitmap))
        }
    }

    fun shareFiles(pathList: List<Path>) {
        ThreadUtils.postOnBackgroundThread { mSharableItemsLiveData.postValue(SharableItems(pathList)) }
    }

    fun createShortcut(uri: Uri) {
        createShortcut(FmItem(Paths.get(uri)))
    }

    fun getFmItemsLiveData(): LiveData<List<FmItem>> = mFmItemsLiveData
    fun getFmErrorLiveData(): LiveData<Throwable> = mFmErrorLiveData
    fun getUriLiveData(): LiveData<Uri> = mUriLiveData
    fun getFolderShortInfoLiveData(): LiveData<FolderShortInfo> = mFolderShortInfoLiveData
    fun getLastUriLiveData(): LiveData<Uri> = mLastUriLiveData
    fun getDisplayPropertiesLiveData(): MutableLiveData<Uri> = mDisplayPropertiesLiveData
    fun getShortcutCreatorLiveData(): LiveData<Pair<Path, Bitmap>> = mShortcutCreatorLiveData
    fun getSharableItemsLiveData(): LiveData<SharableItems> = mSharableItemsLiveData

    private fun handleError(th: Throwable, currentUri: Uri) {
        val folderShortInfo = FolderShortInfo()
        if (ThreadUtils.isMainThread()) {
            mUriLiveData.value = currentUri
            mFolderShortInfoLiveData.value = folderShortInfo
            mFmErrorLiveData.value = th
        } else {
            mUriLiveData.postValue(currentUri)
            mFolderShortInfoLiveData.postValue(folderShortInfo)
            mFmErrorLiveData.postValue(th)
        }
    }

    private fun filterAndSort() {
        val displayDotFiles = (mSelectedOptions and FmListOptions.OPTIONS_DISPLAY_DOT_FILES) != 0
        val foldersOnTop = (mSelectedOptions and FmListOptions.OPTIONS_FOLDERS_FIRST) != 0

        val filteredList: MutableList<FmItem> = synchronized(mFmItems) {
            if (!TextUtils.isEmpty(mQueryString)) {
                AdvancedSearchView.matches(mQueryString, mFmItems, { it.name }, AdvancedSearchView.SEARCH_TYPE_CONTAINS)
            } else {
                ArrayList(mFmItems)
            }
        }
        if (ThreadUtils.isInterrupted()) return
        if (!displayDotFiles) {
            val iterator = filteredList.listIterator()
            while (iterator.hasNext()) {
                if (iterator.next().name.startsWith(".")) iterator.remove()
            }
        }
        if (ThreadUtils.isInterrupted()) return
        // Sort by name first
        filteredList.sortWith { o1, o2 -> AlphanumComparator.compareStringIgnoreCase(o1.name, o2.name) }
        if (mSortBy == FmListOptions.SORT_BY_NAME) {
            if (mReverseSort) filteredList.reverse()
        } else {
            val inverse = if (mReverseSort) -1 else 1
            filteredList.sortWith { o1, o2 ->
                val p1 = o1.path
                val p2 = o2.path
                when (mSortBy) {
                    FmListOptions.SORT_BY_LAST_MODIFIED -> -o1.lastModified.compareTo(o2.lastModified) * inverse
                    FmListOptions.SORT_BY_SIZE -> -o1.size.compareTo(o2.size) * inverse
                    FmListOptions.SORT_BY_TYPE -> p1.getType().compareTo(p2.getType(), ignoreCase = true) * inverse
                    else -> 0
                }
            }
        }
        if (foldersOnTop) {
            filteredList.sortWith { o1, o2 -> -o1.isDirectory.compareTo(o2.isDirectory) }
        }
        if (ThreadUtils.isInterrupted()) return
        if (mScrollToFilename != null) {
            for (i in filteredList.indices) {
                if (mScrollToFilename == filteredList[i].name) {
                    setScrollPosition(currentUri!!, i)
                    break
                }
            }
            mScrollToFilename = null
        }
        mFmItemsLiveData.postValue(filteredList)
    }

    @WorkerThread
    @Throws(IOException::class)
    private fun mountVfs(): VirtualFileSystem {
        if (!mOptions!!.isVfs) throw IOException("VFS expected, found regular FS.")
        var fs = VirtualFileSystem.getFileSystem(mOptions!!.uri)
        if (fs == null) {
            val filePath = Paths.getStrict(mOptions!!.uri)
            val cachedPath = Paths.get(mFileCache.getCachedFile(filePath))
            val type = cachedPath.getType()
            val vfsId = when {
                ContentType.APK.mimeType == type -> VirtualFileSystem.mount(filePath.getUri(), cachedPath, ContentType.APK.mimeType)
                FileUtils.isZip(cachedPath) -> VirtualFileSystem.mount(filePath.getUri(), cachedPath, ContentType.ZIP.mimeType)
                DexUtils.isDex(cachedPath) -> VirtualFileSystem.mount(filePath.getUri(), cachedPath, ContentType2.DEX.mimeType)
                else -> VirtualFileSystem.mount(filePath.getUri(), cachedPath, cachedPath.getType())
            }
            fs = VirtualFileSystem.getFileSystem(vfsId) ?: throw IOException("Could not mount ${mOptions!!.uri}")
        }
        return fs
    }

    companion object {
        val TAG: String = FmViewModel::class.java.simpleName
    }
}
