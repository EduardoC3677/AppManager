// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.parser

import com.reandroid.apk.xmlencoder.XMLEncodeSource
import com.reandroid.xml.source.XMLFileParserSource
import com.reandroid.xml.source.XMLParserSource
import com.reandroid.xml.source.XMLStringParserSource
import java.io.File
import java.io.IOException

object AndroidBinXmlEncoder {
    @JvmStatic
    @Throws(IOException::class)
    fun encodeFile(file: File): ByteArray {
        return encode(XMLFileParserSource(file.name, file))
    }

    @JvmStatic
    @Throws(IOException::class)
    fun encodeString(xml: String): ByteArray {
        return encode(XMLStringParserSource("String.xml", xml))
    }

    @JvmStatic
    @Throws(IOException::class)
    private fun encode(xmlSource: XMLParserSource): ByteArray {
        val xmlEncodeSource = XMLEncodeSource(AndroidBinXmlDecoder.frameworkPackageBlock, xmlSource)
        return xmlEncodeSource.bytes
    }
}
