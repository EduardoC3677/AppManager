// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.compat.xml

import android.os.Build
import android.system.ErrnoException
import android.system.Os
import android.util.TypedXmlPullParser
import android.util.TypedXmlSerializer
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.*
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.*

object Xml {
    /**
     * Feature flag: when set, {@link #resolveSerializer(OutputStream)} will
     * emit binary XML by default.
     */
    @JvmField
    val ENABLE_BINARY_DEFAULT: Boolean

    init {
        var useAbx: Boolean
        try {
            useAbx = Class.forName("android.os.SystemProperties")
                .getDeclaredMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
                .invoke(null, "persist.sys.binary_xml", Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) as Boolean
        } catch (ignore: Exception) {
            useAbx = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        }
        ENABLE_BINARY_DEFAULT = useAbx
    }

    @JvmStatic
    @Throws(IOException::class)
    fun isBinaryXml(`in`: InputStream): Boolean {
        var inputStream = `in`
        val magic = ByteArray(4)
        if (inputStream is FileInputStream) {
            try {
                Os.pread(inputStream.fd, magic, 0, magic.size, 0)
            } catch (e: ErrnoException) {
                throw IOException(e.message, e)
            }
        } else {
            if (!inputStream.markSupported()) {
                inputStream = BufferedInputStream(inputStream)
            }
            inputStream.mark(8)
            inputStream.read(magic)
            inputStream.reset()
        }
        return Arrays.equals(magic, BinaryXmlSerializer.PROTOCOL_MAGIC_VERSION_0)
    }

    @JvmStatic
    fun isBinaryXml(buffer: ByteBuffer): Boolean {
        val magic = ByteArray(4)
        buffer.mark()
        buffer.get(magic)
        buffer.reset()
        return Arrays.equals(magic, BinaryXmlSerializer.PROTOCOL_MAGIC_VERSION_0)
    }

    /**
     * Creates a new {@link TypedXmlPullParser} which is optimized for use
     * inside the system, typically by supporting only a basic set of features.
     * <p>
     * In particular, the returned parser does not support namespaces, prefixes,
     * properties, or options.
     */
    @JvmStatic
    fun newFastPullParser(): TypedXmlPullParser {
        return XmlUtils.makeTyped(Xml.newPullParser())
    }

    /**
     * Creates a new {@link XmlPullParser} that reads XML documents using a
     * custom binary wire protocol which benchmarking has shown to be 8.5x
     * faster than {@code Xml.newFastPullParser()} for a typical
     * {@code packages.xml}.
     */
    @JvmStatic
    fun newBinaryPullParser(): TypedXmlPullParser {
        return BinaryXmlPullParser()
    }

    /**
     * Creates a new {@link XmlPullParser} which is optimized for use inside the
     * system, typically by supporting only a basic set of features.
     * <p>
     * This returned instance may be configured to read using an efficient
     * binary format instead of a human-readable text format, depending on
     * device feature flags.
     * <p>
     * To ensure that both formats are detected and transparently handled
     * correctly, you must shift to using both {@link #resolveSerializer} and
     * {@code #resolvePullParser}.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun resolvePullParser(`in`: InputStream): TypedXmlPullParser {
        var inputStream = `in`
        if (!inputStream.markSupported()) {
            inputStream = BufferedInputStream(inputStream)
        }
        val xml: TypedXmlPullParser = if (isBinaryXml(inputStream)) {
            newBinaryPullParser()
        } else {
            newFastPullParser()
        }
        try {
            xml.setInput(inputStream, StandardCharsets.UTF_8.name())
        } catch (e: XmlPullParserException) {
            throw IOException(e)
        }
        return xml
    }

    /**
     * Creates a new {@link XmlSerializer} which is optimized for use inside the
     * system, typically by supporting only a basic set of features.
     * <p>
     * In particular, the returned parser does not support namespaces, prefixes,
     * properties, or options.
     */
    @JvmStatic
    fun newFastSerializer(): TypedXmlSerializer {
        return XmlUtils.makeTyped(FastXmlSerializer())
    }

    /**
     * Creates a new {@link XmlSerializer} that writes XML documents using a
     * custom binary wire protocol which benchmarking has shown to be 4.4x
     * faster and use 2.8x less disk space than {@code Xml.newFastSerializer()}
     * for a typical {@code packages.xml}.
     */
    @JvmStatic
    fun newBinarySerializer(): TypedXmlSerializer {
        return BinaryXmlSerializer()
    }

    /**
     * Creates a new {@link XmlSerializer} which is optimized for use inside the
     * system, typically by supporting only a basic set of features.
     * <p>
     * This returned instance may be configured to write using an efficient
     * binary format instead of a human-readable text format, depending on
     * device feature flags.
     * <p>
     * To ensure that both formats are detected and transparently handled
     * correctly, you must shift to using both {@code #resolveSerializer} and
     * {@link #resolvePullParser}.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun resolveSerializer(out: OutputStream): TypedXmlSerializer {
        val xml: TypedXmlSerializer = if (ENABLE_BINARY_DEFAULT) {
            newBinarySerializer()
        } else {
            newFastSerializer()
        }
        xml.setOutput(out, StandardCharsets.UTF_8.name())
        return xml
    }
}
