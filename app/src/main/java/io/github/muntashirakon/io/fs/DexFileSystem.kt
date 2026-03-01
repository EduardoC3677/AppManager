// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io.fs

import android.system.OsConstants
import android.util.LruCache
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.writer.io.FileDataStore
import io.github.muntashirakon.AppManager.dex.DexClasses
import io.github.muntashirakon.AppManager.dex.DexUtils
import io.github.muntashirakon.AppManager.fm.ContentType2
import io.github.muntashirakon.AppManager.self.filecache.FileCache
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import org.antlr.runtime.RecognitionException
import java.io.*
import java.nio.charset.StandardCharsets
import java.util.*

internal class DexFileSystem(dexPath: Path) : VirtualFileSystem(dexPath) {
    companion object {
        @JvmField
        val TYPE: String = ContentType2.DEX.mimeType

        @JvmStatic
        private fun buildTree(dexClasses: DexClasses): Node<ClassDef> {
            val rootNode = Node<ClassDef>(null, File.separator)
            val classNames = dexClasses.classNames
            for (className in classNames) {
                val classDef = try {
                    dexClasses.getClassDef(className)
                } catch (e: ClassNotFoundException) {
                    null
                }
                buildTree(rootNode, className, classDef!!)
            }
            return rootNode
        }

        // Build nodes as needed by the entry, entry itself is the last node in the tree if it is not a directory
        private fun buildTree(rootNode: Node<ClassDef>, className: String, classDef: ClassDef) {
            val components = className.split("\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (components.isEmpty()) {
                return
            }
            var lastNode = rootNode
            for (i in 0 until components.size - 1 /* last one will be set manually */) {
                var newNode = lastNode.getChild(components[i])
                if (newNode == null) {
                    // Add children
                    newNode = Node(lastNode, components[i])
                    lastNode.addChild(newNode)
                }
                lastNode = newNode
            }
            lastNode.addChild(Node(lastNode, components[components.size - 1] + ".smali", classDef))
        }
    }

    private class ClassInfo(
        val cachedFile: File?,
        val physical: Boolean,
        val directory: Boolean = false
    )

    private val mCache = LruCache<String, Node<ClassDef>>(100)
    private var mDexClasses: DexClasses? = null
    private var mRootNode: Node<ClassDef>? = null

    fun getApiLevel(): Int {
        // TODO: 26/11/22 Set via MountOptions
        return -1
    }

    override fun getType(): String {
        return TYPE
    }

    fun getDexClasses(): DexClasses {
        checkMounted()
        return mDexClasses!!
    }

    @Throws(IOException::class)
    override fun onMount(): Path {
        if (".dex" == getFile().getExtension()) {
            getFile().openInputStream().use { `is` -> mDexClasses = DexClasses(`is`, getApiLevel()) }
        } else { // APK/Zip file, may need caching
            val file = getFile().getFile()
            if (file != null) {
                mDexClasses = DexClasses(file, getApiLevel())
            } else {
                val cachedFile = FileCache.getGlobalFileCache().getCachedFile(getFile())
                mDexClasses = DexClasses(cachedFile, getApiLevel())
            }
        }
        mRootNode = buildTree(mDexClasses!!)
        return Paths.get(this)
    }

    @Throws(IOException::class)
    override fun onUnmount(actions: Map<String, List<Action>>): File? {
        val cachedFile = getUpdatedDexFile(actions)
        if (mDexClasses != null) {
            mDexClasses!!.close()
        }
        mRootNode = null
        mCache.evictAll()
        return cachedFile
    }

