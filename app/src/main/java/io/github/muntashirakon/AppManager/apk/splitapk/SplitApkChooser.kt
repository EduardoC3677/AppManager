// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.splitapk

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import aosp.libcore.util.EmptyArray
import io.github.muntashirakon.AppManager.apk.ApkFile
import io.github.muntashirakon.AppManager.apk.ApkSource
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerViewModel
import io.github.muntashirakon.AppManager.utils.ArrayUtils
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder
import java.util.*

class SplitApkChooser : Fragment() {
    private var mViewModel: PackageInstallerViewModel? = null
    private var mApkEntries: List<ApkFile.Entry>? = null
    private var mViewBuilder: SearchableMultiChoiceDialogBuilder<String>? = null
    private var mSelectedSplits: MutableSet<String>? = null
    private val mSeenSplits = HashMap<String?, HashSet<Int>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel = ViewModelProvider(requireActivity()).get(PackageInstallerViewModel::class.java)
        mSelectedSplits = mViewModel!!.getSelectedSplits()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val apkFile = mViewModel!!.getApkFile() ?: throw IllegalArgumentException("ApkFile cannot be empty.")
        if (!apkFile.isSplit) throw RuntimeException("Apk file does not contain any split.")
        mApkEntries = apkFile.entries
        val entryIds = Array(mApkEntries!!.size) { mApkEntries!![it].id }
        val entryNames = Array<CharSequence>(mApkEntries!!.size) { mApkEntries!![it].toLocalizedString(requireActivity()) }
        mViewBuilder = SearchableMultiChoiceDialogBuilder(requireActivity(), entryIds, entryNames)
            .showSelectAll(false)
            .addDisabledItems(getUnsupportedOrRequiredSplitIds())
        mViewBuilder!!.create()
        return mViewBuilder!!.view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mViewBuilder!!.addSelections(getInitialSelections())
            .setOnMultiChoiceClickListener { _, which, _, isChecked ->
                if (isChecked) {
                    mViewBuilder!!.addSelections(select(which))
                } else {
                    val itemsToDeselect = deselect(which)
                    if (itemsToDeselect == null) {
                        mViewBuilder!!.addSelections(intArrayOf(which))
                    } else {
                        mViewBuilder!!.removeSelections(itemsToDeselect)
                    }
                }
                mViewBuilder!!.reloadListUi()
            }
    }

    fun getInitialSelections(): IntArray {
        val selections = mutableListOf<Int>()
        try {
            val splitNames = HashSet<String>()
            mViewModel!!.getInstalledPackageInfo()?.let { info ->
                ApkSource.getApkSource(info.applicationInfo!!).resolve().use { af ->
                    af.entries.forEach { splitNames.add(it.name) }
                }
            }
            if (splitNames.isNotEmpty()) {
                mApkEntries!!.forEach { entry ->
                    if (splitNames.contains(entry.name)) {
                        mSelectedSplits!!.add(entry.id)
                        mSeenSplits.getOrPut(entry.feature) { HashSet() }.add(entry.type)
                    }
                }
            }
        } catch (ignore: Exception) {}
        mApkEntries!!.forEachIndexed { i, entry ->
            if (mSelectedSplits!!.contains(entry.id)) {
                selections.add(i)
            } else if (entry.isRequired) {
                mSelectedSplits!!.add(entry.id)
                selections.add(i)
                mSeenSplits.getOrPut(entry.feature) { HashSet() }.add(entry.type)
            }
        }
        mApkEntries!!.forEachIndexed { i, entry ->
            if (mSelectedSplits!!.contains(entry.id)) return@forEachIndexed
            val types = mSeenSplits[entry.feature] ?: return@forEachIndexed
            when (entry.type) {
                ApkFile.APK_SPLIT_DENSITY, ApkFile.APK_SPLIT_ABI, ApkFile.APK_SPLIT_LOCALE -> {
                    if (!types.contains(entry.type)) {
                        types.add(entry.type)
                        selections.add(i)
                        mSelectedSplits!!.add(entry.id)
                    }
                }
            }
        }
        return ArrayUtils.convertToIntArray(selections)
    }

    private fun getUnsupportedOrRequiredSplitIds(): List<String> {
        return mApkEntries!!.filter { !it.supported() || it.isRequired }.map { it.id }
    }

    private fun select(index: Int): IntArray {
        val selections = mutableListOf<Int>()
        val selectedEntry = mApkEntries!![index]
        val types = mSeenSplits.getOrPut(selectedEntry.feature) { HashSet() }
        mSelectedSplits!!.add(selectedEntry.id)
        mApkEntries!!.forEachIndexed { i, entry ->
            if (entry.feature == selectedEntry.feature && entry.type != selectedEntry.type) {
                when (entry.type) {
                    ApkFile.APK_BASE, ApkFile.APK_SPLIT_FEATURE -> {
                        selections.add(i); mSelectedSplits!!.add(entry.id)
                    }
                    ApkFile.APK_SPLIT_DENSITY, ApkFile.APK_SPLIT_ABI, ApkFile.APK_SPLIT_LOCALE -> {
                        if (!types.contains(entry.type)) {
                            types.add(entry.type); selections.add(i); mSelectedSplits!!.add(entry.id)
                        }
                    }
                }
            }
        }
        return ArrayUtils.convertToIntArray(selections)
    }

    private fun deselect(index: Int): IntArray? {
        val entry = mApkEntries!![index]
        if (entry.isRequired) return null
        if (entry.type == ApkFile.APK_SPLIT_FEATURE) {
            val deselected = mutableListOf<Int>()
            mSeenSplits.remove(entry.feature)
            mApkEntries!!.forEachIndexed { i, e ->
                if (e.feature == entry.feature && mSelectedSplits!!.contains(e.id)) {
                    deselected.add(i); mSelectedSplits!!.remove(e.id)
                }
            }
            return ArrayUtils.convertToIntArray(deselected)
        }
        val hasOtherOfSameType = mApkEntries!!.indices.any { i ->
            i != index && mApkEntries!![i].type == entry.type && mApkEntries!![i].feature == entry.feature && mSelectedSplits!!.contains(mApkEntries!![i].id)
        }
        if (hasOtherOfSameType) {
            mSelectedSplits!!.remove(entry.id)
            return EmptyArray.INT
        }
        return null
    }

    companion object {
        val TAG: String = SplitApkChooser::class.java.simpleName
        private const val EXTRA_ACTION_NAME = "name"\nprivate const val EXTRA_VERSION_INFO = "version"

        @JvmStatic
        fun getNewInstance(versionInfo: String, actionName: String?): SplitApkChooser {
            return SplitApkChooser().apply { arguments = Bundle().apply { putString(EXTRA_ACTION_NAME, actionName); putString(EXTRA_VERSION_INFO, versionInfo) } }
        }
    }
}
