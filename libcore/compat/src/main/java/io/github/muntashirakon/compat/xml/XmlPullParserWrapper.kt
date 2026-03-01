// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.compat.xml

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream
import java.io.Reader
import java.util.*

/**
 * Wrapper which delegates all calls through to the given {@link XmlPullParser}.
 */
open class XmlPullParserWrapper(wrapped: XmlPullParser) : XmlPullParser {
    private val mWrapped: XmlPullParser = Objects.requireNonNull(wrapped)

    @Throws(XmlPullParserException::class)
    override fun setFeature(name: String, state: Boolean) {
        mWrapped.setFeature(name, state)
    }

    override fun getFeature(name: String): Boolean {
        return mWrapped.getFeature(name)
    }

    @Throws(XmlPullParserException::class)
    override fun setProperty(name: String, value: Any) {
        mWrapped.setProperty(name, value)
    }

    override fun getProperty(name: String): Any {
        return mWrapped.getProperty(name)
    }

    @Throws(XmlPullParserException::class)
    override fun setInput(`in`: Reader) {
        mWrapped.setInput(`in`)
    }

    @Throws(XmlPullParserException::class)
    override fun setInput(inputStream: InputStream, inputEncoding: String?) {
        mWrapped.setInput(inputStream, inputEncoding)
    }

    override fun getInputEncoding(): String {
        return mWrapped.inputEncoding
    }

    @Throws(XmlPullParserException::class)
    override fun defineEntityReplacementText(entityName: String, replacementText: String) {
        mWrapped.defineEntityReplacementText(entityName, replacementText)
    }

    @Throws(XmlPullParserException::class)
    override fun getNamespaceCount(depth: Int): Int {
        return mWrapped.getNamespaceCount(depth)
    }

    @Throws(XmlPullParserException::class)
    override fun getNamespacePrefix(pos: Int): String {
        return mWrapped.getNamespacePrefix(pos)
    }

    @Throws(XmlPullParserException::class)
    override fun getNamespaceUri(pos: Int): String {
        return mWrapped.getNamespaceUri(pos)
    }

    override fun getNamespace(prefix: String): String {
        return mWrapped.getNamespace(prefix)
    }

    override fun getDepth(): Int {
        return mWrapped.depth
    }

    override fun getPositionDescription(): String {
        return mWrapped.positionDescription
    }

    override fun getLineNumber(): Int {
        return mWrapped.lineNumber
    }

    override fun getColumnNumber(): Int {
        return mWrapped.columnNumber
    }

    @Throws(XmlPullParserException::class)
    override fun isWhitespace(): Boolean {
        return mWrapped.isWhitespace
    }

    override fun getText(): String? {
        return mWrapped.text
    }

    override fun getTextCharacters(holderForStartAndLength: IntArray): CharArray {
        return mWrapped.getTextCharacters(holderForStartAndLength)
    }

    override fun getNamespace(): String {
        return mWrapped.namespace
    }

    override fun getName(): String {
        return mWrapped.name
    }

    override fun getPrefix(): String {
        return mWrapped.prefix
    }

    @Throws(XmlPullParserException::class)
    override fun isEmptyElementTag(): Boolean {
        return mWrapped.isEmptyElementTag
    }

    override fun getAttributeCount(): Int {
        return mWrapped.attributeCount
    }

    override fun getAttributeNamespace(index: Int): String {
        return mWrapped.getAttributeNamespace(index)
    }

    override fun getAttributeName(index: Int): String {
        return mWrapped.getAttributeName(index)
    }

    override fun getAttributePrefix(index: Int): String {
        return mWrapped.getAttributePrefix(index)
    }

    override fun getAttributeType(index: Int): String {
        return mWrapped.getAttributeType(index)
    }

    override fun isAttributeDefault(index: Int): Boolean {
        return mWrapped.isAttributeDefault(index)
    }

    override fun getAttributeValue(index: Int): String {
        return mWrapped.getAttributeValue(index)
    }

    override fun getAttributeValue(namespace: String?, name: String): String {
        return mWrapped.getAttributeValue(namespace, name)
    }

    @Throws(XmlPullParserException::class)
    override fun getEventType(): Int {
        return mWrapped.eventType
    }

    @Throws(XmlPullParserException::class, IOException::class)
    override fun next(): Int {
        return mWrapped.next()
    }

    @Throws(XmlPullParserException::class, IOException::class)
    override fun nextToken(): Int {
        return mWrapped.nextToken()
    }

    @Throws(XmlPullParserException::class, IOException::class)
    override fun require(type: Int, namespace: String?, name: String?) {
        mWrapped.require(type, namespace, name)
    }

    @Throws(XmlPullParserException::class, IOException::class)
    override fun nextText(): String {
        return mWrapped.nextText()
    }

    @Throws(XmlPullParserException::class, IOException::class)
    override fun nextTag(): Int {
        return mWrapped.nextTag()
    }
}
