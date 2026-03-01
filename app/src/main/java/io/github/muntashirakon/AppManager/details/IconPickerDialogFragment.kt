// SPDX-License-Identifier: ISC AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details

import android.app.Application
import android.app.Dialog
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageItemInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader
import io.github.muntashirakon.AppManager.utils.ResourceUtil
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import java.util.*
import java.util.concurrent.Future

class IconPickerDialogFragment : DialogFragment() {
    private var mListener: IconPickerListener? = null
    private var mAdapter: IconListingAdapter? = null
    private var mModel: IconPickerViewModel? = null

    fun attachIconPickerListener(listener: IconPickerListener?) {
        mListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mModel = ViewModelProvider(this).get(IconPickerViewModel::class.java)
        mModel!!.getIconsLiveData().observe(this) { icons ->
            mAdapter?.let {
                it.mIcons = icons
                it.notifyDataSetChanged()
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mAdapter = IconListingAdapter(requireActivity())
        val grid = View.inflate(requireActivity(), R.layout.dialog_icon_picker, null) as GridView
        grid.adapter = mAdapter
        grid.setOnItemClickListener { view, _, index, _ ->
            mListener?.iconPicked(view.adapter.getItem(index) as IconItemInfo)
            dialog?.dismiss()
        }
        mModel!!.resolveIcons()
        return MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.icon_picker)
            .setView(grid)
            .setNegativeButton(R.string.cancel, null).create()
    }

    interface IconPickerListener {
        fun iconPicked(icon: PackageItemInfo)
    }

    private class IconListingAdapter(private val mActivity: FragmentActivity) : BaseAdapter() {
        var mIcons: Array<IconItemInfo>? = null

        override fun getCount(): Int = mIcons?.size ?: 0
        override fun getItem(position: Int): Any = mIcons!![position]
        override fun getItemId(position: Int): Long = 0

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view: ImageView
            val resultView: View
            if (convertView == null) {
                view = AppCompatImageView(mActivity)
                resultView = view
                val size = mActivity.resources.getDimensionPixelSize(R.dimen.icon_size)
                resultView.layoutParams = AbsListView.LayoutParams(size, size)
            } else {
                resultView = convertView
                view = convertView as ImageView
            }
            val info = mIcons!![position]
            view.tag = info.packageName
            ImageLoader.getInstance().displayImage(info.packageName, info, view)
            return resultView
        }
    }

    class IconPickerViewModel(application: Application) : AndroidViewModel(application) {
        private val mPm: PackageManager = application.packageManager
        private val mIconsLiveData = MutableLiveData<Array<IconItemInfo>>()
        private var mIconLoaderResult: Future<*>? = null

        override fun onCleared() {
            mIconLoaderResult?.cancel(true)
            super.onCleared()
        }

        fun getIconsLiveData(): LiveData<Array<IconItemInfo>> = mIconsLiveData

        fun resolveIcons() {
            mIconLoaderResult?.cancel(true)
            mIconLoaderResult = ThreadUtils.postOnBackgroundThread {
                val icons = TreeSet<IconItemInfo>()
                val installedPackages = mPm.getInstalledPackages(0)
                for (pack in installedPackages) {
                    try {
                        val iconResourceName = mPm.getResourcesForApplication(pack.packageName)
                            .getResourceName(pack.applicationInfo.icon)
                        if (iconResourceName != null) {
                            icons.add(IconItemInfo(getApplication(), pack.packageName, iconResourceName))
                        }
                    } catch (ignore: Exception) {}
                    if (ThreadUtils.isInterrupted()) return@postOnBackgroundThread
                }
                mIconsLiveData.postValue(icons.toTypedArray())
            }
        }
    }

    private class IconItemInfo(private val mContext: Context, packageName: String, val iconResourceString: String) : PackageItemInfo(), Comparable<IconItemInfo> {
        init {
            this.packageName = packageName
            this.name = iconResourceString
        }

        override fun loadIcon(pm: PackageManager): Drawable {
            try {
                val drawable = ResourceUtil.getResourceFromName(pm, iconResourceString).getDrawable(mContext.theme)
                if (drawable != null) return drawable
            } catch (ignore: Exception) {}
            return pm.defaultActivityIcon
        }

        override fun compareTo(other: IconItemInfo): Int = iconResourceString.compareTo(other.iconResourceString)
    }

    companion object {
        const val TAG = "IconPickerDialogFragment"
    }
}
