// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.compat.xml

import android.text.TextUtils
import android.util.Base64
import io.github.muntashirakon.compat.HexDump
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlSerializer
import java.io.IOException

object XmlUtils {
    private class ForcedTypedXmlSerializer(wrapped: XmlSerializer) : XmlSerializerWrapper(wrapped), TypedXmlSerializer {
        @Throws(IOException::class)
        override fun attributeInterned(namespace: String?, name: String, value: String): XmlSerializer {
            return attribute(namespace, name, value)
        }

        @Throws(IOException::class)
        override fun attributeBytesHex(namespace: String?, name: String, value: ByteArray): XmlSerializer {
            return attribute(namespace, name, HexDump.toHexString(value))
        }

        @Throws(IOException::class)
        override fun attributeBytesBase64(namespace: String?, name: String, value: ByteArray): XmlSerializer {
            return attribute(namespace, name, Base64.encodeToString(value, Base64.NO_WRAP))
        }

        @Throws(IOException::class)
        override fun attributeInt(namespace: String?, name: String, value: Int): XmlSerializer {
            return attribute(namespace, name, Integer.toString(value))
        }

        @Throws(IOException::class)
        override fun attributeIntHex(namespace: String?, name: String, value: Int): XmlSerializer {
            return attribute(namespace, name, Integer.toString(value, 16))
        }

        @Throws(IOException::class)
        override fun attributeLong(namespace: String?, name: String, value: Long): XmlSerializer {
            return attribute(namespace, name, java.lang.Long.toString(value))
        }

        @Throws(IOException::class)
        override fun attributeLongHex(namespace: String?, name: String, value: Long): XmlSerializer {
            return attribute(namespace, name, java.lang.Long.toString(value, 16))
        }

        @Throws(IOException::class)
        override fun attributeFloat(namespace: String?, name: String, value: Float): XmlSerializer {
            return attribute(namespace, name, java.lang.Float.toString(value))
        }

        @Throws(IOException::class)
        override fun attributeDouble(namespace: String?, name: String, value: Double): XmlSerializer {
            return attribute(namespace, name, java.lang.Double.toString(value))
        }

        @Throws(IOException::class)
        override fun attributeBoolean(namespace: String?, name: String, value: Boolean): XmlSerializer {
            return attribute(namespace, name, java.lang.Boolean.toString(value))
        }
    }

    /**
     * Return a specialization of the given {@link XmlSerializer} which has
     * explicit methods to support consistent and efficient conversion of
     * primitive data types.
     */
    @JvmStatic
    fun makeTyped(xml: XmlSerializer): TypedXmlSerializer {
        return if (xml is TypedXmlSerializer) {
            xml
        } else {
            ForcedTypedXmlSerializer(xml)
        }
    }

    private class ForcedTypedXmlPullParser(wrapped: XmlPullParser) : XmlPullParserWrapper(wrapped), TypedXmlPullParser {
        @Throws(XmlPullParserException::class)
        override fun getAttributeBytesHex(index: Int): ByteArray {
            return try {
                HexDump.hexStringToByteArray(getAttributeValue(index))
            } catch (e: Exception) {
                throw XmlPullParserException("Invalid attribute " + getAttributeName(index) + ": " + e)
            }
        }

        @Throws(XmlPullParserException::class)
        override fun getAttributeBytesBase64(index: Int): ByteArray {
            return try {
                Base64.decode(getAttributeValue(index), Base64.NO_WRAP)
            } catch (e: Exception) {
                throw XmlPullParserException("Invalid attribute " + getAttributeName(index) + ": " + e)
            }
        }

        @Throws(XmlPullParserException::class)
        override fun getAttributeInt(index: Int): Int {
            return try {
                getAttributeValue(index).toInt()
            } catch (e: Exception) {
                throw XmlPullParserException("Invalid attribute " + getAttributeName(index) + ": " + e)
            }
        }

