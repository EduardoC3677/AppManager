// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details

import android.annotation.UserIdInt
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.TypedArray
import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.core.os.BundleCompat
import androidx.core.os.ParcelCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import com.google.android.material.tabs.TabLayoutMediator
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.apk.ApkSource
import io.github.muntashirakon.AppManager.compat.UserHandleHidden
import io.github.muntashirakon.AppManager.details.info.AppInfoFragment
import io.github.muntashirakon.AppManager.intercept.IntentCompat
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.main.MainActivity
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView
import io.github.muntashirakon.AppManager.self.SelfUriManager
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import io.github.muntashirakon.util.UiUtils
import java.util.*

@AndroidEntryPoint
class AppDetailsActivity : BaseActivity() {
    var model: AppDetailsViewModel? = null
    var searchView: AdvancedSearchView? = null

    private var mViewPager: ViewPager2? = null
    private var mTabTitleIds: TypedArray? = null
    private var mTabFragments: Array<Fragment?>? = null

    private var mBackToMainPage: Boolean = false
    private var mPackageName: String? = null
    private var mApkSource: ApkSource? = null
    private var mApkType: String? = null
    @UserIdInt
    private var mUserId: Int = UserHandleHidden.myUserId()

    override fun onAuthenticated(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_app_details)
        setSupportActionBar(findViewById(R.id.toolbar))
        title = "…"\nmodel = ViewModelProvider(this).get(AppDetailsViewModel::class.java)
        val ss = savedInstanceState?.let { BundleCompat.getParcelable(it, "ss", SavedState::class.java) }
        if (ss != null) {
            mBackToMainPage = ss.mBackToMainPage
            mPackageName = ss.mPackageName
            mApkSource = ss.mApkSource
            mApkType = ss.mApkType
            mUserId = ss.mUserId
        } else {
            val intent = intent
            mBackToMainPage = intent.getBooleanExtra(EXTRA_BACK_TO_MAIN, mBackToMainPage)
            val pair = SelfUriManager.getUserPackagePairFromUri(intent.data)
            if (pair != null) {
                mPackageName = pair.packageName
                mApkSource = null
                mUserId = pair.userId
            } else {
                mPackageName = getPackageNameFromExtras(intent)
                mApkSource = getApkSource(intent)
                mUserId = intent.getIntExtra(EXTRA_USER_HANDLE, UserHandleHidden.myUserId())
            }
            mApkType = intent.type
        }
        model!!.setUserId(mUserId)
        mTabTitleIds = resources.obtainTypedArray(R.array.TAB_TITLES)
        mTabFragments = arrayOfNulls(mTabTitleIds!!.length())
        if (mPackageName == null && mApkSource == null) {
            UIUtils.displayLongToast(R.string.empty_package_name)
            finish()
            return
        }
        supportActionBar?.let {
            it.setDisplayShowCustomEnabled(true)
            searchView = UIUtils.setupAdvancedSearchView(it, null)
        }
        mViewPager = findViewById(R.id.pager)
        val tabLayout: TabLayout = findViewById(R.id.tab_layout)
        UiUtils.applyWindowInsetsAsPadding(tabLayout, false, true)
        val progressDialog = UIUtils.getProgressDialog(this, getText(R.string.loading), true)
        if (mPackageName == null) progressDialog.show()
        mViewPager!!.offscreenPageLimit = 4
        mViewPager!!.adapter = AppDetailsFragmentPagerAdapter(this)
        TabLayoutMediator(tabLayout, mViewPager!!) { tab, position -> tab.text = mTabTitleIds!!.getString(position) }.attach()
        val ld = if (mPackageName != null) model!!.setPackage(mPackageName!!) else model!!.setPackage(mApkSource!!)
        ld.observe(this) { packageInfo ->
            progressDialog.dismiss()
            if (packageInfo == null) {
                UIUtils.displayShortToast(R.string.failed_to_fetch_package_info)
                if (!isDestroyed) finish()
                return@observe
            }
            title = packageInfo.applicationInfo.loadLabel(packageManager)
        }
        model!!.isPackageExistLiveData.observe(this) { isPackageExist ->
            if (!isPackageExist) {
                if (!model!!.isExternalApk) UIUtils.displayShortToast(R.string.app_not_installed)
                finish()
            }
        }
        model!!.userInfo.observe(this) { userInfo ->
            supportActionBar?.subtitle = getString(R.string.user_profile_with_id, userInfo.name, userInfo.id)
        }
        model!!.isPackageChanged().observe(this) { isPackageChanged ->
            if (isPackageChanged && model!!.isPackageExist) loadTabs()
        }
    }

    private fun getPackageNameFromExtras(intent: Intent): String? {
        var pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: intent.getStringExtra("pkg")
        return pkg?.let { Paths.sanitizeFilename(it) }
    }

    private fun getApkSource(intent: Intent): ApkSource? {
        val uri = intent.data
        return uri?.let { ApkSource.getApkSource(it, intent.type) }
            ?: IntentCompat.getUnwrappedParcelableExtra(intent, EXTRA_APK_SOURCE, ApkSource::class.java)
    }

    class SavedState : Parcelable {
        var mBackToMainPage: Boolean = false
        var mPackageName: String? = null
        var mApkSource: ApkSource? = null
        var mApkType: String? = null
        var mUserId: Int = 0

        constructor()
        constructor(source: Parcel) {
            mBackToMainPage = ParcelCompat.readBoolean(source)
            mPackageName = source.readString()
            mApkSource = ParcelCompat.readParcelable(source, ApkSource::class.java.classLoader, ApkSource::class.java)
            mApkType = source.readString()
            mUserId = source.readInt()
        }

        override fun describeContents(): Int = 0
        override fun writeToParcel(dest: Parcel, flags: Int) {
            ParcelCompat.writeBoolean(dest, mBackToMainPage)
            dest.writeString(mPackageName)
            dest.writeParcelable(mApkSource, flags)
            dest.writeString(mApkType)
            dest.writeInt(mUserId)
        }

        companion object {
            @JvmField
            val CREATOR = object : Parcelable.ClassLoaderCreator<SavedState> {
                override fun createFromParcel(source: Parcel, loader: ClassLoader?): SavedState = SavedState(source)
                override fun createFromParcel(source: Parcel): SavedState = SavedState(source)
                override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (mApkSource != null || mPackageName != null) {
            val ss = SavedState().apply {
                mBackToMainPage = this@AppDetailsActivity.mBackToMainPage
                mPackageName = this@AppDetailsActivity.mPackageName
                mApkSource = this@AppDetailsActivity.mApkSource
                mApkType = this@AppDetailsActivity.mApkType
                mUserId = this@AppDetailsActivity.mUserId
            }
            outState.putParcelable("ss", ss)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            if (mBackToMainPage) startActivity(Intent(this, MainActivity::class.java))
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadTabs() {
        val id = mViewPager!!.currentItem
        Log.d("ADA - " + mTabTitleIds!!.getText(id), "isPackageChanged called")
        for (i in 0 until mTabTitleIds!!.length()) model!!.load(i)
    }

    private inner class AppDetailsFragmentPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
        override fun createFragment(position: Int): Fragment {
            mTabFragments!![position]?.let { return it }
            val fragment = when (position) {
                AppDetailsFragment.APP_INFO -> AppInfoFragment()
                AppDetailsFragment.ACTIVITIES, AppDetailsFragment.SERVICES, AppDetailsFragment.RECEIVERS, AppDetailsFragment.PROVIDERS -> {
                    AppDetailsComponentsFragment().apply { arguments = Bundle().apply { putInt(AppDetailsFragment.ARG_TYPE, position) } }
                }
                AppDetailsFragment.APP_OPS, AppDetailsFragment.PERMISSIONS, AppDetailsFragment.USES_PERMISSIONS -> {
                    AppDetailsPermissionsFragment().apply { arguments = Bundle().apply { putInt(AppDetailsFragment.ARG_TYPE, position) } }
                }
                AppDetailsFragment.CONFIGURATIONS, AppDetailsFragment.FEATURES, AppDetailsFragment.SHARED_LIBRARIES, AppDetailsFragment.SIGNATURES -> {
                    AppDetailsOtherFragment().apply { arguments = Bundle().apply { putInt(AppDetailsFragment.ARG_TYPE, position) } }
                }
                AppDetailsFragment.OVERLAYS -> {
                    AppDetailsOverlaysFragment().apply { arguments = Bundle().apply { putInt(AppDetailsFragment.ARG_TYPE, position) } }
                }
                else -> throw IllegalStateException("Invalid position")
            }
            mTabFragments!![position] = fragment
            return fragment
        }

        override fun getItemCount(): Int = mTabTitleIds!!.length()
    }

    companion object {
        const val ALIAS_APP_INFO = "io.github.muntashirakon.AppManager.details.AppInfoActivity"\nprivate const val EXTRA_PACKAGE_NAME = "android.intent.extra.PACKAGE_NAME"\nprivate const val EXTRA_APK_SOURCE = "src"\nprivate const val EXTRA_USER_HANDLE = "user"\nprivate const val EXTRA_BACK_TO_MAIN = "main"

        @JvmStatic
        fun getIntent(context: Context, packageName: String, @UserIdInt userId: Int): Intent {
            return Intent(context, AppDetailsActivity::class.java).apply {
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_USER_HANDLE, userId)
            }
        }

        @JvmStatic
        fun getIntent(context: Context, packageName: String, @UserIdInt userId: Int, backToMainPage: Boolean): Intent {
            return getIntent(context, packageName, userId).apply { putExtra(EXTRA_BACK_TO_MAIN, backToMainPage) }
        }

        @JvmStatic
        fun getIntent(context: Context, apkSource: ApkSource, backToMainPage: Boolean): Intent {
            return Intent(context, AppDetailsActivity::class.java).apply {
                IntentCompat.putWrappedParcelableExtra(this, EXTRA_APK_SOURCE, apkSource)
                putExtra(EXTRA_BACK_TO_MAIN, backToMainPage)
            }
        }

        @JvmStatic
        fun getIntent(context: Context, apkPath: Path, backToMainPage: Boolean): Intent {
            return getIntent(context, apkPath.uri, apkPath.type, backToMainPage)
        }

        @JvmStatic
        fun getIntent(context: Context, apkPath: Uri, mimeType: String?, backToMainPage: Boolean): Intent {
            return Intent(context, AppDetailsActivity::class.java).apply {
                if (mimeType != null) setDataAndType(apkPath, mimeType) else data = apkPath
                putExtra(EXTRA_BACK_TO_MAIN, backToMainPage)
            }
        }
    }
}
