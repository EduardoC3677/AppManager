// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import io.github.muntashirakon.AppManager.self.SelfPermissions

class StoragePermission private constructor(caller: ActivityResultCaller) {

    fun interface StoragePermissionCallback {
        fun onResult(granted: Boolean)
    }

    private var mStoragePerm: BetterActivityResult<String, Boolean>? = null

    @RequiresApi(api = Build.VERSION_CODES.R)
    private var mStoragePermApi30: BetterActivityResult<Intent, ActivityResult>? = null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mStoragePermApi30 = BetterActivityResult.registerForActivityResult(
                caller,
                ActivityResultContracts.StartActivityForResult()
            )
        } else {
            mStoragePerm = BetterActivityResult.registerForActivityResult(
                caller,
                ActivityResultContracts.RequestPermission()
            )
        }
    }

    @Suppress("InlinedApi")
    fun request(callback: StoragePermissionCallback?) {
        if (SelfPermissions.checkSelfStoragePermission()) {
            callback?.onResult(true)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mStoragePermApi30?.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) { _: ActivityResult ->
                callback?.onResult(Environment.isExternalStorageManager())
            }
        } else {
            mStoragePerm?.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) { _: Boolean ->
                callback?.onResult(SelfPermissions.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE))
            }
        }
    }

    fun request() {
        request(null)
    }

    companion object {
        @JvmStatic
        fun init(caller: ActivityResultCaller): StoragePermission {
            return StoragePermission(caller)
        }
    }
}
