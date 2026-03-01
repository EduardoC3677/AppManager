// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto.auth

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.R

class AuthManagerActivity : BaseActivity() {
    private var mAuthKeyLayout: TextInputLayout? = null
    private var mAuthKeyField: TextInputEditText? = null

    override fun onAuthenticated(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_auth_management)
        setSupportActionBar(findViewById(R.id.toolbar))
        findViewById<View>(R.id.progress_linear).visibility = View.GONE
        mAuthKeyLayout = findViewById(R.id.auth_field)
        mAuthKeyField = findViewById(android.R.id.text1)
        mAuthKeyField!!.setText(AuthManager.getKey())
        mAuthKeyLayout!!.setEndIconOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.regenerate_auth_key)
                .setMessage(R.string.regenerate_auth_key_warning)
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes) { _, _ ->
                    val authKey = AuthManager.generateKey()
                    AuthManager.setKey(authKey)
                    mAuthKeyField!!.setText(authKey)
                }
                .show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
