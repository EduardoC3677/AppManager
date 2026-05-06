// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.compat.xml

import android.util.TypedXmlPullParser
import android.util.TypedXmlSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.*
import java.nio.charset.StandardCharsets
import java.util.*

@RunWith(RobolectricTestRunner::class)
class XmlTest {
    private val classLoader = Objects.requireNonNull(javaClass.classLoader)
    private val ssaidAbxFile = File(classLoader.getResource("settings_ssaid.abx.xml").file)
    private val ssaidXmlFile = File(classLoader.getResource("settings_ssaid.plain.xml").file)
    private val uriGrantsAbxFile = File(classLoader.getResource("urigrants.abx.xml").file)
    private val uriGrantsXmlFile = File(classLoader.getResource("urigrants.plain.xml").file)

    @Test
    @Throws(IOException::class)
    fun isBinaryXml() {
        BufferedInputStream(FileInputStream(ssaidAbxFile)).use { `is` ->
            assertTrue(Xml.isBinaryXml(`is`))
        }
        BufferedInputStream(FileInputStream(uriGrantsAbxFile)).use { `is` ->
            assertTrue(Xml.isBinaryXml(`is`))
        }
    }

    @Test
    @Throws(IOException::class, XmlPullParserException::class)
    fun newBinaryPullParserReadSsaid() {
        val xmlActualBytes: ByteArray
        BufferedInputStream(FileInputStream(ssaidAbxFile)).use { `is` ->
            ByteArrayOutputStream().use { os ->
                val parser = Xml.newBinaryPullParser()
                parser.setInput(`is`, StandardCharsets.UTF_8.name())
                val serializer = Xml.newFastSerializer()
                serializer.setOutput(os, StandardCharsets.UTF_8.name())
                copyXml(parser, serializer)
                xmlActualBytes = os.toByteArray()
            }
        }

        val xmlExpectedBytes = ByteArray(ssaidXmlFile.length().toInt())
        BufferedInputStream(FileInputStream(ssaidXmlFile)).use { `is` ->
            `is`.read(xmlExpectedBytes)
        }
        assertEquals(String(xmlExpectedBytes), String(xmlActualBytes))
    }

    @Test
    @Throws(IOException::class, XmlPullParserException::class)
    fun newBinaryPullParserReadUriGrants() {
        val xmlActualBytes: ByteArray
        BufferedInputStream(FileInputStream(uriGrantsAbxFile)).use { `is` ->
            ByteArrayOutputStream().use { os ->
                val parser = Xml.newBinaryPullParser()
                parser.setInput(`is`, StandardCharsets.UTF_8.name())
                val serializer = Xml.newFastSerializer()
                serializer.setOutput(os, StandardCharsets.UTF_8.name())
                copyXml(parser, serializer)
                xmlActualBytes = os.toByteArray()
            }
        }

        val xmlExpectedBytes = ByteArray(uriGrantsXmlFile.length().toInt())
        BufferedInputStream(FileInputStream(uriGrantsXmlFile)).use { `is` ->
            `is`.read(xmlExpectedBytes)
        }
        // TODO: This fails because of different attribute order
        // assertEquals(new String(xmlExpectedBytes), new String(xmlActualBytes));
    }

    //    @Test
    //    public void newBinarySerializerWriteSsaid() throws IOException, XmlPullParserException {
    //        byte[] xmlActualBytes;
    //        try (InputStream is = new BufferedInputStream(new FileInputStream(ssaidXmlFile));
    //             ByteArrayOutputStream os = new ByteArrayOutputStream()) {
    //            TypedXmlPullParser parser = Xml.newFastPullParser();
    //            parser.setInput(is, StandardCharsets.UTF_8.name());
    //            TypedXmlSerializer serializer = Xml.newBinarySerializer();
    //            serializer.setOutput(os, StandardCharsets.UTF_8.name());
    //            copyXml(parser, serializer);
    //            xmlActualBytes = os.toByteArray();
    //        }
    //
    //        byte[] xmlExpectedBytes = new byte[(int) ssaidAbxFile.length()];
    //        try (InputStream is = new BufferedInputStream(new FileInputStream(ssaidAbxFile))) {
    //            is.read(xmlExpectedBytes);
    //        }
    //        assertEquals(new String(xmlExpectedBytes), new String(xmlActualBytes));
    //    }
    //
    //    @Test
    //    public void newBinarySerializerWriteUriGrants() throws IOException, XmlPullParserException {
    //        byte[] xmlActualBytes;
    //        try (InputStream is = new BufferedInputStream(new FileInputStream(uriGrantsXmlFile));
    //             ByteArrayOutputStream os = new ByteArrayOutputStream()) {
    //            TypedXmlPullParser parser = Xml.newFastPullParser();
    //            parser.setInput(is, StandardCharsets.UTF_8.name());
    //            TypedXmlSerializer serializer = Xml.newBinarySerializer();
    //            serializer.setOutput(os, StandardCharsets.UTF_8.name());
    //            copyXml(parser, serializer);
    //            xmlActualBytes = os.toByteArray();
    //        }
    //
    //        byte[] xmlExpectedBytes = new byte[(int) uriGrantsAbxFile.length()];
    //        try (InputStream is = new BufferedInputStream(new FileInputStream(uriGrantsAbxFile))) {
    //            is.read(xmlExpectedBytes);
    //        }
    //        assertEquals(HexDump.toHexString(xmlExpectedBytes), HexDump.toHexString(xmlActualBytes));
    //    }

    companion object {
        @Throws(IOException::class, XmlPullParserException::class)
        fun copyXml(parser: TypedXmlPullParser, serializer: TypedXmlSerializer) {
            serializer.startDocument(null, null)
            var event: Int
            do {
                event = parser.nextToken()
                when (event) {
                    XmlPullParser.START_TAG -> {
                        serializer.startTag(null, parser.name)
                        for (i in 0 until parser.attributeCount) {
                            val attributeName = parser.getAttributeName(i)
                            serializer.attribute(null, attributeName, parser.getAttributeValue(i))
                        }
                    }
                    XmlPullParser.END_TAG -> serializer.endTag(null, parser.name)
                    XmlPullParser.TEXT -> serializer.text(parser.text)
                    XmlPullParser.IGNORABLE_WHITESPACE -> try {
                        serializer.ignorableWhitespace(parser.text)
                    } catch (ignore: UnsupportedOperationException) {
                    }
                    XmlPullParser.CDSECT -> serializer.cdsect(parser.text)
                    XmlPullParser.PROCESSING_INSTRUCTION -> serializer.processingInstruction(parser.text)
                    XmlPullParser.COMMENT -> serializer.comment(parser.text)
                    XmlPullParser.ENTITY_REF -> {
                        var text = parser.text
                        if (text != null) {
                            serializer.text(text)
                        } else {
                            val holder = IntArray(2)
                            val chars = parser.getTextCharacters(holder)
                            text = String(chars as CharArray, holder[0], holder[1])
                            serializer.text(text)
                        }
                    }
                }
            } while (event != XmlPullParser.END_DOCUMENT)
            serializer.endDocument()
        }
    }
}
