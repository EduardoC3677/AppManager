// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.manifest

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.annotation.UiThread
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.progressindicator.LinearProgressIndicator
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.apk.ApkSource
import io.github.muntashirakon.AppManager.editor.CodeEditorFragment
import io.github.muntashirakon.AppManager.intercept.IntentCompat
import io.github.muntashirakon.AppManager.utils.UIUtils

class ManifestViewerActivity : BaseActivity() {
    private var mModel: ManifestViewerViewModel? = null

    @SuppressLint("WrongConstant")
    override fun onAuthenticated(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_code_editor)
        setSupportActionBar(findViewById(R.id.toolbar))
        mModel = ViewModelProvider(this).get(ManifestViewerViewModel::class.java)
        val progressIndicator: LinearProgressIndicator = findViewById(R.id.progress_linear)
        progressIndicator.visibilityAfterHide = View.GONE
        val intent = intent
        val packageUri = IntentCompat.getDataUri(intent)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        if (packageUri == null && packageName == null) {
            showErrorAndFinish()
            return
        }
        val apkSource = packageUri?.let { ApkSource.getApkSource(it, intent.type) }
        mModel!!.manifestLiveData.observe(this) { manifest ->
            val options = CodeEditorFragment.Options.Builder()
                .setTitle(getString(R.string.manifest_viewer))
                .setSubtitle("AndroidManifest.xml")
                .setReadOnly(true)
                .setUri(manifest)
                .setJavaSmaliToggle(false)
                .setEnableSharing(true)
                .build()
            val fragment = CodeEditorFragment()
            val args = Bundle()
            args.putParcelable(CodeEditorFragment.ARG_OPTIONS, options)
            fragment.arguments = args
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.container, fragment)
                .commit()
        }
        mModel!!.loadApkFile(apkSource, packageName)
    }

    @UiThread
    private fun showErrorAndFinish() {
        UIUtils.displayShortToast(R.string.error)
        finish()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "pkg"
    }
}
