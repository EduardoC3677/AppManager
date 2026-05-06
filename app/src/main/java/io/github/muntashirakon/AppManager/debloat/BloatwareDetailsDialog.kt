// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat

import android.app.Application
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.textview.MaterialTextView
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.StaticDataset
import io.github.muntashirakon.AppManager.db.utils.AppDb
import io.github.muntashirakon.AppManager.details.AppDetailsActivity
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes
import io.github.muntashirakon.dialog.CapsuleBottomSheetDialogFragment
import io.github.muntashirakon.util.AdapterUtils
import io.github.muntashirakon.widget.FlowLayout
import io.github.muntashirakon.widget.MaterialAlertView
import io.github.muntashirakon.widget.RecyclerView
import java.util.*

class BloatwareDetailsDialog : CapsuleBottomSheetDialogFragment() {
    private var mAppIconView: ImageView? = null
    private var mOpenAppInfoButton: MaterialButton? = null
    private var mAppLabelView: TextView? = null
    private var mPackageNameView: TextView? = null
    private var mFlowLayout: FlowLayout? = null
    private var mWarningView: MaterialAlertView? = null
    private var mDescriptionView: MaterialTextView? = null
    private var mSuggestionContainer: LinearLayoutCompat? = null
    private var mSuggestionView: RecyclerView? = null
    private var mAdapter: SuggestionsAdapter? = null

    override fun displayLoaderByDefault(): Boolean = true

