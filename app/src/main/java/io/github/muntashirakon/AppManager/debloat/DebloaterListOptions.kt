// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat

import android.content.Context
import android.os.Bundle
import android.util.SparseIntArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.chip.Chip
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.dialog.CapsuleBottomSheetDialogFragment

class DebloaterListOptions : CapsuleBottomSheetDialogFragment() {
    private var mModel: DebloaterViewModel? = null

    override fun initRootView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.dialog_debloater_list_options, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = requireActivity() as DebloaterActivity
        mModel = activity.viewModel
        val listTypes: ViewGroup = view.findViewById(R.id.list_types)
        for (i in 0 until LIST_FILTER_MAP.size()) {
            listTypes.addView(getFilterChip(listTypes.context, LIST_FILTER_MAP.keyAt(i), LIST_FILTER_MAP.valueAt(i)))
        }
        val removalTypes: ViewGroup = view.findViewById(R.id.removal_types)
        for (i in 0 until REMOVAL_FILTER_MAP.size()) {
            removalTypes.addView(getFilterChip(removalTypes.context, REMOVAL_FILTER_MAP.keyAt(i), REMOVAL_FILTER_MAP.valueAt(i)))
        }
        val filterView: ViewGroup = view.findViewById(R.id.filter_options)
        for (i in 0 until NORMAL_FILTER_MAP.size()) {
            filterView.addView(getFilterChip(filterView.context, NORMAL_FILTER_MAP.keyAt(i), NORMAL_FILTER_MAP.valueAt(i)))
        }
    }

    private fun getFilterChip(context: Context, flag: Int, strRes: Int): Chip {
        return Chip(context).apply {
            isFocusable = true
            isCloseIconVisible = false
            setText(strRes)
            isChecked = mModel!!.hasFilterFlag(flag)
            setOnCheckedChangeListener { _, isChecked -> if (isChecked) mModel!!.addFilterFlag(flag) else mModel!!.removeFilterFlag(flag) }
        }
    }

    companion object {
        val TAG: String = DebloaterListOptions::class.java.simpleName

        const val FILTER_NO_FILTER = 0
        const val FILTER_LIST_AOSP = 1
        const val FILTER_LIST_OEM = 1 shl 1
        const val FILTER_LIST_CARRIER = 1 shl 2
        const val FILTER_LIST_GOOGLE = 1 shl 3
        const val FILTER_LIST_MISC = 1 shl 4
        const val FILTER_REMOVAL_SAFE = 1 shl 6
        const val FILTER_REMOVAL_REPLACE = 1 shl 7
        const val FILTER_REMOVAL_CAUTION = 1 shl 8
        const val FILTER_REMOVAL_UNSAFE = 1 shl 9
        const val FILTER_USER_APPS = 1 shl 10
        const val FILTER_SYSTEM_APPS = 1 shl 11
        const val FILTER_INSTALLED_APPS = 1 shl 12
        const val FILTER_UNINSTALLED_APPS = 1 shl 13
        const val FILTER_FROZEN_APPS = 1 shl 14
        const val FILTER_UNFROZEN_APPS = 1 shl 15

        private val LIST_FILTER_MAP = SparseIntArray().apply {
            put(FILTER_LIST_AOSP, R.string.debloat_list_aosp)
            put(FILTER_LIST_OEM, R.string.debloat_list_oem)
            put(FILTER_LIST_CARRIER, R.string.debloat_list_carrier)
            put(FILTER_LIST_GOOGLE, R.string.debloat_list_google)
            put(FILTER_LIST_MISC, R.string.debloat_list_misc)
        }

        private val REMOVAL_FILTER_MAP = SparseIntArray().apply {
            put(FILTER_REMOVAL_SAFE, R.string.debloat_removal_safe)
            put(FILTER_REMOVAL_REPLACE, R.string.debloat_removal_replace)
            put(FILTER_REMOVAL_CAUTION, R.string.debloat_removal_caution)
            put(FILTER_REMOVAL_UNSAFE, R.string.debloat_removal_unsafe)
        }

        private val NORMAL_FILTER_MAP = SparseIntArray().apply {
            put(FILTER_USER_APPS, R.string.filter_user_apps)
            put(FILTER_SYSTEM_APPS, R.string.filter_system_apps)
            put(FILTER_INSTALLED_APPS, R.string.installed_apps)
            put(FILTER_UNINSTALLED_APPS, R.string.uninstalled_apps)
            put(FILTER_FROZEN_APPS, R.string.filter_frozen_apps)
            put(FILTER_UNFROZEN_APPS, R.string.filter_unfrozen_apps)
        }

        @JvmStatic
        fun getDefaultFilterFlags(): Int {
            return (FILTER_LIST_AOSP or FILTER_LIST_OEM or FILTER_LIST_CARRIER or FILTER_LIST_GOOGLE
                    or FILTER_LIST_MISC or FILTER_REMOVAL_SAFE or FILTER_REMOVAL_REPLACE
                    or FILTER_REMOVAL_CAUTION or FILTER_INSTALLED_APPS or FILTER_SYSTEM_APPS)
        }
    }
}
