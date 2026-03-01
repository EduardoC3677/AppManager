// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.parser

import android.text.TextUtils
import com.reandroid.apk.AndroidFrameworks
import com.reandroid.arsc.chunk.PackageBlock
import com.reandroid.arsc.chunk.xml.ResXmlDocument
import com.reandroid.arsc.chunk.xml.ResXmlPullParser
import com.reandroid.arsc.io.BlockReader
import io.github.muntashirakon.AppManager.utils.IntegerUtils
import io.github.muntashirakon.io.IoUtils
import org.xmlpull.v1.XmlPullParser.*
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

object AndroidBinXmlDecoder {
    @JvmStatic
    fun isBinaryXml(buffer: ByteBuffer): Boolean {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.mark()
        val version = IntegerUtils.getUInt16(buffer)
        val header = IntegerUtils.getUInt16(buffer)
        buffer.reset()
        // 0x0000 is NULL header. The only example of application using a NULL header is NP Manager
        return (version == 0x0003 || version == 0x0000) && header == 0x0008
    }

    @JvmStatic
    @Throws(IOException::class)
    fun decode(data: ByteArray): String {
        return decode(ByteBuffer.wrap(data))
    }

    @JvmStatic
    @Throws(IOException::class)
    fun decode(`is`: InputStream): String {
        val buffer = ByteArrayOutputStream()
        val buf = ByteArray(IoUtils.DEFAULT_BUFFER_SIZE)
        var n: Int
        while (`is`.read(buf).also { n = it } != -1) {
            buffer.write(buf, 0, n)
        }
        return decode(buffer.toByteArray())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun decode(byteBuffer: ByteBuffer): String {
        ByteArrayOutputStream().use { bos ->
            decode(byteBuffer, bos)
            val bs = bos.toByteArray()
            return String(bs, StandardCharsets.UTF_8)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun decode(byteBuffer: ByteBuffer, os: OutputStream) {
        try {
            BlockReader(byteBuffer.array()).use { reader ->
                PrintStream(os).use { out ->
                    val resXmlDocument = ResXmlDocument()
                    resXmlDocument.readBytes(reader)
                    resXmlDocument.setPackageBlock(frameworkPackageBlock)
                    ResXmlPullParser(resXmlDocument).use { parser ->
                        val indent = StringBuilder(10)
                        val indentStep = "  "
                        out.println("<?xml version="1.0" encoding="utf-8"?>")
                        while (true) {
                            val type = parser.next()
                            when (type) {
                                START_TAG -> {
                                    out.printf("%s<%s%s", indent, getNamespacePrefix(parser.prefix), parser.name)
                                    indent.append(indentStep)
                                    val nsStart = parser.getNamespaceCount(parser.depth - 1)
                                    val nsEnd = parser.getNamespaceCount(parser.depth)
                                    for (i in nsStart until nsEnd) {
                                        out.printf("
%sxmlns:%s="%s"", indent, parser.getNamespacePrefix(i), parser.getNamespaceUri(i))
                                    }
                                    for (i in 0 until parser.attributeCount) {
                                        out.printf("
%s%s%s="%s"", indent, getNamespacePrefix(parser.getAttributePrefix(i)), parser.getAttributeName(i), parser.getAttributeValue(i))
                                    }
                                    out.println(">")
                                }
                                END_TAG -> {
                                    indent.setLength(indent.length - indentStep.length)
                                    out.printf("%s</%s%s>%n", indent, getNamespacePrefix(parser.prefix), parser.name)
                                }
                                END_DOCUMENT -> return
                                START_DOCUMENT -> {
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            throw IOException(e)
        }
    }

    private fun getNamespacePrefix(prefix: String?): String {
        return if (TextUtils.isEmpty(prefix)) "" else "$prefix:"
    }

    @get:JvmStatic
    val frameworkPackageBlock: PackageBlock
        get() {
            if (sFrameworkPackageBlock != null) {
                return sFrameworkPackageBlock!!
            }
            sFrameworkPackageBlock = AndroidFrameworks.getLatest().tableBlock.allPackages.next()
            return sFrameworkPackageBlock!!
        }

    private var sFrameworkPackageBlock: PackageBlock? = null
}
