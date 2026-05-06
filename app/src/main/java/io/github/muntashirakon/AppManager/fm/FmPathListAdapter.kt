// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.utils.Utils
import io.github.muntashirakon.io.Paths
import io.github.muntashirakon.util.AdapterUtils
import java.io.File
import java.util.*

internal class FmPathListAdapter(private val mViewModel: FmViewModel) : RecyclerView.Adapter<FmPathListAdapter.PathHolder>() {
    private val mPathParts = Collections.synchronizedList(mutableListOf<String>())
    private var mAlternativeRootName: String? = null
    private var mCurrentPosition = -1
    private var mCurrentUri: Uri? = null

    fun setCurrentUri(currentUri: Uri) {
        val lastPath = mCurrentUri
        val lastPathStr = lastPath?.toString()
        mCurrentUri = currentUri
        val paths = FmUtils.uriToPathParts(currentUri)
        var currentPathStr = currentUri.toString()
        if (!currentPathStr.endsWith(File.separator)) {
            currentPathStr += File.separator
        }
        if (lastPathStr != null && lastPathStr.startsWith(currentPathStr)) {
            setCurrentPosition(paths.size - 1)
        } else {
            mCurrentPosition = paths.size - 1
            AdapterUtils.notifyDataSetChanged(this, mPathParts, paths)
        }
    }

    fun setAlternativeRootName(alternativeRootName: String?) {
        mAlternativeRootName = alternativeRootName
    }

    fun getCurrentUri(): Uri? = mCurrentUri

    fun getCurrentPosition(): Int = mCurrentPosition

    fun calculateUri(position: Int): Uri {
        return FmUtils.uriFromPathParts(mCurrentUri!!, mPathParts, position)
    }

    private fun setCurrentPosition(currentPosition: Int) {
        val lastPosition = mCurrentPosition
        mCurrentPosition = currentPosition
        if (lastPosition >= 0) {
            notifyItemChanged(lastPosition, AdapterUtils.STUB)
        }
        notifyItemChanged(currentPosition, AdapterUtils.STUB)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PathHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_path, parent, false)
        return PathHolder(view)
    }

    override fun onBindViewHolder(holder: PathHolder, position: Int) {
        val actualPathPart = mPathParts[position]
        val pathPart = if (position == 0) {
            mAlternativeRootName ?: actualPathPart
        } else {
            "» $actualPathPart"\n}
        holder.textView.text = pathPart
        if (position == 0 && pathPart == "/") {
            holder.itemView.contentDescription = holder.itemView.context.getString(R.string.root)
        } else {
            holder.itemView.contentDescription = actualPathPart
        }
        holder.itemView.setOnClickListener {
            if (mCurrentPosition != position) {
                mViewModel.loadFiles(calculateUri(position))
            }
        }
        holder.itemView.setOnLongClickListener {
            val context = it.context
            val popupMenu = PopupMenu(context, it)
            val menu = popupMenu.menu
            // Copy path
            menu.add(R.string.copy_this_path).setOnMenuItemClickListener {
                val path = FmUtils.getDisplayablePath(calculateUri(position))
                Utils.copyToClipboard(context, "Path", path)
                true
            }
            // Open in new window
            menu.add(R.string.open_in_new_window).setOnMenuItemClickListener {
                val intent = Intent(context, FmActivity::class.java).apply {
                    setDataAndType(calculateUri(position), DocumentsContract.Document.MIME_TYPE_DIR)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                }
                context.startActivity(intent)
                true
            }
            // Add to favorites
            menu.add(R.string.add_to_favorites).setOnMenuItemClickListener {
                mViewModel.addToFavorite(Paths.get(calculateUri(position)), mViewModel.getOptions()!!)
                true
            }
            // Properties
            menu.add(R.string.file_properties).setOnMenuItemClickListener {
                mViewModel.getDisplayPropertiesLiveData().value = calculateUri(position)
                true
            }
            popupMenu.show()
            true
        }
        holder.textView.setTextColor(
            if (mCurrentPosition == position) {
                MaterialColors.getColor(holder.textView, com.google.android.material.R.attr.colorPrimary)
            } else {
                MaterialColors.getColor(holder.textView, android.R.attr.textColorSecondary)
            }
        )
    }

    override fun getItemCount(): Int = mPathParts.size

    internal class PathHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.findViewById(android.R.id.text1)
    }
}
