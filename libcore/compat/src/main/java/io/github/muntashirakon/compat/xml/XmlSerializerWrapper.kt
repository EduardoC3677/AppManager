// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.compat.xml

import org.xmlpull.v1.XmlSerializer
import java.io.IOException
import java.io.OutputStream
import java.io.Writer
import java.util.*

/**
 * Wrapper which delegates all calls through to the given {@link XmlSerializer}.
 */
open class XmlSerializerWrapper(wrapped: XmlSerializer) : XmlSerializer {
    private val mWrapped: XmlSerializer = Objects.requireNonNull(wrapped)

    override fun setFeature(name: String, state: Boolean) {
        mWrapped.setFeature(name, state)
    }

    override fun getFeature(name: String): Boolean {
        return mWrapped.getFeature(name)
    }

    override fun setProperty(name: String, value: Any) {
        mWrapped.setProperty(name, value)
    }

    override fun getProperty(name: String): Any {
        return mWrapped.getProperty(name)
    }

    @Throws(IOException::class)
    override fun setOutput(os: OutputStream, encoding: String?) {
        mWrapped.setOutput(os, encoding)
    }

    @Throws(IOException::class, IllegalArgumentException::class, IllegalStateException::class)
    override fun setOutput(writer: Writer) {
        mWrapped.setOutput(writer)
    }

    @Throws(IOException::class)
    override fun startDocument(encoding: String?, standalone: Boolean?) {
        mWrapped.startDocument(encoding, standalone)
    }

    @Throws(IOException::class)
    override fun endDocument() {
        mWrapped.endDocument()
    }

    @Throws(IOException::class)
    override fun setPrefix(prefix: String, namespace: String) {
        mWrapped.setPrefix(prefix, namespace)
    }

    override fun getPrefix(namespace: String, generatePrefix: Boolean): String {
        return mWrapped.getPrefix(namespace, generatePrefix)
    }

    override fun getDepth(): Int {
        return mWrapped.depth
    }

    override fun getNamespace(): String {
        return mWrapped.namespace
    }

    override fun getName(): String {
        return mWrapped.name
    }

    @Throws(IOException::class)
    override fun startTag(namespace: String?, name: String): XmlSerializer {
        return mWrapped.startTag(namespace, name)
    }

    @Throws(IOException::class)
    override fun attribute(namespace: String?, name: String, value: String): XmlSerializer {
        return mWrapped.attribute(namespace, name, value)
    }

    @Throws(IOException::class)
    override fun endTag(namespace: String?, name: String): XmlSerializer {
        return mWrapped.endTag(namespace, name)
    }

    @Throws(IOException::class)
    override fun text(text: String): XmlSerializer {
        return mWrapped.text(text)
    }

    @Throws(IOException::class)
    override fun text(buf: CharArray, start: Int, len: Int): XmlSerializer {
        return mWrapped.text(buf, start, len)
    }

    @Throws(IOException::class, IllegalArgumentException::class, IllegalStateException::class)
    override fun cdsect(text: String) {
        mWrapped.cdsect(text)
    }

    @Throws(IOException::class)
    override fun entityRef(text: String) {
        mWrapped.entityRef(text)
    }

    @Throws(IOException::class)
    override fun processingInstruction(text: String) {
        mWrapped.processingInstruction(text)
    }

    @Throws(IOException::class)
    override fun comment(text: String) {
        mWrapped.comment(text)
    }

    @Throws(IOException::class)
    override fun docdecl(text: String) {
        mWrapped.docdecl(text)
    }

    @Throws(IOException::class)
    override fun ignorableWhitespace(text: String) {
        mWrapped.ignorableWhitespace(text)
    }

    @Throws(IOException::class)
    override fun flush() {
        mWrapped.flush()
    }
}
