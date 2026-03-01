// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner

import androidx.annotation.WorkerThread
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object Pithus {
    private const val BASE_URL = "https://beta.pithus.org/report"

    @JvmStatic
    @WorkerThread
    @Throws(IOException::class)
    fun resolveReport(sha256Sum: String): String? {
        val url = URL("$BASE_URL${File.separator}$sha256Sum")
        val connection = url.openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.useCaches = false
        connection.requestMethod = "GET"
        return if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            url.toString()
        } else null
    }
}
