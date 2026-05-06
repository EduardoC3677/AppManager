// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.uri

import android.net.Uri
import android.os.RemoteException
import android.os.UserHandleHidden
import android.system.ErrnoException
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.misc.OsEnvironment
import io.github.muntashirakon.compat.xml.TypedXmlPullParser
import io.github.muntashirakon.compat.xml.TypedXmlSerializer
import io.github.muntashirakon.compat.xml.Xml
import io.github.muntashirakon.io.AtomicExtendedFile
import io.github.muntashirakon.io.Paths
import org.xmlpull.v1.XmlPullParser.END_DOCUMENT
import org.xmlpull.v1.XmlPullParser.START_TAG
import org.xmlpull.v1.XmlPullParserException
import java.io.*
import java.util.*

class UriManager {
    private val mGrantFile: AtomicExtendedFile = AtomicExtendedFile(Paths.build(OsEnvironment.getDataSystemDirectory(), "urigrants.xml")!!.file!!)
    private val mUriGrantsHashMap = HashMap<String, ArrayList<UriGrant>>()

    init {
        readGrantedUriPermissions()
    }

    fun getGrantedUris(packageName: String): ArrayList<UriGrant>? {
        synchronized(this) {
            return mUriGrantsHashMap[packageName]
        }
    }

    fun grantUri(uriGrant: UriGrant) {
        synchronized(this) {
            val uriGrants = mUriGrantsHashMap.getOrPut(uriGrant.targetPkg) { ArrayList() }
            uriGrants.add(uriGrant)
        }
    }

    fun writeGrantedUriPermissions() {
        val persist = mutableListOf<UriGrant>()
        synchronized(this) {
            mUriGrantsHashMap.values.forEach { persist.addAll(it) }
        }

        var fos: FileOutputStream? = null
        try {
            fos = mGrantFile.startWrite()
            val out: TypedXmlSerializer = Xml.resolveSerializer(fos)
            out.startDocument(null, true)
            out.startTag(null, TAG_URI_GRANTS)
            for (perm in persist) {
                out.startTag(null, TAG_URI_GRANT)
                out.attributeInt(null, ATTR_SOURCE_USER_ID, perm.sourceUserId)
                out.attributeInt(null, ATTR_TARGET_USER_ID, perm.targetUserId)
                out.attributeInterned(null, ATTR_SOURCE_PKG, perm.sourcePkg)
                out.attributeInterned(null, ATTR_TARGET_PKG, perm.targetPkg)
                out.attribute(null, ATTR_URI, perm.uri.toString())
                out.attributeBoolean(null, ATTR_PREFIX, perm.prefix)
                out.attributeInt(null, ATTR_MODE_FLAGS, perm.modeFlags)
                out.attributeLong(null, ATTR_CREATED_TIME, perm.createdTime)
                out.endTag(null, TAG_URI_GRANT)
            }
            out.endTag(null, TAG_URI_GRANTS)
            out.endDocument()
            mGrantFile.finishWrite(fos)
            val file = mGrantFile.baseFile
            file.setMode(384) // 0600 octal
            file.setUidGid(1000, 1000)
            file.restoreSelinuxContext()
        } catch (e: ErrnoException) {
            Log.e(TAG, "Failed to change file permissions.", e)
        } catch (e: IOException) {
            Log.e(TAG, "Failed writing Uri grants", e)
            mGrantFile.failWrite(fos)
        }
    }

