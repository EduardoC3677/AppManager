// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.profiles.struct.BaseProfile
import io.github.muntashirakon.AppManager.shortcut.CreateShortcutDialogFragment
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.AppManager.utils.Utils
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder
import io.github.muntashirakon.dialog.TextInputDialogBuilder
import io.github.muntashirakon.io.Paths
import io.github.muntashirakon.util.AdapterUtils
import io.github.muntashirakon.util.UiUtils
import io.github.muntashirakon.widget.RecyclerView
import java.util.*

class ProfilesActivity : BaseActivity(), NewProfileDialogFragment.OnCreateNewProfileInterface {
    private var mAdapter: ProfilesAdapter? = null
    private var mModel: ProfilesViewModel? = null
    private var mProgressIndicator: LinearProgressIndicator? = null
    private var mProfileId: String? = null

    private val mExportProfile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@registerForActivityResult
        mProfileId?.let { id ->
            try {
                contentResolver.openOutputStream(uri)?.use { os ->
                    val profilePath = ProfileManager.findProfilePathById(id)
                    val profile = BaseProfile.fromPath(profilePath)
                    profile.write(os)
                    UIUtils.displayShortToast(R.string.the_export_was_successful)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error: ", e)
                UIUtils.displayShortToast(R.string.export_failed)
            }
        }
    }

