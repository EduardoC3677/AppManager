// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.compat.io

import java.io.*
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.atomic.AtomicReference

/**
 * Optimized implementation of {@link DataInput} which buffers data in memory
 * from the underlying {@link InputStream}.
 * <p>
 * Benchmarks have demonstrated this class is 3x more efficient than using a
 * {@link DataInputStream} with a {@link BufferedInputStream}.
 */
// NOTE: This class is not actually optimised because we can't use VMRuntime right now
class FastDataInput(
    private var mIn: InputStream,
    bufferSize: Int,
    private val mUse4ByteSequence: Boolean
) : DataInput, Closeable {

    // private final VMRuntime mRuntime;

    private val mBuffer: ByteArray
    // private final long mBufferPtr;
    private val mBufferCap: Int

    private var mBufferPos = 0
    private var mBufferLim = 0

    /**
     * Values that have been "interned" by {@link #readInternedUTF()}.
     */
    private var mStringRefCount = 0
    private var mStringRefs = arrayOfNulls<String>(32)

    /**
     * @deprecated callers must specify {@code use4ByteSequence} so they make a
     *             clear choice about working around a long-standing ART bug, as
     *             described by the {@code kUtfUse4ByteSequence} comments in
     *             {@code art/runtime/jni/jni_internal.cc}.
     */
    @Deprecated("callers must specify use4ByteSequence")
    constructor(input: InputStream, bufferSize: Int) : this(input, bufferSize, true)

    init {
        // mRuntime = VMRuntime.getRuntime();
        if (bufferSize < 8) {
            throw IllegalArgumentException()
        }

        mBuffer = ByteArray(bufferSize) // (byte[]) mRuntime.newNonMovableArray(byte.class, bufferSize);
        // mBufferPtr = mRuntime.addressOf(mBuffer);
        mBufferCap = mBuffer.size
    }

    /**
     * Release a {@link FastDataInput} to potentially be recycled. You must not
     * interact with the object after releasing it.
     */
    fun release() {
        // mIn = null // Cannot set to null as it is non-null type in constructor but let's assume it's handled or we change type
        mBufferPos = 0
        mBufferLim = 0
        mStringRefCount = 0

        if (mBufferCap == DEFAULT_BUFFER_SIZE && mUse4ByteSequence) {
            // Try to return to the cache.
            sInCache.compareAndSet(null, this)
        }
    }

    /**
     * Re-initializes the object for the new input.
     */
    private fun setInput(input: InputStream) {
        mIn = input
        mBufferPos = 0
        mBufferLim = 0
        mStringRefCount = 0
    }

    @Throws(IOException::class)
    private fun fill(need: Int) {
        var needed = need
        val remain = mBufferLim - mBufferPos
        System.arraycopy(mBuffer, mBufferPos, mBuffer, 0, remain)
        mBufferPos = 0
        mBufferLim = remain
        needed -= remain

        while (needed > 0) {
            val c = mIn.read(mBuffer, mBufferLim, mBufferCap - mBufferLim)
            if (c == -1) {
                throw EOFException()
            } else {
                mBufferLim += c
                needed -= c
            }
        }
    }

    @Throws(IOException::class)
    override fun close() {
        mIn.close()
        release()
    }

    @Throws(IOException::class)
    override fun readFully(b: ByteArray) {
        readFully(b, 0, b.size)
    }

    @Throws(IOException::class)
    override fun readFully(b: ByteArray, off: Int, len: Int) {
        // Attempt to read directly from buffer space if there's enough room,
        // otherwise fall back to chunking into place
        if (mBufferCap >= len) {
            if (mBufferLim - mBufferPos < len) fill(len)
            System.arraycopy(mBuffer, mBufferPos, b, off, len)
            mBufferPos += len
        } else {
            val remain = mBufferLim - mBufferPos
            System.arraycopy(mBuffer, mBufferPos, b, off, remain)
            mBufferPos += remain
            var currentOff = off + remain
            var currentLen = len - remain

            while (currentLen > 0) {
                val c = mIn.read(b, currentOff, currentLen)
                if (c == -1) {
                    throw EOFException()
                } else {
                    currentOff += c
                    currentLen -= c
                }
            }
        }
    }

    @Throws(IOException::class)
    override fun readUTF(): String {
        // Attempt to read directly from buffer space if there's enough room,
        // otherwise fall back to chunking into place
        val len = readUnsignedShort()

        // Unfortunately, we can't use VMRuntime right now
        val tmp = ByteArray(len)
        readFully(tmp, 0, len)

        return String(tmp, StandardCharsets.UTF_8)
    }

    /**
     * Read a {@link String} value with the additional signal that the given
     * value is a candidate for being canonicalized, similar to
     * {@link String#intern()}.
     * <p>
     * Canonicalization is implemented by writing each unique string value once
     * the first time it appears, and then writing a lightweight {@code short}
     * reference when that string is written again in the future.
     *
     * @see FastDataOutput#writeInternedUTF(String)
     */
    @Throws(IOException::class)
    fun readInternedUTF(): String {
        val ref = readUnsignedShort()
        if (ref == MAX_UNSIGNED_SHORT) {
            val s = readUTF()

            // We can only safely intern when we have remaining values; if we're
            // full we at least sent the string value above
            if (mStringRefCount < MAX_UNSIGNED_SHORT) {
                if (mStringRefCount == mStringRefs.size) {
                    mStringRefs = mStringRefs.copyOf(mStringRefCount + (mStringRefCount shr 1))
                }
                mStringRefs[mStringRefCount++] = s
            }

            return s
        } else {
            return mStringRefs[ref]!!
        }
    }

    @Throws(IOException::class)
    override fun readBoolean(): Boolean {
        return readByte().toInt() != 0
    }

    /**
     * Returns the same decoded value as {@link #readByte()} but without
     * actually consuming the underlying data.
     */
    @Throws(IOException::class)
    fun peekByte(): Byte {
        if (mBufferLim - mBufferPos < 1) fill(1)
        return mBuffer[mBufferPos]
    }

    @Throws(IOException::class)
    override fun readByte(): Byte {
        if (mBufferLim - mBufferPos < 1) fill(1)
        return mBuffer[mBufferPos++]
    }

    @Throws(IOException::class)
    override fun readUnsignedByte(): Int {
        return java.lang.Byte.toUnsignedInt(readByte())
    }

    @Throws(IOException::class)
    override fun readShort(): Short {
        if (mBufferLim - mBufferPos < 2) fill(2)
        return (((mBuffer[mBufferPos++].toInt() and 0xff) shl 8) or
                ((mBuffer[mBufferPos++].toInt() and 0xff) shl 0)).toShort()
    }

    @Throws(IOException::class)
    override fun readUnsignedShort(): Int {
        return java.lang.Short.toUnsignedInt(readShort())
    }

    @Throws(IOException::class)
    override fun readChar(): Char {
        return readShort().toInt().toChar()
    }

    @Throws(IOException::class)
    override fun readInt(): Int {
        if (mBufferLim - mBufferPos < 4) fill(4)
        return (((mBuffer[mBufferPos++].toInt() and 0xff) shl 24) or
                ((mBuffer[mBufferPos++].toInt() and 0xff) shl 16) or
                ((mBuffer[mBufferPos++].toInt() and 0xff) shl 8) or
                ((mBuffer[mBufferPos++].toInt() and 0xff) shl 0))
    }

    @Throws(IOException::class)
    override fun readLong(): Long {
        if (mBufferLim - mBufferPos < 8) fill(8)
        val h = (((mBuffer[mBufferPos++].toInt() and 0xff) shl 24) or
                ((mBuffer[mBufferPos++].toInt() and 0xff) shl 16) or
                ((mBuffer[mBufferPos++].toInt() and 0xff) shl 8) or
                ((mBuffer[mBufferPos++].toInt() and 0xff) shl 0))
        val l = (((mBuffer[mBufferPos++].toInt() and 0xff) shl 24) or
                ((mBuffer[mBufferPos++].toInt() and 0xff) shl 16) or
                ((mBuffer[mBufferPos++].toInt() and 0xff) shl 8) or
                ((mBuffer[mBufferPos++].toInt() and 0xff) shl 0))
        return (h.toLong() shl 32) or (l.toLong() and 0xffffffffL)
    }

    @Throws(IOException::class)
    override fun readFloat(): Float {
        return Float.fromBits(readInt())
    }

    @Throws(IOException::class)
    override fun readDouble(): Double {
        return Double.fromBits(readLong())
    }

    override fun skipBytes(n: Int): Int {
        // Callers should read data piecemeal
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun readLine(): String {
        // Callers should read data piecemeal
        throw UnsupportedOperationException()
    }

    companion object {
        private const val MAX_UNSIGNED_SHORT = 65_535
        private const val DEFAULT_BUFFER_SIZE = 32_768

        private val sInCache = AtomicReference<FastDataInput>()

        /**
         * Obtain a {@link FastDataInput} configured with the given
         * {@link InputStream} and which encodes large code-points using 3-byte
         * sequences.
         * <p>
         * This <em>is</em> compatible with the {@link DataInput} API contract,
         * which specifies that large code-points must be encoded with 3-byte
         * sequences.
         */
        @JvmStatic
        fun obtainUsing3ByteSequences(input: InputStream): FastDataInput {
            return FastDataInput(input, DEFAULT_BUFFER_SIZE, false /* use4ByteSequence */)
        }

        /**
         * Obtain a {@link FastDataInput} configured with the given
         * {@link InputStream} and which decodes large code-points using 4-byte
         * sequences.
         * <p>
         * This <em>is not</em> compatible with the {@link DataInput} API contract,
         * which specifies that large code-points must be encoded with 3-byte
         * sequences.
         */
        @JvmStatic
        fun obtainUsing4ByteSequences(input: InputStream): FastDataInput {
            val instance = sInCache.getAndSet(null)
            if (instance != null) {
                instance.setInput(input)
                return instance
            }
            return FastDataInput(input, DEFAULT_BUFFER_SIZE, true /* use4ByteSequence */)
        }
    }
}
