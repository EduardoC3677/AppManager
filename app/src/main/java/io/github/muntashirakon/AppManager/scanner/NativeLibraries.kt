// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner

import android.content.Context
import android.text.format.Formatter
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import aosp.libcore.util.HexEncoding
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.utils.LangUtils
import io.github.muntashirakon.util.LocalizedString
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

object NativeLibraries {
    val TAG: String = NativeLibraries::class.java.simpleName

    private const val ELF_MAGIC = 0x7f454c46 // 0x7f ELF

    abstract class NativeLib(
        private val mPath: String,
        private val mSize: Long,
        private val mMagic: ByteArray
    ) : LocalizedString {
        private val mName: String = File(mPath).name

        fun getPath(): String = mPath
        fun getName(): String = mName
        fun getSize(): Long = mSize
        fun getMagic(): ByteArray = mMagic

        companion object {
            @JvmStatic
            @Throws(IOException::class)
            fun parse(path: String, size: Long, `is`: InputStream): NativeLib {
                val header = ByteArray(20) // First 20 bytes is enough
                `is`.read(header)
                val buffer = ByteBuffer.wrap(header)
                val magic = buffer.int
                if (magic != ELF_MAGIC) {
                    Log.w(TAG, "Invalid header magic 0x%x at path %s", magic, path)
                    return InvalidLib(path, size, header)
                }
                val elfLib = ElfLib(path, size)
                elfLib.mArch = buffer.get() // EI_CLASS
                elfLib.mEndianness = buffer.get() // EI_DATA
                if (elfLib.mEndianness == ElfLib.ENDIANNESS_LITTLE_ENDIAN) {
                    buffer.order(ByteOrder.LITTLE_ENDIAN)
                }
                buffer.position(16)
                elfLib.mType = buffer.char // e_type
                elfLib.mIsa = buffer.char // e_machine
                return elfLib
            }
        }
    }

    class InvalidLib(path: String, size: Long, magic: ByteArray) : NativeLib(path, size, magic) {
        override fun toLocalizedString(context: Context): CharSequence {
            val sb = StringBuilder()
            if (getSize() != -1L) {
                sb.append(Formatter.formatFileSize(context, getSize())).append(", ")
            }
            sb.append("Magic")
                .append(LangUtils.getSeparatorString())
                .append(HexEncoding.encodeToString(getMagic()))
                .append("\n")
                .append(getPath())
            return sb
        }

        override fun toString(): String {
            return "InvalidLib(mPath='${getPath()}', mSize=${getSize()})"\n}
    }

    class ElfLib(path: String, size: Long) : NativeLib(path, size, ByteArray(0)) {
        var mArch: Byte = 0
        var mEndianness: Byte = 0
        var mType: Char = 0.toChar()
        var mIsa: Char = 0.toChar()

        fun isSharedObject(): Boolean = mType == TYPE_SHARED

        @AnyThread
        override fun toLocalizedString(context: Context): CharSequence {
            val sb = StringBuilder()
            if (getSize() != -1L) {
                sb.append(Formatter.formatFileSize(context, getSize())).append(", ")
            }
            sb.append(getArchName(mArch)).append(", ")
                .append(getEndiannessName(mEndianness)).append(", ")
                .append(getIsaName(mIsa)).append(", ")
                .append(getTypeName(mType))
                .append("\n")
                .append(getPath())
            return sb
        }

        override fun toString(): String {
            return "ElfLib(mPath='${getPath()}', mSize=${getSize()}, mArch=$mArch, mEndianness=$mEndianness, mType=$mType, mIsa=$mIsa)"\n}

