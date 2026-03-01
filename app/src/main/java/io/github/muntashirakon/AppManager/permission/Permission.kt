// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission

import android.os.Build
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat
import io.github.muntashirakon.AppManager.compat.PermissionCompat
import io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_GRANTED_BY_DEFAULT
import io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_POLICY_FIXED
import io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_REVIEW_REQUIRED
import io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_REVOKED_COMPAT
import io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_REVOKE_ON_UPGRADE
import io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_SYSTEM_FIXED
import io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_USER_FIXED
import io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_USER_SET

// Copyright (C) 2015 The Android Open Source Project
open class Permission(
    private val mName: String,
    private var mGranted: Boolean,
    private val mAppOp: Int,
    private var mAppOpAllowed: Boolean,
    @PermissionCompat.PermissionFlags private var mFlags: Int
) {
    var isRuntime: Boolean = true
        internal set
    var isReadOnly: Boolean = false
        internal set

    fun isReadOnlyIncludingFixed(): Boolean {
        return isReadOnly || isSystemFixed()
    }

    fun getName(): String = mName

    fun getAppOp(): Int = mAppOp

    @PermissionCompat.PermissionFlags
    fun getFlags(): Int = mFlags

    fun hasAppOp(): Boolean = mAppOp != AppOpsManagerCompat.OP_NONE

    /**
     * Does this permission affect app ops.
     *
     * <p>I.e. does this permission have a matching app op or is this a background permission. All
     * background permissions affect the app op if it's assigned foreground permission.
     *
     * @return {@code true} if this permission affects app ops
     */
    fun affectsAppOp(): Boolean = mAppOp != AppOpsManagerCompat.OP_NONE

    fun isGranted(): Boolean = mGranted

    fun isGrantedIncludingAppOp(): Boolean {
        return mGranted && !isReviewRequired() && (!affectsAppOp() || isAppOpAllowed())
    }

    fun isReviewRequired(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            (mFlags and FLAG_PERMISSION_REVIEW_REQUIRED) != 0
        } else false
    }

    fun resetReviewRequired() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mFlags = mFlags and FLAG_PERMISSION_REVIEW_REQUIRED.inv()
        }
    }

    fun unsetReviewRequired() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mFlags = mFlags and FLAG_PERMISSION_REVIEW_REQUIRED.inv()
        }
    }

    fun setGranted(granted: Boolean) {
        mGranted = granted
    }

    fun isAppOpAllowed(): Boolean = mAppOpAllowed

    fun isUserFixed(): Boolean = (mFlags and FLAG_PERMISSION_USER_FIXED) != 0

    fun setUserFixed(userFixed: Boolean) {
        mFlags = if (userFixed) {
            mFlags or FLAG_PERMISSION_USER_FIXED
        } else {
            mFlags and FLAG_PERMISSION_USER_FIXED.inv()
        }
    }

    fun isSystemFixed(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            (mFlags and FLAG_PERMISSION_SYSTEM_FIXED) != 0
        } else false
    }

    fun isPolicyFixed(): Boolean = (mFlags and FLAG_PERMISSION_POLICY_FIXED) != 0

    fun isUserSet(): Boolean = (mFlags and FLAG_PERMISSION_USER_SET) != 0

    fun isGrantedByDefault(): Boolean = (mFlags and FLAG_PERMISSION_GRANTED_BY_DEFAULT) != 0

    fun setUserSet(userSet: Boolean) {
        mFlags = if (userSet) {
            mFlags or FLAG_PERMISSION_USER_SET
        } else {
            mFlags and FLAG_PERMISSION_USER_SET.inv()
        }
    }

    fun setPolicyFixed(policyFixed: Boolean) {
        mFlags = if (policyFixed) {
            mFlags or FLAG_PERMISSION_POLICY_FIXED
        } else {
            mFlags and FLAG_PERMISSION_POLICY_FIXED.inv()
        }
    }

    fun shouldRevokeOnUpgrade(): Boolean = (mFlags and FLAG_PERMISSION_REVOKE_ON_UPGRADE) != 0

    fun setRevokeOnUpgrade(revokeOnUpgrade: Boolean) {
        mFlags = if (revokeOnUpgrade) {
            mFlags or FLAG_PERMISSION_REVOKE_ON_UPGRADE
        } else {
            mFlags and FLAG_PERMISSION_REVOKE_ON_UPGRADE.inv()
        }
    }

    fun isRevokedCompat(): Boolean = (mFlags and FLAG_PERMISSION_REVOKED_COMPAT) != 0

    fun setRevokedCompat(revokedCompat: Boolean) {
        mFlags = if (revokedCompat) {
            mFlags or FLAG_PERMISSION_REVOKED_COMPAT
        } else {
            mFlags and FLAG_PERMISSION_REVOKED_COMPAT.inv()
        }
    }

    fun setAppOpAllowed(appOpAllowed: Boolean) {
        mAppOpAllowed = appOpAllowed
    }
}