    @Throws(IOException::class)
    private fun getUpdatedDexFile(actionList: Map<String, List<Action>>): File? {
        if (!getOptions()!!.readWrite || actionList.isEmpty()) {
            return null
        }
        val extension = getFile().getExtension()
        val file = FileCache.getGlobalFileCache().createCachedFile(extension)
        val classInfoMap = HashMap<String, ClassInfo>()
        for (className in mDexClasses!!.classNames) {
            classInfoMap[File.separator + className] = ClassInfo(null, true)
        }
        for (path in actionList.keys) {
            // Perform action for each path
            val actions = actionList[path] ?: continue
            for (action in actions) {
                val targetNode = action.targetNode
                // Actions are linear
                when (action.action) {
                    ACTION_CREATE ->                         // This must be a new file/folder. So, override the existing one.
                        classInfoMap[targetNode.getFullPath()] = ClassInfo(null, false, targetNode.isDirectory())
                    ACTION_DELETE ->                         // Delete the entry
                        classInfoMap.remove(targetNode.getFullPath())
                    ACTION_UPDATE -> {
                        // It's a file and it's updated. So, cached file must exist.
                        val cachedFile = action.cachedPath!!
                        val targetPath = targetNode.getFullPath()
                        val classInfo = classInfoMap[targetPath]
                        classInfoMap[targetPath] = ClassInfo(cachedFile, classInfo != null && classInfo.physical)
                    }
                    ACTION_MOVE -> {
                        // File/directory move
                        val sourcePath = action.sourcePath!!
                        val classInfo = classInfoMap[sourcePath]
                        if (classInfo != null) {
                            classInfoMap[targetNode.getFullPath()] = classInfo
                        } else {
                            classInfoMap[targetNode.getFullPath()] = ClassInfo(null, false, targetNode.isDirectory())
                        }
                        classInfoMap.remove(sourcePath)
                    }
                }
            }
        }
        // Build dex
        val paths = ArrayList(classInfoMap.keys)
        Collections.sort(paths)
        val classDefList = ArrayList<ClassDef>(paths.size)
        for (path in paths) {
            val className = path.substring(1)
            val classInfo = classInfoMap[path]!!
            if (classInfo.directory) {
                // Skip all directories
                continue
            }
            if (classInfo.cachedFile != null) {
                // The class was modified
                try {
                    classDefList.add(DexUtils.toClassDef(classInfo.cachedFile, getApiLevel()))
                } catch (e: RecognitionException) {
                    throw IOException(e)
                }
                continue
            }
            if (classInfo.physical) {
                try {
                    classDefList.add(mDexClasses!!.getClassDef(className))
                } catch (e: ClassNotFoundException) {
                    throw IOException(e)
                }
            }
            // Skip other non-physical classes because they were never modified and will fail
        }
        DexUtils.storeDex(classDefList, FileDataStore(file), getApiLevel())
        return file
    }

    override fun getNode(path: String): Node<*>? {
        checkMounted()
        var targetNode = mCache[path]
        if (targetNode == null) {
            targetNode = if (path == File.separator) {
                mRootNode
            } else {
                mRootNode!!.getLastChild(Paths.sanitize(path, true))
            }
            if (targetNode != null) {
                mCache.put(path, targetNode)
            }
        }
        return targetNode
    }

    override fun invalidate(path: String) {
        mCache.remove(path)
    }

    override fun lastModified(path: Node<*>): Long {
        return getFile().lastModified()
    }

    override fun length(path: String): Long {
        val targetNode = getNode(path) ?: return -1
        if (targetNode.isDirectory()) {
            return 0L
        }
        if (!targetNode.isFile() || targetNode.getObject() == null) {
            return -1
        }
        return try {
            mDexClasses!!
                .getClassContents(targetNode.getObject() as ClassDef)
                .toByteArray(StandardCharsets.UTF_8)
                .size.toLong()
        } catch (e: ClassNotFoundException) {
            -1
        }
    }

    override fun checkAccess(path: String, access: Int): Boolean {
        val targetNode = getNode(path)
        if (access == OsConstants.F_OK) {
            return targetNode != null
        }
        var canAccess = true
        if (access and OsConstants.R_OK != 0) {
            canAccess = canAccess and true
        }
        if (access and OsConstants.W_OK != 0) {
            canAccess = canAccess and false
        }
        if (access and OsConstants.X_OK != 0) {
            canAccess = canAccess and false
        }
        return canAccess
    }

    @Throws(IOException::class)
    override fun getInputStream(node: Node<*>): InputStream {
        val classDef = node.getObject() as ClassDef? ?: throw FileNotFoundException("Class definition for ${node.getFullPath()} is not found.")
        return try {
            ByteArrayInputStream(getDexClasses().getClassContents(classDef).toByteArray(StandardCharsets.UTF_8))
        } catch (e: ClassNotFoundException) {
            throw IOException(e)
        }
    }

    @Throws(IOException::class)
    override fun cacheFile(src: Node<*>, sink: File) {
        val classDef = src.getObject() as ClassDef? ?: throw FileNotFoundException("Class definition for ${src.getFullPath()} is not found.")
        try {
            FileOutputStream(sink).use { os ->
                os.write(getDexClasses().getClassContents(classDef).toByteArray(StandardCharsets.UTF_8))
            }
        } catch (e: ClassNotFoundException) {
            throw IOException(e)
        }
    }
}
