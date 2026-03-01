// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat

import android.os.Build
import androidx.biometric.BiometricManager.Authenticators

class BiometricAuthenticatorsCompat {
    class Builder {
        private var mAllowWeak = false
        private var mAllowStrong = false
        private var mAllowDeviceCredential = false
        private var mDeviceCredentialOnly = false

        fun allowEverything(allow: Boolean): Builder {
            mAllowWeak = allow
            mAllowDeviceCredential = allow
            return this
        }

        fun allowWeakBiometric(allow: Boolean): Builder {
            mAllowWeak = allow
            return this
        }

        fun allowStrongBiometric(allow: Boolean): Builder {
            mAllowStrong = allow
            return this
        }

        fun allowDeviceCredential(allow: Boolean): Builder {
            mAllowDeviceCredential = allow
            return this
        }

        fun deviceCredentialOnly(only: Boolean): Builder {
            mDeviceCredentialOnly = only
            return this
        }

        fun build(): Int {
            if (mDeviceCredentialOnly) {
                return deviceCredentialOnlyFlags
            }
            var flags: Int = if (mAllowWeak) {
                Authenticators.BIOMETRIC_WEAK
            } else if (mAllowStrong) {
                Authenticators.BIOMETRIC_STRONG
            } else {
                0
            }
            if (mAllowDeviceCredential) {
                if (flags == 0) {
                    return deviceCredentialOnlyFlags
                }
                if (flags == Authenticators.BIOMETRIC_STRONG && (
                            Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
                                    Build.VERSION.SDK_INT > Build.VERSION_CODES.Q)) {
                    flags = Authenticators.BIOMETRIC_WEAK
                }
                return flags or Authenticators.DEVICE_CREDENTIAL
            }
            return flags
        }

        private val deviceCredentialOnlyFlags: Int
            get() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    return Authenticators.DEVICE_CREDENTIAL
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    return Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL
                }
                return Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL
            }
    }
}
