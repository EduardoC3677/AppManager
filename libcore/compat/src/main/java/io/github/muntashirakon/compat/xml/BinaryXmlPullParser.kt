// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.compat.xml

import android.util.Base64
import android.text.TextUtils
import android.util.TypedXmlPullParser
import io.github.muntashirakon.compat.HexDump
import io.github.muntashirakon.compat.io.FastDataInput
import io.github.muntashirakon.compat.xml.BinaryXmlSerializer.Companion.ATTRIBUTE
import io.github.muntashirakon.compat.xml.BinaryXmlSerializer.Companion.PROTOCOL_MAGIC_VERSION_0
import io.github.muntashirakon.compat.xml.BinaryXmlSerializer.Companion.TYPE_BOOLEAN_FALSE
import io.github.muntashirakon.compat.xml.BinaryXmlSerializer.Companion.TYPE_BOOLEAN_TRUE
import io.github.muntashirakon.compat.xml.BinaryXmlSerializer.Companion.TYPE_BYTES_BASE64
import io.github.muntashirakon.compat.xml.BinaryXmlSerializer.Companion.TYPE_BYTES_HEX
import io.github.muntashirakon.compat.xml.BinaryXmlSerializer.Companion.TYPE_DOUBLE
import io.github.muntashirakon.compat.xml.BinaryXmlSerializer.Companion.TYPE_FLOAT
import io.github.muntashirakon.compat.xml.BinaryXmlSerializer.Companion.TYPE_INT
import io.github.muntashirakon.compat.xml.BinaryXmlSerializer.Companion.TYPE_INT_HEX
import io.github.muntashirakon.compat.xml.BinaryXmlSerializer.Companion.TYPE_LONG
import io.github.muntashirakon.compat.xml.BinaryXmlSerializer.Companion.TYPE_LONG_HEX
import io.github.muntashirakon.compat.xml.BinaryXmlSerializer.Companion.TYPE_NULL
import io.github.muntashirakon.compat.xml.BinaryXmlSerializer.Companion.TYPE_STRING
import io.github.muntashirakon.compat.xml.BinaryXmlSerializer.Companion.TYPE_STRING_INTERNED
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.Reader
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * Parser that reads XML documents using a custom binary wire protocol which
 * benchmarking has shown to be 8.5x faster than {@link Xml#newFastPullParser()}
 * for a typical {@code packages.xml}.
 * <p>
 * The high-level design of the wire protocol is to directly serialize the event
 * stream, while efficiently and compactly writing strongly-typed primitives
 * delivered through the {@link TypedXmlSerializer} interface.
 * <p>
 * Each serialized event is a single byte where the lower half is a normal
 * {@link XmlPullParser} token and the upper half is an optional data type
 * signal, such as {@link BinaryXmlSerializer#TYPE_INT}.
 * <p>
 * This parser has some specific limitations:
 * <ul>
 * <li>Only the UTF-8 encoding is supported.
 * <li>Variable length values, such as {@code byte[]} or {@link String}, are
 * limited to 65,535 bytes in length. Note that {@link String} values are stored
 * as UTF-8 on the wire.
 * <li>Namespaces, prefixes, properties, and options are unsupported.
 * </ul>
 */
class BinaryXmlPullParser : TypedXmlPullParser {
    private var mIn: FastDataInput? = null

    private var mCurrentToken = XmlPullParser.START_DOCUMENT
    private var mCurrentDepth = 0
    private var mCurrentName: String? = null
    private var mCurrentText: String? = null

    /**
     * Pool of attributes parsed for the currently tag. All interactions should
     * be done via {@link #obtainAttribute()}, {@link #findAttribute(String)},
     * and {@link #resetAttributes()}.
     */
    private var mAttributeCount = 0
    private var mAttributes: Array<Attribute?> = arrayOfNulls(8)

    @Throws(XmlPullParserException::class)
    override fun setInput(`is`: InputStream, encoding: String?) {
        if (encoding != null && !StandardCharsets.UTF_8.name().equals(encoding, ignoreCase = true)) {
            throw UnsupportedOperationException()
        }

        if (mIn != null) {
            mIn!!.release()
            mIn = null
        }

        mIn = FastDataInput.obtainUsing4ByteSequences(`is`)

        mCurrentToken = XmlPullParser.START_DOCUMENT
        mCurrentDepth = 0
        mCurrentName = null
        mCurrentText = null

        mAttributeCount = 0
        mAttributes = arrayOfNulls(8)
        for (i in mAttributes.indices) {
            mAttributes[i] = Attribute()
        }

        try {
            val magic = ByteArray(4)
            mIn!!.readFully(magic)
            if (!Arrays.equals(magic, PROTOCOL_MAGIC_VERSION_0)) {
                throw IOException("Unexpected magic " + HexDump.toHexString(magic))
            }

            // We're willing to immediately consume a START_DOCUMENT if present,
            // but we're okay if it's missing
            if (peekNextExternalToken() == XmlPullParser.START_DOCUMENT) {
                consumeToken()
            }
        } catch (e: IOException) {
            throw XmlPullParserException(e.toString())
        }
    }

    @Throws(XmlPullParserException::class)
    override fun setInput(`in`: Reader) {
        throw UnsupportedOperationException()
    }

    @Throws(XmlPullParserException::class, IOException::class)
    override fun next(): Int {
        while (true) {
            val token = nextToken()
            when (token) {
                XmlPullParser.START_TAG, XmlPullParser.END_TAG, XmlPullParser.END_DOCUMENT -> return token
                XmlPullParser.TEXT -> {
                    consumeAdditionalText()
                    // Per interface docs, empty text regions are skipped
                    if (mCurrentText == null || mCurrentText!!.isEmpty()) {
                        continue
                    } else {
                        return XmlPullParser.TEXT
                    }
                }
            }
        }
    }

    @Throws(XmlPullParserException::class, IOException::class)
    override fun nextToken(): Int {
        if (mCurrentToken == XmlPullParser.END_TAG) {
            mCurrentDepth--
        }

        var token: Int
        try {
            token = peekNextExternalToken()
            consumeToken()
        } catch (e: EOFException) {
            token = XmlPullParser.END_DOCUMENT
        }
        when (token) {
            XmlPullParser.START_TAG -> {
                // We need to peek forward to find the next external token so
                // that we parse all pending INTERNAL_ATTRIBUTE tokens
                peekNextExternalToken()
                mCurrentDepth++
            }
        }
        mCurrentToken = token
        return token
    }

    /**
     * Peek at the next "external" token without consuming it.
     * <p>
     * External tokens, such as {@link #START_TAG}, are expected by typical
     * {@link XmlPullParser} clients. In contrast, internal tokens, such as
     * {@link BinaryXmlSerializer#ATTRIBUTE}, are not expected by typical clients.
     * <p>
     * This method consumes any internal events until it reaches the next
     * external event.
     */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun peekNextExternalToken(): Int {
        while (true) {
            val token = peekNextToken()
            when (token) {
                ATTRIBUTE -> {
                    consumeToken()
                    continue
                }
                else -> return token
            }
        }
    }

    /**
     * Peek at the next token in the underlying stream without consuming it.
     */
    @Throws(IOException::class)
    private fun peekNextToken(): Int {
        return mIn!!.peekByte().toInt() and 0x0f
    }

    /**
     * Parse and consume the next token in the underlying stream.
     */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun consumeToken() {
        val event = mIn!!.readByte().toInt()
        val token = event and 0x0f
        val type = event and 0xf0
        when (token) {
            ATTRIBUTE -> {
                val attr = obtainAttribute()
                attr.name = mIn!!.readInternedUTF()
                attr.type = type
                when (type) {
                    TYPE_NULL, TYPE_BOOLEAN_TRUE, TYPE_BOOLEAN_FALSE -> {
                    }
                    TYPE_STRING -> attr.valueString = mIn!!.readUTF()
                    TYPE_STRING_INTERNED -> attr.valueString = mIn!!.readInternedUTF()
                    TYPE_BYTES_HEX, TYPE_BYTES_BASE64 -> {
                        val len = mIn!!.readUnsignedShort()
                        val res = ByteArray(len)
                        mIn!!.readFully(res)
                        attr.valueBytes = res
                    }
                    TYPE_INT, TYPE_INT_HEX -> attr.valueInt = mIn!!.readInt()
                    TYPE_LONG, TYPE_LONG_HEX -> attr.valueLong = mIn!!.readLong()
                    TYPE_FLOAT -> attr.valueFloat = mIn!!.readFloat()
                    TYPE_DOUBLE -> attr.valueDouble = mIn!!.readDouble()
                    else -> throw IOException("Unexpected data type $type")
                }
            }
            XmlPullParser.START_DOCUMENT -> {
                mCurrentName = null
                mCurrentText = null
                if (mAttributeCount > 0) resetAttributes()
            }
            XmlPullParser.END_DOCUMENT -> {
                mCurrentName = null
                mCurrentText = null
                if (mAttributeCount > 0) resetAttributes()
            }
            XmlPullParser.START_TAG -> {
                mCurrentName = mIn!!.readInternedUTF()
                mCurrentText = null
                if (mAttributeCount > 0) resetAttributes()
            }
            XmlPullParser.END_TAG -> {
                mCurrentName = mIn!!.readInternedUTF()
                mCurrentText = null
                if (mAttributeCount > 0) resetAttributes()
            }
            XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.PROCESSING_INSTRUCTION, XmlPullParser.COMMENT, XmlPullParser.DOCDECL, XmlPullParser.IGNORABLE_WHITESPACE -> {
                mCurrentName = null
                mCurrentText = mIn!!.readUTF()
                if (mAttributeCount > 0) resetAttributes()
            }
            XmlPullParser.ENTITY_REF -> {
                mCurrentName = mIn!!.readUTF()
                mCurrentText = resolveEntity(mCurrentName!!)
                if (mAttributeCount > 0) resetAttributes()
            }
            else -> {
                throw IOException("Unknown token $token with type $type")
            }
        }
    }

    /**
     * When the current tag is {@link #TEXT}, consume all subsequent "text"
     * events, as described by {@link #next}. When finished, the current event
     * will still be {@link #TEXT}.
     */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun consumeAdditionalText() {
        var combinedText = mCurrentText
        while (true) {
            val token = peekNextExternalToken()
            when (token) {
                XmlPullParser.COMMENT, XmlPullParser.PROCESSING_INSTRUCTION -> {
                    // Quietly consumed
                    consumeToken()
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> {
                    // Additional text regions collected
                    consumeToken()
                    combinedText += mCurrentText
                }
                else -> {
                    // Next token is something non-text, so wrap things up
                    mCurrentToken = XmlPullParser.TEXT
                    mCurrentName = null
                    mCurrentText = combinedText
                    return
                }
            }
        }
    }

    @Throws(XmlPullParserException::class, IOException::class)
    override fun require(type: Int, namespace: String?, name: String?) {
        if (!namespace.isNullOrEmpty()) throw illegalNamespace()
        if (mCurrentToken != type || mCurrentName != name) {
            throw XmlPullParserException(positionDescription)
        }
    }

    @Throws(XmlPullParserException::class, IOException::class)
    override fun nextText(): String {
        if (eventType != XmlPullParser.START_TAG) {
            throw XmlPullParserException(positionDescription)
        }
        var eventType = next()
        if (eventType == XmlPullParser.TEXT) {
            val result = text
            eventType = next()
            if (eventType != XmlPullParser.END_TAG) {
                throw XmlPullParserException(positionDescription)
            }
            return result
        } else if (eventType == XmlPullParser.END_TAG) {
            return ""
        } else {
            throw XmlPullParserException(positionDescription)
        }
    }

    @Throws(XmlPullParserException::class, IOException::class)
    override fun nextTag(): Int {
        var eventType = next()
        if (eventType == XmlPullParser.TEXT && isWhitespace) {
            eventType = next()
        }
        if (eventType != XmlPullParser.START_TAG && eventType != XmlPullParser.END_TAG) {
            throw XmlPullParserException(positionDescription)
        }
        return eventType
    }

    /**
     * Allocate and return a new {@link Attribute} associated with the tag being
     * currently processed. This will automatically grow the internal pool as
     * needed.
     */
    private fun obtainAttribute(): Attribute {
        if (mAttributeCount == mAttributes.size) {
            val before = mAttributes.size
            val after = before + (before shr 1)
            mAttributes = mAttributes.copyOf(after)
            for (i in before until after) {
                mAttributes[i] = Attribute()
            }
        }
        return mAttributes[mAttributeCount++]!!
    }

    /**
     * Clear any {@link Attribute} instances that have been allocated by
     * {@link #obtainAttribute()}, returning them into the pool for recycling.
     */
    private fun resetAttributes() {
        for (i in 0 until mAttributeCount) {
            mAttributes[i]!!.reset()
        }
        mAttributeCount = 0
    }

    override fun getAttributeIndex(namespace: String?, name: String): Int {
        if (!namespace.isNullOrEmpty()) throw illegalNamespace()
        for (i in 0 until mAttributeCount) {
            if (mAttributes[i]!!.name == name) {
                return i
            }
        }
        return -1
    }

    override fun getAttributeValue(namespace: String?, name: String): String? {
        val index = getAttributeIndex(namespace, name)
        return if (index != -1) {
            mAttributes[index]!!.getValueString()
        } else {
            null
        }
    }

    override fun getAttributeValue(index: Int): String? {
        return mAttributes[index]!!.getValueString()
    }

    @Throws(XmlPullParserException::class)
    override fun getAttributeBytesHex(index: Int): ByteArray {
        return mAttributes[index]!!.getValueBytesHex() ?: ByteArray(0)
    }

    @Throws(XmlPullParserException::class)
    override fun getAttributeBytesBase64(index: Int): ByteArray {
        return mAttributes[index]!!.getValueBytesBase64() ?: ByteArray(0)
    }

    @Throws(XmlPullParserException::class)
    override fun getAttributeInt(index: Int): Int {
        return mAttributes[index]!!.getValueInt()
    }

    @Throws(XmlPullParserException::class)
    override fun getAttributeIntHex(index: Int): Int {
        return mAttributes[index]!!.getValueIntHex()
    }

    @Throws(XmlPullParserException::class)
    override fun getAttributeLong(index: Int): Long {
        return mAttributes[index]!!.getValueLong()
    }

    @Throws(XmlPullParserException::class)
    override fun getAttributeLongHex(index: Int): Long {
        return mAttributes[index]!!.getValueLongHex()
    }

    @Throws(XmlPullParserException::class)
    override fun getAttributeFloat(index: Int): Float {
        return mAttributes[index]!!.getValueFloat()
    }

    @Throws(XmlPullParserException::class)
    override fun getAttributeDouble(index: Int): Double {
        return mAttributes[index]!!.getValueDouble()
    }

    @Throws(XmlPullParserException::class)
    override fun getAttributeBoolean(index: Int): Boolean {
        return mAttributes[index]!!.getValueBoolean()
    }

    override fun getText(): String {
        return mCurrentText!!
    }

    override fun getTextCharacters(holderForStartAndLength: IntArray): CharArray {
        val chars = mCurrentText!!.toCharArray()
        holderForStartAndLength[0] = 0
        holderForStartAndLength[1] = chars.size
        return chars
    }

    override fun getInputEncoding(): String {
        return StandardCharsets.UTF_8.name()
    }

    override fun getDepth(): Int {
        return mCurrentDepth
    }

    override fun getPositionDescription(): String {
        // Not very helpful, but it's the best information we have
        return "Token $mCurrentToken at depth $mCurrentDepth"
    }

    override fun getLineNumber(): Int {
        return -1
    }

    override fun getColumnNumber(): Int {
        return -1
    }

    @Throws(XmlPullParserException::class)
    override fun isWhitespace(): Boolean {
        return when (mCurrentToken) {
            XmlPullParser.IGNORABLE_WHITESPACE -> true
            XmlPullParser.TEXT, XmlPullParser.CDSECT -> !TextUtils.isGraphic(mCurrentText)
            else -> throw XmlPullParserException("Not applicable for token $mCurrentToken")
        }
    }

    override fun getNamespace(): String {
        return when (mCurrentToken) {
            XmlPullParser.START_TAG, XmlPullParser.END_TAG -> XmlPullParser.NO_NAMESPACE
            else -> XmlPullParser.NO_NAMESPACE
        }
    }

    override fun getName(): String? {
        return mCurrentName
    }

    override fun getPrefix(): String? {
        // Prefixes are not supported
        return null
    }

    @Throws(XmlPullParserException::class)
    override fun isEmptyElementTag(): Boolean {
        return when (mCurrentToken) {
            XmlPullParser.START_TAG -> try {
                peekNextExternalToken() == XmlPullParser.END_TAG
            } catch (e: IOException) {
                throw XmlPullParserException(e.toString())
            }
            else -> throw XmlPullParserException("Not at START_TAG")
        }
    }

    override fun getAttributeCount(): Int {
        return mAttributeCount
    }

    override fun getAttributeNamespace(index: Int): String {
        // Namespaces are unsupported
        return XmlPullParser.NO_NAMESPACE
    }

    override fun getAttributeName(index: Int): String {
        return mAttributes[index]!!.name!!
    }

    override fun getAttributePrefix(index: Int): String? {
        // Prefixes are not supported
        return null
    }

    override fun getAttributeType(index: Int): String {
        // Validation is not supported
        return "CDATA"
    }

    override fun isAttributeDefault(index: Int): Boolean {
        // Validation is not supported
        return false
    }

    @Throws(XmlPullParserException::class)
    override fun getEventType(): Int {
        return mCurrentToken
    }

    @Throws(XmlPullParserException::class)
    override fun getNamespaceCount(depth: Int): Int {
        // Namespaces are unsupported
        return 0
    }

    @Throws(XmlPullParserException::class)
    override fun getNamespacePrefix(pos: Int): String {
        // Namespaces are unsupported
        throw UnsupportedOperationException()
    }

    @Throws(XmlPullParserException::class)
    override fun getNamespaceUri(pos: Int): String {
        // Namespaces are unsupported
        throw UnsupportedOperationException()
    }

    override fun getNamespace(prefix: String): String {
        // Namespaces are unsupported
        throw UnsupportedOperationException()
    }

    @Throws(XmlPullParserException::class)
    override fun defineEntityReplacementText(entityName: String, replacementText: String) {
        // Custom entities are not supported
        throw UnsupportedOperationException()
    }

    @Throws(XmlPullParserException::class)
    override fun setFeature(name: String, state: Boolean) {
        // Features are not supported
        throw UnsupportedOperationException()
    }

    override fun getFeature(name: String): Boolean {
        // Features are not supported
        throw UnsupportedOperationException()
    }

    @Throws(XmlPullParserException::class)
    override fun setProperty(name: String, value: Any) {
        // Properties are not supported
        throw UnsupportedOperationException()
    }

    override fun getProperty(name: String): Any {
        // Properties are not supported
        throw UnsupportedOperationException()
    }

    /**
     * Holder representing a single attribute. This design enables object
     * recycling without resorting to autoboxing.
     * <p>
     * To support conversion between human-readable XML and binary XML, the
     * various accessor methods will transparently convert from/to
     * human-readable values when needed.
     */
    private class Attribute {
        var name: String? = null
        var type = 0

        var valueString: String? = null
        var valueBytes: ByteArray? = null
        var valueInt = 0
        var valueLong: Long = 0
        var valueFloat = 0f
        var valueDouble = 0.0

        fun reset() {
            name = null
            valueString = null
            valueBytes = null
        }

        fun getValueString(): String? {
            return when (type) {
                TYPE_NULL -> null
                TYPE_STRING, TYPE_STRING_INTERNED -> valueString
                TYPE_BYTES_HEX -> HexDump.toHexString(valueBytes!!)
                TYPE_BYTES_BASE64 -> Base64.encodeToString(valueBytes, Base64.NO_WRAP)
                TYPE_INT -> Integer.toString(valueInt)
                TYPE_INT_HEX -> Integer.toString(valueInt, 16)
                TYPE_LONG -> java.lang.Long.toString(valueLong)
                TYPE_LONG_HEX -> java.lang.Long.toString(valueLong, 16)
                TYPE_FLOAT -> java.lang.Float.toString(valueFloat)
                TYPE_DOUBLE -> java.lang.Double.toString(valueDouble)
                TYPE_BOOLEAN_TRUE -> "true"
                TYPE_BOOLEAN_FALSE -> "false"
                else -> null
            }
        }

        @Throws(XmlPullParserException::class)
        fun getValueBytesHex(): ByteArray? {
            return when (type) {
                TYPE_NULL -> null
                TYPE_BYTES_HEX, TYPE_BYTES_BASE64 -> valueBytes
                TYPE_STRING, TYPE_STRING_INTERNED -> try {
                    HexDump.hexStringToByteArray(valueString!!)
                } catch (e: Exception) {
                    throw XmlPullParserException("Invalid attribute $name: $e")
                }
                else -> throw XmlPullParserException("Invalid conversion from $type")
            }
        }

        @Throws(XmlPullParserException::class)
        fun getValueBytesBase64(): ByteArray? {
            return when (type) {
                TYPE_NULL -> null
                TYPE_BYTES_HEX, TYPE_BYTES_BASE64 -> valueBytes
                TYPE_STRING, TYPE_STRING_INTERNED -> try {
                    Base64.decode(valueString, Base64.NO_WRAP)
                } catch (e: Exception) {
                    throw XmlPullParserException("Invalid attribute $name: $e")
                }
                else -> throw XmlPullParserException("Invalid conversion from $type")
            }
        }

        @Throws(XmlPullParserException::class)
        fun getValueInt(): Int {
            return when (type) {
                TYPE_INT, TYPE_INT_HEX -> valueInt
                TYPE_STRING, TYPE_STRING_INTERNED -> try {
                    valueString!!.toInt()
                } catch (e: Exception) {
                    throw XmlPullParserException("Invalid attribute $name: $e")
                }
                else -> throw XmlPullParserException("Invalid conversion from $type")
            }
        }

        @Throws(XmlPullParserException::class)
        fun getValueIntHex(): Int {
            return when (type) {
                TYPE_INT, TYPE_INT_HEX -> valueInt
                TYPE_STRING, TYPE_STRING_INTERNED -> try {
                    valueString!!.toInt(16)
                } catch (e: Exception) {
                    throw XmlPullParserException("Invalid attribute $name: $e")
                }
                else -> throw XmlPullParserException("Invalid conversion from $type")
            }
        }

        @Throws(XmlPullParserException::class)
        fun getValueLong(): Long {
            return when (type) {
                TYPE_LONG, TYPE_LONG_HEX -> valueLong
                TYPE_STRING, TYPE_STRING_INTERNED -> try {
                    valueString!!.toLong()
                } catch (e: Exception) {
                    throw XmlPullParserException("Invalid attribute $name: $e")
                }
                else -> throw XmlPullParserException("Invalid conversion from $type")
            }
        }

        @Throws(XmlPullParserException::class)
        fun getValueLongHex(): Long {
            return when (type) {
                TYPE_LONG, TYPE_LONG_HEX -> valueLong
                TYPE_STRING, TYPE_STRING_INTERNED -> try {
                    valueString!!.toLong(16)
                } catch (e: Exception) {
                    throw XmlPullParserException("Invalid attribute $name: $e")
                }
                else -> throw XmlPullParserException("Invalid conversion from $type")
            }
        }

        @Throws(XmlPullParserException::class)
        fun getValueFloat(): Float {
            return when (type) {
                TYPE_FLOAT -> valueFloat
                TYPE_STRING, TYPE_STRING_INTERNED -> try {
                    valueString!!.toFloat()
                } catch (e: Exception) {
                    throw XmlPullParserException("Invalid attribute $name: $e")
                }
                else -> throw XmlPullParserException("Invalid conversion from $type")
            }
        }

        @Throws(XmlPullParserException::class)
        fun getValueDouble(): Double {
            return when (type) {
                TYPE_DOUBLE -> valueDouble
                TYPE_STRING, TYPE_STRING_INTERNED -> try {
                    valueString!!.toDouble()
                } catch (e: Exception) {
                    throw XmlPullParserException("Invalid attribute $name: $e")
                }
                else -> throw XmlPullParserException("Invalid conversion from $type")
            }
        }

        @Throws(XmlPullParserException::class)
        fun getValueBoolean(): Boolean {
            return when (type) {
                TYPE_BOOLEAN_TRUE -> true
                TYPE_BOOLEAN_FALSE -> false
                TYPE_STRING, TYPE_STRING_INTERNED -> if ("true".equals(valueString, ignoreCase = true)) {
                    true
                } else if ("false".equals(valueString, ignoreCase = true)) {
                    false
                } else {
                    throw XmlPullParserException("Invalid attribute $name: $valueString")
                }
                else -> throw XmlPullParserException("Invalid conversion from $type")
            }
        }
    }

    companion object {
        @Throws(XmlPullParserException::class)
        internal fun resolveEntity(entity: String): String {
            when (entity) {
                "lt" -> return "<"
                "gt" -> return ">"
                "amp" -> return "&"
                "apos" -> return "'"
                "quot" -> return "\""
            }
            if (entity.length > 1 && entity[0] == '#') {
                val c = entity.substring(1).toInt().toChar()
                return String(charArrayOf(c))
            }
            throw XmlPullParserException("Unknown entity $entity")
        }

        private fun illegalNamespace(): IllegalArgumentException {
            throw IllegalArgumentException("Namespaces are not supported")
        }
    }
}
