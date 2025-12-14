// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.editor

import android.app.Application
import android.net.Uri
import androidx.annotation.IntDef
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.github.muntashirakon.AppManager.apk.parser.AndroidBinXmlDecoder
import io.github.muntashirakon.AppManager.apk.parser.AndroidBinXmlEncoder
import io.github.muntashirakon.AppManager.dex.DexUtils
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.self.filecache.FileCache
import io.github.muntashirakon.AppManager.utils.ExUtils
import io.github.muntashirakon.compat.xml.TypedXmlPullParser
import io.github.muntashirakon.compat.xml.TypedXmlSerializer
import io.github.muntashirakon.compat.xml.Xml
import io.github.muntashirakon.io.CharSequenceInputStream
import io.github.muntashirakon.io.IoUtils
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import io.github.muntashirakon.lifecycle.SingleLiveEvent
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintStream
import java.io.Reader
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class CodeEditorViewModel(application: Application) : AndroidViewModel(application) {

    @IntDef(XML_TYPE_NONE, XML_TYPE_AXML, XML_TYPE_ABX)
    @Retention(AnnotationRetention.SOURCE)
    annotation class XmlType

    private var language: String? = null
    private var canGenerateJava = false
    private var xmlType = XML_TYPE_NONE
    private var sourceFile: Path? = null
    private var options: CodeEditorFragment.Options? = null
    private var contentLoaderJob: Job? = null
    private var javaConverterJob: Job? = null

    private val fileCache = FileCache()
    private val _contentLiveData = MutableLiveData<Content>()
    val contentLiveData: LiveData<Content> = _contentLiveData

    // Only for smali
    private val _javaFileLiveData = SingleLiveEvent<Uri>()
    val javaFileLiveData: LiveData<Uri> = _javaFileLiveData

    private val _saveFileLiveData = MutableLiveData<Boolean>()
    val saveFileLiveData: LiveData<Boolean> = _saveFileLiveData

    override fun onCleared() {
        contentLoaderJob?.cancel()
        javaConverterJob?.cancel()
        IoUtils.closeQuietly(fileCache)
        super.onCleared()
    }

    fun setOptions(options: CodeEditorFragment.Options) {
        this.options = options
        sourceFile = options.uri?.let { Paths.get(it) }
        val extension = sourceFile?.getExtension()
        language = getLanguageFromExt(extension)
        canGenerateJava = options.javaSmaliToggle || language == "smali"
    }

    fun getSourceFile(): Path? = sourceFile

    fun loadFileContentIfAvailable() {
        val file = sourceFile ?: return
        contentLoaderJob?.cancel()
        contentLoaderJob = viewModelScope.launch(Dispatchers.IO) {
            var content: Content? = null
            if (language == "xml") {
                val bytes = file.getContentAsBinary()
                val buffer = ByteBuffer.wrap(bytes)
                try {
                    when {
                        AndroidBinXmlDecoder.isBinaryXml(buffer) -> {
                            content = Content(AndroidBinXmlDecoder.decode(bytes))
                            xmlType = XML_TYPE_AXML
                        }
                        Xml.isBinaryXml(buffer) -> {
                            // FIXME: 19/5/23 Unfortunately, converting ABX to XML is lossy. Find a way to fix this.
                            //  Until then, the feature is disabled.
                            // content = getXmlFromAbx(bytes)
                            // xmlType = XML_TYPE_ABX
                        }
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Unable to convert XML bytes to plain text.: $e")
                }
            }
            if (content == null) {
                try {
                    file.openInputStream().use { inputStream ->
                        content = ContentIO.createFrom(inputStream)
                        xmlType = XML_TYPE_NONE
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Could not read file $file: $e")
                }
            }
            _contentLiveData.postValue(content)
        }
    }

    fun saveFile(content: Content, alternativeFile: Path?) {
        viewModelScope.launch(Dispatchers.IO) {
            if (sourceFile == null && alternativeFile == null) {
                _saveFileLiveData.postValue(false)
                return@launch
            }
            // Important: Alternative file gets the top priority
            val savingPath = alternativeFile ?: sourceFile!!
            try {
                savingPath.openOutputStream().use { os ->
                    when (xmlType) {
                        XML_TYPE_AXML -> {
                            // TODO: Use serializer from the latest update
                            val realContent = AndroidBinXmlEncoder.encodeString(content.toString())
                            os.write(realContent)
                        }
                        XML_TYPE_ABX -> {
                            CharSequenceInputStream(content, StandardCharsets.UTF_8).use { inputStream ->
                                copyAbxFromXml(inputStream, os)
                            }
                        }
                        else -> ContentIO.writeTo(content, os, false)
                    }
                }
                _saveFileLiveData.postValue(true)
            } catch (e: IOException) {
                Log.e(TAG, "Could not write to file $savingPath: $e")
                _saveFileLiveData.postValue(false)
            }
        }
    }

    fun isReadOnly(): Boolean = options?.readOnly ?: true

    fun canWrite(): Boolean = !isReadOnly() && sourceFile?.canWrite() == true

    fun isBackedByAFile(): Boolean = sourceFile != null

    fun getFilename(): String = sourceFile?.getName() ?: "untitled.txt"

    fun canGenerateJava(): Boolean = canGenerateJava

    fun getLanguage(): String? = language

    fun generateJava(smaliContent: Content) {
        if (!canGenerateJava) {
            return
        }
        javaConverterJob?.cancel()
        javaConverterJob = viewModelScope.launch(Dispatchers.IO) {
            val smaliContents = if (sourceFile != null) {
                val parent = sourceFile!!.getParent()
                val baseName = DexUtils.getClassNameWithoutInnerClasses(
                    Paths.trimPathExtension(sourceFile!!.getName())
                )
                val baseSmali = "$baseName.smali"
                val baseStartWith = "$baseName$"
                val paths = parent?.listFiles { _, name ->
                    name == baseSmali || name.startsWith(baseStartWith)
                } ?: emptyArray<Path>()

                val contents = ArrayList<String>(paths.size + 1)
                contents.add(smaliContent.toString())
                for (path in paths) {
                    if (path == sourceFile) {
                        // We already have this file
                        continue
                    }
                    val content = path.getContentAsString(null)
                    if (content != null) {
                        contents.add(content)
                    } else {
                        _javaFileLiveData.postValue(null)
                        return@launch
                    }
                }
                contents
            } else {
                listOf(smaliContent.toString())
            }

            if (!isActive) {
                return@launch
            }

            try {
                val cachedFile = fileCache.createCachedFile("java")
                PrintStream(cachedFile).use { ps ->
                    ps.print(DexUtils.toJavaCode(smaliContents, -1))
                }
                _javaFileLiveData.postValue(Uri.fromFile(cachedFile))
            } catch (e: Throwable) {
                e.printStackTrace()
                _javaFileLiveData.postValue(null)
            }
        }
    }

    companion object {
        const val TAG = "CodeEditorViewModel"

        const val XML_TYPE_NONE = 0
        const val XML_TYPE_AXML = 1
        const val XML_TYPE_ABX = 2

        private val EXT_TO_LANGUAGE_MAP = mapOf(
            // We skip the default ones
            "cmd" to "sh",
            "htm" to "xml",
            "html" to "xml",
            "kt" to "kotlin",
            "prop" to "properties",
            "tokens" to "properties",
            "xhtml" to "xml"
        )

        private fun getLanguageFromExt(ext: String?): String? {
            return EXT_TO_LANGUAGE_MAP[ext] ?: ext
        }

        private fun getXmlFromAbx(data: ByteArray): String {
            return try {
                BufferedInputStream(ByteArrayInputStream(data)).use { inputStream ->
                    ByteArrayOutputStream().use { os ->
                        val parser = Xml.newBinaryPullParser()
                        parser.setInput(inputStream, StandardCharsets.UTF_8.name())
                        val serializer = Xml.newFastSerializer()
                        serializer.setOutput(os, StandardCharsets.UTF_8.name())
                        copyXml(parser, serializer)
                        os.toString()
                    }
                }
            } catch (e: XmlPullParserException) {
                ExUtils.rethrowAsIOException(e)
            }
        }

        private fun copyAbxFromXml(inputStream: InputStream, out: OutputStream) {
            try {
                InputStreamReader(inputStream).use { reader ->
                    val parser = Xml.newFastPullParser()
                    parser.setInput(reader)
                    val serializer = Xml.newBinarySerializer()
                    serializer.setOutput(out, StandardCharsets.UTF_8.name())
                    copyXml(parser, serializer)
                }
            } catch (e: XmlPullParserException) {
                ExUtils.rethrowAsIOException(e)
            }
        }

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
                    XmlPullParser.IGNORABLE_WHITESPACE -> serializer.ignorableWhitespace(parser.text)
                    XmlPullParser.END_DOCUMENT -> serializer.endDocument()
                    else -> throw UnsupportedOperationException()
                }
            } while (event != XmlPullParser.END_DOCUMENT)
        }
    }
}