    private fun readGrantedUriPermissions() {
        val now = System.currentTimeMillis()
        try {
            BufferedInputStream(mGrantFile.openRead()).use { isStream ->
                val `in`: TypedXmlPullParser = Xml.resolvePullParser(isStream)
                var type: Int
                while (`in`.next().also { type = it } != END_DOCUMENT) {
                    val tag = `in`.name
                    if (type == START_TAG && TAG_URI_GRANT == tag) {
                        val userHandle = `in`.getAttributeInt(null, ATTR_USER_HANDLE, UserHandleHidden.USER_NULL)
                        val sourceUserId: Int
                        val targetUserId: Int
                        if (userHandle != UserHandleHidden.USER_NULL) {
                            sourceUserId = userHandle
                            targetUserId = userHandle
                        } else {
                            sourceUserId = `in`.getAttributeInt(null, ATTR_SOURCE_USER_ID)
                            targetUserId = `in`.getAttributeInt(null, ATTR_TARGET_USER_ID)
                        }
                        val sourcePkg = `in`.getAttributeValue(null, ATTR_SOURCE_PKG)
                        val targetPkg = `in`.getAttributeValue(null, ATTR_TARGET_PKG)
                        val uri = Uri.parse(`in`.getAttributeValue(null, ATTR_URI))
                        val prefix = `in`.getAttributeBoolean(null, ATTR_PREFIX, false)
                        val modeFlags = `in`.getAttributeInt(null, ATTR_MODE_FLAGS)
                        val createdTime = `in`.getAttributeLong(null, ATTR_CREATED_TIME, now)

                        val uriGrant = UriGrant(sourceUserId, targetUserId, userHandle, sourcePkg, targetPkg, uri, prefix, modeFlags, createdTime)
                        synchronized(this) {
                            val uriGrants = mUriGrantsHashMap.getOrPut(targetPkg) { ArrayList() }
                            uriGrants.add(uriGrant)
                        }
                    }
                }
            }
        } catch (ignore: FileNotFoundException) {
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading Uri grants", e)
        }
    }

    class UriGrant(
        val sourceUserId: Int,
        val targetUserId: Int,
        val userHandle: Int,
        val sourcePkg: String,
        val targetPkg: String,
        val uri: Uri,
        val prefix: Boolean,
        val modeFlags: Int,
        val createdTime: Long
    ) {
        override fun toString(): String = flattenToString()

        fun flattenToString(): String {
            return "$sourceUserId,$targetUserId,$userHandle,$sourcePkg,$targetPkg,$prefix,$modeFlags,$createdTime,${uri}"\n}

        companion object {
            @JvmStatic
            fun unflattenFromString(string: String): UriGrant {
                val tokenizer = StringTokenizer(string, ",")
                val sourceUserId = tokenizer.nextToken().toInt()
                val targetUserId = tokenizer.nextToken().toInt()
                val userHandle = tokenizer.nextToken().toInt()
                val sourcePkg = tokenizer.nextToken()
                val targetPkg = tokenizer.nextToken()
                val prefix = tokenizer.nextToken().toBoolean()
                val modeFlags = tokenizer.nextToken().toInt()
                val createdTime = tokenizer.nextToken().toLong()
                val uriString = StringBuilder(tokenizer.nextToken())
                while (tokenizer.hasMoreElements()) {
                    uriString.append(",").append(tokenizer.nextToken())
                }
                val uri = Uri.parse(uriString.toString())
                return UriGrant(sourceUserId, targetUserId, userHandle, sourcePkg, targetPkg, uri, prefix, modeFlags, createdTime)
            }
        }
    }

    companion object {
        const val TAG = "UriManager"\nprivate const val TAG_URI_GRANTS = "uri-grants"\nprivate const val TAG_URI_GRANT = "uri-grant"\nprivate const val ATTR_USER_HANDLE = "userHandle"\nprivate const val ATTR_SOURCE_USER_ID = "sourceUserId"\nprivate const val ATTR_TARGET_USER_ID = "targetUserId"\nprivate const val ATTR_SOURCE_PKG = "sourcePkg"\nprivate const val ATTR_TARGET_PKG = "targetPkg"\nprivate const val ATTR_URI = "uri"\nprivate const val ATTR_MODE_FLAGS = "modeFlags"\nprivate const val ATTR_CREATED_TIME = "createdTime"\nprivate const val ATTR_PREFIX = "prefix"
    }
}