        @Throws(XmlPullParserException::class)
        override fun getAttributeIntHex(index: Int): Int {
            return try {
                getAttributeValue(index).toInt(16)
            } catch (e: Exception) {
                throw XmlPullParserException("Invalid attribute " + getAttributeName(index) + ": " + e)
            }
        }

        @Throws(XmlPullParserException::class)
        override fun getAttributeLong(index: Int): Long {
            return try {
                getAttributeValue(index).toLong()
            } catch (e: Exception) {
                throw XmlPullParserException("Invalid attribute " + getAttributeName(index) + ": " + e)
            }
        }

        @Throws(XmlPullParserException::class)
        override fun getAttributeLongHex(index: Int): Long {
            return try {
                getAttributeValue(index).toLong(16)
            } catch (e: Exception) {
                throw XmlPullParserException("Invalid attribute " + getAttributeName(index) + ": " + e)
            }
        }

        @Throws(XmlPullParserException::class)
        override fun getAttributeFloat(index: Int): Float {
            return try {
                getAttributeValue(index).toFloat()
            } catch (e: Exception) {
                throw XmlPullParserException("Invalid attribute " + getAttributeName(index) + ": " + e)
            }
        }

        @Throws(XmlPullParserException::class)
        override fun getAttributeDouble(index: Int): Double {
            return try {
                getAttributeValue(index).toDouble()
            } catch (e: Exception) {
                throw XmlPullParserException("Invalid attribute " + getAttributeName(index) + ": " + e)
            }
        }

        @Throws(XmlPullParserException::class)
        override fun getAttributeBoolean(index: Int): Boolean {
            val value = getAttributeValue(index)
            return if ("true".equals(value, ignoreCase = true)) {
                true
            } else if ("false".equals(value, ignoreCase = true)) {
                false
            } else {
                throw XmlPullParserException("Invalid attribute " + getAttributeName(index) + ": " + value)
            }
        }
    }

    /**
     * Return a specialization of the given {@link XmlPullParser} which has
     * explicit methods to support consistent and efficient conversion of
     * primitive data types.
     */
    @JvmStatic
    fun makeTyped(xml: XmlPullParser): TypedXmlPullParser {
        return if (xml is TypedXmlPullParser) {
            xml
        } else {
            ForcedTypedXmlPullParser(xml)
        }
    }

    @JvmStatic
    @Throws(XmlPullParserException::class, IOException::class)
    fun skipCurrentTag(parser: XmlPullParser) {
        val outerDepth = parser.depth
        var type: Int
        while (parser.next().also { type = it } != XmlPullParser.END_DOCUMENT && (type != XmlPullParser.END_TAG || parser.depth > outerDepth)) {
        }
    }

    @JvmStatic
    @Throws(XmlPullParserException::class, IOException::class)
    fun nextElement(parser: XmlPullParser) {
        var type: Int
        while (parser.next().also { type = it } != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT) {
        }
    }

    @JvmStatic
    @Throws(IOException::class, XmlPullParserException::class)
    fun nextElementWithin(parser: XmlPullParser, outerDepth: Int): Boolean {
        while (true) {
            val type = parser.next()
            if (type == XmlPullParser.END_DOCUMENT || type == XmlPullParser.END_TAG && parser.depth == outerDepth) {
                return false
            }
            if (type == XmlPullParser.START_TAG && parser.depth == outerDepth + 1) {
                return true
            }
        }
    }

    @JvmStatic
    fun readIntAttribute(`in`: XmlPullParser, name: String?, defaultValue: Int): Int {
        val value = `in`.getAttributeValue(null, name)
        if (TextUtils.isEmpty(value)) {
            return defaultValue
        }
        return try {
            value.toInt()
        } catch (e: NumberFormatException) {
            defaultValue
        }
    }

    @JvmStatic
    fun readBooleanAttribute(
        `in`: XmlPullParser, name: String?,
        defaultValue: Boolean
    ): Boolean {
        val value = `in`.getAttributeValue(null, name)
        return if (TextUtils.isEmpty(value)) {
            defaultValue
        } else {
            java.lang.Boolean.parseBoolean(value)
        }
    }
}
