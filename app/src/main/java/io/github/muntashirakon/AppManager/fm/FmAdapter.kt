// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm

import android.text.TextUtils
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.MainThread
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.fm.dialogs.OpenWithDialogFragment
import io.github.muntashirakon.AppManager.fm.dialogs.RenameDialogFragment
import io.github.muntashirakon.AppManager.fm.icons.FmIconFetcher
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader
import io.github.muntashirakon.AppManager.utils.DateUtils
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.AppManager.utils.Utils
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.util.AccessibilityUtils
import io.github.muntashirakon.util.AdapterUtils
import io.github.muntashirakon.widget.MultiSelectionView
import java.lang.ref.WeakReference
import java.util.*

internal class FmAdapter(private val mViewModel: FmViewModel, private val mFmActivity: FmActivity) :
    MultiSelectionView.Adapter<FmAdapter.ViewHolder>() {

    private val mAdapterList = Collections.synchronizedList(mutableListOf<FmItem>())

    fun setFmList(list: List<FmItem>) {
        AdapterUtils.notifyDataSetChanged(this, mAdapterList, list)
        notifySelectionChange()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_fm, parent, false)
        val actionView = LayoutInflater.from(parent.context).inflate(R.layout.item_right_standalone_action, parent, false)
        val layout: LinearLayoutCompat = view.findViewById(android.R.id.widget_frame)
        layout.addView(actionView)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = mAdapterList[position]
        holder.itemView.tag = item.path
        holder.title.text = item.name
        // Load attributes
        cacheAndLoadAttributes(holder, item)
        if (item.isDirectory) {
            holder.itemView.setOnClickListener {
                if (isInSelectionMode) {
                    toggleSelection(position)
                    AccessibilityUtils.requestAccessibilityFocus(holder.itemView)
                    return@setOnClickListener
                }
                mViewModel.loadFiles(item.path.getUri())
            }
        } else {
            holder.itemView.setOnClickListener {
                if (isInSelectionMode) {
                    toggleSelection(position)
                    AccessibilityUtils.requestAccessibilityFocus(holder.itemView)
                    return@setOnClickListener
                }
                val fragment = OpenWithDialogFragment.getInstance(item.path)
                fragment.show(mFmActivity.supportFragmentManager, OpenWithDialogFragment.TAG)
            }
        }
        // Symbolic link
        holder.symbolicLinkIcon.visibility = if (item.path.isSymbolicLink()) View.VISIBLE else View.GONE
        // Set background colors
        holder.itemView.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.context, android.R.color.transparent))
        // Set selections
        holder.icon.setOnClickListener {
            toggleSelection(position)
            AccessibilityUtils.requestAccessibilityFocus(holder.itemView)
        }
        // Set actions
        val popupMenu = getPopupMenu(holder.action, item, position)
        holder.action.setOnClickListener { popupMenu.show() }
        holder.itemView.setOnLongClickListener {
            val lastSelectedItem = mViewModel.getLastSelectedItem()
            var lastSelectedItemPosition = -1
            if (lastSelectedItem != null) {
                for (i in mAdapterList.indices) {
                    if (mAdapterList[i].path == lastSelectedItem) {
                        lastSelectedItemPosition = i
                        break
                    }
                }
            }
            if (lastSelectedItemPosition >= 0) {
                selectRange(lastSelectedItemPosition, position)
            } else {
                toggleSelection(position)
                AccessibilityUtils.requestAccessibilityFocus(holder.itemView)
            }
            true
        }
        super.onBindViewHolder(holder, position)
    }

    private fun cacheAndLoadAttributes(holder: ViewHolder, item: FmItem) {
        if (item.isCached) {
            loadAttributes(holder, item)
        } else {
            ThreadUtils.postOnBackgroundThread {
                val holderRef = WeakReference(holder)
                val itemRef = WeakReference(item)
                item.cache()
                ThreadUtils.postOnMainThread {
                    val h = holderRef.get()
                    val i = itemRef.get()
                    if (h != null && i != null && h.itemView.tag == i.path) {
                        loadAttributes(h, i)
                    }
                }
            }
        }
    }

    @MainThread
    private fun loadAttributes(holder: ViewHolder, item: FmItem) {
        val tag = item.tag
        holder.icon.tag = tag
        ImageLoader.getInstance().displayImage(tag, holder.icon, FmIconFetcher(item))
        val modificationDate = DateUtils.formatDateTime(mFmActivity, item.lastModified)
        if (item.isDirectory) {
            holder.subtitle.text = String.format(Locale.getDefault(), "%d • %s", item.childCount, modificationDate)
        } else {
            holder.subtitle.text = String.format(Locale.getDefault(), "%s • %s",
                Formatter.formatShortFileSize(mFmActivity, item.size), modificationDate)
        }
    }

    override fun getItemId(position: Int): Long = mAdapterList[position].hashCode().toLong()

    override fun getItemCount(): Int = mAdapterList.size

    override fun select(position: Int): Boolean {
        mViewModel.setSelectedItem(mAdapterList[position].path, true)
        return true
    }

    override fun deselect(position: Int): Boolean {
        mViewModel.setSelectedItem(mAdapterList[position].path, false)
        return true
    }

    override fun isSelected(position: Int): Boolean = mViewModel.isSelected(mAdapterList[position].path)

    override fun cancelSelection() {
        super.cancelSelection()
        mViewModel.clearSelections()
    }

    override fun getSelectedItemCount(): Int = mViewModel.getSelectedItemCount()

    override fun getTotalItemCount(): Int = mAdapterList.size

    private fun getPopupMenu(anchor: View, item: FmItem, position: Int): PopupMenu {
        val popupMenu = PopupMenu(anchor.context, anchor)
        popupMenu.setForceShowIcon(true)
        popupMenu.inflate(R.menu.fragment_fm_item_actions)
        val menu = popupMenu.menu
        val openWithAction = menu.findItem(R.id.action_open_with)
        val cutAction = menu.findItem(R.id.action_cut)
        val copyAction = menu.findItem(R.id.action_copy)
        val renameAction = menu.findItem(R.id.action_rename)
        val deleteAction = menu.findItem(R.id.action_delete)
        val shareAction = menu.findItem(R.id.action_share)
        val selectAction = menu.findItem(R.id.action_select)

        val canRead = item.path.canRead()
        val canWrite = item.path.canWrite()
        openWithAction.isEnabled = canRead
        cutAction.isEnabled = canRead && canWrite
        copyAction.isEnabled = canRead
        renameAction.isEnabled = canRead && canWrite
        deleteAction.isEnabled = canRead && canWrite
        shareAction.isEnabled = canRead

        openWithAction.setOnMenuItemClickListener {
            OpenWithDialogFragment.getInstance(item.path).show(mFmActivity.supportFragmentManager, OpenWithDialogFragment.TAG)
            true
        }
        cutAction.setOnMenuItemClickListener {
            FmTasks.instance.enqueue(FmTasks.FmTask(FmTasks.FmTask.TYPE_CUT, listOf(item.path)))
            UIUtils.displayShortToast(R.string.copied_to_clipboard)
            false
        }
        copyAction.setOnMenuItemClickListener {
            FmTasks.instance.enqueue(FmTasks.FmTask(FmTasks.FmTask.TYPE_COPY, listOf(item.path)))
            UIUtils.displayShortToast(R.string.copied_to_clipboard)
            false
        }
        renameAction.setOnMenuItemClickListener {
            val dialog = RenameDialogFragment.getInstance(item.path.getName()) { prefix, extension ->
                val displayName = if (!TextUtils.isEmpty(extension)) "$prefix.$extension" else prefix
                if (item.path.renameTo(displayName)) {
                    UIUtils.displayShortToast(R.string.renamed_successfully)
                    mViewModel.reload()
                } else {
                    UIUtils.displayShortToast(R.string.failed)
                }
            }
            dialog.show(mFmActivity.supportFragmentManager, RenameDialogFragment.TAG)
            false
        }
        deleteAction.setOnMenuItemClickListener {
            MaterialAlertDialogBuilder(mFmActivity)
                .setTitle(mFmActivity.getString(R.string.delete_filename, item.path.getName()))
                .setMessage(R.string.are_you_sure)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm_file_deletion) { _, _ ->
                    if (item.path.delete()) {
                        UIUtils.displayShortToast(R.string.deleted_successfully)
                        mViewModel.reload()
                    } else {
                        UIUtils.displayShortToast(R.string.failed)
                    }
                }
                .show()
            true
        }
        shareAction.setOnMenuItemClickListener {
            mViewModel.shareFiles(listOf(item.path))
            true
        }
        selectAction.setOnMenuItemClickListener {
            select(position)
            notifySelectionChange()
            notifyItemChanged(position, AdapterUtils.STUB)
            true
        }
        val isVfs = mViewModel.getOptions()!!.isVfs
        menu.findItem(R.id.action_shortcut)
            .setEnabled(!isVfs)
            .setVisible(!isVfs)
            .setOnMenuItemClickListener {
                mViewModel.createShortcut(item)
                true
            }
        val favItem = menu.findItem(R.id.action_add_to_favorites)
        favItem.setOnMenuItemClickListener {
            mViewModel.addToFavorite(item.path, mViewModel.getOptions()!!)
            true
        }
        favItem.isEnabled = item.isDirectory
        favItem.isVisible = item.isDirectory
        menu.findItem(R.id.action_copy_path).setOnMenuItemClickListener {
            val path = FmUtils.getDisplayablePath(item.path)
            Utils.copyToClipboard(mFmActivity, "Path", path)
            true
        }
        menu.findItem(R.id.action_properties).setOnMenuItemClickListener {
            mViewModel.getDisplayPropertiesLiveData().value = item.path.getUri()
            true
        }
        return popupMenu
    }

    internal class ViewHolder(itemView: View) : MultiSelectionView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView as MaterialCardView
        val icon: ShapeableImageView = itemView.findViewById(android.R.id.icon)
        val symbolicLinkIcon: ShapeableImageView = itemView.findViewById(R.id.symbolic_link_icon)
        val action: MaterialButton = itemView.findViewById(android.R.id.button1)
        val title: AppCompatTextView = itemView.findViewById(android.R.id.title)
        val subtitle: AppCompatTextView = itemView.findViewById(android.R.id.summary)

        init {
            action.contentDescription = itemView.context.getString(com.google.android.material.R.string.abc_action_menu_overflow_description)
            action.setIconResource(io.github.muntashirakon.ui.R.drawable.ic_more_vert)
            itemView.findViewById<View>(R.id.divider).visibility = View.GONE
        }
    }
}