        companion object {
            const val ARCH_NONE: Byte = 0
            const val ARCH_32BIT: Byte = 1
            const val ARCH_64BIT: Byte = 2

            const val ENDIANNESS_NONE: Byte = 0
            const val ENDIANNESS_LITTLE_ENDIAN: Byte = 1
            const val ENDIANNESS_BIG_ENDIAN: Byte = 2

            const val TYPE_NONE: Char = 0.toChar()
            const val TYPE_RELOCATABLE: Char = 1.toChar()
            const val TYPE_EXECUTABLE: Char = 2.toChar()
            const val TYPE_SHARED: Char = 3.toChar()
            const val TYPE_CORE: Char = 4.toChar()

            const val ISA_NONE: Char = 0.toChar()
            const val ISA_ARM: Char = 0x28.toChar()
            const val ISA_X86: Char = 0x03.toChar()
            const val ISA_MIPS: Char = 0x08.toChar()
            const val ISA_ARM64: Char = 0xB7.toChar()
            const val ISA_X86_64: Char = 0x3E.toChar()
            const val ISA_MIPS64: Char = 0x08.toChar()

            @AnyThread
            fun getArchName(arch: Byte): String {
                return when (arch) {
                    ARCH_NONE -> "None"\nARCH_32BIT -> "32-bit"\nARCH_64BIT -> "64-bit"\nelse -> "Unknown"\n}
            }

            @AnyThread
            fun getEndiannessName(endianness: Byte): String {
                return when (endianness) {
                    ENDIANNESS_NONE -> "None"\nENDIANNESS_LITTLE_ENDIAN -> "Little-endian"\nENDIANNESS_BIG_ENDIAN -> "Big-endian"\nelse -> "Unknown"\n}
            }

            @AnyThread
            fun getTypeName(type: Char): String {
                return when (type) {
                    TYPE_NONE -> "None"\nTYPE_RELOCATABLE -> "Relocatable"\nTYPE_EXECUTABLE -> "Executable"\nTYPE_SHARED -> "Shared"\nTYPE_CORE -> "Core"\nelse -> "Unknown"\n}
            }

            @AnyThread
            fun getIsaName(isa: Char): String {
                return when (isa) {
                    ISA_NONE -> "None"\nISA_ARM -> "ARM"\nISA_X86 -> "X86"\nISA_MIPS -> "MIPS"\nISA_ARM64 -> "AArch64"\nISA_X86_64 -> "X86-64"\nISA_MIPS64 -> "MIPS-64"\nelse -> "Unknown"\n}
            }
        }
    }

    @WorkerThread
    @JvmStatic
    fun getNativeLibs(apkPath: File): List<NativeLib> {
        val nativeLibs = mutableListOf<NativeLib>()
        try {
            ZipFile(apkPath).use { zipFile ->
                val entries = zipFile.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue

                    val entryName = entry.name
                    if (entryName.startsWith("lib/") && entryName.endsWith(".so")) {
                        zipFile.getInputStream(entry).use { inputStream ->
                            val nativeLib = NativeLib.parse(entryName, entry.size, inputStream)
                            nativeLibs.add(nativeLib)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, e)
        }
        return nativeLibs
    }

    @WorkerThread
    @JvmStatic
    fun getDexClasses(apkPath: File): Set<String> {
        val classes = HashSet<String>()
        try {
            ZipFile(apkPath).use { zipFile ->
                val entries = zipFile.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue

                    val entryName = entry.name
                    if (entryName.endsWith(".dex")) {
                        zipFile.getInputStream(entry).use { inputStream ->
                            ZipInputStream(inputStream).use { zipInputStream ->
                                var zipEntry: ZipEntry?
                                while (zipInputStream.nextEntry.also { zipEntry = it } != null) {
                                    if (zipEntry!!.isDirectory) continue
                                    val name = zipEntry!!.name
                                    if (name.endsWith(".class")) {
                                        classes.add(name.replace('/', '.').removeSuffix(".class"))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, e)
        }
        return classes
    }

    @WorkerThread
    @JvmStatic
    fun getClasses(apkPath: File): Set<String> {
        val classes = HashSet<String>()
        try {
            ZipFile(apkPath).use { zipFile ->
                val entries = zipFile.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue

                    val entryName = entry.name
                    if (entryName.endsWith(".class")) {
                        classes.add(entryName.replace('/', '.').removeSuffix(".class"))
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, e)
        }
        return classes
    }
}
