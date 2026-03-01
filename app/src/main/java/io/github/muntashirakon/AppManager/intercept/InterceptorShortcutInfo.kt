// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.intercept

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.os.PersistableBundle
import androidx.core.os.ParcelCompat
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.shortcut.ShortcutInfo

class InterceptorShortcutInfo : ShortcutInfo {
    private val intent: Intent

    constructor(intent: Intent) : super() {
        this.intent = Intent(intent)
        fixIntent(this.intent)
    }

    protected constructor(`in`: Parcel) : super(`in`) {
        intent = ParcelCompat.readParcelable(`in`, Intent::class.java.classLoader, Intent::class.java)!!
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)
        dest.writeParcelable(intent, flags)
    }

    override fun toShortcutIntent(context: Context): Intent = intent

    companion object {
        val TAG: String = InterceptorShortcutInfo::class.java.simpleName

        @JvmField
        val CREATOR: Parcelable.Creator<InterceptorShortcutInfo> = object : Parcelable.Creator<InterceptorShortcutInfo> {
            override fun createFromParcel(source: Parcel): InterceptorShortcutInfo = InterceptorShortcutInfo(source)
            override fun newArray(size: Int): Array<InterceptorShortcutInfo?> = arrayOfNulls(size)
        }

        private fun fixIntent(intent: Intent) {
            val extras = intent.extras ?: return
            for (key in extras.keySet()) {
                val value = extras.get(key)
                if (!isValidType(value)) {
                    Log.w(TAG, "Removing unsupported key %s (class: %s, value: %s)", key, value?.javaClass?.name ?: "null", value)
                    intent.removeExtra(key)
                }
            }
        }

        private fun isValidType(value: Any?): Boolean {
            return value is Int || value is Long || value is Double || value is String ||
                    value is IntArray || value is LongArray || value is DoubleArray || value is Array<*> && value.isArrayOf<String>() ||
                    value is PersistableBundle || value == null || value is Boolean || value is BooleanArray
        }
    }
}
