// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.parser

import android.content.ComponentName
import com.reandroid.arsc.chunk.xml.ResXmlDocument
import com.reandroid.arsc.chunk.xml.ResXmlElement
import com.reandroid.arsc.io.BlockReader
import io.github.muntashirakon.AppManager.logs.Log
import java.io.IOException
import java.nio.ByteBuffer

class ManifestParser {
    private val mManifestBytes: ByteBuffer
    private var mPackageName: String? = null

    constructor(manifestBytes: ByteArray) : this(ByteBuffer.wrap(manifestBytes))

    constructor(manifestBytes: ByteBuffer) {
        mManifestBytes = manifestBytes
    }

    @Throws(IOException::class)
    fun parseComponents(): List<ManifestComponent> {
        BlockReader(mManifestBytes.array()).use { reader ->
            val xmlBlock = ResXmlDocument()
            xmlBlock.readBytes(reader)
            xmlBlock.setPackageBlock(AndroidBinXmlDecoder.frameworkPackageBlock)
            val resManifestElement = xmlBlock.documentElement
            if (TAG_MANIFEST != resManifestElement.name) {
                throw IOException(""manifest" tag not found.")
            }
            val packageName = getAttributeValue(resManifestElement, ATTR_MANIFEST_PACKAGE)
                ?: throw IOException(""manifest" does not have required attribute "package".")
            mPackageName = packageName
            var resApplicationElement: ResXmlElement? = null
            val resXmlElementIt = resManifestElement.getElements(TAG_APPLICATION)
            if (resXmlElementIt.hasNext()) {
                resApplicationElement = resXmlElementIt.next()
            }
            if (resXmlElementIt.hasNext()) {
                throw IOException(""manifest" has duplicate "application" tags.")
            }
            if (resApplicationElement == null) {
                Log.i(TAG, "package $mPackageName does not have "application" tag.")
                return emptyList()
            }
            val componentIfList = ArrayList<ManifestComponent>(resApplicationElement.elementsCount)
            val it = resApplicationElement.elements
            while (it.hasNext()) {
                val elem = it.next()
                val tagName = elem.name
                if (tagName != null) {
                    when (tagName) {
                        TAG_ACTIVITY, TAG_ACTIVITY_ALIAS, TAG_SERVICE, TAG_RECEIVER, TAG_PROVIDER -> componentIfList.add(parseComponentInfo(elem))
                    }
                }
            }
            return componentIfList
        }
    }

    @Throws(IOException::class)
    private fun parseComponentInfo(componentElement: ResXmlElement): ManifestComponent {
        val componentName = getAttributeValue(componentElement, ATTR_NAME)
            ?: throw IOException(""${componentElement.name}" does not have  required attribute "android:name".")
        val componentIf = ManifestComponent(ComponentName(mPackageName!!, componentName))
        val resXmlElementIt = componentElement.getElements(TAG_INTENT_FILTER)
        while (resXmlElementIt.hasNext()) {
            val elem = resXmlElementIt.next()
            componentIf.intentFilters.add(parseIntentFilter(elem))
        }
        return componentIf
    }

    private fun parseIntentFilter(intentFilterElement: ResXmlElement): ManifestIntentFilter {
        val intentFilter = ManifestIntentFilter()
        val priorityString = getAttributeValue(intentFilterElement, ATTR_PRIORITY)
        if (priorityString != null) {
            intentFilter.priority = priorityString.toInt()
        }
        val resXmlElementIt = intentFilterElement.elements
        while (resXmlElementIt.hasNext()) {
            val elem = resXmlElementIt.next()
            val tagName = elem.name
            if (tagName != null) {
                when (tagName) {
                    TAG_ACTION -> intentFilter.actions.add(getAttributeValue(elem, ATTR_NAME)!!)
                    TAG_CATEGORY -> intentFilter.categories.add(getAttributeValue(elem, ATTR_NAME)!!)
                    TAG_DATA -> intentFilter.data.add(parseData(elem))
                }
            }
        }
        return intentFilter
    }

    private fun parseData(dataElement: ResXmlElement): ManifestIntentFilter.ManifestData {
        val data = ManifestIntentFilter.ManifestData()
        for (i in 0 until dataElement.attributeCount) {
            val attribute = dataElement.getAttributeAt(i)
            when {
                attribute.equalsName("scheme") -> data.scheme = attribute.valueAsString
                attribute.equalsName("host") -> data.host = attribute.valueAsString
                attribute.equalsName("port") -> data.port = attribute.valueAsString
                attribute.equalsName("path") -> data.path = attribute.valueAsString
                attribute.equalsName("pathPrefix") -> data.pathPrefix = attribute.valueAsString
                attribute.equalsName("pathSuffix") -> data.pathSuffix = attribute.valueAsString
                attribute.equalsName("pathPattern") -> data.pathPattern = attribute.valueAsString
                attribute.equalsName("pathAdvancedPattern") -> data.pathAdvancedPattern = attribute.valueAsString
                attribute.equalsName("mimeType") -> data.mimeType = attribute.valueAsString
                else -> Log.i(TAG, "Unknown intent-filter > data attribute ${attribute.name}")
            }
        }
        return data
    }

    private fun getAttributeValue(element: ResXmlElement, attrName: String): String? {
        for (i in 0 until element.attributeCount) {
            val attribute = element.getAttributeAt(i)
            if (attribute.equalsName(attrName)) {
                return attribute.valueAsString
            }
        }
        return null
    }

    companion object {
        val TAG: String = ManifestParser::class.java.simpleName
        private const val TAG_MANIFEST = "manifest"\nprivate const val ATTR_MANIFEST_PACKAGE = "package"\nprivate const val TAG_APPLICATION = "application"\nprivate const val TAG_ACTIVITY = "activity"\nprivate const val TAG_ACTIVITY_ALIAS = "activity-alias"\nprivate const val TAG_SERVICE = "service"\nprivate const val TAG_RECEIVER = "receiver"\nprivate const val TAG_PROVIDER = "provider"\nprivate const val ATTR_NAME = "name"\nprivate const val TAG_INTENT_FILTER = "intent-filter"\nprivate const val ATTR_PRIORITY = "priority"\nprivate const val TAG_ACTION = "action"\nprivate const val TAG_CATEGORY = "category"\nprivate const val TAG_DATA = "data"
    }
}
