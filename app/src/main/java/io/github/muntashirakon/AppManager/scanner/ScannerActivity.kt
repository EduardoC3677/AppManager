// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.app.ActionBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.progressindicator.LinearProgressIndicator
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerActivity
import io.github.muntashirakon.AppManager.fm.FmProvider
import io.github.muntashirakon.AppManager.intercept.IntentCompat
import io.github.muntashirakon.AppManager.settings.FeatureController
import io.github.muntashirakon.AppManager.utils.FileUtils
import io.github.muntashirakon.AppManager.utils.IoUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import java.io.File
import java.io.FileNotFoundException

// Copyright 2015 Google, Inc.
class ScannerActivity : BaseActivity() {
    private var mActionBar: ActionBar? = null
    private var mProgressIndicator: LinearProgressIndicator? = null
    private var mFd: ParcelFileDescriptor? = null
    private var mApkUri: Uri? = null
    private var mIsExternalApk: Boolean = false

    override fun onDestroy() {
        FileUtils.deleteSilently(codeCacheDir)
        IoUtils.closeQuietly(mFd)
        super.onDestroy()
    }

    override fun onAuthenticated(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_fm)
        setSupportActionBar(findViewById(R.id.toolbar))
        val model = ViewModelProvider(this).get(ScannerViewModel::class.java)
        mActionBar = supportActionBar

        val intent = intent
        mIsExternalApk = intent.getBooleanExtra(EXTRA_IS_EXTERNAL, true)

        mProgressIndicator = findViewById(R.id.progress_linear)
        mProgressIndicator!!.visibilityAfterHide = View.GONE
        showProgress(true)

        mApkUri = IntentCompat.getDataUri(intent)
        if (mApkUri == null) {
            UIUtils.displayShortToast(R.string.error)
            finish()
            return
        }

        var apkFile: File? = null
        if (Intent.ACTION_VIEW == intent.action) {
            if (FmProvider.AUTHORITY != mApkUri!!.authority) {
                try {
                    mFd = FileUtils.getFdFromUri(this, mApkUri!!, "r")
                    apkFile = FileUtils.getFileFromFd(mFd!!)
                } catch (e: FileNotFoundException) {
                    e.printStackTrace()
                }
            }
        } else {
            val path = mApkUri!!.path
            if (path != null) apkFile = File(path)
        }

        model.setApkFile(mApkUri!!, apkFile?.name) // Pass apkUri and packageName (apkFile.name)
        model.startScan()

        supportFragmentManager
            .beginTransaction()
            .setCustomAnimations(
                R.animator.enter_from_left,
                R.animator.enter_from_right,
                R.animator.exit_from_right,
                R.animator.exit_from_left
            )
            .replace(R.id.main_layout, ScannerFragment())
            .commit()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_scanner, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_install).isVisible = mIsExternalApk && FeatureController.isInstallerEnabled()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        } else if (id == R.id.action_install) {
            if (mApkUri != null) {
                startActivity(PackageInstallerActivity.getLaunchableInstance(applicationContext, mApkUri!!))
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    fun setSubtitle(subtitle: CharSequence?) {
        mActionBar?.subtitle = subtitle
    }

    fun setSubtitle(@StringRes subtitle: Int) {
        mActionBar?.setSubtitle(subtitle)
    }

    fun showProgress(willShow: Boolean) {
        if (mProgressIndicator == null) return
        if (willShow) mProgressIndicator!!.show() else mProgressIndicator!!.hide()
    }

    fun loadNewFragment(fragment: Fragment) {
        supportFragmentManager
            .beginTransaction()
            .setCustomAnimations(
                R.animator.enter_from_left,
                R.animator.enter_from_right,
                R.animator.exit_from_right,
                R.animator.exit_from_left
            )
            .replace(R.id.main_layout, fragment)
            .addToBackStack(null)
            .commit()
    }

    companion object {
        const val EXTRA_IS_EXTERNAL = "is_external"
    }
}
