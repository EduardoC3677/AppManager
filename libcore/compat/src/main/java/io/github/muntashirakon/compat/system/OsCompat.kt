// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.compat.system

import android.system.ErrnoException
import android.system.StructPasswd
import androidx.annotation.Keep

@Keep
object OsCompat {
    // Lists the syscalls unavailable in Os

    @JvmField
    var UTIME_NOW: Long = 0
    @JvmField
    var UTIME_OMIT: Long = 0
    @JvmField
    var AT_FDCWD: Int = 0
    @JvmField
    var AT_SYMLINK_NOFOLLOW: Int = 0

    init {
        System.loadLibrary("am")
        setNativeConstants()
    }

    @JvmStatic
    private external fun setNativeConstants()

    @JvmStatic
    @Throws(ErrnoException::class)
    external fun setgrent()

    @JvmStatic
    @Throws(ErrnoException::class)
    external fun setpwent()

    @JvmStatic
    @Throws(ErrnoException::class)
    external fun getgrent(): StructGroup?

    @JvmStatic
    @Throws(ErrnoException::class)
    external fun getpwent(): StructPasswd?

    @JvmStatic
    @Throws(ErrnoException::class)
    external fun endgrent()

    @JvmStatic
    @Throws(ErrnoException::class)
    external fun endpwent()

    @JvmStatic
    @Throws(ErrnoException::class)
    external fun utimensat(dirfd: Int, pathname: String?, atime: StructTimespec?, mtime: StructTimespec?, flags: Int)
}