    override fun initRootView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialog_bloatware_details, container, false)
        mAppIconView = view.findViewById(R.id.icon)
        mOpenAppInfoButton = view.findViewById(R.id.info)
        mAppLabelView = view.findViewById(R.id.name)
        mPackageNameView = view.findViewById(R.id.package_name)
        mFlowLayout = view.findViewById(R.id.tag_cloud)
        mWarningView = view.findViewById(R.id.alert_text)
        mDescriptionView = view.findViewById(R.id.apk_description)
        mSuggestionContainer = view.findViewById(R.id.container)
        mSuggestionView = view.findViewById(R.id.recycler_view)
        mSuggestionView!!.layoutManager = LinearLayoutManager(requireContext())
        mAdapter = SuggestionsAdapter()
        mSuggestionView!!.adapter = mAdapter
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val packageName = requireArguments().getString(ARG_PACKAGE_NAME) ?: run { dismiss(); return }
        val viewModel = ViewModelProvider(requireActivity()).get(BloatwareDetailsViewModel::class.java)
        viewModel.debloatObjectLiveData.observe(viewLifecycleOwner) { debloatObject ->
            if (debloatObject == null) { dismiss(); return@observe }
            finishLoading()
            updateDialog(debloatObject)
            updateSuggestions(debloatObject.suggestions)
        }
        viewModel.findDebloatObject(packageName)
    }

    private fun updateDialog(debloatObject: DebloatObject) {
        mAppIconView!!.setImageDrawable(debloatObject.icon ?: requireActivity().packageManager.defaultActivityIcon)
        val users = debloatObject.users
        if (users != null && users.isNotEmpty()) {
            mOpenAppInfoButton!!.visibility = View.VISIBLE
            mOpenAppInfoButton!!.setOnClickListener {
                startActivity(AppDetailsActivity.getIntent(requireContext(), debloatObject.packageName!!, users[0]))
                dismiss()
            }
        } else {
            mOpenAppInfoButton!!.visibility = View.GONE
        }
        mAppLabelView!!.text = debloatObject.getLabelOrPackageName()
        mPackageNameView!!.text = debloatObject.packageName
        debloatObject.getWarning()?.let {
            mWarningView!!.visibility = View.VISIBLE
            mWarningView!!.text = it
            mWarningView!!.setAlertType(if (debloatObject.getRemoval() >= DebloatObject.REMOVAL_CAUTION) MaterialAlertView.ALERT_TYPE_INFO else MaterialAlertView.ALERT_TYPE_WARN)
        } ?: run { mWarningView!!.visibility = View.GONE }
        mDescriptionView!!.text = getDescription(debloatObject)
        val removalColor: Int
        @StringRes val removalRes: Int
        when (debloatObject.getRemoval()) {
            DebloatObject.REMOVAL_SAFE -> {
                removalColor = ColorCodes.getRemovalSafeIndicatorColor(requireContext())
                removalRes = R.string.debloat_removal_safe_short_description
            }
            DebloatObject.REMOVAL_REPLACE -> {
                removalColor = ColorCodes.getRemovalReplaceIndicatorColor(requireContext())
                removalRes = R.string.debloat_removal_replace_short_description
            }
            DebloatObject.REMOVAL_UNSAFE -> {
                removalColor = ColorCodes.getRemovalUnsafeIndicatorColor(requireContext())
                removalRes = R.string.debloat_removal_unsafe
            }
            else -> {
                removalColor = ColorCodes.getRemovalCautionIndicatorColor(requireContext())
                removalRes = R.string.debloat_removal_caution_short_description
            }
        }
        mFlowLayout!!.removeAllViews()
        debloatObject.type?.let { addTag(mFlowLayout!!, it) }
        addTag(mFlowLayout!!, removalRes, removalColor)
    }

    private fun updateSuggestions(suggestionObjects: List<SuggestionObject>?) {
        if (suggestionObjects.isNullOrEmpty()) {
            mSuggestionContainer!!.visibility = View.GONE
            return
        }
        mSuggestionContainer!!.visibility = View.VISIBLE
        mAdapter!!.setList(suggestionObjects)
    }

    private fun getDescription(debloatObject: DebloatObject): CharSequence {
        val sb = SpannableStringBuilder()
        debloatObject.getDescription()?.let { sb.append(it.trim()) }
        val dependencies = debloatObject.getDependencies()
        if (dependencies.isNotEmpty()) {
            if (dependencies.size == 1) sb.append(UIUtils.getBoldString("\nDependency: ")).append(dependencies[0])
            else sb.append(UIUtils.getBoldString("\nDependencies
")).append(UIUtils.getOrderedList(dependencies.toList()))
        }
        val requiredBy = debloatObject.getRequiredBy()
        if (requiredBy.isNotEmpty()) {
            if (requiredBy.size == 1) sb.append(UIUtils.getBoldString("\nRequired by: ")).append(requiredBy[0])
            else sb.append(UIUtils.getBoldString("\nRequired by
")).append(UIUtils.getOrderedList(requiredBy.toList()))
        }
        val refSites = debloatObject.getWebRefs()
        if (refSites.isNotEmpty()) {
            sb.append(UIUtils.getBoldString("\nReferences
")).append(UIUtils.getOrderedList(refSites.toList()))
        }
        return sb
    }

    private fun addTag(parent: ViewGroup, @StringRes titleRes: Int, @ColorInt background: Int) {
        val chip = LayoutInflater.from(requireContext()).inflate(R.layout.item_chip, parent, false) as Chip
        chip.setText(titleRes)
        chip.chipBackgroundColor = ColorStateList.valueOf(background)
        val luminance = ColorUtils.calculateLuminance(background)
        chip.setTextColor(if (luminance < 0.5) Color.WHITE else Color.BLACK)
        parent.addView(chip)
    }

    private fun addTag(parent: ViewGroup, title: CharSequence) {
        val chip = LayoutInflater.from(requireContext()).inflate(R.layout.item_chip, parent, false) as Chip
        chip.text = title
        parent.addView(chip)
    }

    class BloatwareDetailsViewModel(application: Application) : AndroidViewModel(application) {
        val debloatObjectLiveData = MutableLiveData<DebloatObject?>()

        fun findDebloatObject(packageName: String) {
            ThreadUtils.postOnBackgroundThread {
                StaticDataset.getDebloatObjects().find { packageName == it.packageName }?.let {
                    it.fillInstallInfo(getApplication(), AppDb())
                    debloatObjectLiveData.postValue(it)
                } ?: debloatObjectLiveData.postValue(null)
            }
        }
    }

    private inner class SuggestionsAdapter : RecyclerView.Adapter<SuggestionsAdapter.SuggestionViewHolder>() {
        private val mSuggestions = Collections.synchronizedList(mutableListOf<SuggestionObject>())

        fun setList(suggestions: List<SuggestionObject>) {
            AdapterUtils.notifyDataSetChanged(this, mSuggestions, suggestions)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
            return SuggestionViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_bloatware_details, parent, false))
        }

        override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
            val suggestion = mSuggestions[position]
            holder.labelView.text = suggestion.label
            holder.packageNameView.text = suggestion.packageName
            val users = suggestion.users
            if (users != null && users.isNotEmpty()) {
                holder.marketOrAppInfoButton.setIconResource(io.github.muntashirakon.ui.R.drawable.ic_information)
                holder.marketOrAppInfoButton.setOnClickListener {
                    startActivity(AppDetailsActivity.getIntent(requireContext(), suggestion.packageName!!, users[0]))
                }
            } else {
                holder.marketOrAppInfoButton.setIconResource(if (suggestion.isInFDroidMarket()) R.drawable.ic_frost_fdroid else R.drawable.ic_frost_aurorastore)
                holder.marketOrAppInfoButton.setOnClickListener {
                    try { startActivity(suggestion.getMarketLink().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    catch (th: Throwable) { UIUtils.displayLongToast("Error: ${th.message}") }
                }
            }
            val sb = StringBuilder()
            suggestion.reason?.let { sb.append(it).append("\n") }
            sb.append(suggestion.repo)
            holder.repoView.text = sb
        }

        override fun getItemCount(): Int = mSuggestions.size
        override fun getItemId(position: Int): Long = mSuggestions[position].hashCode().toLong()

        inner class SuggestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val labelView: TextView = itemView.findViewById(R.id.name)
            val packageNameView: TextView = itemView.findViewById(R.id.package_name)
            val repoView: TextView = itemView.findViewById(R.id.message)
            val marketOrAppInfoButton: MaterialButton = itemView.findViewById(R.id.info)
        }
    }

    companion object {
        val TAG: String = BloatwareDetailsDialog::class.java.simpleName
        const val ARG_PACKAGE_NAME = "pkg"

        @JvmStatic
        fun getInstance(packageName: String): BloatwareDetailsDialog {
            return BloatwareDetailsDialog().apply { arguments = Bundle().apply { putString(ARG_PACKAGE_NAME, packageName) } }
        }
    }
}
