// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.google.android.material.button.MaterialButton
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.accessibility.activity.LeadingActivityTrackerActivity
import io.github.muntashirakon.AppManager.editor.CodeEditorActivity
import io.github.muntashirakon.AppManager.fm.FmActivity
import io.github.muntashirakon.AppManager.history.ops.OpHistoryActivity
import io.github.muntashirakon.AppManager.intercept.ActivityInterceptor
import io.github.muntashirakon.AppManager.logcat.LogViewerActivity
import io.github.muntashirakon.AppManager.settings.FeatureController
import io.github.muntashirakon.AppManager.sysconfig.SysConfigActivity
import io.github.muntashirakon.AppManager.terminal.TermActivity
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes
import io.github.muntashirakon.widget.FlowLayout

class LabsActivity : BaseActivity() {
    override fun onAuthenticated(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_labs)
        setSupportActionBar(findViewById(R.id.toolbar))
        val flowLayout: FlowLayout = findViewById(R.id.action_container)
        if (FeatureController.isLogViewerEnabled()) {
            addAction(this, flowLayout, R.string.log_viewer, R.drawable.ic_view_list).setOnClickListener {
                startActivity(Intent(this, LogViewerActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            }
        }
        addAction(this, flowLayout, R.string.sys_config, R.drawable.ic_hammer_wrench).setOnClickListener {
            startActivity(Intent(this, SysConfigActivity::class.java))
        }
        if (FeatureController.isTerminalEnabled()) {
            addAction(this, flowLayout, R.string.title_terminal_emulator, R.drawable.ic_frost_termux).setOnClickListener {
                startActivity(Intent(this, TermActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            }
        }
        addAction(this, flowLayout, R.string.files, R.drawable.ic_file_document_multiple).setOnClickListener {
            startActivity(Intent(this, FmActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }
        addAction(this, flowLayout, R.string.title_ui_tracker, R.drawable.ic_cursor_default_click).setOnClickListener {
            startActivity(Intent(this, LeadingActivityTrackerActivity::class.java))
        }
        if (FeatureController.isInterceptorEnabled()) {
            addAction(this, flowLayout, R.string.interceptor, R.drawable.ic_transit_connection).setOnClickListener {
                startActivity(Intent(this, ActivityInterceptor::class.java))
            }
        }
        if (FeatureController.isCodeEditorEnabled()) {
            addAction(this, flowLayout, R.string.title_code_editor, R.drawable.ic_code).setOnClickListener {
                startActivity(Intent(this, CodeEditorActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            }
        }
        addAction(this, flowLayout, R.string.op_history, R.drawable.ic_history).setOnClickListener {
            startActivity(Intent(this, OpHistoryActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun addAction(context: Context, parent: ViewGroup, @StringRes stringResId: Int, @DrawableRes iconResId: Int): MaterialButton {
        val button = LayoutInflater.from(context).inflate(R.layout.item_app_info_action, parent, false) as MaterialButton
        button.backgroundTintList = ColorStateList.valueOf(ColorCodes.getListItemColor1(context))
        button.setText(stringResId)
        button.setIconResource(iconResId)
        parent.addView(button)
        return button
    }
}
