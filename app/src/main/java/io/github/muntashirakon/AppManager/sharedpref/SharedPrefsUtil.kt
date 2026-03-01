// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sharedpref

import android.text.TextUtils
import android.util.Xml
import io.github.muntashirakon.io.IoUtils
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.StringWriter

object SharedPrefsUtil {
    const val TAG_ROOT = "map"
    const val TAG_BOOLEAN = "boolean"
    const val TAG_FLOAT = "float"
    const val TAG_INTEGER = "int"
    const val TAG_LONG = "long"
    const val TAG_STRING = "string"
    const val TAG_SET = "set"

    @JvmStatic
    @Throws(XmlPullParserException::class, IOException::class)
    fun readSharedPref(isStream: InputStream): HashMap<String, Any> {
        val prefs = HashMap<String, Any>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(isStream, null)
        parser.nextTag()
        parser.require(XmlPullParser.START_TAG, null, TAG_ROOT)
        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val tagName = parser.name
                var attrName = parser.getAttributeValue(null, "name")
                if (attrName == null) attrName = ""
                val attrValue = parser.getAttributeValue(null, "value")
                when (tagName) {
                    TAG_BOOLEAN -> prefs[attrName] = (attrValue == "true")
                    TAG_FLOAT -> attrValue?.let { prefs[attrName] = it.toFloat() }
                    TAG_INTEGER -> attrValue?.let { prefs[attrName] = it.toInt() }
                    TAG_LONG -> attrValue?.let { prefs[attrName] = it.toLong() }
                    TAG_STRING -> prefs[attrName] = parser.nextText()
                    TAG_SET -> {
                        val stringSet = HashSet<String>()
                        prefs[attrName] = stringSet
                        event = parser.next()
                        var innerTagName = parser.name
                        while (event != XmlPullParser.END_TAG || innerTagName != TAG_SET) {
                            if (event == XmlPullParser.START_TAG) {
                                if (innerTagName != TAG_STRING) {
                                    throw XmlPullParserException("Invalid tag inside <set>: $innerTagName")
                                }
                                stringSet.add(parser.nextText())
                            }
                            event = parser.next()
                            innerTagName = parser.name
                        }
                    }
                    else -> throw XmlPullParserException("Invalid tag: $tagName")
                }
            }
            event = parser.next()
        }
        return prefs
    }

    @JvmStatic
    @Throws(IOException::class)
    fun writeSharedPref(os: OutputStream, hashMap: Map<String, Any>) {
        val xmlSerializer = Xml.newSerializer()
        val stringWriter = StringWriter()
        xmlSerializer.setOutput(stringWriter)
        xmlSerializer.startDocument("UTF-8", true)
        xmlSerializer.startTag("", TAG_ROOT)
        for ((name, value) in hashMap) {
            when (value) {
                is Boolean -> {
                    xmlSerializer.startTag("", TAG_BOOLEAN)
                    xmlSerializer.attribute("", "name", name)
                    xmlSerializer.attribute("", "value", value.toString())
                    xmlSerializer.endTag("", TAG_BOOLEAN)
                }
                is Float -> {
                    xmlSerializer.startTag("", TAG_FLOAT)
                    xmlSerializer.attribute("", "name", name)
                    xmlSerializer.attribute("", "value", value.toString())
                    xmlSerializer.endTag("", TAG_FLOAT)
                }
                is Int -> {
                    xmlSerializer.startTag("", TAG_INTEGER)
                    xmlSerializer.attribute("", "name", name)
                    xmlSerializer.attribute("", "value", value.toString())
                    xmlSerializer.endTag("", TAG_INTEGER)
                }
                is Long -> {
                    xmlSerializer.startTag("", TAG_LONG)
                    xmlSerializer.attribute("", "name", name)
                    xmlSerializer.attribute("", "value", value.toString())
                    xmlSerializer.endTag("", TAG_LONG)
                }
                is String -> {
                    xmlSerializer.startTag("", TAG_STRING)
                    xmlSerializer.attribute("", "name", name)
                    xmlSerializer.text(value)
                    xmlSerializer.endTag("", TAG_STRING)
                }
                is Set<*> -> {
                    xmlSerializer.startTag("", TAG_SET)
                    xmlSerializer.attribute("", "name", name)
                    for (v in value) {
                        xmlSerializer.startTag("", TAG_STRING)
                        xmlSerializer.text(v.toString())
                        xmlSerializer.endTag("", TAG_STRING)
                    }
                    xmlSerializer.endTag("", TAG_SET)
                }
                else -> throw IOException("Invalid value for key: $name (value: $value)")
            }
        }
        xmlSerializer.endTag("", TAG_ROOT)
        xmlSerializer.endDocument()
        xmlSerializer.flush()
        os.write(stringWriter.toString().toByteArray())
    }

    @JvmStatic
    fun flattenToString(stringSet: Set<String>): String {
        val stringList = ArrayList<String>(stringSet.size)
        for (string in stringSet) {
            stringList.add(string.replace(",", "\,"))
        }
        return TextUtils.join(",", stringList)
    }

    @JvmStatic
    fun unflattenToSet(rawValue: String): Set<String> {
        val strings = rawValue.split("(?<!\),".toRegex()).toTypedArray()
        val stringSet = HashSet<String>(strings.size)
        stringSet.addAll(listOf(*strings))
        return stringSet
    }
}
