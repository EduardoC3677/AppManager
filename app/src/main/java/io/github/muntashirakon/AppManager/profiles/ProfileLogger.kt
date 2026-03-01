// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles

import io.github.muntashirakon.AppManager.logs.Logger
import io.github.muntashirakon.io.Paths
import java.io.File
import java.io.IOException

class ProfileLogger @Throws(IOException::class) constructor(profileId: String) :
    Logger(getLogFile(profileId), true) {

    companion object {
        @JvmStatic
        fun getLogFile(profileId: String): File {
            return File(getLoggingDirectory(), "profile_$profileId.log")
        }

        @JvmStatic
        fun getAllLogs(profileId: String): String {
            return Paths.get(getLogFile(profileId)).contentAsString
        }

        @JvmStatic
        fun clearLogs(profileId: String) {
            getLogFile(profileId).delete()
        }
    }
}
