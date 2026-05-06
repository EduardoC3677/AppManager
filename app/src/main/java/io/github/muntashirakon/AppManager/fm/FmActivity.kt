// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm

import android.app.Activity
import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Parcel
import android.os.Parcelable
import android.provider.DocumentsContract
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.PopupMenu
import androidx.collection.ArrayMap
import androidx.core.os.BundleCompat
import androidx.core.os.ParcelCompat
import androidx.core.util.Pair
import androidx.documentfile.provider.DocumentFileUtils
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.db.entity.FmFavorite
import io.github.muntashirakon.AppManager.fm.dialogs.FilePropertiesDialogFragment
import io.github.muntashirakon.AppManager.fm.dialogs.RenameDialogFragment
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.utils.*
import io.github.muntashirakon.io.Paths
import io.github.muntashirakon.util.AdapterUtils
import java.io.File
import java.util.*

class FmActivity : BaseActivity() {
    class Options : Parcelable {
        val uri: Uri
        val options: Int
        var initUriForVfs: Uri? = null

        constructor(uri: Uri) : this(uri, false, false, false)

        protected constructor(uri: Uri, options: Int) {
            this.uri = uri
            this.options = options
        }

        constructor(uri: Uri, isVfs: Boolean, readOnly: Boolean, mountDexFiles: Boolean) {
            this.uri = uri
            var opts = 0
            if (isVfs) opts = opts or OPTION_VFS
            if (readOnly) opts = opts or OPTION_RO
            if (mountDexFiles) opts = opts or OPTION_MOUNT_DEX
            this.options = opts
        }

        val isVfs: Boolean
            get() = (options and OPTION_VFS) != 0

        val isMountDex: Boolean
            get() = (options and OPTION_MOUNT_DEX) != 0

        protected constructor(`in`: Parcel) {
            uri = ParcelCompat.readParcelable(`in`, Uri::class.java.classLoader, Uri::class.java)!!
            options = `in`.readInt()
            initUriForVfs = ParcelCompat.readParcelable(`in`, Uri::class.java.classLoader, Uri::class.java)
        }

        override fun describeContents(): Int = 0

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeParcelable(uri, flags)
            dest.writeInt(options)
            dest.writeParcelable(initUriForVfs, flags)
        }

