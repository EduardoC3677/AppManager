// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.annotation.CallSuper
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.progressindicator.LinearProgressIndicator
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.profiles.struct.BaseProfile
import io.github.muntashirakon.AppManager.shortcut.CreateShortcutDialogFragment
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.util.UiUtils
import java.util.*

abstract class AppsBaseProfileActivity : BaseActivity(), NavigationBarView.OnItemSelectedListener {
    private var mViewPager: ViewPager2? = null
    lateinit var bottomNavigationView: NavigationBarView
    private var mPrevMenuItem: MenuItem? = null
    private val mFragments = arrayOfNulls<Fragment>(3)
    private val mPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            mPrevMenuItem?.isChecked = false ?: run {
                bottomNavigationView.menu.getItem(0).isChecked = false
            }
            bottomNavigationView.menu.getItem(position).isChecked = true
            mPrevMenuItem = bottomNavigationView.menu.getItem(position)
        }
    }
    private val mOnBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (model.isModified()) {
                MaterialAlertDialogBuilder(this@AppsBaseProfileActivity)
                    .setTitle(R.string.exit_confirmation)
                    .setMessage(R.string.profile_modified_are_you_sure)
                    .setPositiveButton(R.string.no, null)
                    .setNegativeButton(R.string.yes) { _, _ ->
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                    .setNeutralButton(R.string.save_and_exit) { _, _ ->
                        model.save(true)
                        isEnabled = false
                    }
                    .show()
                return
            }
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
        }
    }
    protected var profileId: String? = null
    lateinit var model: AppsProfileViewModel
    lateinit var fab: FloatingActionButton
    lateinit var progressIndicator: LinearProgressIndicator

    abstract fun getAppsBaseFragment(): Fragment

    abstract fun loadNewProfile(newProfileName: String, intent: Intent)

    @CallSuper
    override fun onAuthenticated(savedInstanceState: Bundle?) {
        model = ViewModelProvider(this).get(AppsProfileViewModel::class.java)
        setContentView(R.layout.activity_apps_profile)
        setSupportActionBar(findViewById(R.id.toolbar))
        onBackPressedDispatcher.addCallback(this, mOnBackPressedCallback)
        progressIndicator = findViewById(R.id.progress_linear)
        progressIndicator.visibilityAfterHide = View.GONE
        fab = findViewById(R.id.floatingActionButton)
        UiUtils.applyWindowInsetsAsMargin(fab)
        val newProfileName = intent.getStringExtra(EXTRA_NEW_PROFILE_NAME)
        profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
        if (profileId == null && newProfileName == null) {
            finish()
            return
        }
        if (newProfileName != null) {
            if (profileId != null) {
                model.loadAndCloneProfile(profileId!!, newProfileName)
            } else {
                loadNewProfile(newProfileName, intent)
            }
        } else {
            model.loadProfile(profileId!!)
        }
        mViewPager = findViewById(R.id.pager)
        mViewPager!!.offscreenPageLimit = 2
        mViewPager!!.registerOnPageChangeCallback(mPageChangeCallback)
        mViewPager!!.adapter = ProfileFragmentPagerAdapter(this)
        bottomNavigationView = findViewById(R.id.bottom_navigation)
        bottomNavigationView.setOnItemSelectedListener(this)
        model.getProfileModifiedLiveData().observe(this) { modified ->
            mOnBackPressedCallback.isEnabled = modified
            supportActionBar?.let {
                val name = (if (modified) "* " else "") + model.getProfileName()
                it.title = name
            }
        }
        model.observeToast().observe(this) { stringResAndIsFinish ->
            UIUtils.displayShortToast(stringResAndIsFinish.first)
            if (stringResAndIsFinish.second) {
                onBackPressedDispatcher.onBackPressed()
            }
        }
        model.observeProfileLoaded().observe(this) { profileName ->
            title = profileName
            progressIndicator.hide()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.fragment_profile_apps_actions, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.action_apply -> {
                val intent = ProfileApplierActivity.getApplierIntent(this, model.getProfileName()!!)
                startActivity(intent)
                true
            }
            R.id.action_save -> {
                model.save(false)
                true
            }
            R.id.action_discard -> {
                model.discard()
                true
            }
            R.id.action_delete -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.delete_filename, model.getProfileName()))
                    .setMessage(R.string.are_you_sure)
                    .setPositiveButton(R.string.cancel, null)
                    .setNegativeButton(R.string.ok) { _, _ -> model.delete() }
                    .show()
                true
            }
            R.id.action_duplicate -> {
                TextInputDialogBuilder(this, R.string.input_profile_name)
                    .setTitle(R.string.new_profile)
                    .setHelperText(R.string.input_profile_name_description)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.go) { _, _, profName, _ ->
                        if (TextUtils.isEmpty(profName)) {
                            UIUtils.displayShortToast(R.string.failed_to_duplicate_profile)
                            return@setPositiveButton
                        }
                        progressIndicator.show()
                        model.cloneProfile(profName.toString())
                    }
                    .show()
                true
            }
            R.id.action_shortcut -> {
                val shortcutTypesL = arrayOf(getString(R.string.simple), getString(R.string.advanced))
                val shortcutTypes = arrayOf(ProfileApplierActivity.ST_SIMPLE, ProfileApplierActivity.ST_ADVANCED)
                SearchableSingleChoiceDialogBuilder(this, shortcutTypes.toList(), shortcutTypesL)
                    .setTitle(R.string.create_shortcut)
                    .setOnSingleChoiceClickListener { dialog, which, _, isChecked ->
                        if (!isChecked) return@setOnSingleChoiceClickListener
                        val icon = ContextCompat.getDrawable(this, R.drawable.ic_launcher_foreground)!!
                        val shortcutInfo = ProfileShortcutInfo(model.getProfileId()!!,
                            model.getProfileName()!!, shortcutTypes[which], shortcutTypesL[which])
                        shortcutInfo.icon = UIUtils.getBitmapFromDrawable(icon)
                        val dialog1 = CreateShortcutDialogFragment.getInstance(shortcutInfo)
                        dialog1.show(supportFragmentManager, CreateShortcutDialogFragment.TAG)
                        dialog.dismiss()
                    }
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        mViewPager?.unregisterOnPageChangeCallback(mPageChangeCallback)
        super.onDestroy()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_apps, R.id.action_preview -> {
                mViewPager!!.setCurrentItem(0, true)
                true
            }
            R.id.action_conf -> {
                mViewPager!!.setCurrentItem(1, true)
                true
            }
            R.id.action_logs -> {
                mViewPager!!.setCurrentItem(2, true)
                true
            }
            else -> false
        }
    }

    private inner class ProfileFragmentPagerAdapter(fragmentActivity: androidx.fragment.app.FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
        override fun createFragment(position: Int): Fragment {
            return mFragments[position] ?: when (position) {
                0 -> getAppsBaseFragment().also { mFragments[0] = it }
                1 -> ConfFragment().also { mFragments[1] = it }
                2 -> LogViewerFragment().also { mFragments[2] = it }
                else -> throw IllegalStateException("Invalid position")
            }
        }

        override fun getItemCount(): Int = mFragments.size
    }

    companion object {
        protected const val EXTRA_NEW_PROFILE_NAME = "new_prof"\nprotected const val EXTRA_PROFILE_ID = "prof"\nprotected const val EXTRA_STATE = "state"
    }
}
