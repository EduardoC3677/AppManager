// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm

import com.j256.simplemagic.entries.IanaEntries
import com.j256.simplemagic.entries.IanaEntry
import java.util.*

enum class ContentType2(
    val mimeType: String,
    val simpleName: String,
    vararg val fileExtensions: String
) {
    APKM("application/vnd.apkm", "apkm", "apkm"),
    APKS("application/x-apks", "apks", "apks"),
    CONFIGURATION("text/plain", "configuration", "cnf", "conf", "cfg", "cf", "ini", "rc", "sys"),
    DEX("application/x-dex", "dex", "dex"),
    KOTLIN("text/x-kotlin", "kotlin", "kt"),
    LOG("text/plain", "log", "log"),
    LUA("text/x-lua", "lua", "lua"),
    M4A("audio/mp4a-latm", "mp4a-latm", "m4a"),
    MARKDOWN("text/markdown", "markdown", "md", "markdown"),
    PEM("application/pem-certificate-chain", "pem", "pem"),
    PK8("application/pkcs8", "pkcs8", "pk8"),
    PLIST("application/x-plist", "property-list", "plist"),
    PROPERTIES("text/plain", "properties", "prop", "properties"),
    SMALI("text/x-smali", "smali", "smali"),
    SQLITE3("application/vnd.sqlite3", "sqlite", "db", "db3", "s3db", "sl3", "sqlite", "sqlite3"),
    TOML("application/toml", "toml", "toml"),
    XAPK("application/xapk-package-archive", "xapk", "xapk"),
    YAML("text/plain", "yaml", "yml", "yaml"),

    /** default if no specific match to the mime-type  */
    OTHER("application/octet-stream", "other");

    private val ianaEntry: IanaEntry? = findIanaEntryByMimeType(mimeType)

    /**
     * Returns the references of the mime type or null if none.
     */
    val references: List<String>?
        get() = ianaEntry?.references

    /**
     * Returns the URL of the references or null if none.
     */
    val referenceUrls: List<String>?
        get() = ianaEntry?.referenceUrls

    companion object {
        private val sMimeTypeMap = HashMap<String, ContentType2>()
        private val sFileExtensionMap = HashMap<String, ContentType2>()
        private var sIanaEntries: IanaEntries? = null

        init {
            for (type in values()) {
                // NOTE: this may overwrite this mapping
                sMimeTypeMap[type.mimeType.lowercase(Locale.ROOT)] = type
                for (fileExtension in type.fileExtensions) {
                    // NOTE: this may overwrite this mapping
                    sFileExtensionMap[fileExtension] = type
                }
            }
        }

        /**
         * Return the type associated with the mime-type string or [OTHER] if not found.
         */
        @JvmStatic
        fun fromMimeType(mimeType: String?): ContentType2 {
            val lowerMimeType = mimeType?.lowercase(Locale.ROOT)
            return sMimeTypeMap[lowerMimeType] ?: OTHER
        }

        /**
         * Return the type associated with the file-extension string or `null` if not found.
         */
        @JvmStatic
        fun fromFileExtension(fileExtension: String): ContentType2? {
            return sFileExtensionMap[fileExtension.lowercase(Locale.ROOT)]
        }

        private fun findIanaEntryByMimeType(mimeType: String): IanaEntry? {
            if (sIanaEntries == null) {
                sIanaEntries = IanaEntries()
            }
            return sIanaEntries!!.lookupByMimeType(mimeType)
        }
    }
}
