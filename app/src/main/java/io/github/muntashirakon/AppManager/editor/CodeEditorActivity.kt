// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.progressindicator.LinearProgressIndicator
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.intercept.IntentCompat
import io.github.muntashirakon.io.Paths

class CodeEditorActivity : BaseActivity() {
    override fun onAuthenticated(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_code_editor)
        setSupportActionBar(findViewById(R.id.toolbar))
        val progressIndicator: LinearProgressIndicator = findViewById(R.id.progress_linear)
        progressIndicator.visibilityAfterHide = View.GONE
        val title = intent.getStringExtra(Intent.EXTRA_TITLE) ?: getString(R.string.title_code_editor)
        var subtitle = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        val fileUri = IntentCompat.getDataUri(intent)
        val readOnly = intent.getBooleanExtra(EXTRA_READ_ONLY, false)
        if (subtitle == null) {
            subtitle = if (fileUri != null) Paths.trimPathExtension(fileUri.lastPathSegment) else "Untitled.txt"
        }
        if (fileUri == null) progressIndicator.hide()
        val options = CodeEditorFragment.Options.Builder()
            .setUri(fileUri).setTitle(title).setSubtitle(subtitle)
            .setEnableSharing(false).setJavaSmaliToggle(false).setReadOnly(readOnly).build()
        val fragment = CodeEditorFragment().apply { arguments = Bundle().apply { putParcelable(CodeEditorFragment.ARG_OPTIONS, options) } }
        supportFragmentManager.beginTransaction().replace(R.id.container, fragment).commit()
        supportActionBar?.let { it.title = title; it.subtitle = subtitle }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val ALIAS_EDITOR = "io.github.muntashirakon.AppManager.editor.EditorActivity"
        private const val EXTRA_READ_ONLY = "read_only"

        @JvmStatic
        fun getIntent(context: Context, uri: Uri, title: String?, subtitle: String?, readOnly: Boolean): Intent {
            return Intent(context, CodeEditorActivity::class.java).apply {
                data = uri; putExtra(EXTRA_READ_ONLY, readOnly); putExtra(Intent.EXTRA_TITLE, title); putExtra(Intent.EXTRA_SUBJECT, subtitle)
            }
        }

        @JvmStatic
        fun getIntent(context: Context, uri: Uri, title: String?, subtitle: String?): Intent {
            return Intent(context, CodeEditorActivity::class.java).apply {
                data = uri; putExtra(Intent.EXTRA_TITLE, title); putExtra(Intent.EXTRA_SUBJECT, subtitle)
            }
        }
    }
}
