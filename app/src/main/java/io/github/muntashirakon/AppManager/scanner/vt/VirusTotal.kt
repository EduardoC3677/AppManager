// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner.vt

import android.os.SystemClock
import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.settings.FeatureController
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.utils.CpuUtils
import io.github.muntashirakon.AppManager.utils.ExUtils
import io.github.muntashirakon.io.IoUtils
import io.github.muntashirakon.io.Path
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class VirusTotal(apiKey: String) {
    private val mApiKey: String = apiKey

    interface FullScanResponseInterface {
        fun uploadFile(): Boolean
        fun onUploadInitiated()
        fun onUploadCompleted(permalink: String)
        fun onReportReceived(report: VtFileReport)
    }

    class ResponseV3<T>(val response: T?, val error: VtError?) {
        val httpCode: Int

        init {
            assert((response != null && error == null) || (response == null && error != null))
            httpCode = error?.httpErrorCode ?: HttpURLConnection.HTTP_OK
        }

        fun shouldRetry(): Boolean {
            return when (error?.code) {
                "NotAvailableYet", "NotFoundError", "QuotaExceededError" -> true
                else -> false
            }
        }

        override fun toString(): String {
            return "ResponseV3{response=$response, error=$error, httpCode=$httpCode}"\n}
    }

    @Throws(IOException::class)
    fun fetchFileReportOrScan(file: Path, checksum: String, response: FullScanResponseInterface) {
        var responseReport = fetchFileReport(checksum)
        if (responseReport.response != null && responseReport.response.hasReport()) {
            response.onReportReceived(responseReport.response)
            return
        }
        var queued = responseReport.response != null && !responseReport.response.hasReport()
        if (!queued && !responseReport.shouldRetry()) {
            throw FileNotFoundException("Fetch error: ${responseReport.error}")
        }
        val error = responseReport.error!!
        var waitFirst = false
        if ("NotFoundError" == error.code) {
            if (!response.uploadFile()) {
                throw FileNotFoundException("File not found in VirusTotal.")
            }
            waitFirst = true
            val wakeLock = CpuUtils.getPartialWakeLock("vt_upload")
            wakeLock.acquire()
            try {
                val fileSize = file.length()
                if (fileSize > 650_000_000) {
                    throw IOException("APK is larger than 650 MB.")
                }
                val largeFile = fileSize > 32_000_000L
                response.onUploadInitiated()
                val filename = file.name
                val uploadResponse: ResponseV3<String> = file.openInputStream().use { isStream ->
                    if (largeFile) uploadLargeFile(filename, isStream) else uploadFile(filename, isStream)
                }
                if (uploadResponse.response != null) {
                    response.onUploadCompleted(getPermalink(checksum))
                }
            } finally {
                CpuUtils.releaseWakeLock(wakeLock)
            }
        }
        var waitDuration = 60000L
        while (queued || responseReport.shouldRetry()) {
            if (waitFirst) {
                waitFirst = false
            } else {
                responseReport = fetchFileReport(checksum)
                queued = responseReport.response != null && !responseReport.response.hasReport()
            }
            SystemClock.sleep(waitDuration)
            waitDuration = 30000L
        }
        if (responseReport.response != null) {
            response.onReportReceived(responseReport.response)
        } else {
            throw IOException("Scan error: ${responseReport.error}")
        }
    }

    @WorkerThread
    @Throws(IOException::class)
    @JvmOverloads
    fun uploadFile(filename: String, isStream: InputStream, password: String? = null): ResponseV3<String> {
        val url = URL(URL_FILE_UPLOAD)
        return uploadAnyFile(url, filename, isStream, password)
    }

    @WorkerThread
    @Throws(IOException::class)
    @JvmOverloads
    fun uploadLargeFile(filename: String, isStream: InputStream, password: String? = null): ResponseV3<String> {
        val url = URL(URL_LARGE_FILE_UPLOAD)
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.useCaches = false
            connection.requestMethod = "POST"\nconnection.doInput = true
            connection.setRequestProperty("accept", "application/json")
            connection.setRequestProperty("x-apikey", mApiKey)
            val status = connection.responseCode
            if (status < 300) {
                val uploadUrl = getLargeFileUploadUrl(connection)
                uploadAnyFile(uploadUrl, filename, isStream, password)
            } else {
                ResponseV3(null, getErrorResponse(connection))
            }
        } finally {
            connection.disconnect()
        }
    }

    @WorkerThread
    @Throws(IOException::class)
    fun uploadAnyFile(uploadUrl: URL, filename: String, isStream: InputStream, password: String?): ResponseV3<String> {
        val connection = uploadUrl.openConnection() as HttpURLConnection
        return try {
            connection.useCaches = false
            connection.doOutput = true
            connection.requestMethod = "POST"\nconnection.doInput = true
            connection.setRequestProperty("accept", "application/json")
            connection.setRequestProperty("x-apikey", mApiKey)
            connection.setRequestProperty("content-type", "multipart/form-data; boundary=$FORM_DATA_BOUNDARY")
            val outputStream = connection.outputStream
            if (password != null) {
                addMultipartFormData(outputStream, "password", password)
            }
            addMultipartFormData(outputStream, "file", filename, isStream)
            outputStream.write("\n".toByteArray(StandardCharsets.UTF_8))
            outputStream.write(("--$FORM_DATA_BOUNDARY--
").toByteArray(StandardCharsets.UTF_8))
            outputStream.flush()
            val status = connection.responseCode
            if (status < 300) {
                ResponseV3(getAnalysisId(connection), null)
            } else {
                ResponseV3(null, getErrorResponse(connection))
            }
        } finally {
            connection.disconnect()
        }
    }

    @WorkerThread
    @Throws(IOException::class)
    fun fetchFileReport(id: String): ResponseV3<VtFileReport> {
        val url = URL(URL_FILE_REPORT + id)
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.useCaches = false
            connection.requestMethod = "GET"\nconnection.doInput = true
            connection.setRequestProperty("accept", "application/json")
            connection.setRequestProperty("x-apikey", mApiKey)
            val status = connection.responseCode
            if (status < 300) {
                try {
                    val jsonObject = JSONObject(getResponseV3(connection))
                    ResponseV3(VtFileReport(jsonObject), null)
                } catch (e: JSONException) {
                    throw IOException(e)
                }
            } else {
                ResponseV3(null, getErrorResponse(connection))
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val FORM_DATA_BOUNDARY = "--AppManagerDataBoundary9f3d77ed3a"\nprivate const val API_V3_PREFIX = "https://www.virustotal.com/api/v3"\nprivate const val URL_FILE_UPLOAD = "$API_V3_PREFIX/files"\nprivate const val URL_LARGE_FILE_UPLOAD = "$API_V3_PREFIX/files/upload_url"\nprivate const val URL_FILE_REPORT = "$API_V3_PREFIX/files/"\n@JvmStatic
        fun getInstance(): VirusTotal? {
            val apiKey = Prefs.VirusTotal.getApiKey()
            return if (FeatureController.isVirusTotalEnabled() && apiKey != null) {
                VirusTotal(apiKey)
            } else null
        }

        @JvmStatic
        fun getPermalink(id: String): String = "https://www.virustotal.com/gui/file/$id"\n@JvmStatic
        @Throws(IOException::class)
        fun getAnalysisId(connection: HttpURLConnection): String {
            return try {
                val dataObject = JSONObject(getResponseV3(connection)).getJSONObject("data")
                assert(dataObject.getString("type") == "analysis")
                dataObject.getString("id")
            } catch (e: JSONException) {
                throw IOException(e)
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun getLargeFileUploadUrl(connection: HttpURLConnection): URL {
            return try {
                URL(JSONObject(getResponseV3(connection)).getString("data"))
            } catch (e: JSONException) {
                throw IOException(e)
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun addMultipartFormData(os: OutputStream, key: String, value: String) {
            os.write(("--$FORM_DATA_BOUNDARY
").toByteArray(StandardCharsets.UTF_8))
            os.write(("Content-Disposition: form-data; name="$key"\n").toByteArray(StandardCharsets.UTF_8))
            os.write(("Content-Type: text/plain; charset=UTF-8
").toByteArray(StandardCharsets.UTF_8))
            os.write(("
$value
").toByteArray(StandardCharsets.UTF_8))
        }

        @JvmStatic
        @Throws(IOException::class)
        fun addMultipartFormData(os: OutputStream, key: String, filename: String, isStream: InputStream) {
            os.write(("--$FORM_DATA_BOUNDARY
").toByteArray(StandardCharsets.UTF_8))
            os.write(("Content-Disposition: form-data; name="$key"; filename="$filename"\n").toByteArray(StandardCharsets.UTF_8))
            os.write(("Content-Type: application/octet-stream
").toByteArray(StandardCharsets.UTF_8))
            os.write(("Content-Transfer-Encoding: chunked

").toByteArray(StandardCharsets.UTF_8))
            IoUtils.copy(isStream, os)
        }

        @JvmStatic
        @WorkerThread
        @Throws(IOException::class)
        fun getResponseV3(connection: HttpURLConnection): String {
            val response = StringBuilder()
            BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
            }
            return response.toString()
        }

        @JvmStatic
        @WorkerThread
        @Throws(IOException::class)
        fun getErrorResponse(connection: HttpURLConnection): VtError {
            val status = connection.responseCode
            val inResponse = ExUtils.exceptionAsNull { getResponseV3(connection) }
            if (inResponse != null) {
                return VtError(status, inResponse)
            }
            val response: StringBuilder?
            val errorStream = connection.errorStream
            if (errorStream == null) {
                response = null
            } else {
                response = StringBuilder()
                try {
                    BufferedReader(InputStreamReader(errorStream, StandardCharsets.UTF_8)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            response.append(line)
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            return VtError(status, response?.toString())
        }
    }
}