        companion object {
            const val OPTION_VFS = 1 shl 0
            const val OPTION_RO = 1 shl 1 // read-only
            const val OPTION_MOUNT_DEX = 1 shl 2

            @JvmField
            val CREATOR: Parcelable.Creator<Options> = object : Parcelable.Creator<Options> {
                override fun createFromParcel(`in`: Parcel): Options = Options(`in`)
                override fun newArray(size: Int): Array<Options?> = arrayOfNulls(size)
            }
        }
    }

    private var mDrawerLayout: DrawerLayout? = null
    private var mDrawerRecyclerView: RecyclerView? = null
    private var mDrawerAdapter: DrawerRecyclerViewAdapter? = null
    private var mViewModel: FmDrawerViewModel? = null
    private val mAddDocumentProvider: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult
            val treeUri = data.data ?: return@registerForActivityResult
            val takeFlags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            contentResolver.takePersistableUriPermission(treeUri, takeFlags)
        } finally {
            // Display backup volumes again
            mViewModel!!.loadDrawerItems()
        }
    }
    private val mStoragePermission = StoragePermission.init(this)

    override fun onAuthenticated(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_fm)
        setSupportActionBar(findViewById(R.id.toolbar))
        findViewById<View>(R.id.progress_linear).visibility = View.GONE
        mViewModel = ViewModelProvider(this).get(FmDrawerViewModel::class.java)
        mDrawerLayout = findViewById(R.id.drawer_layout)
        mDrawerRecyclerView = findViewById(R.id.recycler_view)
        mDrawerRecyclerView!!.layoutManager = LinearLayoutManager(this)
        mDrawerAdapter = DrawerRecyclerViewAdapter(this)
        mDrawerRecyclerView!!.adapter = mDrawerAdapter
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_menu)
        mViewModel!!.drawerItemsLiveData.observe(this) { mDrawerAdapter!!.setAdapterItems(it) }
        FmFavoritesManager.getFavoriteAddedLiveData().observe(this) { mViewModel!!.loadDrawerItems() }
        mViewModel!!.loadDrawerItems()
        var uri = intent.data
        if (uri != null && uri.scheme == null) {
            uri = if (uri.path != null && uri.authority == null) {
                uri.buildUpon().scheme(ContentResolver.SCHEME_FILE).build()
            } else null
        }
        uri = FmUtils.sanitizeContentInput(uri)
        if (savedInstanceState == null) {
            var options = intent.extras?.let { BundleCompat.getParcelable(it, EXTRA_OPTIONS, Options::class.java) }
            var position: Int? = null
            if (options == null) {
                if (uri != null) {
                    options = Options(uri)
                } else if (Prefs.FileManager.isRememberLastOpenedPath()) {
                    val pair = Prefs.FileManager.getLastOpenedPath()
                    if (pair != null) {
                        options = pair.first
                        if (options!!.isVfs) uri = pair.second.first
                        position = pair.second.second
                    }
                }
                if (options == null) options = Options(Prefs.FileManager.getHome())
            }
            val uncheckedUri = options!!.uri
            val checkedUri = ExUtils.exceptionAsNull { if (Paths.getStrict(uncheckedUri).exists()) uncheckedUri else null }
            if (checkedUri == null) {
                options = Options(Uri.fromFile(Environment.getExternalStorageDirectory()))
            }
            if (options!!.isVfs) options!!.initUriForVfs = uri
            loadFragment(options!!, position)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val uri = intent.data
        val options = intent.extras?.let { BundleCompat.getParcelable(it, EXTRA_OPTIONS, Options::class.java) }
        if (options != null) {
            val intent2 = Intent(this, FmActivity::class.java).apply {
                if (uri != null) setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
                putExtra(EXTRA_OPTIONS, options)
                addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
            startActivity(intent2)
            return
        }
        if (uri != null) {
            val intent2 = Intent(this, FmActivity::class.java).apply {
                setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
                addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
            startActivity(intent2)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            mDrawerLayout!!.open()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    fun loadFragment(options: Options, position: Int?) {
        if (ContentResolver.SCHEME_FILE == options.uri.scheme) {
            mStoragePermission.request { doLoadFragment(options, position) }
        } else doLoadFragment(options, position)
    }

    private fun doLoadFragment(options: Options, position: Int?) {
        val fragment = FmFragment.getNewInstance(options, position)
        supportFragmentManager.beginTransaction().replace(R.id.main_layout, fragment, FmFragment.TAG).commit()
    }

    class FmDrawerViewModel(application: Application) : AndroidViewModel(application) {
        val drawerItemsLiveData = MutableLiveData<List<FmDrawerItem>>()

        fun removeFavorite(id: Long) {
            ThreadUtils.postOnBackgroundThread { FmFavoritesManager.removeFromFavorite(id) }
        }

        fun renameFavorite(id: Long, newName: String) {
            ThreadUtils.postOnBackgroundThread { FmFavoritesManager.renameFavorite(id, newName) }
        }

        fun releaseUri(uri: Uri) {
            try {
                getApplication<Application>().contentResolver.releasePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                loadDrawerItems()
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }

        fun loadDrawerItems() {
            ThreadUtils.postOnBackgroundThread {
                val drawerItems = mutableListOf<FmDrawerItem>()
                val context = getApplication<Application>()
                drawerItems.add(FmDrawerItem(-1, context.getString(R.string.favorites), null, FmDrawerItem.ITEM_TYPE_LABEL))
                val fmFavorites = FmFavoritesManager.getAllFavorites()
                for (fmFavorite in fmFavorites) {
                    val options = Options(Uri.parse(fmFavorite.uri), fmFavorite.options)
                    options.initUriForVfs = fmFavorite.initUri?.let { Uri.parse(it) }
                    val drawerItem = FmDrawerItem(fmFavorite.id, fmFavorite.name, options, FmDrawerItem.ITEM_TYPE_FAVORITE)
                    drawerItem.iconRes = getIconResFromName(fmFavorite.name)
                    drawerItems.add(drawerItem)
                }
                drawerItems.add(FmDrawerItem(-2, context.getString(R.string.storage), null, FmDrawerItem.ITEM_TYPE_LABEL))
                val storageLocations = StorageUtils.getAllStorageLocations(getApplication())
                for (i in 0 until storageLocations.size) {
                    val uri = storageLocations.valueAt(i)
                    val options = Options(uri)
                    val pm = getApplication<Application>().packageManager
                    val resolveInfo = DocumentFileUtils.getUriSource(getApplication(), uri)
                    val name = resolveInfo?.loadLabel(pm)?.toString() ?: storageLocations.keyAt(i)
                    val icon = resolveInfo?.loadIcon(pm)
                    val drawerItem = FmDrawerItem(-4, name, options, FmDrawerItem.ITEM_TYPE_LOCATION)
                    drawerItem.iconRes = R.drawable.ic_content_save
                    drawerItem.icon = icon
                    drawerItems.add(drawerItem)
                }
                drawerItemsLiveData.postValue(drawerItems)
            }
        }

        private fun getIconResFromName(filename: String): Int {
            return when (filename) {
                "Documents" -> R.drawable.ic_file_document
                "Download", "Downloads" -> R.drawable.ic_get_app
                "Pictures", "DCIM" -> R.drawable.ic_image
                "Movies", "Music", "Podcasts", "Recordings", "Ringtones" -> R.drawable.ic_audio_file
                else -> R.drawable.ic_folder
            }
        }
    }

    class DrawerRecyclerViewAdapter(private val mFmActivity: FmActivity) : RecyclerView.Adapter<DrawerRecyclerViewAdapter.ViewHolder>() {
        private val mAdapterItems = mutableListOf<FmDrawerItem>()

        fun setAdapterItems(adapterItems: List<FmDrawerItem>) {
            AdapterUtils.notifyDataSetChanged(this, mAdapterItems, adapterItems)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val layoutId = if (viewType == FmDrawerItem.ITEM_TYPE_LABEL) R.layout.item_title_action else R.layout.item_fm_drawer
            val v = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = mAdapterItems[position]
            holder.labelView.text = item.name
            if (item.type == FmDrawerItem.ITEM_TYPE_LABEL) {
                setupLabelView(holder, item)
            } else {
                setupItemView(holder, item)
            }
        }

        private fun setupLabelView(holder: ViewHolder, item: FmDrawerItem) {
            val actionView = holder.actionView ?: return
            when (item.id) {
                -1L -> actionView.visibility = View.GONE
                -2L -> {
                    actionView.visibility = View.VISIBLE
                    actionView.setIconResource(R.drawable.ic_add)
                    actionView.contentDescription = holder.itemView.context.getString(R.string.add)
                    actionView.setOnClickListener {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).putExtra("android.provider.extra.SHOW_ADVANCED", true)
                        mFmActivity.mAddDocumentProvider.launch(intent)
                    }
                }
                else -> actionView.visibility = View.GONE
            }
        }

        private fun setupItemView(holder: ViewHolder, item: FmDrawerItem) {
            val options = item.options!!
            holder.iconView?.let {
                if (item.icon != null) it.setImageDrawable(item.icon) else it.setImageResource(item.iconRes)
            }
            holder.itemView.setOnClickListener {
                mFmActivity.mDrawerLayout!!.close()
                mFmActivity.loadFragment(options, null)
            }
            holder.itemView.setOnLongClickListener { v ->
                val context = v.context
                val popupMenu = PopupMenu(context, v)
                val menu = popupMenu.menu
                menu.add(R.string.copy_this_path).setOnMenuItemClickListener {
                    val uri = options.initUriForVfs ?: options.uri
                    Utils.copyToClipboard(context, "Path", FmUtils.getDisplayablePath(uri))
                    true
                }
                val removable = item.type != FmDrawerItem.ITEM_TYPE_LOCATION || ContentResolver.SCHEME_CONTENT == options.uri.scheme
                if (removable) {
                    menu.add(R.string.item_remove).setOnMenuItemClickListener {
                        MaterialAlertDialogBuilder(mFmActivity)
                            .setTitle(context.getString(R.string.remove_filename, item.name))
                            .setMessage(R.string.are_you_sure)
                            .setNegativeButton(R.string.no, null)
                            .setPositiveButton(R.string.yes) { _, _ ->
                                if (item.type == FmDrawerItem.ITEM_TYPE_LOCATION) mFmActivity.mViewModel!!.releaseUri(options.uri)
                                else if (item.type == FmDrawerItem.ITEM_TYPE_FAVORITE) mFmActivity.mViewModel!!.removeFavorite(item.id)
                            }.show()
                        true
                    }
                }
                if (item.type == FmDrawerItem.ITEM_TYPE_FAVORITE) {
                    menu.add(R.string.item_edit).setOnMenuItemClickListener {
                        RenameDialogFragment.getInstance(item.name) { prefix, extension ->
                            val displayName = if (!TextUtils.isEmpty(extension)) "$prefix.$extension" else prefix
                            mFmActivity.mViewModel!!.renameFavorite(item.id, displayName)
                        }.show(mFmActivity.supportFragmentManager, RenameDialogFragment.TAG)
                        true
                    }
                }
                if (!options.isVfs) {
                    menu.add(R.string.file_properties).setOnMenuItemClickListener {
                        FilePropertiesDialogFragment.getInstance(Paths.get(options.uri)).show(mFmActivity.supportFragmentManager, FilePropertiesDialogFragment.TAG)
                        true
                    }
                }
                popupMenu.show()
                true
            }
        }

        override fun getItemCount(): Int = mAdapterItems.size
        override fun getItemViewType(position: Int): Int = mAdapterItems[position].type

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val iconView: AppCompatImageView? = itemView.findViewById(R.id.item_icon)
            val labelView: AppCompatTextView = itemView.findViewById(R.id.item_title)
            val actionView: MaterialButton? = itemView.findViewById(R.id.item_action)
        }
    }

    companion object {
        const val LAUNCHER_ALIAS = "io.github.muntashirakon.AppManager.fm.FilesActivity"\nconst val EXTRA_OPTIONS = "opt"
    }
}
