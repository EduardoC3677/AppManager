// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.compat.xml

import io.github.muntashirakon.compat.io.FastDataOutput
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlSerializer
import java.io.IOException
import java.io.OutputStream
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * Serializer that writes XML documents using a custom binary wire protocol
 * which benchmarking has shown to be 4.3x faster and use 2.4x less disk space
 * than {@code Xml.newFastSerializer()} for a typical {@code packages.xml}.
 * <p>
 * The high-level design of the wire protocol is to directly serialize the event
 * stream, while efficiently and compactly writing strongly-typed primitives
 * delivered through the {@link TypedXmlSerializer} interface.
 * <p>
 * Each serialized event is a single byte where the lower half is a normal
 * {@link XmlPullParser} token and the upper half is an optional data type
 * signal, such as {@link #TYPE_INT}.
 * <p>
 * This serializer has some specific limitations:
 * <ul>
 * <li>Only the UTF-8 encoding is supported.
 * <li>Variable length values, such as {@code byte[]} or {@link String}, are
 * limited to 65,535 bytes in length. Note that {@link String} values are stored
 * as UTF-8 on the wire.
 * <li>Namespaces, prefixes, properties, and options are unsupported.
 * </ul>
 */
class BinaryXmlSerializer : TypedXmlSerializer {
    private var mOut: FastDataOutput? = null

    /**
     * Stack of tags which are currently active via {@link #startTag} and which
     * haven't been terminated via {@link #endTag}.
     */
    private var mTagCount = 0
    private var mTagNames = arrayOfNulls<String>(8)

    /**
     * Write the given token and optional {@link String} into our buffer.
     */
    @Throws(IOException::class)
    private fun writeToken(token: Int, text: String?) {
        if (text != null) {
            mOut!!.writeByte(token or TYPE_STRING)
            mOut!!.writeUTF(text)
        } else {
            mOut!!.writeByte(token or TYPE_NULL)
        }
    }

    @Throws(IOException::class)
    override fun setOutput(os: OutputStream, encoding: String?) {
        if (encoding != null && !StandardCharsets.UTF_8.name().equals(encoding, ignoreCase = true)) {
            throw UnsupportedOperationException()
        }

        mOut = FastDataOutput.obtainUsing4ByteSequences(os)
        mOut!!.write(PROTOCOL_MAGIC_VERSION_0)

        mTagCount = 0
        mTagNames = arrayOfNulls(8)
    }

    override fun setOutput(writer: Writer) {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun flush() {
        if (mOut != null) {
            mOut!!.flush()
        }
    }

    @Throws(IOException::class)
    override fun startDocument(encoding: String?, standalone: Boolean?) {
        if (encoding != null && !StandardCharsets.UTF_8.name().equals(encoding, ignoreCase = true)) {
            throw UnsupportedOperationException()
        }
        if (standalone != null && !standalone) {
            throw UnsupportedOperationException()
        }
        mOut!!.writeByte(XmlPullParser.START_DOCUMENT or TYPE_NULL)
    }

    @Throws(IOException::class)
    override fun endDocument() {
        mOut!!.writeByte(XmlPullParser.END_DOCUMENT or TYPE_NULL)
        flush()

        mOut!!.release()
        mOut = null
    }

    override fun getDepth(): Int {
        return mTagCount
    }

    override fun getNamespace(): String {
        // Namespaces are unsupported
        return XmlPullParser.NO_NAMESPACE
    }

    override fun getName(): String? {
        return mTagNames[mTagCount - 1]
    }

    @Throws(IOException::class)
    override fun startTag(namespace: String?, name: String): XmlSerializer {
        if (!namespace.isNullOrEmpty()) throw illegalNamespace()
        if (mTagCount == mTagNames.size) {
            mTagNames = mTagNames.copyOf(mTagCount + (mTagCount shr 1))
        }
        mTagNames[mTagCount++] = name
        mOut!!.writeByte(XmlPullParser.START_TAG or TYPE_STRING_INTERNED)
        mOut!!.writeInternedUTF(name)
        return this
    }

    @Throws(IOException::class)
    override fun endTag(namespace: String?, name: String): XmlSerializer {
        if (!namespace.isNullOrEmpty()) throw illegalNamespace()
        mTagCount--
        mOut!!.writeByte(XmlPullParser.END_TAG or TYPE_STRING_INTERNED)
        mOut!!.writeInternedUTF(name)
        return this
    }

    @Throws(IOException::class)
    override fun attribute(namespace: String?, name: String, value: String): XmlSerializer {
        if (!namespace.isNullOrEmpty()) throw illegalNamespace()
        mOut!!.writeByte(ATTRIBUTE or TYPE_STRING)
        mOut!!.writeInternedUTF(name)
        mOut!!.writeUTF(value)
        return this
    }

    @Throws(IOException::class)
    override fun attributeInterned(namespace: String?, name: String, value: String): XmlSerializer {
        if (!namespace.isNullOrEmpty()) throw illegalNamespace()
        mOut!!.writeByte(ATTRIBUTE or TYPE_STRING_INTERNED)
        mOut!!.writeInternedUTF(name)
        mOut!!.writeInternedUTF(value)
        return this
    }

    @Throws(IOException::class)
    override fun attributeBytesHex(namespace: String?, name: String, value: ByteArray): XmlSerializer {
        if (!namespace.isNullOrEmpty()) throw illegalNamespace()
        mOut!!.writeByte(ATTRIBUTE or TYPE_BYTES_HEX)
        mOut!!.writeInternedUTF(name)
        mOut!!.writeShort(value.size)
        mOut!!.write(value)
        return this
    }

    @Throws(IOException::class)
    override fun attributeBytesBase64(namespace: String?, name: String, value: ByteArray): XmlSerializer {
        if (!namespace.isNullOrEmpty()) throw illegalNamespace()
        mOut!!.writeByte(ATTRIBUTE or TYPE_BYTES_BASE64)
        mOut!!.writeInternedUTF(name)
        mOut!!.writeShort(value.size)
        mOut!!.write(value)
        return this
    }

    @Throws(IOException::class)
    override fun attributeInt(namespace: String?, name: String, value: Int): XmlSerializer {
        if (!namespace.isNullOrEmpty()) throw illegalNamespace()
        mOut!!.writeByte(ATTRIBUTE or TYPE_INT)
        mOut!!.writeInternedUTF(name)
        mOut!!.writeInt(value)
        return this
    }

    @Throws(IOException::class)
    override fun attributeIntHex(namespace: String?, name: String, value: Int): XmlSerializer {
        if (!namespace.isNullOrEmpty()) throw illegalNamespace()
        mOut!!.writeByte(ATTRIBUTE or TYPE_INT_HEX)
        mOut!!.writeInternedUTF(name)
        mOut!!.writeInt(value)
        return this
    }

    @Throws(IOException::class)
    override fun attributeLong(namespace: String?, name: String, value: Long): XmlSerializer {
        if (!namespace.isNullOrEmpty()) throw illegalNamespace()
        mOut!!.writeByte(ATTRIBUTE or TYPE_LONG)
        mOut!!.writeInternedUTF(name)
        mOut!!.writeLong(value)
        return this
    }

    @Throws(IOException::class)
    override fun attributeLongHex(namespace: String?, name: String, value: Long): XmlSerializer {
        if (!namespace.isNullOrEmpty()) throw illegalNamespace()
        mOut!!.writeByte(ATTRIBUTE or TYPE_LONG_HEX)
        mOut!!.writeInternedUTF(name)
        mOut!!.writeLong(value)
        return this
    }

    @Throws(IOException::class)
    override fun attributeFloat(namespace: String?, name: String, value: Float): XmlSerializer {
        if (!namespace.isNullOrEmpty()) throw illegalNamespace()
        mOut!!.writeByte(ATTRIBUTE or TYPE_FLOAT)
        mOut!!.writeInternedUTF(name)
        mOut!!.writeFloat(value)
        return this
    }

    @Throws(IOException::class)
    override fun attributeDouble(namespace: String?, name: String, value: Double): XmlSerializer {
        if (!namespace.isNullOrEmpty()) throw illegalNamespace()
        mOut!!.writeByte(ATTRIBUTE or TYPE_DOUBLE)
        mOut!!.writeInternedUTF(name)
        mOut!!.writeDouble(value)
        return this
    }

    @Throws(IOException::class)
    override fun attributeBoolean(namespace: String?, name: String, value: Boolean): XmlSerializer {
        if (!namespace.isNullOrEmpty()) throw illegalNamespace()
        if (value) {
            mOut!!.writeByte(ATTRIBUTE or TYPE_BOOLEAN_TRUE)
            mOut!!.writeInternedUTF(name)
        } else {
            mOut!!.writeByte(ATTRIBUTE or TYPE_BOOLEAN_FALSE)
            mOut!!.writeInternedUTF(name)
        }
        return this
    }

    @Throws(IOException::class)
    override fun text(buf: CharArray, start: Int, len: Int): XmlSerializer {
        writeToken(XmlPullParser.TEXT, String(buf, start, len))
        return this
    }

    @Throws(IOException::class)
    override fun text(text: String): XmlSerializer {
        writeToken(XmlPullParser.TEXT, text)
        return this
    }

    @Throws(IOException::class)
    override fun cdsect(text: String) {
        writeToken(XmlPullParser.CDSECT, text)
    }

    @Throws(IOException::class)
    override fun entityRef(text: String) {
        writeToken(XmlPullParser.ENTITY_REF, text)
    }

    @Throws(IOException::class)
    override fun processingInstruction(text: String) {
        writeToken(XmlPullParser.PROCESSING_INSTRUCTION, text)
    }

    @Throws(IOException::class)
    override fun comment(text: String) {
        writeToken(XmlPullParser.COMMENT, text)
    }

    @Throws(IOException::class)
    override fun docdecl(text: String) {
        writeToken(XmlPullParser.DOCDECL, text)
    }

    @Throws(IOException::class)
    override fun ignorableWhitespace(text: String) {
        writeToken(XmlPullParser.IGNORABLE_WHITESPACE, text)
    }

    override fun setFeature(name: String, state: Boolean) {
        // Quietly handle no-op features
        if ("http://xmlpull.org/v1/doc/features.html#indent-output" == name) {
            return
        }
        // Features are not supported
        throw UnsupportedOperationException()
    }

    override fun getFeature(name: String): Boolean {
        // Features are not supported
        throw UnsupportedOperationException()
    }

    override fun setProperty(name: String, value: Any) {
        // Properties are not supported
        throw UnsupportedOperationException()
    }

    override fun getProperty(name: String): Any {
        // Properties are not supported
        throw UnsupportedOperationException()
    }

    override fun setPrefix(prefix: String, namespace: String) {
        // Prefixes are not supported
        throw UnsupportedOperationException()
    }

    override fun getPrefix(namespace: String, generatePrefix: Boolean): String {
        // Prefixes are not supported
        throw UnsupportedOperationException()
    }

    companion object {
        /**
         * The wire protocol always begins with a well-known magic value of
         * {@code ABX_}, representing "Android Binary XML." The final byte is a
         * version number which may be incremented as the protocol changes.
         */
        @JvmField
        val PROTOCOL_MAGIC_VERSION_0 = byteArrayOf(0x41, 0x42, 0x58, 0x00)

        /**
         * Internal token which represents an attribute associated with the most
         * recent {@link XmlPullParser#START_TAG} token.
         */
        internal const val ATTRIBUTE = 15

        internal const val TYPE_NULL = 1 shl 4
        internal const val TYPE_STRING = 2 shl 4
        internal const val TYPE_STRING_INTERNED = 3 shl 4
        internal const val TYPE_BYTES_HEX = 4 shl 4
        internal const val TYPE_BYTES_BASE64 = 5 shl 4
        internal const val TYPE_INT = 6 shl 4
        internal const val TYPE_INT_HEX = 7 shl 4
        internal const val TYPE_LONG = 8 shl 4
        internal const val TYPE_LONG_HEX = 9 shl 4
        internal const val TYPE_FLOAT = 10 shl 4
        internal const val TYPE_DOUBLE = 11 shl 4
        internal const val TYPE_BOOLEAN_TRUE = 12 shl 4
        internal const val TYPE_BOOLEAN_FALSE = 13 shl 4

        private fun illegalNamespace(): IllegalArgumentException {
            throw IllegalArgumentException("Namespaces are not supported")
        }
    }
}