    private val mImportProfile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val profilePath = Paths.get(uri)
            val profile = BaseProfile.fromPath(profilePath)
            val newProfile = BaseProfile.newProfile(profile.name, profile.type, profile)
            val innerProfilePath = ProfileManager.requireProfilePathById(newProfile.profileId)
            innerProfilePath.openOutputStream().use { os -> newProfile.write(os) }
            UIUtils.displayShortToast(R.string.the_import_was_successful)
            startActivity(ProfileManager.getProfileIntent(this, newProfile.type, newProfile.profileId))
        } catch (e: Exception) {
            Log.e(TAG, "Error: ", e)
            UIUtils.displayShortToast(R.string.import_failed)
        }
    }

    override fun onAuthenticated(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_profiles)
        setSupportActionBar(findViewById(R.id.toolbar))
        mModel = ViewModelProvider(this).get(ProfilesViewModel::class.java)
        mProgressIndicator = findViewById(R.id.progress_linear)
        mProgressIndicator!!.visibilityAfterHide = View.GONE
        val listView: RecyclerView = findViewById(android.R.id.list)
        listView.layoutManager = UIUtils.getGridLayoutAt450Dp(this)
        listView.setEmptyView(findViewById(android.R.id.empty))
        UiUtils.applyWindowInsetsAsPaddingNoTop(listView)
        mAdapter = ProfilesAdapter(this)
        listView.adapter = mAdapter
        val fab: FloatingActionButton = findViewById(R.id.floatingActionButton)
        UiUtils.applyWindowInsetsAsMargin(fab)
        fab.setOnClickListener {
            val dialog = NewProfileDialogFragment.getInstance(this)
            dialog.show(supportFragmentManager, NewProfileDialogFragment.TAG)
        }
        mModel!!.getProfilesLiveData().observe(this) { profiles ->
            mProgressIndicator!!.hide()
            mAdapter!!.setDefaultList(profiles)
        }
        mProgressIndicator!!.show()
        mModel!!.loadProfiles()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_profiles_actions, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_import -> {
                mImportProfile.launch("application/json")
                true
            }
            R.id.action_refresh -> {
                mProgressIndicator!!.show()
                mModel!!.loadProfiles()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateNewProfile(newProfileName: String, type: Int) {
        val intent = ProfileManager.getNewProfileIntent(this, type, newProfileName)
        startActivity(intent)
    }

    class ProfilesAdapter(private val mActivity: ProfilesActivity) : RecyclerView.Adapter<ProfilesAdapter.ViewHolder>(), Filterable {
        private var mFilter: Filter? = null
        private var mConstraint: String? = null
        private var mDefaultList: Array<BaseProfile>? = null
        private var mAdapterList: Array<BaseProfile>? = null
        private var mAdapterMap: HashMap<BaseProfile, CharSequence>? = null
        private val mQueryStringHighlightColor: Int = ColorCodes.getQueryStringHighlightColor(mActivity)

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val title: TextView = itemView.findViewById(android.R.id.title)
            val summary: TextView = itemView.findViewById(android.R.id.summary)

            init {
                itemView.findViewById<View>(R.id.icon_frame).visibility = View.GONE
            }
        }

        fun setDefaultList(list: HashMap<BaseProfile, CharSequence>) {
            mDefaultList = list.keys.toTypedArray()
            val previousCount = itemCount
            mAdapterList = mDefaultList
            mAdapterMap = list
            AdapterUtils.notifyDataSetChanged(this, previousCount, mAdapterList!!.size)
        }

        override fun getItemCount(): Int = mAdapterList?.size ?: 0

        override fun getItemId(position: Int): Long = mAdapterList!![position].hashCode().toLong()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(io.github.muntashirakon.ui.R.layout.m3_preference, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val profile = mAdapterList!![position]
            if (mConstraint != null && profile.name.lowercase(Locale.ROOT).contains(mConstraint!!)) {
                holder.title.text = UIUtils.getHighlightedText(profile.name, mConstraint!!, mQueryStringHighlightColor)
            } else {
                holder.title.text = profile.name
            }
            val value = mAdapterMap?.get(profile)
            holder.summary.text = value ?: ""
            holder.itemView.setOnClickListener {
                val intent = ProfileManager.getProfileIntent(mActivity, profile.type, profile.profileId)
                mActivity.startActivity(intent)
            }
            holder.itemView.setOnLongClickListener { v ->
                val popupMenu = PopupMenu(mActivity, v)
                popupMenu.setForceShowIcon(true)
                popupMenu.inflate(R.menu.activity_profiles_popup_actions)
                popupMenu.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_apply -> {
                            val intent = ProfileApplierActivity.getApplierIntent(mActivity, profile.profileId)
                            mActivity.startActivity(intent)
                        }
                        R.id.action_delete -> {
                            MaterialAlertDialogBuilder(mActivity)
                                .setTitle(mActivity.getString(R.string.delete_filename, profile.name))
                                .setMessage(R.string.are_you_sure)
                                .setPositiveButton(R.string.cancel, null)
                                .setNegativeButton(R.string.ok) { _, _ ->
                                    if (ProfileManager.deleteProfile(profile.profileId)) {
                                        UIUtils.displayShortToast(R.string.deleted_successfully)
                                    } else {
                                        UIUtils.displayShortToast(R.string.deletion_failed)
                                    }
                                }
                                .show()
                        }
                        R.id.action_routine_ops -> UIUtils.displayShortToast("Not yet implemented")
                        R.id.action_duplicate -> {
                            TextInputDialogBuilder(mActivity, R.string.input_profile_name)
                                .setTitle(R.string.new_profile)
                                .setHelperText(R.string.input_profile_name_description)
                                .setNegativeButton(R.string.cancel, null)
                                .setPositiveButton(R.string.go) { _, _, newProfName, _ ->
                                    if (!TextUtils.isEmpty(newProfName)) {
                                        val intent = ProfileManager.getCloneProfileIntent(mActivity, profile.type, profile.profileId, newProfName.toString())
                                        mActivity.startActivity(intent)
                                    }
                                }
                                .show()
                        }
                        R.id.action_export -> {
                            mActivity.mProfileId = profile.profileId
                            mActivity.mExportProfile.launch("${profile.name}.am.json")
                        }
                        R.id.action_copy -> Utils.copyToClipboard(mActivity, profile.name, profile.profileId)
                        R.id.action_shortcut -> {
                            val shortcutTypesL = arrayOf(mActivity.getString(R.string.simple), mActivity.getString(R.string.advanced))
                            val shortcutTypes = arrayOf(ProfileApplierActivity.ST_SIMPLE, ProfileApplierActivity.ST_ADVANCED)
                            SearchableSingleChoiceDialogBuilder(mActivity, shortcutTypes.toList(), shortcutTypesL)
                                .setTitle(R.string.create_shortcut)
                                .setOnSingleChoiceClickListener { dialog, which, _, isChecked ->
                                    if (!isChecked) return@setOnSingleChoiceClickListener
                                    val icon = ContextCompat.getDrawable(mActivity, R.drawable.ic_launcher_foreground)!!
                                    val shortcutInfo = ProfileShortcutInfo(profile.profileId, profile.name, shortcutTypes[which], shortcutTypesL[which])
                                    shortcutInfo.icon = UIUtils.getBitmapFromDrawable(icon)
                                    val dialog1 = CreateShortcutDialogFragment.getInstance(shortcutInfo)
                                    dialog1.show(mActivity.supportFragmentManager, CreateShortcutDialogFragment.TAG)
                                    dialog.dismiss()
                                }
                                .show()
                        }
                        else -> return@setOnMenuItemClickListener false
                    }
                    true
                }
                popupMenu.show()
                true
            }
        }

        override fun getFilter(): Filter {
            if (mFilter == null) {
                mFilter = object : Filter() {
                    override fun performFiltering(charSequence: CharSequence): FilterResults {
                        val constraint = charSequence.toString().lowercase(Locale.ROOT)
                        mConstraint = constraint
                        val filterResults = FilterResults()
                        if (constraint.isEmpty()) {
                            filterResults.count = 0
                            filterResults.values = null
                            return filterResults
                        }
                        val list = mDefaultList!!.filter { it.name.lowercase(Locale.ROOT).contains(constraint) }
                        filterResults.count = list.size
                        filterResults.values = list.toTypedArray()
                        return filterResults
                    }

                    override fun publishResults(charSequence: CharSequence, filterResults: FilterResults) {
                        val previousCount = mAdapterList?.size ?: 0
                        mAdapterList = if (filterResults.values == null) mDefaultList else filterResults.values as Array<BaseProfile>
                        AdapterUtils.notifyDataSetChanged(this@ProfilesAdapter, previousCount, mAdapterList!!.size)
                    }
                }
            }
            return mFilter!!
        }
    }

    companion object {
        private const val TAG = "ProfilesActivity"
    }
}
